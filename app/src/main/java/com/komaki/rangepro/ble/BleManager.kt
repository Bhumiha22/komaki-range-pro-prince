package com.komaki.rangepro.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.komaki.rangepro.bms.BleUuids
import com.komaki.rangepro.bms.BmsDecoder
import com.komaki.rangepro.model.BleStatus
import com.komaki.rangepro.model.ConnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Single-connection GATT client with reconnect/backoff.
 *
 * Safety: the only things this class ever writes are
 *  1. the standard CCCD descriptor that turns on notifications for the telemetry characteristic, and
 *  2. request frames supplied by a *verified* [BmsDecoder]. With the default decoder there are none.
 * It never sends anything to the BMS that it was not explicitly given by a verified decoder.
 */
@SuppressLint("MissingPermission")
class BleManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val decoder: BmsDecoder,
) {
    private val _status = MutableStateFlow(BleStatus())
    val status: StateFlow<BleStatus> = _status.asStateFlow()

    private val _frames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 128)
    val frames: SharedFlow<ByteArray> = _frames.asSharedFlow()

    private val _services = MutableStateFlow<List<String>>(emptyList())
    val services: StateFlow<List<String>> = _services.asStateFlow()

    val log = RawLog()

    private val lock = Any()
    private var gatt: BluetoothGatt? = null
    private var wantConnected = false
    private var mac = ""
    private var attempt = 0
    private var retryJob: Job? = null
    private var pollJob: Job? = null

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun set(state: ConnState, detail: String = "") {
        _status.value = BleStatus(state, detail, attempt)
        log.add("STATE $state ${detail.ifEmpty { "" }}".trim())
    }

    /** Start (or keep) a connection to [address]. Never opens a second GATT while one exists. */
    fun connect(address: String) {
        synchronized(lock) {
            if (!hasConnectPermission()) { set(ConnState.NO_PERMISSION, "Bluetooth permission not granted"); return }
            if (!BluetoothAdapter.checkBluetoothAddress(address.uppercase())) { set(ConnState.ERROR, "Invalid MAC address"); return }
            mac = address.uppercase()
            wantConnected = true
            attempt = 0
            retryJob?.cancel()
            if (gatt != null) return
            openGatt()
        }
    }

    fun disconnect() {
        synchronized(lock) {
            wantConnected = false
            retryJob?.cancel()
            pollJob?.cancel()
            gatt?.let { runCatching { it.disconnect() }; runCatching { it.close() } }
            gatt = null
            attempt = 0
            set(ConnState.IDLE, "Disconnected by user")
        }
    }

    private fun openGatt() {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            set(ConnState.BLUETOOTH_OFF, "Bluetooth is off")
            scheduleRetry()
            return
        }
        set(ConnState.CONNECTING, mac)
        val device = adapter.getRemoteDevice(mac)
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun scheduleRetry() {
        synchronized(lock) {
            if (!wantConnected) return
            attempt++
            if (attempt > MAX_ATTEMPTS) {
                wantConnected = false
                set(ConnState.GAVE_UP, "Gave up after $MAX_ATTEMPTS attempts. Is the scooter on and PackDial closed?")
                return
            }
            val wait = minOf(60_000L, 2_000L shl (attempt - 1))
            set(ConnState.WAITING_RETRY, "Retry $attempt/$MAX_ATTEMPTS in ${wait / 1000}s")
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(wait)
                synchronized(lock) { if (wantConnected && gatt == null) openGatt() }
            }
        }
    }

    private fun handleDisconnect(g: BluetoothGatt, status: Int) {
        synchronized(lock) {
            pollJob?.cancel()
            runCatching { g.close() }
            if (gatt === g) gatt = null
            log.add("GATT closed (status=$status)")
            if (wantConnected) scheduleRetry() else if (_status.value.state != ConnState.GAVE_UP) set(ConnState.IDLE)
        }
    }

    /** Read a characteristic (read-only, safe). Result appears in the raw log. */
    fun readCharacteristic(uuid: UUID) {
        val g = gatt ?: run { log.add("READ skipped: not connected"); return }
        val ch = g.services.firstNotNullOfOrNull { it.getCharacteristic(uuid) }
        if (ch == null) { log.add("READ $uuid: characteristic not found"); return }
        if (!g.readCharacteristic(ch)) log.add("READ $uuid: request rejected")
    }

    private fun findCharacteristic(g: BluetoothGatt, uuid: UUID): BluetoothGattCharacteristic? =
        g.services.firstNotNullOfOrNull { it.getCharacteristic(uuid) }

    private fun enableNotifications(g: BluetoothGatt) {
        val ch = findCharacteristic(g, decoder.notifyCharacteristic)
        if (ch == null) { set(ConnState.ERROR, "Telemetry characteristic ${decoder.notifyCharacteristic} not found"); return }
        g.setCharacteristicNotification(ch, true)
        val cccd = ch.getDescriptor(BleUuids.CCCD)
        if (cccd == null) { set(ConnState.ERROR, "No CCCD on telemetry characteristic"); return }
        val value = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(cccd, value)
        } else {
            @Suppress("DEPRECATION") run { cccd.value = value; g.writeDescriptor(cccd) }
        }
    }

    private fun startPollingIfVerified(g: BluetoothGatt) {
        pollJob?.cancel()
        val writeUuid = decoder.writeCharacteristic
        if (!decoder.isVerified || writeUuid == null || decoder.pollRequests.isEmpty()) {
            log.add("No verified request frames: app is listening only, sending nothing")
            return
        }
        val ch = findCharacteristic(g, writeUuid) ?: run { log.add("Write characteristic $writeUuid not found"); return }
        pollJob = scope.launch {
            while (isActive) {
                for (req in decoder.pollRequests) {
                    write(g, ch, req)
                    delay(decoder.pollGapMs)
                }
                delay(decoder.pollIntervalMs)
            }
        }
    }

    private fun write(g: BluetoothGatt, ch: BluetoothGattCharacteristic, data: ByteArray) {
        log.add("TX ${ch.uuid} ${RawLog.hex(data)}")
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(ch, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION") run {
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                ch.value = data
                g.writeCharacteristic(ch)
            }
        }
    }

    private fun onRx(uuid: UUID, value: ByteArray) {
        log.add("RX $uuid ${RawLog.hex(value)}")
        _frames.tryEmit(value)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                set(ConnState.DISCOVERING)
                g.discoverServices()
            } else {
                handleDisconnect(g, status)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val lines = mutableListOf<String>()
            g.services.forEach { s ->
                lines += "Service ${s.uuid}"
                s.characteristics.forEach { c -> lines += "   char ${c.uuid} props=0x${"%02X".format(c.properties)}" }
            }
            _services.value = lines
            if (status != BluetoothGatt.GATT_SUCCESS) { set(ConnState.ERROR, "Service discovery failed ($status)"); return }
            enableNotifications(g)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                attempt = 0
                set(ConnState.CONNECTED, "Notifications on")
                startPollingIfVerified(g)
            } else {
                set(ConnState.ERROR, "Enabling notifications failed ($status)")
            }
        }

        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            c.value?.let { onRx(c.uuid, it) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            onRx(c.uuid, value)
        }

        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            log.add("READ ${c.uuid} status=$status ${c.value?.let { RawLog.hex(it) } ?: ""}")
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            log.add("READ ${c.uuid} status=$status ${RawLog.hex(value)}")
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 8
    }
}

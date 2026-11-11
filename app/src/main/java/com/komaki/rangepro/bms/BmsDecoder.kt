package com.komaki.rangepro.bms

import com.komaki.rangepro.model.BatterySample
import java.util.UUID

/**
 * The ONLY place that knows the BMS wire protocol.
 *
 * The Komaki DB20SA02L20S40A protocol is currently UNKNOWN. Nothing here may be filled in from
 * guesses: request frames, checksums and field offsets must come from a captured PackDial session
 * and be checked against PackDial's on-screen values (see docs/PROTOCOL.md).
 */
interface BmsDecoder {
    val name: String

    /** True only once every field this decoder emits was verified against real packets. */
    val isVerified: Boolean

    /** Characteristic that carries telemetry notifications (known from nRF Connect: FFF1). */
    val notifyCharacteristic: UUID

    /** Characteristic for requests. Null = the app never writes. */
    val writeCharacteristic: UUID?

    /** Request frames PackDial was observed sending. Empty = the app sends nothing. */
    val pollRequests: List<ByteArray>
    val pollIntervalMs: Long
    val pollGapMs: Long

    /** Feed one notification payload; return zero or more fully decoded samples. */
    fun feed(chunk: ByteArray, nowMs: Long): List<BatterySample>
}

object BleUuids {
    val FFF0: UUID = UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB")
    val FFF1: UUID = UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB")
    val FFF2: UUID = UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
}

/** Placeholder until the protocol is proven. Subscribes and logs raw bytes; decodes nothing; writes nothing. */
object UnknownProtocolDecoder : BmsDecoder {
    override val name = "Unknown protocol (raw logging only)"
    override val isVerified = false
    override val notifyCharacteristic: UUID = BleUuids.FFF1
    override val writeCharacteristic: UUID? = null
    override val pollRequests: List<ByteArray> = emptyList()
    override val pollIntervalMs = 0L
    override val pollGapMs = 0L
    override fun feed(chunk: ByteArray, nowMs: Long): List<BatterySample> = emptyList()
}

object BmsDecoders {
    /** Swap this for a verified decoder once docs/PROTOCOL.md is filled in with packet evidence. */
    val active: BmsDecoder = UnknownProtocolDecoder
}

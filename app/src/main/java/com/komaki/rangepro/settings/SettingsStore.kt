package com.komaki.rangepro.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small SharedPreferences-backed settings. All data stays on the device. */
class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _mac = MutableStateFlow(sp.getString("mac", DEFAULT_MAC) ?: DEFAULT_MAC)
    val deviceMac: StateFlow<String> = _mac.asStateFlow()

    private val _demo = MutableStateFlow(sp.getBoolean("demo", false))
    val demoMode: StateFlow<Boolean> = _demo.asStateFlow()

    private val _passenger = MutableStateFlow(sp.getBoolean("passenger", false))
    val passenger: StateFlow<Boolean> = _passenger.asStateFlow()

    private val _storeRoute = MutableStateFlow(sp.getBoolean("storeRoute", true))
    val storeRoute: StateFlow<Boolean> = _storeRoute.asStateFlow()

    private val _verifiedAh = MutableStateFlow(sp.getFloat("verifiedAh", 0f).takeIf { it > 0f })
    val verifiedCapacityAh: StateFlow<Float?> = _verifiedAh.asStateFlow()

    fun setMac(v: String) { _mac.value = v.trim().uppercase(); sp.edit().putString("mac", _mac.value).apply() }
    fun setDemo(v: Boolean) { _demo.value = v; sp.edit().putBoolean("demo", v).apply() }
    fun setPassenger(v: Boolean) { _passenger.value = v; sp.edit().putBoolean("passenger", v).apply() }
    fun setStoreRoute(v: Boolean) { _storeRoute.value = v; sp.edit().putBoolean("storeRoute", v).apply() }
    fun setVerifiedCapacity(v: Float?) {
        _verifiedAh.value = v?.takeIf { it > 0f }
        sp.edit().putFloat("verifiedAh", _verifiedAh.value ?: 0f).apply()
    }

    companion object {
        const val DEFAULT_MAC = "A5:C2:3A:4C:1D:57"
    }
}

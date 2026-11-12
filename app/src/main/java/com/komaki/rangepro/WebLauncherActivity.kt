package com.komaki.rangepro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/** Opens the hosted web app (Web Bluetooth needs Chrome; an in-app WebView cannot do BLE). */
class WebLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = Uri.parse("https://bhumiha22.github.io/komaki-range-pro-prince/")
        val chrome = Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.chrome")
        try { startActivity(chrome) } catch (e: Exception) { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        finish()
    }
}

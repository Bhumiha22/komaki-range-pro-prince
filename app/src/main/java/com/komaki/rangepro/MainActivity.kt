package com.komaki.rangepro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.komaki.rangepro.ui.AppRoot
import com.komaki.rangepro.ui.KomakiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KomakiApp).container
        setContent { KomakiTheme { AppRoot(container) } }
    }
}

package com.komaki.rangepro.gps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.komaki.rangepro.KomakiApp
import com.komaki.rangepro.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Foreground service that keeps GPS ride recording (and the app process) alive while a ride is active. */
class RideTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recorder = (application as KomakiApp).container.recorder
        when (intent?.action) {
            ACTION_STOP -> {
                recorder.stop()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                createChannel()
                val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
                ServiceCompat.startForeground(this, NOTIF_ID, notification("Starting ride…"), type)
                if (!recorder.start() && !recorder.live.value.active) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                scope.launch {
                    recorder.live.collect { live ->
                        if (!live.active) return@collect
                        val km = "%.2f".format(live.distanceM / 1000.0)
                        val text = if (live.hasFix) "$km km  •  ${"%.0f".format(live.speedMps * 3.6)} km/h" else "Waiting for GPS fix…"
                        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "Ride tracking", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RideTrackingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Komaki ride in progress")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Stop ride", stop)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.komaki.rangepro.STOP_RIDE"
        private const val CHANNEL = "ride"
        private const val NOTIF_ID = 42

        fun start(context: Context) {
            androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, RideTrackingService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RideTrackingService::class.java).setAction(ACTION_STOP))
        }
    }
}

package io.karoo.insta360.ble

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.karoo.insta360.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber

class Insta360BleService : Service() {

    companion object {
        const val ACTION_START     = "io.karoo.insta360.ACTION_START"
        const val ACTION_STOP      = "io.karoo.insta360.ACTION_STOP"
        const val ACTION_RECORD    = "io.karoo.insta360.ACTION_RECORD"
        const val ACTION_STOP_REC  = "io.karoo.insta360.ACTION_STOP_REC"
        const val ACTION_HIGHLIGHT = "io.karoo.insta360.ACTION_HIGHLIGHT"
        private const val NOTIF_CHANNEL = "insta360_ctrl"
        private const val NOTIF_ID = 1001
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    lateinit var bleManager: Insta360BleManager

    override fun onCreate() {
        super.onCreate()
        bleManager = Insta360BleManager(applicationContext)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Buscando cámara..."))
        observeCameraState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START     -> bleManager.startScan()
            ACTION_STOP      -> { bleManager.disconnect(); stopSelf() }
            ACTION_RECORD    -> bleManager.startRecording()
            ACTION_STOP_REC  -> bleManager.stopRecording()
            ACTION_HIGHLIGHT -> bleManager.markHighlight()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        bleManager.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeCameraState() {
        scope.launch {
            bleManager.connectionState.collectLatest { state ->
                val text = when (state) {
                    ConnectionState.DISCONNECTED -> "Desconectado"
                    ConnectionState.SCANNING     -> "Buscando GO Ultra..."
                    ConnectionState.CONNECTING   -> "Conectando..."
                    ConnectionState.CONNECTED    -> "GO Ultra conectada ✓"
                }
                updateNotification(text)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL, "Insta360 Control", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("Insta360 GO Ultra")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }
}

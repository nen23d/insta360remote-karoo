package io.karoo.insta360.ble

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.RideState
import io.karoo.insta360.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber

/**
 * Servicio foreground que:
 * 1. Mantiene la conexión BLE activa aunque el Karoo pase la app a background.
 * 2. Escucha el estado del ride (KarooSystemService) para auto-grabar.
 * 3. Envía datos GPS periódicamente a la cámara.
 */
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
    private lateinit var bleManager: Insta360BleManager
    private lateinit var karooSystem: KarooSystemService
    private var autoRecordEnabled = true
    private var wasRideActive = false

    override fun onCreate() {
        super.onCreate()
        bleManager = Insta360BleManager(applicationContext)
        karooSystem = KarooSystemService(applicationContext)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Buscando cámara..."))
        connectToKaroo()
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
        karooSystem.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── Karoo SDK: escuchar ride state ─────────────────────────────────────
    private fun connectToKaroo() {
        karooSystem.connect {
            Timber.d("Conectado a KarooSystem")
            karooSystem.addConsumer { rideState: RideState ->
                handleRideState(rideState)
            }
        }
    }

    private fun handleRideState(state: RideState) {
        val isActive = state is RideState.Recording
        if (isActive && !wasRideActive) {
            Timber.d("Ride iniciado — auto-grabación")
            if (autoRecordEnabled && bleManager.isConnected) {
                bleManager.startRecording()
            }
        } else if (!isActive && wasRideActive) {
            Timber.d("Ride finalizado — deteniendo grabación")
            if (bleManager.isConnected) {
                bleManager.stopRecording()
            }
        }
        wasRideActive = isActive
    }

    // ─── Observar estado de conexión y actualizar notificación ──────────────
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

        scope.launch {
            bleManager.cameraStatus.collectLatest { status ->
                if (status.isRecording) updateNotification("● REC activo | Bat: ${status.batteryLevel}%")
            }
        }
    }

    // ─── Notificación foreground ─────────────────────────────────────────────
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL,
            "Insta360 Control",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Control de cámara Insta360 GO Ultra"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, Insta360BleService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("Insta360 GO Ultra")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_delete, "Desconectar", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }
}

package io.karoo.insta360.extension

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.*
import io.karoo.insta360.R
import io.karoo.insta360.ble.ConnectionState
import io.karoo.insta360.ble.Insta360BleManager
import io.karoo.insta360.ble.Insta360BleService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber

/**
 * Campo de datos Karoo TÁCTIL para la Insta360 GO Ultra.
 *
 * — Muestra el estado de grabación en la página de datos del ride.
 * — TAP sobre el campo: toggle start/stop grabación directamente.
 *
 * Usa RemoteViews + setOnClickPendingIntent para que el toque
 * funcione aunque el campo se renderice en el proceso de Karoo OS.
 *
 * Layout (field_camera_status.xml):
 *   root_layout  → LinearLayout raíz, recibe el click
 *   tv_status    → "● REC" / "◉ LISTO" / "✕ SIN CAM"
 *   tv_battery   → "78%" o "--"
 */
class CameraStatusDataType : DataTypeImpl(
    "io.karoo.insta360",
    "camera_status"
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var bleManager: Insta360BleManager

    // ─── Stream numérico ────────────────────────────────────────────────────
    override fun startStream(emitter: Emitter<StreamState>) {
        bleManager = Insta360BleManager(applicationContext)
        scope.launch {
            bleManager.connectionState.collectLatest { connState ->
                when (connState) {
                    ConnectionState.DISCONNECTED,
                    ConnectionState.SCANNING,
                    ConnectionState.CONNECTING -> {
                        emitter.onNext(StreamState.Streaming(
                            DataPoint(dataTypeId, listOf(Field("recording", -1.0)))
                        ))
                    }
                    ConnectionState.CONNECTED -> {
                        bleManager.cameraStatus.collectLatest { camStatus ->
                            emitter.onNext(StreamState.Streaming(
                                DataPoint(dataTypeId, listOf(
                                    Field("recording", if (camStatus.isRecording) 1.0 else 0.0),
                                    Field("battery", camStatus.batteryLevel.toDouble())
                                ))
                            ))
                        }
                    }
                }
            }
        }
    }

    override fun stopStream() {
        scope.coroutineContext.cancelChildren()
    }

    // ─── Vista gráfica táctil ───────────────────────────────────────────────
    override fun startView(context: Context, config: FieldConfig, emitter: Emitter<UpdateGraphicConfig>) {
        if (!::bleManager.isInitialized) bleManager = Insta360BleManager(context.applicationContext)

        scope.launch {
            bleManager.connectionState.collectLatest { connState ->
                if (connState == ConnectionState.CONNECTED) {
                    bleManager.cameraStatus.collectLatest { camStatus ->
                        emitter.onNext(UpdateGraphicConfig(
                            remoteViews = buildRemoteViews(context, camStatus.isRecording, camStatus.batteryLevel, true)
                        ))
                    }
                } else {
                    emitter.onNext(UpdateGraphicConfig(
                        remoteViews = buildRemoteViews(context, false, -1, false)
                    ))
                }
            }
        }
    }

    override fun stopView() {
        scope.coroutineContext.cancelChildren()
    }

    // ─── RemoteViews con PendingIntent táctil ───────────────────────────────
    private fun buildRemoteViews(
        context: Context,
        isRecording: Boolean,
        battery: Int,
        connected: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.field_camera_status)

        // Texto de estado
        views.setTextViewText(R.id.tv_status, when {
            !connected  -> "✕ SIN CAM"
            isRecording -> "● REC"
            else        -> "◉ LISTO"
        })

        // Color de fondo según estado
        views.setInt(R.id.root_layout, "setBackgroundColor", when {
            !connected  -> 0xFF1A1A1A.toInt()
            isRecording -> 0xFF3A0000.toInt()   // rojo oscuro cuando graba
            else        -> 0xFF003A1A.toInt()   // verde oscuro cuando listo
        })

        // Batería
        views.setTextViewText(R.id.tv_battery, if (battery >= 0) "$battery%" else "--")

        // PendingIntent táctil: tap → toggle grabar/parar
        val tapAction = if (isRecording) Insta360BleService.ACTION_STOP_REC
                        else             Insta360BleService.ACTION_RECORD

        val pendingIntent = PendingIntent.getService(
            context,
            if (isRecording) 101 else 100,       // requestCode diferente para evitar cache
            Intent(context, Insta360BleService::class.java).apply { action = tapAction },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Todo el campo es táctil
        views.setOnClickPendingIntent(R.id.root_layout, pendingIntent)

        Timber.d("Vista actualizada: connected=$connected rec=$isRecording bat=$battery → tap=$tapAction")
        return views
    }
}

/**
 * Campo de datos Karoo: muestra el porcentaje de batería de la GO Ultra.
 * Campo numérico estándar (no gráfico).
 */
class CameraBatteryDataType : DataTypeImpl(
    "io.karoo.insta360",
    "camera_battery"
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var bleManager: Insta360BleManager

    override fun startStream(emitter: Emitter<StreamState>) {
        bleManager = Insta360BleManager(applicationContext)
        scope.launch {
            bleManager.cameraStatus.collectLatest { status ->
                if (status.batteryLevel >= 0) {
                    emitter.onNext(StreamState.Streaming(
                        DataPoint(dataTypeId, listOf(Field("battery", status.batteryLevel.toDouble())))
                    ))
                }
            }
        }
    }

    override fun stopStream() {
        scope.coroutineContext.cancelChildren()
    }
}

package io.karoo.insta360.extension

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.karoo.insta360.ble.ConnectionState
import io.karoo.insta360.ble.Insta360BleManager
import io.karoo.insta360.ble.Insta360BleService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber

class CameraStatusDataType(
    private val context: Context,
    private val bleManager: Insta360BleManager
) : DataTypeImpl("io.karoo.insta360", "camera_status") {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun startStream(emitter: Emitter<StreamState>) {
        scope.launch {
            bleManager.connectionState.collectLatest { connState ->
                when (connState) {
                    ConnectionState.DISCONNECTED,
                    ConnectionState.SCANNING,
                    ConnectionState.CONNECTING -> {
                        emitter.onNext(StreamState.Streaming(
                            DataPoint("camera_status", mapOf("recording" to -1.0))
                        ))
                    }
                    ConnectionState.CONNECTED -> {
                        bleManager.cameraStatus.collectLatest { camStatus ->
                            emitter.onNext(StreamState.Streaming(
                                DataPoint("camera_status", mapOf(
                                    "recording" to if (camStatus.isRecording) 1.0 else 0.0,
                                    "battery" to camStatus.batteryLevel.toDouble()
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
}

class CameraBatteryDataType(
    private val context: Context,
    private val bleManager: Insta360BleManager
) : DataTypeImpl("io.karoo.insta360", "camera_battery") {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun startStream(emitter: Emitter<StreamState>) {
        scope.launch {
            bleManager.cameraStatus.collectLatest { status ->
                if (status.batteryLevel >= 0) {
                    emitter.onNext(StreamState.Streaming(
                        DataPoint("camera_battery", mapOf(
                            "battery" to status.batteryLevel.toDouble()
                        ))
                    ))
                }
            }
        }
    }

    override fun stopStream() {
        scope.coroutineContext.cancelChildren()
    }
}

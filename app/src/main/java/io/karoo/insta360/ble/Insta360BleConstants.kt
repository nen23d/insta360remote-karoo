package io.karoo.insta360.ble

import java.util.UUID

object Insta360BleConstants {

    const val REMOTE_NAME = "Insta360 Mini Remote"

    // Servicio del mando real (el que viste en LightBlue)
    val SERVICE_UUID: UUID = UUID.fromString("0000CE80-0000-1000-8000-00805F9B34FB")
    val CHAR_WRITE: UUID   = UUID.fromString("0000CE81-0000-1000-8000-00805F9B34FB")
    val CHAR_NOTIFY: UUID  = UUID.fromString("0000CE82-0000-1000-8000-00805F9B34FB")
    val CHAR_INFO: UUID    = UUID.fromString("0000CE83-0000-1000-8000-00805F9B34FB")

    // Solo se anuncia (HID), no se aloja como servicio
    val HID_SERVICE_UUID: UUID = UUID.fromString("00001812-0000-1000-8000-00805F9B34FB")

    val DESCRIPTOR_CCC: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    object Commands {
        private fun frame(b7: Int, b8: Int) = byteArrayOf(
            0xfc.toByte(), 0xef.toByte(), 0xfe.toByte(), 0x86.toByte(),
            0x00, 0x03, 0x01, b7.toByte(), b8.toByte()
        )

        // VERIFICADO: capturado de tu Insta360 Mini Remote (botón de apagar)
        val POWER_OFF = frame(0x00, 0x01)

        // SIN VERIFICAR: copiados del repo del ESP32 (otro mando/cámara)
        val SHUTTER = frame(0x02, 0x00)
        val MODE    = frame(0x01, 0x00)
        val SCREEN  = frame(0x00, 0x00)
    }
}

enum class CameraMode { VIDEO, PHOTO, TIMELAPSE, UNKNOWN }

data class CameraStatus(
    val isRecording: Boolean,
    val batteryLevel: Int,
    val mode: CameraMode,
)

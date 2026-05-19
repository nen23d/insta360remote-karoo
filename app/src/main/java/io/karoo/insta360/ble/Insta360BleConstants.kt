package io.karoo.insta360.ble

import java.util.UUID

object Insta360BleConstants {

    const val REMOTE_NAME = "Insta360 GPS Remote"

    // UUIDs reales de la GO Ultra
    val SERVICE_UUID: UUID          = UUID.fromString("0000BE80-0000-1000-8000-00805F9B34FB")
    val CHAR_WRITE: UUID            = UUID.fromString("0000BE81-0000-1000-8000-00805F9B34FB")
    val CHAR_NOTIFY: UUID           = UUID.fromString("0000BE82-0000-1000-8000-00805F9B34FB")
    val SECONDARY_SERVICE_UUID: UUID = UUID.fromString("0000D0FF-3C17-D293-8E48-14FE2E4DA212")
    val DESCRIPTOR_CCC: UUID        = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    // Bytes específicos de tu cámara GO Ultra 1RWJRE
    val CAMERA_ID = byteArrayOf(0x31, 0x52, 0x57, 0x4a, 0x52, 0x45)

    object Commands {
        // Shutter — toggle graba/para
        val SHUTTER   = byteArrayOf(0xfc.toByte(), 0xef.toByte(), 0xfe.toByte(), 0x86.toByte(), 0x00, 0x03, 0x01, 0x02, 0x00)
        val MODE      = byteArrayOf(0xfc.toByte(), 0xef.toByte(), 0xfe.toByte(), 0x86.toByte(), 0x00, 0x03, 0x01, 0x01, 0x00)
        val SCREEN    = byteArrayOf(0xfc.toByte(), 0xef.toByte(), 0xfe.toByte(), 0x86.toByte(), 0x00, 0x03, 0x01, 0x00, 0x00)
        val POWER_OFF = byteArrayOf(0xfc.toByte(), 0xef.toByte(), 0xfe.toByte(), 0x86.toByte(), 0x00, 0x03, 0x01, 0x00, 0x03)
    }
}

enum class CameraMode { VIDEO, PHOTO, TIMELAPSE, UNKNOWN }

data class CameraStatus(
    val isRecording: Boolean,
    val batteryLevel: Int,
    val mode: CameraMode,
)

package io.karoo.insta360.ble

import java.util.UUID

object Insta360BleConstants {

    // ─── Servicios GATT reales de la GO Ultra ───────────────────────────────
    val SERVICE_CAMERA_CONTROL: UUID = UUID.fromString("0000BE80-0000-1000-8000-00805F9B34FB")
    val SERVICE_CAMERA_STATUS:  UUID = UUID.fromString("0000B000-0000-1000-8000-00805F9B34FB")

    // ─── Características reales ─────────────────────────────────────────────
    val CHAR_COMMAND_WRITE:  UUID = UUID.fromString("0000BE81-0000-1000-8000-00805F9B34FB")
    val CHAR_STATUS_NOTIFY:  UUID = UUID.fromString("0000BE82-0000-1000-8000-00805F9B34FB")
    val CHAR_BATTERY_READ:   UUID = UUID.fromString("0000B001-0000-1000-8000-00805F9B34FB")

    val DESCRIPTOR_CLIENT_CHAR_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    object Commands {
        val START_RECORDING = byteArrayOf(0x08, 0x01, 0x10, 0x01)
        val STOP_RECORDING  = byteArrayOf(0x08, 0x01, 0x10, 0x02)
        val TAKE_PHOTO      = byteArrayOf(0x08, 0x02, 0x10, 0x01)
        val MARK_HIGHLIGHT  = byteArrayOf(0x08, 0x03, 0x10, 0x01)
        val GET_STATUS      = byteArrayOf(0x08, 0x04)
        val MODE_VIDEO      = byteArrayOf(0x08, 0x05, 0x10, 0x01)
        val MODE_PHOTO      = byteArrayOf(0x08, 0x05, 0x10, 0x02)
        val MODE_TIMELAPSE  = byteArrayOf(0x08, 0x05, 0x10, 0x03)

        fun gpsData(lat: Double, lon: Double, speed: Float): ByteArray {
            val latInt = (lat * 1e7).toInt()
            val lonInt = (lon * 1e7).toInt()
            val speedCms = (speed * 100).toInt()
            return buildList {
                add(0x08.toByte()); addAll(encodeVarint(latInt.toLong()))
                add(0x10.toByte()); addAll(encodeVarint(lonInt.toLong()))
                add(0x18.toByte()); addAll(encodeVarint(speedCms.toLong()))
            }.toByteArray()
        }

        private fun encodeVarint(value: Long): List<Byte> {
            var v = value
            val result = mutableListOf<Byte>()
            while (v and 0x7FL.inv() != 0L) {
                result.add(((v and 0x7F) or 0x80).toByte())
                v = v ushr 7
            }
            result.add((v and 0x7F).toByte())
            return result
        }
    }

    object ResponseParser {
        fun parseStatus(bytes: ByteArray): CameraStatus {
            var isRecording = false
            var batteryLevel = -1
            var mode = CameraMode.UNKNOWN
            var i = 0
            while (i < bytes.size) {
                val tag = bytes[i].toInt() and 0xFF
                val fieldNumber = tag ushr 3
                i++
                if (i >= bytes.size) break
                when (fieldNumber) {
                    1 -> { isRecording = bytes[i].toInt() == 1; i++ }
                    2 -> { batteryLevel = bytes[i].toInt() and 0xFF; i++ }
                    3 -> {
                        mode = when (bytes[i].toInt()) {
                            1 -> CameraMode.VIDEO
                            2 -> CameraMode.PHOTO
                            3 -> CameraMode.TIMELAPSE
                            else -> CameraMode.UNKNOWN
                        }
                        i++
                    }
                    else -> i++
                }
            }
            return CameraStatus(isRecording, batteryLevel, mode)
        }
    }
}

enum class CameraMode { VIDEO, PHOTO, TIMELAPSE, UNKNOWN }

data class CameraStatus(
    val isRecording: Boolean,
    val batteryLevel: Int,
    val mode: CameraMode,
)

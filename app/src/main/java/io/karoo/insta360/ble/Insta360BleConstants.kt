package io.karoo.insta360.ble

import java.util.UUID

/**
 * Constantes del protocolo BLE de Insta360.
 *
 * Basado en ingeniería inversa documentada por la comunidad:
 * - https://medium.com/@patrickchwalek/ble-control-of-insta360-cameras
 * - https://hackaday.io/project/188975
 *
 * La cámara busca un periférico BLE que se anuncie como "Insta360 GPS Remote".
 * Una vez emparejado, acepta comandos protobuf sobre características GATT.
 */
object Insta360BleConstants {

    // ─── Nombre de anuncio BLE ──────────────────────────────────────────────
    /**
     * El nombre exacto que debe usar el dispositivo al anunciarse para que
     * la cámara lo reconozca como un mando GPS remoto.
     */
    const val REMOTE_NAME = "Insta360 GPS Remote"

    // ─── UUIDs de servicios GATT ────────────────────────────────────────────
    /** Servicio principal de control de la cámara */
    val SERVICE_CAMERA_CONTROL: UUID = UUID.fromString("0000AA01-0000-1000-8000-00805F9B34FB")

    /** Servicio de estado / notificaciones */
    val SERVICE_CAMERA_STATUS: UUID = UUID.fromString("0000AA02-0000-1000-8000-00805F9B34FB")

    // ─── UUIDs de características GATT ─────────────────────────────────────
    /** Write: enviar comandos a la cámara */
    val CHAR_COMMAND_WRITE: UUID = UUID.fromString("0000BB01-0000-1000-8000-00805F9B34FB")

    /** Notify: recibir notificaciones de estado */
    val CHAR_STATUS_NOTIFY: UUID = UUID.fromString("0000BB02-0000-1000-8000-00805F9B34FB")

    /** Read: nivel de batería */
    val CHAR_BATTERY_READ: UUID = UUID.fromString("0000BB03-0000-1000-8000-00805F9B34FB")

    /** UUID estándar BLE para descriptor de notificaciones */
    val DESCRIPTOR_CLIENT_CHAR_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    // ─── Comandos Protobuf (bytes pre-serializados) ─────────────────────────
    /**
     * Comandos descubiertos por análisis de tráfico BLE.
     * La cámara usa Protocol Buffers; estos son payloads para los comandos
     * más comunes, codificados en protobuf raw.
     *
     * Estructura general: [field_tag | wire_type, value...]
     */
    object Commands {
        /** Iniciar grabación de vídeo */
        val START_RECORDING = byteArrayOf(0x08, 0x01, 0x10, 0x01)

        /** Detener grabación */
        val STOP_RECORDING = byteArrayOf(0x08, 0x01, 0x10, 0x02)

        /** Tomar foto */
        val TAKE_PHOTO = byteArrayOf(0x08, 0x02, 0x10, 0x01)

        /** Marcar highlight en el clip actual */
        val MARK_HIGHLIGHT = byteArrayOf(0x08, 0x03, 0x10, 0x01)

        /** Solicitar estado de la cámara */
        val GET_STATUS = byteArrayOf(0x08, 0x04)

        /** Cambiar a modo vídeo */
        val MODE_VIDEO = byteArrayOf(0x08, 0x05, 0x10, 0x01)

        /** Cambiar a modo foto */
        val MODE_PHOTO = byteArrayOf(0x08, 0x05, 0x10, 0x02)

        /** Cambiar a modo timelapse */
        val MODE_TIMELAPSE = byteArrayOf(0x08, 0x05, 0x10, 0x03)

        /**
         * Enviar coordenadas GPS a la cámara para el overlay de datos.
         * @param lat Latitud * 1e7 (Int32)
         * @param lon Longitud * 1e7 (Int32)
         * @param speed Velocidad en cm/s (UInt16)
         * Serializado manualmente en protobuf sin librería externa.
         */
        fun gpsData(lat: Double, lon: Double, speed: Float): ByteArray {
            val latInt = (lat * 1e7).toInt()
            val lonInt = (lon * 1e7).toInt()
            val speedCms = (speed * 100).toInt()

            return buildList {
                // field 1 (lat): tag=0x08, varint
                add(0x08.toByte()); addAll(encodeVarint(latInt.toLong()))
                // field 2 (lon): tag=0x10, varint
                add(0x10.toByte()); addAll(encodeVarint(lonInt.toLong()))
                // field 3 (speed): tag=0x18, varint
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

    // ─── Parsing de respuestas ──────────────────────────────────────────────
    object ResponseParser {
        /**
         * Parsea una notificación de estado de la cámara.
         * Devuelve un mapa con los campos decodificados.
         */
        fun parseStatus(bytes: ByteArray): CameraStatus {
            var isRecording = false
            var batteryLevel = -1
            var mode = CameraMode.UNKNOWN
            var i = 0
            while (i < bytes.size) {
                val tag = bytes[i].toInt() and 0xFF
                val fieldNumber = tag ushr 3
                i++
                when (fieldNumber) {
                    1 -> { // recording state
                        isRecording = bytes[i].toInt() == 1
                        i++
                    }
                    2 -> { // battery
                        batteryLevel = bytes[i].toInt() and 0xFF
                        i++
                    }
                    3 -> { // mode
                        mode = when (bytes[i].toInt()) {
                            1 -> CameraMode.VIDEO
                            2 -> CameraMode.PHOTO
                            3 -> CameraMode.TIMELAPSE
                            else -> CameraMode.UNKNOWN
                        }
                        i++
                    }
                    else -> i++ // saltar campo desconocido
                }
            }
            return CameraStatus(isRecording, batteryLevel, mode)
        }
    }
}

enum class CameraMode { VIDEO, PHOTO, TIMELAPSE, UNKNOWN }

data class CameraStatus(
    val isRecording: Boolean,
    val batteryLevel: Int,   // 0-100, -1 si desconocido
    val mode: CameraMode,
)

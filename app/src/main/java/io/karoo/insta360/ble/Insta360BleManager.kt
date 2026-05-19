package io.karoo.insta360.ble

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
import io.karoo.insta360.ble.Insta360BleConstants.CHAR_COMMAND_WRITE
import io.karoo.insta360.ble.Insta360BleConstants.CHAR_STATUS_NOTIFY
import io.karoo.insta360.ble.Insta360BleConstants.DESCRIPTOR_CLIENT_CHAR_CONFIG
import io.karoo.insta360.ble.Insta360BleConstants.SERVICE_CAMERA_CONTROL
import io.karoo.insta360.ble.Insta360BleConstants.ResponseParser
import kotlinx.coroutines.flow.*
import timber.log.Timber

/**
 * Insta360BleManager — modo GATT Server.
 *
 * El Karoo se anuncia como "Insta360 GPS Remote" para que la cámara
 * lo reconozca como mando remoto y se conecte ella sola.
 */
class Insta360BleManager(private val context: Context) {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _cameraStatus = MutableStateFlow(CameraStatus(false, -1, CameraMode.UNKNOWN))
    val cameraStatus: StateFlow<CameraStatus> = _cameraStatus.asStateFlow()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var connectedDevice: BluetoothDevice? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // ─── Iniciar advertising ────────────────────────────────────────────────
    fun startScan() {
        if (!hasPermissions()) {
            Timber.w("Faltan permisos BLE")
            return
        }
        if (_connectionState.value != ConnectionState.DISCONNECTED) return

        _connectionState.value = ConnectionState.SCANNING
        Timber.d("Iniciando GATT Server + advertising como 'Insta360 GPS Remote'...")

        startGattServer()
        startAdvertising()
    }

    private fun startAdvertising() {
        if (!hasPermissions()) return
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser

        // El nombre del adaptador debe ser "Insta360 GPS Remote"
        bluetoothAdapter?.name = "Insta360 GPS Remote"

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0) // sin timeout — sigue anunciando hasta conectar
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(SERVICE_CAMERA_CONTROL))
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
        Timber.d("Advertising iniciado como 'Insta360 GPS Remote'")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Timber.d("Advertising activo — esperando conexión de la cámara...")
        }
        override fun onStartFailure(errorCode: Int) {
            Timber.e("Error advertising: $errorCode")
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    // ─── GATT Server ────────────────────────────────────────────────────────
    private fun startGattServer() {
        if (!hasPermissions()) return

        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        // Servicio principal con característica de comando (Write) y notificación
        val service = BluetoothGattService(
            SERVICE_CAMERA_CONTROL,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        // Característica Write — recibe comandos de la cámara
        val writeChar = BluetoothGattCharacteristic(
            CHAR_COMMAND_WRITE,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        // Característica Notify — envía estado al dispositivo conectado
        val notifyChar = BluetoothGattCharacteristic(
            CHAR_STATUS_NOTIFY,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        // Descriptor obligatorio para notificaciones
        val descriptor = BluetoothGattDescriptor(
            DESCRIPTOR_CLIENT_CHAR_CONFIG,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        notifyChar.addDescriptor(descriptor)

        service.addCharacteristic(writeChar)
        service.addCharacteristic(notifyChar)
        gattServer?.addService(service)

        commandChar = notifyChar
        Timber.d("GATT Server iniciado")
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (!hasPermissions()) return
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.d("Cámara conectada: ${device.name} [${device.address}]")
                    connectedDevice = device
                    stopAdvertising()
                    _connectionState.value = ConnectionState.CONNECTED
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d("Cámara desconectada")
                    connectedDevice = null
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _cameraStatus.value = CameraStatus(false, -1, CameraMode.UNKNOWN)
                    // Volver a anunciarse para reconexión
                    startAdvertising()
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (!hasPermissions()) return
            Timber.d("Comando recibido de cámara: ${value.toHex()}")
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            // Parsear el comando recibido como estado
            val status = ResponseParser.parseStatus(value)
            _cameraStatus.value = status
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (!hasPermissions()) return
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            Timber.d("Descriptor escrito por cámara")
        }
    }

    private fun stopAdvertising() {
        if (hasPermissions()) advertiser?.stopAdvertising(advertiseCallback)
        advertiser = null
    }

    // ─── Enviar comandos a la cámara ────────────────────────────────────────
    // En modo servidor, enviamos notificaciones a la cámara conectada
    private fun sendCommand(bytes: ByteArray) {
        if (!hasPermissions()) return
        val device = connectedDevice ?: run {
            Timber.w("Sin cámara conectada")
            return
        }
        val char = commandChar ?: return
        char.value = bytes
        gattServer?.notifyCharacteristicChanged(device, char, false)
        Timber.d("Comando enviado: ${bytes.toHex()}")
    }

    fun startRecording() {
        Timber.d("Iniciando grabación")
        sendCommand(Insta360BleConstants.Commands.START_RECORDING)
    }

    fun stopRecording() {
        Timber.d("Deteniendo grabación")
        sendCommand(Insta360BleConstants.Commands.STOP_RECORDING)
    }

    fun takePhoto() {
        Timber.d("Tomando foto")
        sendCommand(Insta360BleConstants.Commands.TAKE_PHOTO)
    }

    fun markHighlight() {
        Timber.d("Marcando highlight")
        sendCommand(Insta360BleConstants.Commands.MARK_HIGHLIGHT)
    }

    fun setMode(mode: CameraMode) {
        val cmd = when (mode) {
            CameraMode.VIDEO     -> Insta360BleConstants.Commands.MODE_VIDEO
            CameraMode.PHOTO     -> Insta360BleConstants.Commands.MODE_PHOTO
            CameraMode.TIMELAPSE -> Insta360BleConstants.Commands.MODE_TIMELAPSE
            else -> return
        }
        sendCommand(cmd)
    }

    fun sendGpsData(lat: Double, lon: Double, speed: Float) {
        sendCommand(Insta360BleConstants.Commands.gpsData(lat, lon, speed))
    }

    fun connectToAddress(address: String) {
        // En modo servidor no necesitamos conectar por dirección
        // la cámara se conecta sola al ver el advertising
        Timber.d("Modo servidor — esperando conexión de $address")
    }

    fun disconnect() {
        if (hasPermissions()) {
            connectedDevice?.let { gattServer?.cancelConnection(it) }
        }
        stopAdvertising()
        gattServer?.close()
        gattServer = null
        connectedDevice = null
        commandChar = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _cameraStatus.value = CameraStatus(false, -1, CameraMode.UNKNOWN)
        // Restaurar nombre original del adaptador
        bluetoothAdapter?.name = "Karoo"
        Timber.d("Servidor BLE cerrado")
    }

    val isConnected: Boolean
        get() = _connectionState.value == ConnectionState.CONNECTED

    private fun hasPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }
}

enum class ConnectionState {
    DISCONNECTED, SCANNING, CONNECTING, CONNECTED
}

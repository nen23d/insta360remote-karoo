package io.karoo.insta360.ble

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
import io.karoo.insta360.ble.Insta360BleConstants.CAMERA_ID
import io.karoo.insta360.ble.Insta360BleConstants.CHAR_NOTIFY
import io.karoo.insta360.ble.Insta360BleConstants.CHAR_WRITE
import io.karoo.insta360.ble.Insta360BleConstants.DESCRIPTOR_CCC
import io.karoo.insta360.ble.Insta360BleConstants.SECONDARY_SERVICE_UUID
import io.karoo.insta360.ble.Insta360BleConstants.SERVICE_UUID
import io.karoo.insta360.ble.Insta360BleConstants.Commands
import kotlinx.coroutines.flow.*
import timber.log.Timber

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
    private var notifyChar: BluetoothGattCharacteristic? = null

    fun startScan() {
        if (!hasPermissions()) { Timber.w("Faltan permisos"); return }
        if (_connectionState.value != ConnectionState.DISCONNECTED) return
        _connectionState.value = ConnectionState.SCANNING
        bluetoothAdapter?.name = "Insta360 GPS Remote"
        startGattServer()
        startAdvertising()
    }

    private fun startAdvertising() {
        if (!hasPermissions()) return
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        // Manufacturing data con ID específico de la cámara
        val manufData = byteArrayOf(
            0x4c, 0x00, 0x02, 0x15,
            0x09, 0x4f, 0x52, 0x42, 0x49, 0x54,
            0x09, 0xff.toByte(), 0x0f, 0x00,
            CAMERA_ID[0], CAMERA_ID[1], CAMERA_ID[2],
            CAMERA_ID[3], CAMERA_ID[4], CAMERA_ID[5],
            0x00, 0x00, 0x00, 0x00,
            0xe4.toByte(), 0x01
        )

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceUuid(ParcelUuid(SECONDARY_SERVICE_UUID))
            .addManufacturerData(0x004c, manufData)
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
        Timber.d("Advertising como 'Insta360 GPS Remote' con ID 1RWJRE")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Timber.d("Advertising activo — esperando cámara")
        }
        override fun onStartFailure(errorCode: Int) {
            Timber.e("Error advertising: $errorCode")
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    private fun startGattServer() {
        if (!hasPermissions()) return
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        // Servicio principal BE80
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val writeChar = BluetoothGattCharacteristic(
            CHAR_WRITE,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val notify = BluetoothGattCharacteristic(
            CHAR_NOTIFY,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val descriptor = BluetoothGattDescriptor(
            DESCRIPTOR_CCC,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        notify.addDescriptor(descriptor)

        service.addCharacteristic(writeChar)
        service.addCharacteristic(notify)
        gattServer?.addService(service)

        // Servicio secundario D0FF
        val service2 = BluetoothGattService(SECONDARY_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        gattServer?.addService(service2)

        notifyChar = notify
        Timber.d("GATT Server iniciado con BE80 + D0FF")
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (!hasPermissions()) return
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.d("¡Cámara conectada! ${device.address}")
                    connectedDevice = device
                    stopAdvertising()
                    _connectionState.value = ConnectionState.CONNECTED
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d("Cámara desconectada")
                    connectedDevice = null
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _cameraStatus.value = CameraStatus(false, -1, CameraMode.UNKNOWN)
                    startAdvertising()
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (!hasPermissions()) return
            Timber.d("Datos recibidos: ${value.toHex()}")
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (!hasPermissions()) return
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            Timber.d("Notificaciones activadas por la cámara")
        }
    }

    private fun stopAdvertising() {
        if (hasPermissions()) advertiser?.stopAdvertising(advertiseCallback)
        advertiser = null
    }

    private fun sendCommand(bytes: ByteArray) {
        if (!hasPermissions()) return
        val device = connectedDevice ?: run { Timber.w("Sin cámara conectada"); return }
        val char = notifyChar ?: return
        char.value = bytes
        gattServer?.notifyCharacteristicChanged(device, char, false)
        Timber.d("Comando enviado: ${bytes.toHex()}")
    }

    fun startRecording() {
        _cameraStatus.value = _cameraStatus.value.copy(isRecording = true)
        sendCommand(Commands.SHUTTER)
    }

    fun stopRecording() {
        _cameraStatus.value = _cameraStatus.value.copy(isRecording = false)
        sendCommand(Commands.SHUTTER)
    }

    fun takePhoto() = sendCommand(Commands.SHUTTER)
    fun markHighlight() = sendCommand(Commands.SHUTTER)
    fun setMode(mode: CameraMode) = sendCommand(Commands.MODE)
    fun connectToAddress(address: String) { Timber.d("Modo servidor — la cámara conecta sola") }

    fun disconnect() {
        if (hasPermissions()) connectedDevice?.let { gattServer?.cancelConnection(it) }
        stopAdvertising()
        gattServer?.close()
        gattServer = null
        connectedDevice = null
        notifyChar = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _cameraStatus.value = CameraStatus(false, -1, CameraMode.UNKNOWN)
        bluetoothAdapter?.name = "Karoo"
    }

    val isConnected: Boolean get() = _connectionState.value == ConnectionState.CONNECTED

    private fun hasPermissions() =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    private fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }
}

enum class ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

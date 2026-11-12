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
import io.karoo.insta360.ble.Insta360BleConstants.CHAR_INFO
import io.karoo.insta360.ble.Insta360BleConstants.CHAR_NOTIFY
import io.karoo.insta360.ble.Insta360BleConstants.CHAR_WRITE
import io.karoo.insta360.ble.Insta360BleConstants.Commands
import io.karoo.insta360.ble.Insta360BleConstants.DESCRIPTOR_CCC
import io.karoo.insta360.ble.Insta360BleConstants.HID_SERVICE_UUID
import io.karoo.insta360.ble.Insta360BleConstants.REMOTE_NAME
import io.karoo.insta360.ble.Insta360BleConstants.SERVICE_UUID
import kotlinx.coroutines.flow.*
import timber.log.Timber

/**
 * El Karoo actúa como mando: servidor GATT CE80 + anuncio.
 * SCANNING = "modo sincronización" (anunciando, esperando a la cámara).
 */
class Insta360BleManager(private val context: Context) {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _cameraStatus = MutableStateFlow(CameraStatus(false, -1, CameraMode.UNKNOWN))
    val cameraStatus: StateFlow<CameraStatus> = _cameraStatus.asStateFlow()

    private val _pairingSecondsLeft = MutableStateFlow(0)
    val pairingSecondsLeft: StateFlow<Int> = _pairingSecondsLeft.asStateFlow()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var connectedDevice: BluetoothDevice? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var subscribed = false
    private var userDisconnect = false
    private var originalName: String? = null
    private val handler = Handler(Looper.getMainLooper())

    private val countdown = object : Runnable {
        override fun run() {
            val left = _pairingSecondsLeft.value - 1
            if (left <= 0) {
                Timber.d("Fin del modo sincronización")
                stopPairing()
            } else {
                _pairingSecondsLeft.value = left
                handler.postDelayed(this, 1000)
            }
        }
    }

    // ─── Modo sincronización ────────────────────────────────────────────────
    fun startScan() = startPairing()

    fun startPairing(durationSec: Int = 120) {
        if (!hasPermissions()) { Timber.w("Faltan permisos BLE"); return }
        if (_connectionState.value == ConnectionState.CONNECTED) return
        userDisconnect = false
        closeServer()
        stopAdvertising()
        if (originalName == null) originalName = adapter?.name
        adapter?.name = REMOTE_NAME
        _connectionState.value = ConnectionState.SCANNING
        _pairingSecondsLeft.value = durationSec
        handler.removeCallbacks(countdown)
        handler.postDelayed(countdown, 1000)
        startGattServer() // el anuncio arranca en onServiceAdded
    }

    fun stopPairing() {
        handler.removeCallbacks(countdown)
        _pairingSecondsLeft.value = 0
        if (_connectionState.value == ConnectionState.SCANNING) {
            stopAdvertising()
            closeServer()
            restoreName()
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    // ─── Anuncio ────────────────────────────────────────────────────────────
    private fun startAdvertising() {
        if (!hasPermissions()) return
        advertiser = adapter?.bluetoothLeAdvertiser
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
        // Paquete principal: servicio HID 1812. Nombre en la respuesta al escaneo.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(HID_SERVICE_UUID))
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()
        advertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Timber.d("Anunciando como $REMOTE_NAME")
        }
        override fun onStartFailure(errorCode: Int) {
            Timber.e("Error de anuncio: $errorCode")
            stopPairing()
        }
    }

    private fun stopAdvertising() {
        if (hasPermissions()) advertiser?.stopAdvertising(advertiseCallback)
        advertiser = null
    }

    // ─── Servidor GATT (copia del mando real) ───────────────────────────────
    private fun startGattServer() {
        if (!hasPermissions()) return
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val ce81 = BluetoothGattCharacteristic(
            CHAR_WRITE,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val ce82 = BluetoothGattCharacteristic(
            CHAR_NOTIFY,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        ce82.value = ByteArray(8) // el mando real devuelve 8 ceros
        ce82.addDescriptor(
            BluetoothGattDescriptor(
                DESCRIPTOR_CCC,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
        )

        val ce83 = BluetoothGattCharacteristic(
            CHAR_INFO,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        ce83.value = byteArrayOf(0x01, 0x02)

        service.addCharacteristic(ce81)
        service.addCharacteristic(ce82)
        service.addCharacteristic(ce83)
        gattServer?.addService(service)
        notifyChar = ce82
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status == BluetoothGatt.GATT_SUCCESS && _connectionState.value == ConnectionState.SCANNING) {
                startAdvertising()
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("No se pudo añadir el servicio: $status")
                stopPairing()
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (!hasPermissions()) return
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.d("Cámara conectada: ${device.address}")
                    connectedDevice = device
                    handler.removeCallbacks(countdown)
                    _pairingSecondsLeft.value = 0
                    stopAdvertising()
                    _connectionState.value = ConnectionState.CONNECTED
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d("Cámara desconectada")
                    connectedDevice = null
                    subscribed = false
                    _cameraStatus.value = CameraStatus(false, -1, CameraMode.UNKNOWN)
                    if (!userDisconnect) {
                        // Pérdida inesperada: reabrir 60 s para que la cámara reconecte
                        _connectionState.value = ConnectionState.DISCONNECTED
                        handler.post { startPairing(60) }
                    }
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (!hasPermissions()) return
            gattServer?.sendResponse(
                device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.value
            )
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (!hasPermissions()) return
            Timber.d("Escrito por la cámara en ${characteristic.uuid}: ${value.toHex()}")
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            if (!hasPermissions()) return
            val v = if (subscribed) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, v)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (!hasPermissions()) return
            subscribed = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            Timber.d("La cámara ${if (subscribed) "se suscribió a" else "canceló"} CE82")
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    // ─── Comandos ───────────────────────────────────────────────────────────
    private fun sendCommand(bytes: ByteArray) {
        if (!hasPermissions()) return
        val device = connectedDevice ?: run { Timber.w("Sin cámara conectada"); return }
        val char = notifyChar ?: return
        if (!subscribed) { Timber.w("La cámara aún no se ha suscrito a CE82"); return }
        char.value = bytes
        gattServer?.notifyCharacteristicChanged(device, char, false)
        Timber.d("Enviado: ${bytes.toHex()}")
    }

    // El obturador es un toggle; el estado de grabación es una suposición local
    fun toggleShutter() {
        _cameraStatus.value = _cameraStatus.value.copy(isRecording = !_cameraStatus.value.isRecording)
        sendCommand(Commands.SHUTTER)
    }

    fun startRecording() {
        if (!_cameraStatus.value.isRecording) toggleShutter()
    }

    fun stopRecording() {
        if (_cameraStatus.value.isRecording) toggleShutter()
    }

    fun takePhoto() = sendCommand(Commands.SHUTTER)
    fun toggleScreen() = sendCommand(Commands.SCREEN)
    fun powerOff() = sendCommand(Commands.POWER_OFF)
    fun setMode(mode: CameraMode) = sendCommand(Commands.MODE)

    // No hay comando de highlight conocido (enviar SHUTTER pararía la grabación)
    fun markHighlight() { Timber.w("Highlight no disponible por BLE") }

    // ─── Cierre ─────────────────────────────────────────────────────────────
    fun disconnect() {
        userDisconnect = true
        handler.removeCallbacks(countdown)
        _pairingSecondsLeft.value = 0
        if (hasPermissions()) connectedDevice?.let { gattServer?.cancelConnection(it) }
        stopAdvertising()
        closeServer()
        restoreName()
        _connectionState.value = ConnectionState.DISCONNECTED
        _cameraStatus.value = CameraStatus(false, -1, CameraMode.UNKNOWN)
    }

    private fun closeServer() {
        if (hasPermissions()) gattServer?.close()
        gattServer = null
        notifyChar = null
        connectedDevice = null
        subscribed = false
    }

    private fun restoreName() {
        if (hasPermissions()) originalName?.let { adapter?.name = it }
        originalName = null
    }

    val isConnected: Boolean get() = _connectionState.value == ConnectionState.CONNECTED

    private fun hasPermissions() =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED

    private fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }
}

enum class ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

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
import io.karoo.insta360.ble.Insta360BleConstants.Commands
import io.karoo.insta360.ble.Insta360BleConstants.DESCRIPTOR_CLIENT_CHAR_CONFIG
import io.karoo.insta360.ble.Insta360BleConstants.CHAR_COMMAND_WRITE
import io.karoo.insta360.ble.Insta360BleConstants.CHAR_STATUS_NOTIFY
import io.karoo.insta360.ble.Insta360BleConstants.SERVICE_CAMERA_CONTROL
import io.karoo.insta360.ble.Insta360BleConstants.SERVICE_CAMERA_STATUS
import io.karoo.insta360.ble.Insta360BleConstants.ResponseParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.UUID

/**
 * Gestor BLE para la Insta360 GO Ultra.
 *
 * Emula el "Insta360 GPS Remote" para que la cámara acepte la conexión.
 * Usa GATT para enviar comandos y recibir notificaciones de estado.
 */
class Insta360BleManager(private val context: Context) {

    // ─── Estado observable ──────────────────────────────────────────────────
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _cameraStatus = MutableStateFlow(CameraStatus(false, -1, CameraMode.UNKNOWN))
    val cameraStatus: StateFlow<CameraStatus> = _cameraStatus.asStateFlow()

    // ─── BLE internals ──────────────────────────────────────────────────────
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Timeout de escaneo: 15 segundos
    private val SCAN_TIMEOUT_MS = 15_000L

    // ─── Escaneo ────────────────────────────────────────────────────────────
    fun startScan() {
        if (!hasPermissions()) {
            Timber.w("Faltan permisos BLE")
            return
        }
        if (_connectionState.value != ConnectionState.DISCONNECTED) return

        _connectionState.value = ConnectionState.SCANNING
        Timber.d("Iniciando escaneo BLE para Insta360...")

        scanner = bluetoothAdapter?.bluetoothLeScanner
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Filtramos por nombre del dispositivo "Insta360 GPS Remote"
        val filters = listOf(
            ScanFilter.Builder()
                .setDeviceName(Insta360BleConstants.REMOTE_NAME)
                .build()
        )

        scanner?.startScan(filters, settings, scanCallback)

        // Timeout automático
        mainHandler.postDelayed({
            if (_connectionState.value == ConnectionState.SCANNING) {
                Timber.w("Timeout de escaneo — cámara no encontrada")
                stopScan()
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }, SCAN_TIMEOUT_MS)
    }

    private fun stopScan() {
        if (hasPermissions()) scanner?.stopScan(scanCallback)
        scanner = null
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            Timber.d("GO Ultra encontrada: ${device.name} [${device.address}]")
            stopScan()
            connect(device)
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("Escaneo fallido: código $errorCode")
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    // ─── Conexión GATT ──────────────────────────────────────────────────────
    private fun connect(device: BluetoothDevice) {
        if (!hasPermissions()) return
        _connectionState.value = ConnectionState.CONNECTING
        Timber.d("Conectando a ${device.name}...")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        if (hasPermissions()) {
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
        commandChar = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _cameraStatus.value = CameraStatus(false, -1, CameraMode.UNKNOWN)
        Timber.d("Desconectado de GO Ultra")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.d("GATT conectado — descubriendo servicios...")
                    _connectionState.value = ConnectionState.CONNECTED
                    if (hasPermissions()) gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d("GATT desconectado")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    commandChar = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("Descubrimiento de servicios fallido: $status")
                return
            }
            Timber.d("Servicios descubiertos")
            setupCharacteristics(gatt)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == CHAR_STATUS_NOTIFY) {
                val status = ResponseParser.parseStatus(value)
                _cameraStatus.value = status
                Timber.d("Estado cámara: rec=${status.isRecording} bat=${status.batteryLevel}% modo=${status.mode}")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.d("Comando enviado correctamente")
            } else {
                Timber.e("Error enviando comando: $status")
            }
        }
    }

    private fun setupCharacteristics(gatt: BluetoothGatt) {
        if (!hasPermissions()) return
        val controlService = gatt.getService(SERVICE_CAMERA_CONTROL)
        val statusService = gatt.getService(SERVICE_CAMERA_STATUS)

        commandChar = controlService?.getCharacteristic(CHAR_COMMAND_WRITE)

        // Habilitar notificaciones de estado
        statusService?.getCharacteristic(CHAR_STATUS_NOTIFY)?.let { char ->
            gatt.setCharacteristicNotification(char, true)
            char.getDescriptor(DESCRIPTOR_CLIENT_CHAR_CONFIG)?.let { descriptor ->
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        }

        // Solicitar estado inicial
        mainHandler.postDelayed({ sendCommand(Commands.GET_STATUS) }, 500)
    }

    // ─── Comandos públicos ──────────────────────────────────────────────────
    fun startRecording() {
        Timber.d("Iniciando grabación")
        sendCommand(Commands.START_RECORDING)
    }

    fun stopRecording() {
        Timber.d("Deteniendo grabación")
        sendCommand(Commands.STOP_RECORDING)
    }

    fun takePhoto() {
        Timber.d("Tomando foto")
        sendCommand(Commands.TAKE_PHOTO)
    }

    fun markHighlight() {
        Timber.d("Marcando highlight")
        sendCommand(Commands.MARK_HIGHLIGHT)
    }

    fun setMode(mode: CameraMode) {
        val cmd = when (mode) {
            CameraMode.VIDEO     -> Commands.MODE_VIDEO
            CameraMode.PHOTO     -> Commands.MODE_PHOTO
            CameraMode.TIMELAPSE -> Commands.MODE_TIMELAPSE
            else -> return
        }
        sendCommand(cmd)
    }

    fun sendGpsData(lat: Double, lon: Double, speed: Float) {
        sendCommand(Commands.gpsData(lat, lon, speed))
    }

    private fun sendCommand(bytes: ByteArray) {
        if (!hasPermissions()) return
        val char = commandChar ?: run {
            Timber.w("Intento de envío sin característica inicializada")
            return
        }
        char.value = bytes
        val success = gatt?.writeCharacteristic(char) ?: false
        if (!success) Timber.e("writeCharacteristic devolvió false")
    }

    // ─── Utilidades ─────────────────────────────────────────────────────────
    val isConnected: Boolean
        get() = _connectionState.value == ConnectionState.CONNECTED

    private fun hasPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
    }
}

enum class ConnectionState {
    DISCONNECTED, SCANNING, CONNECTING, CONNECTED
}

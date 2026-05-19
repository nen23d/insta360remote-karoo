package io.karoo.insta360.ui

import android.Manifest
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.karoo.insta360.ble.*
import timber.log.Timber

data class BleDevice(val name: String, val address: String)

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: Insta360BleManager
    private val foundDevices = mutableStateListOf<BleDevice>()
    private var isScanning = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) startScan()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleManager = Insta360BleManager(applicationContext)

        setContent {
            val connState by bleManager.connectionState.collectAsState()
            val camStatus by bleManager.cameraStatus.collectAsState()

            Insta360ControlScreen(
                bleManager = bleManager,
                connState = connState,
                camStatus = camStatus,
                foundDevices = foundDevices,
                isScanning = isScanning.value,
                onScan = { requestPermissionsAndScan() },
                onConnectDevice = { device -> connectToDevice(device) },
                onDisconnect = { bleManager.disconnect(); foundDevices.clear() },
                onRecord = { bleManager.startRecording() },
                onStop = { bleManager.stopRecording() },
                onPhoto = { bleManager.takePhoto() },
                onHighlight = { bleManager.markHighlight() },
                onMode = { mode -> bleManager.setMode(mode) }
            )
        }
    }

    private fun requestPermissionsAndScan() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN))
                needed += Manifest.permission.BLUETOOTH_SCAN
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT))
                needed += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        if (needed.isEmpty()) startScan()
        else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun startScan() {
        foundDevices.clear()
        isScanning.value = true
        val bluetoothManager = getSystemService(android.bluetooth.BluetoothManager::class.java)
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
                val name = result.device.name ?: return
                val address = result.device.address
                // Mostrar todos los dispositivos BLE encontrados
                val device = BleDevice(name, address)
                if (foundDevices.none { it.address == address }) {
                    foundDevices.add(device)
                }
            }
        }

        scanner?.startScan(emptyList(), settings, callback)

        // Parar escaneo después de 10 segundos
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            scanner?.stopScan(callback)
            isScanning.value = false
        }, 10_000)
    }

    private fun connectToDevice(device: BleDevice) {
        bleManager.connectToAddress(device.address)
    }

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun Insta360ControlScreen(
    bleManager: Insta360BleManager,
    connState: ConnectionState,
    camStatus: CameraStatus,
    foundDevices: List<BleDevice>,
    isScanning: Boolean,
    onScan: () -> Unit,
    onConnectDevice: (BleDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onPhoto: () -> Unit,
    onHighlight: () -> Unit,
    onMode: (CameraMode) -> Unit,
) {
    val bg = Color(0xFF0A0A0F)
    val green = Color(0xFF00FF88)
    val red = Color(0xFFFF2828)
    val mono = FontFamily.Monospace
    val isConnected = connState == ConnectionState.CONNECTED
    val isRecording = camStatus.isRecording

    Box(Modifier.fillMaxSize().background(bg).padding(16.dp)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("INSTA360 GO ULTRA", color = green, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, fontFamily = mono, letterSpacing = 3.sp)
            Text("Extensión para Hammerhead Karoo", color = Color(0xFF444444),
                fontSize = 11.sp, fontFamily = mono)

            Divider(color = Color(0xFF1A1A1A))

            // Estado conexión
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0D0D14)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ESTADO", color = Color(0xFF444444), fontSize = 9.sp, fontFamily = mono)
                    Text(
                        when (connState) {
                            ConnectionState.CONNECTED    -> "CONECTADO"
                            ConnectionState.CONNECTING   -> "CONECTANDO..."
                            ConnectionState.SCANNING     -> "BUSCANDO..."
                            ConnectionState.DISCONNECTED -> "DESCONECTADO"
                        },
                        color = when (connState) {
                            ConnectionState.CONNECTED    -> green
                            ConnectionState.DISCONNECTED -> Color(0xFF444444)
                            else -> Color(0xFFFFCC00)
                        },
                        fontSize = 14.sp, fontFamily = mono, fontWeight = FontWeight.Bold
                    )
                }
                if (isConnected && camStatus.batteryLevel >= 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("BATERÍA", color = Color(0xFF444444), fontSize = 9.sp, fontFamily = mono)
                        Text("${camStatus.batteryLevel}%",
                            color = if (camStatus.batteryLevel > 20) green else red,
                            fontSize = 20.sp, fontFamily = mono, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (!isConnected) {
                // Botón escanear
                KarooButton(
                    if (isScanning) "ESCANEANDO..." else "BUSCAR CÁMARAS",
                    green, Color(0xFF004422), onClick = { if (!isScanning) onScan() }
                )

                // Lista de dispositivos encontrados
                if (foundDevices.isNotEmpty()) {
                    Text("DISPOSITIVOS ENCONTRADOS", color = Color(0xFF444444),
                        fontSize = 9.sp, fontFamily = mono, letterSpacing = 2.sp)

                    foundDevices.forEach { device ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0D0D14))
                                .border(1.dp, Color(0xFF1A1A2A), RoundedCornerShape(8.dp))
                                .clickable { onConnectDevice(device) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(device.name, color = green, fontFamily = mono,
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(device.address, color = Color(0xFF444444),
                                    fontFamily = mono, fontSize = 10.sp)
                            }
                            Text("CONECTAR →", color = Color(0xFF00AA55),
                                fontFamily = mono, fontSize = 10.sp)
                        }
                    }
                }

                if (isScanning) {
                    Text("Buscando dispositivos BLE...",
                        color = Color(0xFF555555), fontSize = 10.sp, fontFamily = mono)
                }
            }

            // Controles cuando está conectado
            if (isConnected) {
                if (isRecording) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            KarooButton("■ STOP", red, Color(0xFF440000), onClick = onStop)
                        }
                        Box(Modifier.width(56.dp)) {
                            KarooButton("⭐", Color(0xFFFFCC00), Color(0xFF1A1400), onClick = onHighlight)
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            KarooButton("● GRABAR", red, Color(0xFF440000), onClick = onRecord)
                        }
                        Box(Modifier.weight(1f)) {
                            KarooButton("📷 FOTO", green, Color(0xFF004422), onClick = onPhoto)
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(CameraMode.VIDEO, CameraMode.PHOTO, CameraMode.TIMELAPSE).forEach { mode ->
                        Box(Modifier.weight(1f)) {
                            KarooModeButton(mode.name, camStatus.mode == mode,
                                !isRecording, onClick = { onMode(mode) })
                        }
                    }
                }

                KarooButton("DESCONECTAR", Color(0xFF444444), Color(0xFF111111), onClick = onDisconnect)
            }
        }
    }
}

@Composable
fun KarooButton(label: String, borderColor: Color, bgColor: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = borderColor, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp)
    }
}

@Composable
fun KarooModeButton(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val color = if (selected) Color(0xFF00FF88) else Color(0xFF333333)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color(0xFF00331A) else Color(0xFF0D0D0D))
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontFamily = FontFamily.Monospace,
            fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

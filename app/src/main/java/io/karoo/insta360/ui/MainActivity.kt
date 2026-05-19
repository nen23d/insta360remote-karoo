package io.karoo.insta360.ui

import android.Manifest
import android.content.Intent
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
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: Insta360BleManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            bleManager.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleManager = Insta360BleManager(applicationContext)

        setContent {
            Insta360ControlScreen(
                bleManager = bleManager,
                onScan = { requestPermissionsAndScan() },
                onDisconnect = { bleManager.disconnect() },
                onRecord = { bleManager.startRecording() },
                onStop = { bleManager.stopRecording() },
                onPhoto = { bleManager.takePhoto() },
                onHighlight = { bleManager.markHighlight() },
                onMode = { mode -> bleManager.setMode(mode) }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // No desconectamos aquí — el servicio foreground mantiene la conexión
    }

    private fun requestPermissionsAndScan() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN))
                needed += Manifest.permission.BLUETOOTH_SCAN
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT))
                needed += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
                needed += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (needed.isEmpty()) bleManager.startScan()
        else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
}

// ─── UI Composable ─────────────────────────────────────────────────────────
@Composable
fun Insta360ControlScreen(
    bleManager: Insta360BleManager,
    onScan: () -> Unit,
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

    val connState by bleManager.connectionState.collectAsState()
    val camStatus by bleManager.cameraStatus.collectAsState()

    val statusColor = when (connState) {
        ConnectionState.CONNECTED    -> green
        ConnectionState.SCANNING,
        ConnectionState.CONNECTING   -> Color(0xFFFFCC00)
        ConnectionState.DISCONNECTED -> Color(0xFF444444)
    }

    val statusLabel = when (connState) {
        ConnectionState.CONNECTED    -> "CONECTADO"
        ConnectionState.SCANNING     -> "BUSCANDO..."
        ConnectionState.CONNECTING   -> "CONECTANDO..."
        ConnectionState.DISCONNECTED -> "DESCONECTADO"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .padding(16.dp)
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header ───────────────────────────────────────────────────
            Text(
                "INSTA360 GO ULTRA",
                color = green,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                letterSpacing = 3.sp
            )
            Text(
                "Extensión para Hammerhead Karoo",
                color = Color(0xFF444444),
                fontSize = 11.sp,
                fontFamily = mono
            )

            Divider(color = Color(0xFF1A1A1A))

            // ── Estado ───────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0D0D14))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ESTADO BLE", color = Color(0xFF444444), fontSize = 9.sp, fontFamily = mono, letterSpacing = 2.sp)
                    Text(statusLabel, color = statusColor, fontSize = 14.sp, fontFamily = mono, fontWeight = FontWeight.Bold)
                }
                if (connState == ConnectionState.CONNECTED && camStatus.batteryLevel >= 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("BATERÍA", color = Color(0xFF444444), fontSize = 9.sp, fontFamily = mono)
                        Text("${camStatus.batteryLevel}%",
                            color = if (camStatus.batteryLevel > 20) green else red,
                            fontSize = 20.sp, fontFamily = mono, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Botón conectar / desconectar ─────────────────────────────
            when (connState) {
                ConnectionState.DISCONNECTED -> {
                    KarooButton("BUSCAR CÁMARA", green, Color(0xFF004422), onClick = onScan)
                }
                ConnectionState.SCANNING, ConnectionState.CONNECTING -> {
                    KarooButton(statusLabel, Color(0xFFFFCC00), Color(0xFF1A1400), onClick = {})
                }
                ConnectionState.CONNECTED -> {
                    // Grabación
                    if (camStatus.isRecording) {
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

                    // Selector de modo
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(CameraMode.VIDEO, CameraMode.PHOTO, CameraMode.TIMELAPSE).forEach { mode ->
                            Box(Modifier.weight(1f)) {
                                KarooModeButton(
                                    label = mode.name,
                                    selected = camStatus.mode == mode,
                                    enabled = !camStatus.isRecording,
                                    onClick = { onMode(mode) }
                                )
                            }
                        }
                    }

                    KarooButton("DESCONECTAR", Color(0xFF444444), Color(0xFF111111), onClick = onDisconnect)
                }
            }

            Divider(color = Color(0xFF1A1A1A))

            // ── Info campos de datos ─────────────────────────────────────
            Text("CAMPOS DE DATOS", color = Color(0xFF444444), fontSize = 9.sp, fontFamily = mono, letterSpacing = 2.sp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0D0D14))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoRow("Cámara Estado", "Estado grabación (gráfico)")
                InfoRow("Cámara Batería", "Nivel batería en %")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Añade estos campos a cualquier página de tu perfil de ride desde el editor de perfiles del Karoo.",
                    color = Color(0xFF555555),
                    fontSize = 10.sp,
                    fontFamily = mono,
                    lineHeight = 15.sp
                )
            }

            // ── Protocolo ────────────────────────────────────────────────
            Text("PROTOCOLO", color = Color(0xFF444444), fontSize = 9.sp, fontFamily = mono, letterSpacing = 2.sp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0D0D14))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InfoRow("Nombre BLE", "Insta360 GPS Remote")
                InfoRow("Protocolo", "GATT + Protobuf")
                InfoRow("SDK Karoo", "karoo-ext 1.x")
                InfoRow("Auto-rec", "Al iniciar/parar ride")
            }
        }
    }
}

@Composable
fun KarooButton(label: String, borderColor: Color, bgColor: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
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
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color(0xFF00331A) else Color(0xFF0D0D0D))
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontFamily = FontFamily.Monospace,
            fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF555555), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Text(value, color = Color(0xFF00FF88), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

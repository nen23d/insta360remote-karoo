package io.karoo.insta360.ui

import android.Manifest
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

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: Insta360BleManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) bleManager.startScan()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleManager = Insta360BleManager(applicationContext)

        setContent {
            val connState by bleManager.connectionState.collectAsState()
            val camStatus by bleManager.cameraStatus.collectAsState()

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
                    Text("Control BLE · Karoo 3", color = Color(0xFF444444),
                        fontSize = 11.sp, fontFamily = mono)

                    Divider(color = Color(0xFF1A1A1A))

                    // Estado
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0D0D14)).padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("ESTADO BLE", color = Color(0xFF444444), fontSize = 9.sp, fontFamily = mono)
                            Text(
                                when (connState) {
                                    ConnectionState.CONNECTED    -> "CONECTADO"
                                    ConnectionState.CONNECTING   -> "CONECTANDO..."
                                    ConnectionState.SCANNING     -> "ESPERANDO CÁMARA..."
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
                        // Instrucciones
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0D0D14)).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("CÓMO CONECTAR", color = Color(0xFF444444),
                                fontSize = 9.sp, fontFamily = mono, letterSpacing = 2.sp)
                            Text("1. Pulsa ANUNCIAR abajo", color = Color(0xFF666666),
                                fontSize = 11.sp, fontFamily = mono)
                            Text("2. En la cámara → Ajustes → Control remoto", color = Color(0xFF666666),
                                fontSize = 11.sp, fontFamily = mono)
                            Text("3. Selecciona 'Insta360 GPS Remote'", color = Color(0xFF666666),
                                fontSize = 11.sp, fontFamily = mono)
                        }

                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF004422))
                                .border(1.dp, green, RoundedCornerShape(8.dp))
                                .clickable { requestPermissionsAndStart() }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (connState == ConnectionState.SCANNING) "ESPERANDO CÁMARA..." else "ANUNCIAR COMO MANDO",
                                color = green, fontFamily = mono,
                                fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp
                            )
                        }
                    }

                    if (isConnected) {
                        if (isRecording) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) {
                                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF440000))
                                        .border(1.dp, red, RoundedCornerShape(8.dp))
                                        .clickable { bleManager.stopRecording() }
                                        .padding(vertical = 13.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("■ STOP", color = red, fontFamily = mono,
                                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                                Box(Modifier.width(56.dp)) {
                                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1A1400))
                                        .border(1.dp, Color(0xFFFFCC00), RoundedCornerShape(8.dp))
                                        .clickable { bleManager.markHighlight() }
                                        .padding(vertical = 13.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("⭐", fontSize = 16.sp)
                                    }
                                }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) {
                                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF440000))
                                        .border(1.dp, red, RoundedCornerShape(8.dp))
                                        .clickable { bleManager.startRecording() }
                                        .padding(vertical = 13.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("● GRABAR", color = red, fontFamily = mono,
                                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                                Box(Modifier.weight(1f)) {
                                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF004422))
                                        .border(1.dp, green, RoundedCornerShape(8.dp))
                                        .clickable { bleManager.takePhoto() }
                                        .padding(vertical = 13.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("📷 FOTO", color = green, fontFamily = mono,
                                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF111111))
                            .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                            .clickable { bleManager.disconnect() }
                            .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("DESCONECTAR", color = Color(0xFF444444), fontFamily = mono,
                                fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp)
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissionsAndStart() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN))
                needed += Manifest.permission.BLUETOOTH_SCAN
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT))
                needed += Manifest.permission.BLUETOOTH_CONNECT
            if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE))
                needed += Manifest.permission.BLUETOOTH_ADVERTISE
        }
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        if (needed.isEmpty()) bleManager.startScan()
        else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
}

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
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
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
import io.karoo.insta360.ble.ConnectionState
import io.karoo.insta360.ble.Insta360BleManager

class MainActivity : ComponentActivity() {

    private lateinit var ble: Insta360BleManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> if (results.values.all { it }) ble.startPairing() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ble = Insta360BleManager(applicationContext)

        setContent {
            val connState by ble.connectionState.collectAsState()
            val camStatus by ble.cameraStatus.collectAsState()
            val secondsLeft by ble.pairingSecondsLeft.collectAsState()

            val green = Color(0xFF00FF88)
            val red = Color(0xFFFF2828)
            val amber = Color(0xFFFFCC00)
            val grey = Color(0xFF444444)
            val mono = FontFamily.Monospace

            Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0F)).padding(16.dp)) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("INSTA360 GO ULTRA", color = green, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, fontFamily = mono, letterSpacing = 3.sp)
                    Text("Mando BLE · Karoo 3", color = grey, fontSize = 11.sp, fontFamily = mono)
                    Divider(color = Color(0xFF1A1A1A))

                    // Estado
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0D0D14)).padding(12.dp)
                    ) {
                        Text("ESTADO", color = grey, fontSize = 9.sp, fontFamily = mono)
                        Text(
                            when (connState) {
                                ConnectionState.CONNECTED -> "CONECTADO"
                                ConnectionState.SCANNING -> "SINCRONIZANDO · ${secondsLeft}s"
                                ConnectionState.CONNECTING -> "CONECTANDO..."
                                ConnectionState.DISCONNECTED -> "DESCONECTADO"
                            },
                            color = when (connState) {
                                ConnectionState.CONNECTED -> green
                                ConnectionState.DISCONNECTED -> grey
                                else -> amber
                            },
                            fontSize = 14.sp, fontFamily = mono, fontWeight = FontWeight.Bold
                        )
                    }

                    when (connState) {
                        ConnectionState.DISCONNECTED -> {
                            Text(
                                "Pulsa SINCRONIZAR y, durante los 2 minutos siguientes, " +
                                    "pon la cámara a buscar un mando. Debería aparecer " +
                                    "\"Insta360 Mini Remote\".",
                                color = Color(0xFF666666), fontSize = 11.sp, fontFamily = mono
                            )
                            ActionButton("SINCRONIZAR", green, Color(0xFF004422)) {
                                requestPermissionsAndPair()
                            }
                        }
                        ConnectionState.SCANNING, ConnectionState.CONNECTING -> {
                            Text(
                                "Visible como \"Insta360 Mini Remote\". " +
                                    "Selecciónalo desde la cámara.",
                                color = amber, fontSize = 11.sp, fontFamily = mono
                            )
                            ActionButton("CANCELAR", Color(0xFF888888), Color(0xFF111111)) {
                                ble.stopPairing()
                            }
                        }
                        ConnectionState.CONNECTED -> {
                            ActionButton(
                                if (camStatus.isRecording) "■ PARAR" else "● GRABAR",
                                red, Color(0xFF440000)
                            ) { ble.toggleShutter() }
                            ActionButton("PANTALLA ON/OFF", green, Color(0xFF004422)) {
                                ble.toggleScreen()
                            }
                            ActionButton("APAGAR CÁMARA", red, Color(0xFF440000)) {
                                ble.powerOff()
                            }
                            ActionButton("DESCONECTAR", grey, Color(0xFF111111)) {
                                ble.disconnect()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissionsAndPair() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ).forEach { if (!has(it)) needed += it }
        }
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) needed += Manifest.permission.ACCESS_FINE_LOCATION
        if (needed.isEmpty()) ble.startPairing() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun has(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun ActionButton(label: String, border: Color, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = border, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp)
    }
}

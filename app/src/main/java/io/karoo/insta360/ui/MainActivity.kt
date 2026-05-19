package io.karoo.insta360.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.karoo.insta360.wifi.ConnectionState
import io.karoo.insta360.wifi.Insta360WifiManager

class MainActivity : ComponentActivity() {

    private lateinit var wifiManager: Insta360WifiManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifiManager = Insta360WifiManager(applicationContext)

        setContent {
            val connState by wifiManager.connectionState.collectAsState()
            val camStatus by wifiManager.cameraStatus.collectAsState()
            var ssid by remember { mutableStateOf("GO Ultra 1RWJRE") }
            var password by remember { mutableStateOf("") }

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
                    Text("Control vía WiFi · Karoo 3", color = Color(0xFF444444),
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
                            Text("ESTADO", color = Color(0xFF444444), fontSize = 9.sp, fontFamily = mono)
                            Text(
                                when (connState) {
                                    ConnectionState.CONNECTED   -> "CONECTADO"
                                    ConnectionState.CONNECTING  -> "CONECTANDO..."
                                    ConnectionState.DISCONNECTED -> "DESCONECTADO"
                                },
                                color = when (connState) {
                                    ConnectionState.CONNECTED   -> green
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
                        // Campos WiFi
                        Text("RED WIFI DE LA CÁMARA", color = Color(0xFF444444),
                            fontSize = 9.sp, fontFamily = mono, letterSpacing = 2.sp)

                        OutlinedTextField(
                            value = ssid,
                            onValueChange = { ssid = it },
                            label = { Text("Nombre WiFi (SSID)", color = Color(0xFF555555),
                                fontFamily = mono, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = green,
                                unfocusedBorderColor = Color(0xFF333333),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = green
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Contraseña WiFi", color = Color(0xFF555555),
                                fontFamily = mono, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = green,
                                unfocusedBorderColor = Color(0xFF333333),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = green
                            ),
                            singleLine = true
                        )

                        Text("Encuéntralos en: Cámara → Ajustes → WiFi",
                            color = Color(0xFF444444), fontSize = 10.sp, fontFamily = mono)

                        // Botón conectar
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF004422))
                                .border(1.dp, green, RoundedCornerShape(8.dp))
                                .clickable { wifiManager.connect(ssid, password) }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("CONECTAR", color = green, fontFamily = mono,
                                fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp)
                        }
                    }

                    // Controles cuando conectado
                    if (isConnected) {
                        if (isRecording) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) {
                                    Box(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF440000))
                                            .border(1.dp, red, RoundedCornerShape(8.dp))
                                            .clickable { wifiManager.stopRecording() }
                                            .padding(vertical = 13.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("■ STOP", color = red, fontFamily = mono,
                                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                                Box(Modifier.width(56.dp)) {
                                    Box(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1A1400))
                                            .border(1.dp, Color(0xFFFFCC00), RoundedCornerShape(8.dp))
                                            .clickable { wifiManager.markHighlight() }
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
                                    Box(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF440000))
                                            .border(1.dp, red, RoundedCornerShape(8.dp))
                                            .clickable { wifiManager.startRecording() }
                                            .padding(vertical = 13.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("● GRABAR", color = red, fontFamily = mono,
                                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                                Box(Modifier.weight(1f)) {
                                    Box(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF004422))
                                            .border(1.dp, green, RoundedCornerShape(8.dp))
                                            .clickable { wifiManager.takePhoto() }
                                            .padding(vertical = 13.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("📷 FOTO", color = green, fontFamily = mono,
                                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF111111))
                                .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                                .clickable { wifiManager.disconnect() }
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
}

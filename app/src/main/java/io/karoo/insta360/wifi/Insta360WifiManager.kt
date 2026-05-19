package io.karoo.insta360.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

data class CameraStatus(
    val isRecording: Boolean,
    val batteryLevel: Int,
    val mode: String,
)

class Insta360WifiManager(private val context: Context) {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _cameraStatus = MutableStateFlow(CameraStatus(false, -1, ""))
    val cameraStatus: StateFlow<CameraStatus> = _cameraStatus.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var statusJob: Job? = null
    private var boundNetwork: Network? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val BASE_URL = "http://192.168.42.1"
    private val JSON = "application/json".toMediaType()

    // ─── Conectar al WiFi de la cámara ──────────────────────────────────────
    fun connect(ssid: String, password: String) {
        _connectionState.value = ConnectionState.CONNECTING
        Timber.d("Conectando al WiFi de la cámara: $ssid")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Timber.d("WiFi de cámara disponible")
                    boundNetwork = network
                    _connectionState.value = ConnectionState.CONNECTED
                    startStatusPolling()
                }
                override fun onLost(network: Network) {
                    Timber.d("WiFi de cámara perdido")
                    boundNetwork = null
                    _connectionState.value = ConnectionState.DISCONNECTED
                    stopStatusPolling()
                }
            })
        } else {
            // Android < 10: conectar manualmente
            _connectionState.value = ConnectionState.CONNECTED
            startStatusPolling()
        }
    }

    fun disconnect() {
        stopStatusPolling()
        boundNetwork = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _cameraStatus.value = CameraStatus(false, -1, "")
    }

    // ─── Polling de estado cada 2 segundos ──────────────────────────────────
    private fun startStatusPolling() {
        statusJob = scope.launch {
            while (isActive) {
                fetchStatus()
                delay(2000)
            }
        }
    }

    private fun stopStatusPolling() {
        statusJob?.cancel()
        statusJob = null
    }

    private suspend fun fetchStatus() {
        try {
            val response = get("$BASE_URL/osc/state") ?: return
            val json = JSONObject(response)
            val state = json.optJSONObject("state") ?: return
            val isRecording = state.optInt("_captureStatus", 0) == 1
            val battery = state.optInt("_battery", -1)
            val mode = state.optString("_captureMode", "")
            _cameraStatus.value = CameraStatus(isRecording, battery, mode)
        } catch (e: Exception) {
            Timber.e("Error polling estado: ${e.message}")
        }
    }

    // ─── Comandos ────────────────────────────────────────────────────────────
    fun startRecording() = executeCommand("camera.startCapture")
    fun stopRecording()  = executeCommand("camera.stopCapture")
    fun takePhoto()      = executeCommand("camera.takePicture")

    fun markHighlight() {
        scope.launch {
            post("$BASE_URL/osc/commands/execute",
                """{"name":"camera._setMute","parameters":{}}""")
        }
    }

    private fun executeCommand(name: String, parameters: Map<String, Any> = emptyMap()) {
        scope.launch {
            try {
                val params = JSONObject(parameters)
                val body = """{"name":"$name","parameters":$params}"""
                val response = post("$BASE_URL/osc/commands/execute", body)
                Timber.d("Comando $name → $response")
                // Actualizar estado inmediatamente
                fetchStatus()
            } catch (e: Exception) {
                Timber.e("Error comando $name: ${e.message}")
            }
        }
    }

    // ─── HTTP helpers ────────────────────────────────────────────────────────
    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).get().build()
            val call = boundNetwork?.let {
                OkHttpClient.Builder()
                    .socketFactory(it.socketFactory)
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                    .newCall(request)
            } ?: client.newCall(request)
            call.execute().use { it.body?.string() }
        } catch (e: IOException) {
            Timber.e("GET error: ${e.message}")
            null
        }
    }

    private suspend fun post(url: String, json: String): String? = withContext(Dispatchers.IO) {
        try {
            val body = json.toRequestBody(JSON)
            val request = Request.Builder().url(url).post(body).build()
            val call = boundNetwork?.let {
                OkHttpClient.Builder()
                    .socketFactory(it.socketFactory)
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                    .newCall(request)
            } ?: client.newCall(request)
            call.execute().use { it.body?.string() }
        } catch (e: IOException) {
            Timber.e("POST error: ${e.message}")
            null
        }
    }

    val isConnected: Boolean
        get() = _connectionState.value == ConnectionState.CONNECTED
}

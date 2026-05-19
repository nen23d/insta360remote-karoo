package io.karoo.insta360.extension

import android.content.Context
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.karoo.insta360.ble.ConnectionState
import io.karoo.insta360.ble.Insta360BleManager
import io.karoo.insta360.ble.Insta360BleService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber

class Insta360Extension : KarooExtension("io.karoo.insta360", "1") {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var bleManager: Insta360BleManager

    override fun onCreate() {
        super.onCreate()
        bleManager = Insta360BleManager(applicationContext)
        Timber.d("Insta360Extension creada")
    }

    override fun onDestroy() {
        bleManager.disconnect()
        scope.cancel()
        super.onDestroy()
    }
}

package io.karoo.insta360

import android.app.Application
import timber.log.Timber

class Insta360App : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}

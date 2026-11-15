package com.ares.callguard

import android.app.Application
import com.ares.callguard.alert.AlertNotifier
import com.ares.callguard.guard.Guard
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AresApp : Application() {

    @Inject lateinit var notifier: AlertNotifier

    // Injected so the guard starts watching its own state as soon as any part of the app
    // wakes up, including a broadcast delivered to a freshly started process.
    @Inject lateinit var guard: Guard

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
    }
}

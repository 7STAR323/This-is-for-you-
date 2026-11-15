package com.ares.callguard.detection

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ares.callguard.di.ApplicationScope
import com.ares.callguard.guard.DetectionLog
import com.ares.callguard.guard.Guard
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Android hands a notification listener every notification on the phone. Anything that isn't
 * from a supported calling app is dropped in the first line, before it is looked at.
 */
@AndroidEntryPoint
class CallNotificationListener : NotificationListenerService() {

    @Inject lateinit var tracker: AppCallTracker
    @Inject lateinit var guard: Guard
    @Inject lateinit var log: DetectionLog

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!CallNotificationReader.isSupportedApp(sbn.packageName)) return
        val call = CallNotificationReader.read(sbn)
        val description = CallNotificationReader.describe(sbn)
        scope.launch {
            if (!guard.isWanted()) return@launch
            description?.let { log.add("${sbn.packageName}: $it") }
            call?.let { tracker.onPosted(it) }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!CallNotificationReader.isSupportedApp(sbn.packageName)) return
        scope.launch { tracker.onRemoved(sbn.key) }
    }

    override fun onListenerConnected() {
        guard.recheckSetup()
    }

    override fun onListenerDisconnected() {
        // ColorOS sometimes drops the connection while leaving the permission granted.
        requestRebind(ComponentName(this, CallNotificationListener::class.java))
    }
}

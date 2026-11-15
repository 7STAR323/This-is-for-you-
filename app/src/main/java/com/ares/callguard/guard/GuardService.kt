package com.ares.callguard.guard

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.ares.callguard.alert.AlertNotifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Does no work of its own. Its job is to keep the app's process alive (and visibly so) while
 * ARES is ACTIVE, so the grace timer and alert can run when a call arrives.
 */
@AndroidEntryPoint
class GuardService : Service() {

    @Inject lateinit var notifier: AlertNotifier

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            AlertNotifier.WATCHING_NOTIFICATION_ID,
            notifier.watchingNotification(),
            type,
        )
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

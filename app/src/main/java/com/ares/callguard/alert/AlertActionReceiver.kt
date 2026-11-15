package com.ares.callguard.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ares.callguard.guard.CallSessionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Handles the buttons on ARES's own notifications. */
@AndroidEntryPoint
class AlertActionReceiver : BroadcastReceiver() {

    @Inject lateinit var sessions: CallSessionManager
    @Inject lateinit var notifier: AlertNotifier

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP_ALERT -> sessions.stopAlertByUser()
            ACTION_DISMISS -> notifier.cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
        }
    }

    companion object {
        private const val ACTION_STOP_ALERT = "com.ares.callguard.STOP_ALERT"
        private const val ACTION_DISMISS = "com.ares.callguard.DISMISS"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun stopAlert(context: Context) =
            Intent(context, AlertActionReceiver::class.java).setAction(ACTION_STOP_ALERT)

        fun dismiss(context: Context, notificationId: Int) =
            Intent(context, AlertActionReceiver::class.java)
                .setAction(ACTION_DISMISS)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
    }
}

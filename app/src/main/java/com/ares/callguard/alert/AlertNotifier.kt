package com.ares.callguard.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.ares.callguard.MainActivity
import com.ares.callguard.R
import com.ares.callguard.detection.CallNotificationClassifier
import com.ares.callguard.domain.model.Platform
import com.ares.callguard.guard.ActiveAlert
import com.ares.callguard.guard.PriorityCall
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The visible half of an alert; the audible half is [AlertPlayer]. */
interface AlertDisplay {
    fun showAlert(alert: ActiveAlert)
    fun cancelAlert()
    fun showMissedCall(call: PriorityCall)
}

@Singleton
class AlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlertDisplay {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        // Every channel is silent: the sound and vibration come from AlertPlayer, which can
        // reach the alarm stream. A channel's own sound would be muted in Silent mode anyway.
        manager.createNotificationChannels(
            listOf(
                silentChannel(CHANNEL_WATCHING, R.string.channel_watching, NotificationManager.IMPORTANCE_LOW),
                silentChannel(CHANNEL_ALERT, R.string.channel_alert, NotificationManager.IMPORTANCE_HIGH)
                    .apply { setBypassDnd(true) },
                silentChannel(CHANNEL_MISSED, R.string.channel_missed, NotificationManager.IMPORTANCE_HIGH)
                    .apply { setBypassDnd(true) },
            ),
        )
        // A channel's Do Not Disturb setting is fixed when it is created, so the channels
        // from the first version are replaced rather than edited.
        RETIRED_CHANNELS.forEach(manager::deleteNotificationChannel)
    }

    fun watchingNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_WATCHING)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(context.getString(R.string.status_watching))
            .setContentIntent(activity(Intent(context, MainActivity::class.java), requestCode = 0))
            .setOngoing(true)
            .setShowWhen(false)
            .build()

    override fun showAlert(alert: ActiveAlert) {
        val openAlert = activity(
            Intent(context, AlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            requestCode = 1,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(alert.personName)
            .setContentText(context.getString(R.string.alert_reason))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(openAlert)
            .setFullScreenIntent(openAlert, true)
            .addAction(0, context.getString(R.string.stop_alert), broadcast(AlertActionReceiver.stopAlert(context), 2))
            .build()
        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    override fun cancelAlert() = manager.cancel(ALERT_NOTIFICATION_ID)

    override fun showMissedCall(call: PriorityCall) {
        val id = MISSED_NOTIFICATION_BASE + (call.person.id.toInt() * 10 + call.platform.ordinal)
        val builder = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(context.getString(R.string.missed_call_title, call.person.name))
            .setContentText(context.getString(R.string.missed_call_text, call.platform.label))
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .addAction(0, context.getString(R.string.dismiss), broadcast(AlertActionReceiver.dismiss(context, id), id))

        callBackIntent(call)?.let { callBack ->
            val pending = activity(callBack, requestCode = id)
            builder.setContentIntent(pending)
            builder.addAction(0, context.getString(R.string.call_back), pending)
        }
        manager.notify(id, builder.build())
    }

    fun cancel(notificationId: Int) = manager.cancel(notificationId)

    /** Opens the right app ready to call back. It never places the call itself. */
    private fun callBackIntent(call: PriorityCall): Intent? = when (call.platform) {
        Platform.SIM -> call.phoneNumber?.let { Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", it, null)) }
        else -> CallNotificationClassifier.packagesOf(call.platform)
            .firstNotNullOfOrNull(context.packageManager::getLaunchIntentForPackage)
    }

    private fun silentChannel(id: String, nameRes: Int, importance: Int) =
        NotificationChannel(id, context.getString(nameRes), importance).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

    private fun activity(intent: Intent, requestCode: Int) = PendingIntent.getActivity(
        context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun broadcast(intent: Intent, requestCode: Int) = PendingIntent.getBroadcast(
        context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val WATCHING_NOTIFICATION_ID = 1
        private const val ALERT_NOTIFICATION_ID = 2
        private const val MISSED_NOTIFICATION_BASE = 1000

        private const val CHANNEL_WATCHING = "watching"
        private const val CHANNEL_ALERT = "priority_alert_2"
        private const val CHANNEL_MISSED = "missed_call_2"
        private val RETIRED_CHANNELS = listOf("priority_alert", "missed_call")
    }
}

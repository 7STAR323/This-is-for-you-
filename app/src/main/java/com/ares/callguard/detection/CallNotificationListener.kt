package com.ares.callguard.detection

import android.app.Notification
import android.app.Person
import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.os.BundleCompat
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
        if (CallNotificationClassifier.platformOf(sbn.packageName) == null) return
        val snapshot = snapshotOf(sbn)
        scope.launch {
            if (guard.isWanted()) tracker.onPosted(snapshot)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (CallNotificationClassifier.platformOf(sbn.packageName) == null) return
        scope.launch { tracker.onRemoved(sbn.key) }
    }

    override fun onListenerConnected() {
        log.add("Connected to notifications")
        guard.recheckSetup()
        // A call may already be ringing if the phone only just (re)connected us.
        runCatching { activeNotifications }.getOrNull()?.forEach(::onNotificationPosted)
    }

    override fun onListenerDisconnected() {
        log.add("Lost the connection to notifications; asking Android to reconnect")
        // ColorOS sometimes drops the connection while leaving the permission granted.
        requestRebind(ComponentName(this, CallNotificationListener::class.java))
    }

    private fun snapshotOf(sbn: StatusBarNotification): NotificationSnapshot {
        val notification = sbn.notification
        val extras = notification.extras
        val people = peopleIn(extras)
        return NotificationSnapshot(
            packageName = sbn.packageName,
            key = sbn.key,
            category = notification.category,
            template = extras.getString(Notification.EXTRA_TEMPLATE),
            callType = extras.getInt(EXTRA_CALL_TYPE),
            channelId = notification.channelId,
            hasFullScreenIntent = notification.fullScreenIntent != null,
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            showsChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),
            actionCount = notification.actions?.size ?: 0,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            personNames = people.mapNotNull { it.name?.toString() },
            personUris = people.mapNotNull { it.uri } + legacyPeopleUris(extras),
            shortcutId = notification.shortcutId,
            tag = sbn.tag,
        )
    }

    private fun peopleIn(extras: Bundle): List<Person> = runCatching {
        listOfNotNull(BundleCompat.getParcelable(extras, EXTRA_CALL_PERSON, Person::class.java)) +
            BundleCompat.getParcelableArrayList(extras, Notification.EXTRA_PEOPLE_LIST, Person::class.java).orEmpty()
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION") // Older apps still list people as plain URIs here.
    private fun legacyPeopleUris(extras: Bundle): List<String> =
        extras.getStringArray(Notification.EXTRA_PEOPLE)?.toList().orEmpty()

    private companion object {
        // Written by Notification.CallStyle; the constants themselves aren't public API.
        const val EXTRA_CALL_TYPE = "android.callType"
        const val EXTRA_CALL_PERSON = "android.callPerson"
    }
}

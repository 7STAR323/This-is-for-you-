package com.ares.callguard.detection

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.ares.callguard.domain.model.Platform

enum class CallNotificationKind { INCOMING, ONGOING, MISSED }

/** The only facts ARES keeps from another app's notification. */
data class CallNotification(
    val key: String,
    val platform: Platform,
    val kind: CallNotificationKind,
    val callerName: String,
)

/**
 * Recognises call notifications from the supported calling apps, and nothing else. Messages,
 * group chats and every other notification come back as null and are dropped on the spot.
 */
object CallNotificationReader {

    private val platformByPackage = mapOf(
        "com.whatsapp" to Platform.WHATSAPP,
        "com.whatsapp.w4b" to Platform.WHATSAPP,
    )

    fun isSupportedApp(packageName: String) = packageName in platformByPackage

    fun read(sbn: StatusBarNotification): CallNotification? {
        val platform = platformByPackage[sbn.packageName] ?: return null
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null

        val kind = kindOf(notification) ?: return null
        val name = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        if (name.isNullOrEmpty()) return null
        return CallNotification(sbn.key, platform, kind, name)
    }

    /** A one-line technical description for the diagnostics log; contains no message text. */
    fun describe(sbn: StatusBarNotification): String? {
        val notification = sbn.notification
        val callType = notification.extras.getInt(EXTRA_CALL_TYPE)
        val looksCallRelated = notification.category in CALL_CATEGORIES || callType != 0
        if (!looksCallRelated) return null
        return "category=${notification.category} callType=$callType " +
            "channel=${notification.channelId} fullScreen=${notification.fullScreenIntent != null} " +
            "actions=${notification.actions?.size ?: 0} → ${kindOf(notification) ?: "ignored"}"
    }

    private fun kindOf(notification: Notification): CallNotificationKind? {
        val extras = notification.extras
        val callType = extras.getInt(EXTRA_CALL_TYPE)
        val isCall = notification.category == Notification.CATEGORY_CALL || callType != 0

        return when {
            notification.category == Notification.CATEGORY_MISSED_CALL -> CallNotificationKind.MISSED
            isCall && callType == CALL_TYPE_INCOMING -> CallNotificationKind.INCOMING
            isCall && callType == CALL_TYPE_ONGOING -> CallNotificationKind.ONGOING
            isCall && extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) -> CallNotificationKind.ONGOING
            // Apps that don't use Android's call style still ring with a full-screen request.
            isCall && notification.fullScreenIntent != null -> CallNotificationKind.INCOMING
            saysMissedCall(extras.getCharSequence(Notification.EXTRA_TEXT)) -> CallNotificationKind.MISSED
            else -> null
        }
    }

    // WhatsApp doesn't always mark missed calls with a category, so fall back to its wording.
    // This fallback only understands English; other languages rely on the category.
    private fun saysMissedCall(text: CharSequence?): Boolean {
        val lowered = text?.toString()?.lowercase() ?: return false
        return "missed" in lowered && "call" in lowered
    }

    private val CALL_CATEGORIES = setOf(Notification.CATEGORY_CALL, Notification.CATEGORY_MISSED_CALL)

    // Notification.CallStyle writes these, but the constants themselves aren't public API.
    private const val EXTRA_CALL_TYPE = "android.callType"
    private const val CALL_TYPE_INCOMING = 1
    private const val CALL_TYPE_ONGOING = 2
}

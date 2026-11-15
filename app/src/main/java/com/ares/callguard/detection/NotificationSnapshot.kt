package com.ares.callguard.detection

/**
 * The parts of another app's notification that ARES is allowed to look at, copied out of the
 * Android object so the rules that interpret them can be tested without a phone.
 */
data class NotificationSnapshot(
    val packageName: String,
    val key: String,
    val category: String? = null,
    val template: String? = null,
    val callType: Int = 0,
    val channelId: String? = null,
    val hasFullScreenIntent: Boolean = false,
    val isOngoing: Boolean = false,
    val isGroupSummary: Boolean = false,
    val showsChronometer: Boolean = false,
    val actionCount: Int = 0,
    val title: String? = null,
    val text: String? = null,
    val personNames: List<String> = emptyList(),
    val personUris: List<String> = emptyList(),
    val shortcutId: String? = null,
    val tag: String? = null,
)

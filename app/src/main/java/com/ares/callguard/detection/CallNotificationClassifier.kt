package com.ares.callguard.detection

import com.ares.callguard.domain.matching.CallerIdentity
import com.ares.callguard.domain.matching.ContactMatcher
import com.ares.callguard.domain.model.Platform

enum class CallNotificationKind { INCOMING, ONGOING, MISSED }

/**
 * Works out whether a notification from a calling app is a call, and which stage of one.
 * It relies on how the notification is built (its category, call style and buttons) rather
 * than on its wording, so it does not depend on the phone's language. Anything that is not
 * clearly a call, such as a message or a group chat, is classified as nothing.
 */
object CallNotificationClassifier {

    private val platformByPackage = mapOf(
        "com.whatsapp" to Platform.WHATSAPP,
        "com.whatsapp.w4b" to Platform.WHATSAPP,
        "org.telegram.messenger" to Platform.TELEGRAM,
        "org.telegram.messenger.web" to Platform.TELEGRAM,
        "com.facebook.orca" to Platform.MESSENGER,
        "com.facebook.mlite" to Platform.MESSENGER,
        "com.instagram.android" to Platform.INSTAGRAM,
        "com.snapchat.android" to Platform.SNAPCHAT,
    )

    fun platformOf(packageName: String): Platform? = platformByPackage[packageName]

    fun packagesOf(platform: Platform): List<String> =
        platformByPackage.filterValues { it == platform }.keys.toList()

    fun kindOf(notification: NotificationSnapshot): CallNotificationKind? {
        if (notification.isGroupSummary) return null

        val usesCallStyle = notification.template == CALL_STYLE || notification.callType != 0
        val isCall = usesCallStyle || notification.category == CATEGORY_CALL

        return when {
            notification.category == CATEGORY_MISSED_CALL -> CallNotificationKind.MISSED
            notification.callType == CALL_TYPE_ONGOING -> CallNotificationKind.ONGOING
            notification.callType == CALL_TYPE_INCOMING ||
                notification.callType == CALL_TYPE_SCREENING -> CallNotificationKind.INCOMING
            isCall && notification.showsChronometer -> CallNotificationKind.ONGOING
            // Without the call style, a ringing call still asks for the whole screen or
            // offers both an answer and a decline button. An outgoing or ongoing call
            // offers only "hang up".
            isCall && (notification.hasFullScreenIntent || notification.actionCount >= 2) ->
                CallNotificationKind.INCOMING
            looksLikeMissedCall(notification) -> CallNotificationKind.MISSED
            else -> null
        }
    }

    /** Who the notification says is calling. Never guesses: absent data stays absent. */
    fun callerOf(notification: NotificationSnapshot): CallerIdentity {
        val title = notification.title?.let(::visibleText).orEmpty()
        val titleIsNumber = PHONE_NUMBER.matches(title)

        val numbers = buildList {
            notification.personUris.forEach { uri ->
                if (uri.startsWith(TEL_SCHEME, ignoreCase = true)) add(uri.substring(TEL_SCHEME.length))
            }
            // WhatsApp addresses a chat by "<number>@s.whatsapp.net" where it still uses numbers.
            listOfNotNull(notification.shortcutId, notification.tag).forEach { id ->
                WHATSAPP_NUMBER_ID.find(id)?.let { add("+" + it.groupValues[1]) }
            }
            // An unsaved caller is shown by number instead of by name.
            if (titleIsNumber) add(title)
        }
        val names = buildList {
            if (title.isNotEmpty() && !titleIsNumber) add(title)
            addAll(notification.personNames.map(::visibleText).filter(String::isNotEmpty))
        }
        return CallerIdentity(numbers.distinct(), names.distinctBy(ContactMatcher::comparableName))
    }

    /** One line for the diagnostics screen. Describes the notification's shape, never its words. */
    fun describe(notification: NotificationSnapshot): String {
        val caller = callerOf(notification)
        return buildString {
            append("category=").append(notification.category ?: "none")
            if (notification.template != null) append(" style=").append(notification.template.substringAfterLast('$'))
            if (notification.callType != 0) append(" callType=").append(notification.callType)
            append(" channel=").append(notification.channelId ?: "none")
            append(" fullScreen=").append(yesNo(notification.hasFullScreenIntent))
            append(" ongoing=").append(yesNo(notification.isOngoing))
            append(" timer=").append(yesNo(notification.showsChronometer))
            append(" buttons=").append(notification.actionCount)
            append(" name=").append(yesNo(caller.displayNames.isNotEmpty()))
            append(" number=").append(yesNo(caller.phoneNumbers.isNotEmpty()))
        }
    }

    // Apps don't always mark missed calls with the category, so fall back to the channel
    // name and, last, to the app's own English phrase. The phrase must be the whole text and
    // must not sit in a chat-style notification, or a message saying "missed your call"
    // would count. Other languages rely on the category and channel.
    private fun looksLikeMissedCall(notification: NotificationSnapshot): Boolean {
        if (notification.channelId?.contains("missed", ignoreCase = true) == true) return true
        if (notification.template == MESSAGING_STYLE) return false
        val text = notification.text?.let(::visibleText)?.lowercase() ?: return false
        return MISSED_CALL_PHRASE.matches(text)
    }

    private fun visibleText(text: String): String =
        text.filterNot { Character.getType(it) == Character.FORMAT.toInt() }.trim()

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"

    const val CALL_STYLE = "android.app.Notification\$CallStyle"
    const val MESSAGING_STYLE = "android.app.Notification\$MessagingStyle"
    const val CATEGORY_CALL = "call"
    const val CATEGORY_MISSED_CALL = "missed_call"

    // Written by Notification.CallStyle; the constants themselves aren't public API.
    const val CALL_TYPE_INCOMING = 1
    const val CALL_TYPE_ONGOING = 2
    const val CALL_TYPE_SCREENING = 3

    private const val TEL_SCHEME = "tel:"
    private val PHONE_NUMBER = Regex("""\+?[\d\s\-().]{7,20}""")
    private val MISSED_CALL_PHRASE = Regex("""(\d+ )?missed (group )?(voice |video )?calls?""")
    private val WHATSAPP_NUMBER_ID = Regex("""(\d{7,15})@s\.whatsapp\.net""")
}

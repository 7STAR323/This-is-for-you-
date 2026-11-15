package com.ares.callguard.detection

import com.ares.callguard.detection.CallNotificationClassifier.CALL_STYLE
import com.ares.callguard.detection.CallNotificationClassifier.callerOf
import com.ares.callguard.detection.CallNotificationClassifier.describe
import com.ares.callguard.detection.CallNotificationClassifier.kindOf
import com.ares.callguard.domain.matching.CallerIdentity
import com.ares.callguard.domain.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CallNotificationClassifierTest {

    private fun whatsApp(
        category: String? = null,
        template: String? = null,
        callType: Int = 0,
        channelId: String? = null,
        fullScreen: Boolean = false,
        chronometer: Boolean = false,
        actions: Int = 0,
        groupSummary: Boolean = false,
        title: String? = "Alishuu ❤️",
        text: String? = null,
        personUris: List<String> = emptyList(),
        shortcutId: String? = null,
    ) = NotificationSnapshot(
        packageName = "com.whatsapp",
        key = "key",
        category = category,
        template = template,
        callType = callType,
        channelId = channelId,
        hasFullScreenIntent = fullScreen,
        showsChronometer = chronometer,
        actionCount = actions,
        isGroupSummary = groupSummary,
        title = title,
        text = text,
        personUris = personUris,
        shortcutId = shortcutId,
    )

    @Test
    fun `supported apps map to their platform and everything else is ignored`() {
        assertEquals(Platform.WHATSAPP, CallNotificationClassifier.platformOf("com.whatsapp"))
        assertEquals(Platform.WHATSAPP, CallNotificationClassifier.platformOf("com.whatsapp.w4b"))
        assertEquals(Platform.TELEGRAM, CallNotificationClassifier.platformOf("org.telegram.messenger"))
        assertNull(CallNotificationClassifier.platformOf("com.facebook.katana"))
        assertNull(CallNotificationClassifier.platformOf("com.android.mms"))
    }

    @Test
    fun `a call-style incoming notification is a ringing call`() {
        assertEquals(CallNotificationKind.INCOMING, kindOf(whatsApp(category = "call", template = CALL_STYLE, callType = 1)))
    }

    @Test
    fun `call style is recognised even when the app sets no category`() {
        assertEquals(CallNotificationKind.INCOMING, kindOf(whatsApp(callType = 1)))
    }

    @Test
    fun `a call notification asking for the full screen is a ringing call`() {
        assertEquals(CallNotificationKind.INCOMING, kindOf(whatsApp(category = "call", fullScreen = true)))
    }

    @Test
    fun `a call notification with answer and decline buttons is a ringing call`() {
        assertEquals(CallNotificationKind.INCOMING, kindOf(whatsApp(category = "call", actions = 2)))
    }

    @Test
    fun `an ongoing call is not mistaken for a ringing one`() {
        assertEquals(CallNotificationKind.ONGOING, kindOf(whatsApp(category = "call", callType = 2, actions = 1)))
        assertEquals(CallNotificationKind.ONGOING, kindOf(whatsApp(category = "call", chronometer = true, actions = 2)))
    }

    @Test
    fun `an outgoing call with only a hang-up button is nothing`() {
        assertNull(kindOf(whatsApp(category = "call", actions = 1)))
    }

    @Test
    fun `a missed call is recognised by category, channel or wording`() {
        assertEquals(CallNotificationKind.MISSED, kindOf(whatsApp(category = "missed_call")))
        assertEquals(CallNotificationKind.MISSED, kindOf(whatsApp(category = "msg", channelId = "missed_calls_3")))
        assertEquals(CallNotificationKind.MISSED, kindOf(whatsApp(text = "Missed voice call")))
    }

    @Test
    fun `a missed call is never treated as ringing`() {
        val missed = whatsApp(category = "missed_call", fullScreen = true, actions = 2)
        assertEquals(CallNotificationKind.MISSED, kindOf(missed))
    }

    @Test
    fun `an ordinary message is not a call`() {
        assertNull(kindOf(whatsApp(category = "msg", actions = 2, text = "Call me when you can")))
        assertNull(kindOf(whatsApp(category = "msg", fullScreen = true, text = "hello")))
    }

    @Test
    fun `a message that talks about a missed call is not a missed call`() {
        assertNull(kindOf(whatsApp(category = "msg", text = "Missed your call, ring me")))
        assertNull(kindOf(whatsApp(text = "Sorry I missed the call")))
        assertNull(kindOf(whatsApp(template = CallNotificationClassifier.MESSAGING_STYLE, text = "Missed call")))
    }

    @Test
    fun `the app's own missed-call phrases are recognised`() {
        listOf("Missed voice call", "Missed video call", "Missed call", "2 missed calls", "Missed group voice call")
            .forEach { assertEquals(it, CallNotificationKind.MISSED, kindOf(whatsApp(text = it))) }
    }

    @Test
    fun `a ringing call marked as a group summary is still a ringing call`() {
        // Seen on an OPPO Reno with WhatsApp: channel voip_notification_15, call style,
        // full screen, two buttons, and the group-summary flag set.
        val seenOnDevice = whatsApp(
            category = "call",
            template = CALL_STYLE,
            callType = 1,
            channelId = "voip_notification_15",
            fullScreen = true,
            actions = 2,
            groupSummary = true,
        )
        assertEquals(CallNotificationKind.INCOMING, kindOf(seenOnDevice))
    }

    @Test
    fun `a group summary is not guessed to be a missed call from its wording`() {
        assertNull(kindOf(whatsApp(groupSummary = true, text = "2 missed calls")))
        assertEquals(CallNotificationKind.MISSED, kindOf(whatsApp(groupSummary = true, category = "missed_call")))
    }

    @Test
    fun `a notification with no call evidence at all is nothing`() {
        assertNull(kindOf(whatsApp()))
    }

    @Test
    fun `caller name is read from the title without invisible marks`() {
        val caller = callerOf(whatsApp(title = "‪Alishuu ❤️‬"))
        assertEquals(CallerIdentity(displayNames = listOf("Alishuu ❤️")), caller)
    }

    @Test
    fun `caller number is read from a tel link when the app provides one`() {
        val caller = callerOf(whatsApp(personUris = listOf("tel:+919876543210")))
        assertEquals(listOf("+919876543210"), caller.phoneNumbers)
        assertEquals(listOf("Alishuu ❤️"), caller.displayNames)
    }

    @Test
    fun `caller number is read from a WhatsApp number address`() {
        val caller = callerOf(whatsApp(shortcutId = "919876543210@s.whatsapp.net"))
        assertEquals(listOf("+919876543210"), caller.phoneNumbers)
    }

    @Test
    fun `a WhatsApp privacy address reveals no number`() {
        val caller = callerOf(whatsApp(shortcutId = "184467440737095@lid"))
        assertEquals(emptyList<String>(), caller.phoneNumbers)
    }

    @Test
    fun `an unsaved caller shown by number is a number, not a name`() {
        val caller = callerOf(whatsApp(title = "‪+91 98765 43210‬"))
        assertEquals(CallerIdentity(phoneNumbers = listOf("+91 98765 43210")), caller)
    }

    @Test
    fun `missing metadata yields an empty identity rather than a guess`() {
        assertEquals(CallerIdentity(), callerOf(whatsApp(title = null)))
        assertEquals(CallerIdentity(), callerOf(whatsApp(title = "  ")))
    }

    @Test
    fun `the diagnostics line never contains the caller's name, number or message`() {
        val line = describe(
            whatsApp(
                category = "call",
                callType = 1,
                title = "Alishuu ❤️",
                text = "Incoming voice call",
                personUris = listOf("tel:+919876543210"),
            ),
        )
        assertFalse("Alishuu" in line)
        assertFalse("9876543210" in line)
        assertFalse("Incoming" in line)
    }
}

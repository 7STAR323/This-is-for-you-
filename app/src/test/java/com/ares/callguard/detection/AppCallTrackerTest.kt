package com.ares.callguard.detection

import com.ares.callguard.FakePeopleRepository
import com.ares.callguard.FakeSeenCallerRepository
import com.ares.callguard.FakeSettingsRepository
import com.ares.callguard.RecordingCallSink
import com.ares.callguard.detection.CallNotificationClassifier.CALL_STYLE
import com.ares.callguard.domain.matching.ContactMatcher
import com.ares.callguard.domain.matching.PhoneNumberNormalizer
import com.ares.callguard.domain.model.GuardSettings
import com.ares.callguard.domain.model.Person
import com.ares.callguard.domain.model.Platform
import com.ares.callguard.domain.model.PlatformIdentity
import com.ares.callguard.guard.DetectionLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppCallTrackerTest {

    private val alishuu = Person(
        id = 1,
        name = "Alishuu",
        phoneNumbers = listOf("+91 98765 43210"),
        platforms = setOf(Platform.SIM, Platform.WHATSAPP),
        identities = listOf(PlatformIdentity(Platform.WHATSAPP, "Alishuu ❤️")),
    )

    private val sink = RecordingCallSink()
    private val seen = FakeSeenCallerRepository()
    private val log = DetectionLog()
    private var settings = FakeSettingsRepository()

    private fun TestScope.tracker() = AppCallTracker(
        scope = backgroundScope,
        people = FakePeopleRepository(listOf(alishuu)),
        seenCallers = seen,
        settings = settings,
        matcher = ContactMatcher(PhoneNumberNormalizer { "IN" }),
        sessions = sink,
        log = log,
    )

    /** Lets the tracker's wait for a call's ending run out. */
    private fun TestScope.settle() {
        advanceTimeBy(5_000)
        runCurrent()
    }

    private fun ringing(title: String? = "Alishuu ❤️", key: String = "ring", shortcutId: String? = null) =
        NotificationSnapshot(
            packageName = "com.whatsapp",
            key = key,
            category = "call",
            template = CALL_STYLE,
            callType = 1,
            hasFullScreenIntent = true,
            actionCount = 2,
            title = title,
            shortcutId = shortcutId,
        )

    private fun ongoing(key: String = "ongoing") = NotificationSnapshot(
        packageName = "com.whatsapp", key = key, category = "call", callType = 2, actionCount = 1, title = "Alishuu ❤️",
    )

    private fun missed(title: String = "Alishuu ❤️") = NotificationSnapshot(
        packageName = "com.whatsapp", key = "missed", category = "missed_call", title = title, text = "Missed voice call",
    )

    private fun message() = NotificationSnapshot(
        packageName = "com.whatsapp", key = "msg", category = "msg", title = "Alishuu ❤️", text = "Missed your call, ring me",
    )

    @Test
    fun `a ringing call from a chosen person starts one session`() = runTest {
        tracker().onPosted(ringing())
        assertEquals(listOf("incoming Alishuu call1"), sink.events)
    }

    @Test
    fun `repeated updates of the ringing notification do not start a second session`() = runTest {
        val tracker = tracker()
        repeat(5) { tracker.onPosted(ringing()) }
        assertEquals(listOf("incoming Alishuu call1"), sink.events)
    }

    @Test
    fun `a message from the same person starts nothing`() = runTest {
        tracker().onPosted(message())
        settle()
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun `a ringing call from someone else starts nothing but is remembered for Test match`() = runTest {
        tracker().onPosted(ringing(title = "Stranger"))
        assertTrue(sink.events.isEmpty())
        assertEquals(listOf("Stranger"), seen.remembered.map { it.displayName })
    }

    @Test
    fun `a ringing call with no caller shown is ignored and explained`() = runTest {
        tracker().onPosted(ringing(title = null))
        assertTrue(sink.events.isEmpty())
        assertTrue(log.entries.value.any { "neither a name nor a number" in it.message })
    }

    @Test
    fun `a revealed number that is not theirs overrides a matching name`() = runTest {
        tracker().onPosted(ringing(shortcutId = "917000000000@s.whatsapp.net"))
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun `a revealed number that is theirs matches even under another name`() = runTest {
        tracker().onPosted(ringing(title = "Renamed", shortcutId = "919876543210@s.whatsapp.net"))
        assertEquals(listOf("incoming Alishuu call1"), sink.events)
    }

    @Test
    fun `number-only mode ignores a name match`() = runTest {
        settings = FakeSettingsRepository(GuardSettings(numberMatchOnly = true))
        tracker().onPosted(ringing())
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun `ringing then an ongoing-call notification means answered`() = runTest {
        val tracker = tracker()
        tracker.onPosted(ringing())
        tracker.onRemoved("ring")
        tracker.onPosted(ongoing())
        settle()
        assertEquals(listOf("incoming Alishuu call1", "stopped call1", "answered call1"), sink.events)
    }

    @Test
    fun `a ringing notification that turns into the ongoing one means answered`() = runTest {
        val tracker = tracker()
        tracker.onPosted(ringing())
        tracker.onPosted(ongoing(key = "ring"))
        assertEquals(listOf("incoming Alishuu call1", "answered call1"), sink.events)
    }

    @Test
    fun `ringing then a missed-call notification means missed, reported once`() = runTest {
        val tracker = tracker()
        tracker.onPosted(ringing())
        tracker.onRemoved("ring")
        tracker.onPosted(missed())
        settle()
        assertEquals(listOf("incoming Alishuu call1", "stopped call1", "missed call1"), sink.events)
    }

    @Test
    fun `ringing that ends with nothing left behind counts as declined`() = runTest {
        val tracker = tracker()
        tracker.onPosted(ringing())
        tracker.onRemoved("ring")
        settle()
        assertEquals(listOf("incoming Alishuu call1", "stopped call1", "declined call1"), sink.events)
    }

    @Test
    fun `a missed call that was never seen ringing is only reported as missed`() = runTest {
        tracker().onPosted(missed())
        settle()
        assertEquals(listOf("missed-later Alishuu"), sink.events)
    }

    @Test
    fun `a missed call from someone else is ignored`() = runTest {
        tracker().onPosted(missed(title = "Stranger"))
        settle()
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun `removal of an unrelated notification changes nothing`() = runTest {
        val tracker = tracker()
        tracker.onPosted(ringing())
        tracker.onRemoved("some-message")
        settle()
        assertEquals(listOf("incoming Alishuu call1"), sink.events)
    }

    @Test
    fun `the first recognised ringing call marks the app as seen on this phone`() = runTest {
        tracker().onPosted(ringing(title = "Stranger"))
        assertEquals(setOf(Platform.WHATSAPP), settings.current.platformsSeenRinging)
    }
}

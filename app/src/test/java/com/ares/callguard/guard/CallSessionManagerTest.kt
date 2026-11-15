package com.ares.callguard.guard

import com.ares.callguard.FakeAlertDisplay
import com.ares.callguard.FakeAlertPlayer
import com.ares.callguard.FakeHistoryRepository
import com.ares.callguard.FakePhoneSoundState
import com.ares.callguard.FakeSettingsRepository
import com.ares.callguard.domain.model.CallOutcome
import com.ares.callguard.domain.model.GuardSettings
import com.ares.callguard.domain.model.Person
import com.ares.callguard.domain.model.Platform
import com.ares.callguard.domain.model.PriorityLevel
import com.ares.callguard.domain.session.SessionEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallSessionManagerTest {

    private val alishuu = Person(id = 1, name = "Alishuu", platforms = setOf(Platform.SIM, Platform.WHATSAPP))
    private val call = PriorityCall("call-1", alishuu, Platform.WHATSAPP)

    private val sound = FakePhoneSoundState(quiet = true)
    private val player = FakeAlertPlayer()
    private val display = FakeAlertDisplay()
    private val history = FakeHistoryRepository()
    private var settings = FakeSettingsRepository()

    private fun TestScope.manager() =
        CallSessionManager(backgroundScope, settings, history, sound, player, display, DetectionLog())

    private fun TestScope.after(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `nothing happens during the first ten seconds`() = runTest {
        manager().onIncoming(call)
        after(9_900)
        assertFalse(player.isPlaying)
        assertNull(display.showing)
    }

    @Test
    fun `the alert starts at ten seconds on a quiet phone`() = runTest {
        val manager = manager()
        manager.onIncoming(call)
        after(10_000)
        assertTrue(player.isPlaying)
        assertEquals(ActiveAlert("Alishuu", Platform.WHATSAPP), display.showing)
        assertEquals(display.showing, manager.activeAlert.value)
    }

    @Test
    fun `no alert when the phone is ringing out loud`() = runTest {
        sound.quiet = false
        manager().onIncoming(call)
        after(30_000)
        assertEquals(0, player.starts)
    }

    @Test
    fun `the grace period follows the setting`() = runTest {
        settings = FakeSettingsRepository(GuardSettings(graceSeconds = 5))
        manager().onIncoming(call)
        after(4_900)
        assertFalse(player.isPlaying)
        after(100)
        assertTrue(player.isPlaying)
    }

    @Test
    fun `answering within the grace period cancels the timer`() = runTest {
        val manager = manager()
        manager.onIncoming(call)
        after(4_000)
        manager.onOutcome(call.key, SessionEvent.Answered)
        after(60_000)
        assertEquals(0, player.starts)
        assertEquals(listOf(CallOutcome.ANSWERED), history.added.map { it.outcome })
    }

    @Test
    fun `declining within the grace period cancels the timer and shows no missed call`() = runTest {
        val manager = manager()
        manager.onIncoming(call)
        after(4_000)
        manager.onRingingStopped(call.key)
        manager.onOutcome(call.key, SessionEvent.Declined)
        after(60_000)
        assertEquals(0, player.starts)
        assertTrue(display.missedCalls.isEmpty())
    }

    @Test
    fun `a call missed before ten seconds is recorded and never rings afterwards`() = runTest {
        val manager = manager()
        manager.onIncoming(call)
        after(6_000)
        manager.onRingingStopped(call.key)
        manager.onOutcome(call.key, SessionEvent.Missed)
        after(60_000)
        assertEquals(0, player.starts)
        assertEquals(listOf(call), display.missedCalls)
        assertEquals(1, player.chimes)
        assertEquals(listOf(CallOutcome.MISSED), history.added.map { it.outcome })
    }

    @Test
    fun `the alert stops the moment the call stops ringing`() = runTest {
        val manager = manager()
        manager.onIncoming(call)
        after(10_000)
        manager.onRingingStopped(call.key)
        runCurrent()
        assertFalse(player.isPlaying)
        assertNull(display.showing)

        manager.onOutcome(call.key, SessionEvent.Missed)
        runCurrent()
        assertEquals(listOf(CallOutcome.MISSED_AFTER_ALERT), history.added.map { it.outcome })
    }

    @Test
    fun `stop alert silences it while the call keeps ringing`() = runTest {
        val manager = manager()
        manager.onIncoming(call)
        after(10_000)
        manager.stopAlertByUser()
        runCurrent()
        assertFalse(player.isPlaying)

        after(60_000)
        assertEquals(1, player.starts)
    }

    @Test
    fun `the same call reported twice gets one timer and one alert`() = runTest {
        val manager = manager()
        manager.onIncoming(call)
        manager.onIncoming(call)
        manager.onIncoming(call.copy(key = "call-1-again"))
        after(30_000)
        assertEquals(1, player.starts)
    }

    @Test
    fun `a second caller joins the alert without restarting the sound`() = runTest {
        val manager = manager()
        manager.onIncoming(call)
        after(5_000)
        manager.onIncoming(PriorityCall("call-2", alishuu.copy(id = 2, name = "Dad"), Platform.SIM))
        after(10_000)
        assertEquals(1, player.starts)
        assertEquals("Dad", display.showing?.personName)

        manager.onOutcome("call-2", SessionEvent.Answered)
        runCurrent()
        assertTrue(player.isPlaying)
        assertEquals("Alishuu", display.showing?.personName)
    }

    @Test
    fun `turning the guard off stops the alert and cancels pending timers`() = runTest {
        val manager = manager()
        manager.onIncoming(call)
        manager.onIncoming(PriorityCall("call-2", alishuu.copy(id = 2, name = "Dad"), Platform.SIM))
        after(10_000)
        assertTrue(player.isPlaying)

        manager.cancelEverything()
        runCurrent()
        assertFalse(player.isPlaying)
        assertNull(display.showing)
        assertTrue(history.added.isEmpty())

        after(60_000)
        assertEquals(1, player.starts)
    }

    @Test
    fun `a missed-alert-only person never gets the loud alert`() = runTest {
        val quietPerson = alishuu.copy(priority = PriorityLevel.MISSED_ALERT_ONLY)
        val manager = manager()
        manager.onIncoming(call.copy(person = quietPerson))
        after(30_000)
        assertEquals(0, player.starts)

        manager.onRingingStopped(call.key)
        manager.onOutcome(call.key, SessionEvent.Missed)
        runCurrent()
        assertEquals(1, display.missedCalls.size)
    }

    @Test
    fun `on another call the alert vibrates without sound`() = runTest {
        sound.onAnotherCall = true
        manager().onIncoming(call)
        after(10_000)
        assertTrue(player.lastStartWasVibrateOnly)
    }

    @Test
    fun `a missed call reported after the fact is announced once`() = runTest {
        val manager = manager()
        manager.onMissedAfterTheFact(call)
        manager.onMissedAfterTheFact(call.copy(key = "duplicate"))
        runCurrent()
        assertEquals(1, display.missedCalls.size)
        assertEquals(0, player.starts)
    }

    @Test
    fun `a call that never reports an ending is abandoned rather than ringing forever`() = runTest {
        manager().onIncoming(call)
        after(10_000)
        assertTrue(player.isPlaying)
        after(120_000)
        assertFalse(player.isPlaying)
    }

    @Test
    fun `the test alert plays and ends by itself`() = runTest {
        val manager = manager()
        manager.sendTestAlert()
        runCurrent()
        assertTrue(player.isPlaying)
        assertEquals(CallSessionManager.TEST_CALLER_NAME, display.showing?.personName)
        after(20_000)
        assertFalse(player.isPlaying)
    }
}

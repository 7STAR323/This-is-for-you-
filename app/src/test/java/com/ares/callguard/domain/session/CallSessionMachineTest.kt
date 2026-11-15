package com.ares.callguard.domain.session

import com.ares.callguard.domain.model.CallOutcome
import com.ares.callguard.domain.model.PriorityLevel
import com.ares.callguard.domain.session.SessionEffect.CancelGraceTimer
import com.ares.callguard.domain.session.SessionEffect.Record
import com.ares.callguard.domain.session.SessionEffect.ShowMissedCall
import com.ares.callguard.domain.session.SessionEffect.StartAlert
import com.ares.callguard.domain.session.SessionEffect.StopAlert
import com.ares.callguard.domain.session.SessionEvent.Answered
import com.ares.callguard.domain.session.SessionEvent.Declined
import com.ares.callguard.domain.session.SessionEvent.GraceElapsed
import com.ares.callguard.domain.session.SessionEvent.Cancelled
import com.ares.callguard.domain.session.SessionEvent.Missed
import com.ares.callguard.domain.session.SessionEvent.RingingStopped
import com.ares.callguard.domain.session.SessionEvent.StoppedByUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSessionMachineTest {

    private val ringThrough = CallSessionMachine(PriorityLevel.RING_THROUGH)
    private val missedOnly = CallSessionMachine(PriorityLevel.MISSED_ALERT_ONLY)

    private val quietPhone = GraceElapsed(phoneIsQuiet = true)
    private val loudPhone = GraceElapsed(phoneIsQuiet = false)

    /** Feeds events in order and returns the final state with every effect produced on the way. */
    private fun CallSessionMachine.run(vararg events: SessionEvent): Transition =
        events.fold(Transition(SessionState.Ringing)) { soFar, event ->
            val next = reduce(soFar.state, event)
            Transition(next.state, soFar.effects + next.effects)
        }

    @Test
    fun `alert starts when the call is still ringing after the grace period on a quiet phone`() {
        val result = ringThrough.run(quietPhone)
        assertEquals(SessionState.Alerting, result.state)
        assertEquals(listOf(StartAlert), result.effects)
    }

    @Test
    fun `no alert when the phone is already ringing out loud`() {
        val result = ringThrough.run(loudPhone)
        assertEquals(SessionState.Ringing, result.state)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun `missed-alert-only people never get the loud alert`() {
        val result = missedOnly.run(quietPhone)
        assertEquals(SessionState.Ringing, result.state)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun `answering within the grace period produces no alert`() {
        val result = ringThrough.run(Answered)
        assertEquals(SessionState.Finished, result.state)
        assertEquals(listOf(CancelGraceTimer, Record(CallOutcome.ANSWERED)), result.effects)
    }

    @Test
    fun `declining within the grace period produces no alert and no missed call`() {
        val result = ringThrough.run(RingingStopped, Declined)
        assertEquals(listOf(CancelGraceTimer, Record(CallOutcome.DECLINED)), result.effects)
    }

    @Test
    fun `a call that ends unanswered within the grace period is a missed call`() {
        val result = ringThrough.run(RingingStopped, Missed)
        assertEquals(SessionState.Finished, result.state)
        assertEquals(
            listOf(CancelGraceTimer, ShowMissedCall, Record(CallOutcome.MISSED)),
            result.effects,
        )
    }

    @Test
    fun `alert stops the moment ringing stops, before the outcome is known`() {
        val result = ringThrough.run(quietPhone, RingingStopped)
        assertEquals(
            SessionState.AwaitingOutcome(alertWasShown = true, acknowledged = false),
            result.state,
        )
        assertEquals(listOf(StartAlert, StopAlert), result.effects)
    }

    @Test
    fun `answering during the alert stops it and is recorded as answered after alert`() {
        val result = ringThrough.run(quietPhone, Answered)
        assertEquals(
            listOf(StartAlert, StopAlert, Record(CallOutcome.ANSWERED_AFTER_ALERT)),
            result.effects,
        )
    }

    @Test
    fun `call ending during the alert shows a missed call`() {
        val result = ringThrough.run(quietPhone, RingingStopped, Missed)
        assertEquals(
            listOf(StartAlert, StopAlert, ShowMissedCall, Record(CallOutcome.MISSED)),
            result.effects,
        )
    }

    @Test
    fun `stop alert silences the alert while the call keeps ringing`() {
        val result = ringThrough.run(quietPhone, StoppedByUser)
        assertEquals(SessionState.Acknowledged, result.state)
        assertEquals(listOf(StartAlert, StopAlert), result.effects)
    }

    @Test
    fun `after stop alert the call ending is not announced as missed`() {
        val result = ringThrough.run(quietPhone, StoppedByUser, RingingStopped, Missed)
        assertEquals(
            listOf(StartAlert, StopAlert, Record(CallOutcome.STOPPED_BY_YOU)),
            result.effects,
        )
    }

    @Test
    fun `answering after stop alert still counts as answered after alert`() {
        val result = ringThrough.run(quietPhone, StoppedByUser, Answered)
        assertEquals(Record(CallOutcome.ANSWERED_AFTER_ALERT), result.effects.last())
    }

    @Test
    fun `the alert does not restart once stopped`() {
        val result = ringThrough.run(quietPhone, StoppedByUser, quietPhone)
        assertEquals(SessionState.Acknowledged, result.state)
        assertEquals(1, result.effects.count { it == StartAlert })
    }

    @Test
    fun `missed-alert-only people still get the missed call alert`() {
        val result = missedOnly.run(quietPhone, RingingStopped, Missed)
        assertEquals(
            listOf(CancelGraceTimer, ShowMissedCall, Record(CallOutcome.MISSED)),
            result.effects,
        )
    }

    @Test
    fun `turning the guard off during the grace period cancels quietly`() {
        val result = ringThrough.run(Cancelled)
        assertEquals(SessionState.Finished, result.state)
        assertEquals(listOf(CancelGraceTimer), result.effects)
    }

    @Test
    fun `turning the guard off during an alert stops it without a missed call or history`() {
        val result = ringThrough.run(quietPhone, Cancelled)
        assertEquals(SessionState.Finished, result.state)
        assertEquals(listOf(StartAlert, StopAlert), result.effects)
    }

    @Test
    fun `a finished call ignores late events`() {
        val result = ringThrough.run(Answered, quietPhone, Missed, StoppedByUser)
        assertEquals(SessionState.Finished, result.state)
        assertEquals(listOf(CancelGraceTimer, Record(CallOutcome.ANSWERED)), result.effects)
    }

    @Test
    fun `stray events while waiting for the outcome change nothing`() {
        val waiting = SessionState.AwaitingOutcome(alertWasShown = false, acknowledged = false)
        assertEquals(Transition(waiting), ringThrough.reduce(waiting, quietPhone))
        assertEquals(Transition(waiting), ringThrough.reduce(waiting, StoppedByUser))
        assertEquals(Transition(waiting), ringThrough.reduce(waiting, RingingStopped))
    }
}

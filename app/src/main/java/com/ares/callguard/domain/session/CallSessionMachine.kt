package com.ares.callguard.domain.session

import com.ares.callguard.domain.model.CallOutcome
import com.ares.callguard.domain.model.PriorityLevel

sealed interface SessionState {
    /** Ringing normally; the grace timer is running. */
    data object Ringing : SessionState

    /** Still ringing past the grace period, and the priority alert is playing. */
    data object Alerting : SessionState

    /** Still ringing, but I tapped STOP ALERT. */
    data object Acknowledged : SessionState

    /** Ringing has stopped and we are waiting to learn how the call ended. */
    data class AwaitingOutcome(val alertWasShown: Boolean, val acknowledged: Boolean) : SessionState

    data object Finished : SessionState
}

sealed interface SessionEvent {
    /** [phoneIsQuiet] is true when the phone is in Silent, Vibrate or Do Not Disturb. */
    data class GraceElapsed(val phoneIsQuiet: Boolean) : SessionEvent
    data object StoppedByUser : SessionEvent
    data object RingingStopped : SessionEvent
    data object Answered : SessionEvent
    data object Declined : SessionEvent
    data object Missed : SessionEvent
    data object Cancelled : SessionEvent
}

sealed interface SessionEffect {
    data object CancelGraceTimer : SessionEffect
    data object StartAlert : SessionEffect
    data object StopAlert : SessionEffect
    data object ShowMissedCall : SessionEffect
    data class Record(val outcome: CallOutcome) : SessionEffect
}

data class Transition(val state: SessionState, val effects: List<SessionEffect> = emptyList())

/**
 * The rules of one priority call, from first ring to the end. It is pure: timers, sound and
 * storage live in the caller, which feeds events in and carries the returned effects out.
 */
class CallSessionMachine(private val priority: PriorityLevel) {

    fun reduce(state: SessionState, event: SessionEvent): Transition = when (state) {
        SessionState.Ringing -> whileRinging(event)
        SessionState.Alerting -> whileAlerting(event)
        SessionState.Acknowledged -> whileAcknowledged(event)
        is SessionState.AwaitingOutcome -> whileAwaitingOutcome(state, event)
        SessionState.Finished -> Transition(SessionState.Finished)
    }

    private fun whileRinging(event: SessionEvent): Transition = when (event) {
        is SessionEvent.GraceElapsed ->
            if (event.phoneIsQuiet && priority == PriorityLevel.RING_THROUGH) {
                Transition(SessionState.Alerting, listOf(SessionEffect.StartAlert))
            } else {
                Transition(SessionState.Ringing)
            }

        SessionEvent.StoppedByUser -> Transition(SessionState.Ringing)

        SessionEvent.RingingStopped -> Transition(
            SessionState.AwaitingOutcome(alertWasShown = false, acknowledged = false),
            listOf(SessionEffect.CancelGraceTimer),
        )

        SessionEvent.Cancelled ->
            Transition(SessionState.Finished, listOf(SessionEffect.CancelGraceTimer))

        else -> finish(event, alertWasShown = false, acknowledged = false, SessionEffect.CancelGraceTimer)
    }

    private fun whileAlerting(event: SessionEvent): Transition = when (event) {
        is SessionEvent.GraceElapsed -> Transition(SessionState.Alerting)

        SessionEvent.StoppedByUser ->
            Transition(SessionState.Acknowledged, listOf(SessionEffect.StopAlert))

        SessionEvent.RingingStopped -> Transition(
            SessionState.AwaitingOutcome(alertWasShown = true, acknowledged = false),
            listOf(SessionEffect.StopAlert),
        )

        SessionEvent.Cancelled ->
            Transition(SessionState.Finished, listOf(SessionEffect.StopAlert))

        else -> finish(event, alertWasShown = true, acknowledged = false, SessionEffect.StopAlert)
    }

    private fun whileAcknowledged(event: SessionEvent): Transition = when (event) {
        is SessionEvent.GraceElapsed, SessionEvent.StoppedByUser ->
            Transition(SessionState.Acknowledged)

        SessionEvent.RingingStopped ->
            Transition(SessionState.AwaitingOutcome(alertWasShown = true, acknowledged = true))

        SessionEvent.Cancelled -> Transition(SessionState.Finished)

        else -> finish(event, alertWasShown = true, acknowledged = true)
    }

    private fun whileAwaitingOutcome(
        state: SessionState.AwaitingOutcome,
        event: SessionEvent,
    ): Transition = when (event) {
        SessionEvent.Answered, SessionEvent.Declined, SessionEvent.Missed ->
            finish(event, state.alertWasShown, state.acknowledged)

        SessionEvent.Cancelled -> Transition(SessionState.Finished)

        else -> Transition(state)
    }

    private fun finish(
        ending: SessionEvent,
        alertWasShown: Boolean,
        acknowledged: Boolean,
        vararg cleanup: SessionEffect,
    ): Transition {
        val effects = cleanup.toMutableList()
        when (ending) {
            SessionEvent.Answered -> effects += SessionEffect.Record(
                if (alertWasShown) CallOutcome.ANSWERED_AFTER_ALERT else CallOutcome.ANSWERED,
            )

            SessionEvent.Declined -> effects += SessionEffect.Record(
                if (acknowledged) CallOutcome.STOPPED_BY_YOU else CallOutcome.DECLINED,
            )

            // Once I've stopped the alert I already know about the call, so it isn't "missed".
            SessionEvent.Missed -> if (acknowledged) {
                effects += SessionEffect.Record(CallOutcome.STOPPED_BY_YOU)
            } else {
                effects += SessionEffect.ShowMissedCall
                effects += SessionEffect.Record(
                    if (alertWasShown) CallOutcome.MISSED_AFTER_ALERT else CallOutcome.MISSED,
                )
            }

            else -> error("$ending does not end a call")
        }
        return Transition(SessionState.Finished, effects)
    }
}

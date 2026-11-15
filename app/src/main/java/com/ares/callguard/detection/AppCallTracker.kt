package com.ares.callguard.detection

import com.ares.callguard.di.ApplicationScope
import com.ares.callguard.domain.matching.ContactMatcher
import com.ares.callguard.domain.model.Person
import com.ares.callguard.domain.model.SeenCaller
import com.ares.callguard.domain.repository.PeopleRepository
import com.ares.callguard.domain.repository.SeenCallerRepository
import com.ares.callguard.domain.session.SessionEvent
import com.ares.callguard.guard.CallSessionManager
import com.ares.callguard.guard.PriorityCall
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Follows calls inside apps like WhatsApp, which only speak through notifications:
 * a ringing notification appears, then disappears, and what replaces it tells us how it ended.
 */
@Singleton
class AppCallTracker @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val people: PeopleRepository,
    private val seenCallers: SeenCallerRepository,
    private val matcher: ContactMatcher,
    private val sessions: CallSessionManager,
) {
    private class TrackedCall(val call: PriorityCall, val shownName: String) {
        val outcome = CompletableDeferred<SessionEvent>()
    }

    private val mutex = Mutex()
    private val ringing = mutableMapOf<String, TrackedCall>()
    private val awaitingOutcome = mutableListOf<TrackedCall>()

    suspend fun onPosted(notification: CallNotification) = mutex.withLock {
        when (notification.kind) {
            CallNotificationKind.INCOMING -> onIncoming(notification)
            CallNotificationKind.ONGOING -> onOngoing(notification)
            CallNotificationKind.MISSED -> onMissed(notification)
        }
    }

    suspend fun onRemoved(notificationKey: String) = mutex.withLock {
        val tracked = ringing.remove(notificationKey) ?: return@withLock
        sessions.onRingingStopped(tracked.call.key)
        awaitingOutcome += tracked

        scope.launch {
            // Declined calls, and calls answered on another device, leave nothing behind.
            val outcome = withTimeoutOrNull(OUTCOME_WAIT) { tracked.outcome.await() }
                ?: SessionEvent.Declined
            mutex.withLock { awaitingOutcome -= tracked }
            sessions.onOutcome(tracked.call.key, outcome)
        }
    }

    private suspend fun onIncoming(notification: CallNotification) {
        if (notification.key in ringing) return
        remember(notification)

        val person = match(notification) ?: return
        val call = PriorityCall(
            key = "${notification.platform}:${notification.key}:${System.currentTimeMillis()}",
            person = person,
            platform = notification.platform,
        )
        ringing[notification.key] = TrackedCall(call, notification.callerName)
        sessions.onIncoming(call)
    }

    private fun onOngoing(notification: CallNotification) {
        // Some apps turn the ringing notification into the in-call one instead of replacing it.
        val updatedInPlace = ringing.remove(notification.key)
        if (updatedInPlace != null) {
            sessions.onOutcome(updatedInPlace.call.key, SessionEvent.Answered)
            return
        }
        awaitingOutcome.firstOrNull { it.call.platform == notification.platform }
            ?.outcome?.complete(SessionEvent.Answered)
    }

    private suspend fun onMissed(notification: CallNotification) {
        val justRang = awaitingOutcome.firstOrNull {
            it.call.platform == notification.platform && sameName(it.shownName, notification.callerName)
        }
        if (justRang != null) {
            justRang.outcome.complete(SessionEvent.Missed)
            return
        }

        // We never saw this call ring, so there is nothing to pretend about: report it missed.
        val person = match(notification) ?: return
        sessions.onMissedAfterTheFact(
            PriorityCall("${notification.platform}:missed:${notification.key}", person, notification.platform),
        )
    }

    private suspend fun match(notification: CallNotification): Person? =
        matcher.matchDisplayName(notification.platform, notification.callerName, people.currentPeople())

    private suspend fun remember(notification: CallNotification) {
        seenCallers.remember(
            SeenCaller(notification.platform, notification.callerName, System.currentTimeMillis()),
        )
    }

    private fun sameName(first: String, second: String) =
        ContactMatcher.comparableName(first) == ContactMatcher.comparableName(second)

    private companion object {
        val OUTCOME_WAIT = 3.seconds
    }
}

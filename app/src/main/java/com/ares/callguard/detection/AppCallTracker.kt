package com.ares.callguard.detection

import com.ares.callguard.di.ApplicationScope
import com.ares.callguard.domain.matching.CallerIdentity
import com.ares.callguard.domain.matching.ContactMatcher
import com.ares.callguard.domain.matching.IdentifiedBy
import com.ares.callguard.domain.matching.MatchResult
import com.ares.callguard.domain.model.Person
import com.ares.callguard.domain.model.Platform
import com.ares.callguard.domain.model.SeenCaller
import com.ares.callguard.domain.repository.PeopleRepository
import com.ares.callguard.domain.repository.SeenCallerRepository
import com.ares.callguard.domain.repository.SettingsRepository
import com.ares.callguard.domain.session.SessionEvent
import com.ares.callguard.guard.DetectionLog
import com.ares.callguard.guard.PriorityCall
import com.ares.callguard.guard.PriorityCallSink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
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
 * A missed-call notification on its own never starts a ringing session.
 */
@Singleton
class AppCallTracker @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val people: PeopleRepository,
    private val seenCallers: SeenCallerRepository,
    private val settings: SettingsRepository,
    private val matcher: ContactMatcher,
    private val sessions: PriorityCallSink,
    private val log: DetectionLog,
) {
    private class TrackedCall(val call: PriorityCall, val caller: CallerIdentity) {
        val outcome = CompletableDeferred<SessionEvent>()
    }

    private val mutex = Mutex()
    private val ringing = mutableMapOf<String, TrackedCall>()
    private val awaitingOutcome = mutableListOf<TrackedCall>()

    suspend fun onPosted(notification: NotificationSnapshot) = mutex.withLock {
        val platform = CallNotificationClassifier.platformOf(notification.packageName) ?: return@withLock
        val kind = CallNotificationClassifier.kindOf(notification)
        val alreadyRinging = kind == CallNotificationKind.INCOMING && notification.key in ringing
        // Apps refresh a ringing notification many times a second; say it once.
        if (!alreadyRinging) {
            log.add("${platform.label}: ${kind ?: "not a call"} (${CallNotificationClassifier.describe(notification)})")
        }

        when (kind) {
            CallNotificationKind.INCOMING -> if (!alreadyRinging) onIncoming(platform, notification)
            CallNotificationKind.ONGOING -> onOngoing(platform, notification)
            CallNotificationKind.MISSED -> onMissed(platform, notification)
            null -> Unit
        }
    }

    suspend fun onRemoved(notificationKey: String) = mutex.withLock {
        val tracked = ringing.remove(notificationKey) ?: return@withLock
        log.add("${tracked.call.platform.label}: ringing stopped, waiting to see how it ended")
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

    private suspend fun onIncoming(platform: Platform, notification: NotificationSnapshot) {
        val caller = CallNotificationClassifier.callerOf(notification)
        rememberRinging(platform, caller)

        val person = identify(platform, caller) ?: return
        val call = PriorityCall(
            key = "$platform:${notification.key}:${System.currentTimeMillis()}",
            person = person,
            platform = platform,
            phoneNumber = caller.phoneNumbers.firstOrNull(),
        )
        ringing[notification.key] = TrackedCall(call, caller)
        sessions.onIncoming(call)
    }

    private fun onOngoing(platform: Platform, notification: NotificationSnapshot) {
        // Some apps turn the ringing notification into the in-call one instead of replacing it.
        val updatedInPlace = ringing.remove(notification.key)
        if (updatedInPlace != null) {
            sessions.onOutcome(updatedInPlace.call.key, SessionEvent.Answered)
            return
        }
        awaitingOutcome.firstOrNull { it.call.platform == platform }
            ?.outcome?.complete(SessionEvent.Answered)
    }

    private suspend fun onMissed(platform: Platform, notification: NotificationSnapshot) {
        val caller = CallNotificationClassifier.callerOf(notification)
        val justRang = awaitingOutcome.firstOrNull { it.call.platform == platform && sameCaller(it.caller, caller) }
        if (justRang != null) {
            justRang.outcome.complete(SessionEvent.Missed)
            return
        }

        // We never saw this call ring, so there is nothing to pretend about: report it missed.
        val person = identify(platform, caller) ?: return
        sessions.onMissedAfterTheFact(
            PriorityCall("$platform:missed:${notification.key}", person, platform, caller.phoneNumbers.firstOrNull()),
        )
    }

    private suspend fun identify(platform: Platform, caller: CallerIdentity): Person? {
        val allowNames = !settings.settings.first().numberMatchOnly
        return when (val result = matcher.match(platform, caller, people.currentPeople(), allowNames)) {
            is MatchResult.Matched -> {
                val proof = when (result.by) {
                    IdentifiedBy.PHONE_NUMBER -> "confirmed by phone number"
                    IdentifiedBy.DISPLAY_NAME -> "matched by the name the app shows (no number available)"
                }
                log.add("${platform.label}: caller is one of your people, $proof")
                result.person
            }
            MatchResult.Ambiguous -> {
                log.add("${platform.label}: ignored, more than one of your people fits this caller")
                null
            }
            MatchResult.Unidentified -> {
                log.add("${platform.label}: ignored, the app gave neither a name nor a number")
                null
            }
            MatchResult.NotOnList -> {
                log.add("${platform.label}: ignored, ${whyNotOnList(caller, allowNames)}")
                null
            }
        }
    }

    private fun whyNotOnList(caller: CallerIdentity, allowNames: Boolean): String = when {
        caller.phoneNumbers.isNotEmpty() -> "the caller's number is not one of your people's"
        !allowNames -> "the app gave no number and names are switched off in Settings"
        else -> "the name shown does not exactly match any of your people for this app"
    }

    private suspend fun rememberRinging(platform: Platform, caller: CallerIdentity) {
        settings.update { current ->
            if (platform in current.platformsSeenRinging) current
            else current.copy(platformsSeenRinging = current.platformsSeenRinging + platform)
        }
        caller.displayNames.firstOrNull()?.let { name ->
            seenCallers.remember(SeenCaller(platform, name, System.currentTimeMillis()))
        }
    }

    private fun sameCaller(first: CallerIdentity, second: CallerIdentity): Boolean {
        val firstNames = first.displayNames.map(ContactMatcher::comparableName).toSet()
        return second.displayNames.any { ContactMatcher.comparableName(it) in firstNames } ||
            second.phoneNumbers.any { it in first.phoneNumbers }
    }

    private companion object {
        val OUTCOME_WAIT = 3.seconds
    }
}

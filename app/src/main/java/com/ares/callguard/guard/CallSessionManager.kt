package com.ares.callguard.guard

import com.ares.callguard.alert.AlertDisplay
import com.ares.callguard.alert.AlertPlayer
import com.ares.callguard.di.ApplicationScope
import com.ares.callguard.domain.model.CallOutcome
import com.ares.callguard.domain.model.CallRecord
import com.ares.callguard.domain.model.Person
import com.ares.callguard.domain.model.Platform
import com.ares.callguard.domain.repository.HistoryRepository
import com.ares.callguard.domain.repository.SettingsRepository
import com.ares.callguard.domain.session.CallSessionMachine
import com.ares.callguard.domain.session.SessionEffect
import com.ares.callguard.domain.session.SessionEvent
import com.ares.callguard.domain.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** A call from one of the chosen people, as reported by a detector. */
data class PriorityCall(
    val key: String,
    val person: Person,
    val platform: Platform,
    val phoneNumber: String? = null,
)

/** What the alert screen needs to show. */
data class ActiveAlert(
    val personName: String,
    val platform: Platform?,
)

/** Where detectors report the life of a priority call. */
interface PriorityCallSink {
    fun onIncoming(call: PriorityCall)
    fun onRingingStopped(key: String)

    /** [outcome] must be Answered, Declined or Missed. */
    fun onOutcome(key: String, outcome: SessionEvent)

    /** For apps that only tell us about a call once it has already been missed. */
    fun onMissedAfterTheFact(call: PriorityCall)
}

/**
 * Runs every priority call that is currently ringing: one grace timer and one state machine
 * each, with a single shared alert. All state is confined to one thread.
 */
@Singleton
class CallSessionManager @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val history: HistoryRepository,
    private val soundState: PhoneSoundState,
    private val player: AlertPlayer,
    private val display: AlertDisplay,
    private val log: DetectionLog,
) : PriorityCallSink {

    private class Session(val call: PriorityCall, val machine: CallSessionMachine) {
        var state: SessionState = SessionState.Ringing
        var graceTimer: Job? = null
        var watchdog: Job? = null
    }

    private val sessions = LinkedHashMap<String, Session>()
    private val lastMissedAt = mutableMapOf<Pair<Long, Platform>, Long>()
    private var isTesting = false
    private var testTimeout: Job? = null

    private val _activeAlert = MutableStateFlow<ActiveAlert?>(null)
    val activeAlert: StateFlow<ActiveAlert?> = _activeAlert.asStateFlow()

    override fun onIncoming(call: PriorityCall) {
        scope.launch {
            if (isAlreadyTracked(call)) {
                log.add("${call.platform.label}: already following this call, no second timer")
                return@launch
            }

            val graceSeconds = settings.settings.first().graceSeconds
            val session = Session(call, CallSessionMachine(call.person.priority))
            sessions[call.key] = session
            log.add("${call.platform.label}: waiting $graceSeconds s before alerting")

            session.graceTimer = launch {
                delay(graceSeconds.seconds)
                val quiet = soundState.isQuiet()
                log.add(
                    "${call.platform.label}: still ringing after $graceSeconds s, phone is " +
                        if (quiet) "quiet" else "ringing out loud, so no alert is needed",
                )
                dispatch(call.key, SessionEvent.GraceElapsed(phoneIsQuiet = quiet))
            }
            // If a detector loses track of the call, don't leave an alert ringing forever.
            session.watchdog = launch {
                delay(LONGEST_PLAUSIBLE_RING)
                log.add("${call.platform.label}: gave up on a call that never reported an ending")
                dispatch(call.key, SessionEvent.Cancelled)
            }
        }
    }

    override fun onRingingStopped(key: String) {
        scope.launch { dispatch(key, SessionEvent.RingingStopped) }
    }

    override fun onOutcome(key: String, outcome: SessionEvent) {
        scope.launch { dispatch(key, outcome) }
    }

    override fun onMissedAfterTheFact(call: PriorityCall) {
        scope.launch {
            if (wasJustReportedMissed(call)) return@launch
            log.add("${call.platform.label}: missed call reported after it ended")
            announceMissed(call)
            record(call, CallOutcome.MISSED)
        }
    }

    fun stopAlertByUser() {
        scope.launch {
            stopTestAlert()
            sessions.keys.toList().forEach { dispatch(it, SessionEvent.StoppedByUser) }
        }
    }

    fun cancelEverything() {
        scope.launch {
            stopTestAlert()
            sessions.keys.toList().forEach { dispatch(it, SessionEvent.Cancelled) }
            refreshAlert()
        }
    }

    fun sendTestAlert() {
        scope.launch {
            testTimeout?.cancel()
            isTesting = true
            refreshAlert()
            testTimeout = scope.launch {
                delay(TEST_ALERT_LENGTH)
                stopTestAlert()
            }
        }
    }

    private suspend fun stopTestAlert() {
        if (!isTesting) return
        isTesting = false
        testTimeout?.cancel()
        refreshAlert()
    }

    // The same person ringing twice on one app at once is one call, not two alerts.
    private fun isAlreadyTracked(call: PriorityCall) = sessions.values.any {
        it.call.key == call.key ||
            (it.call.person.id == call.person.id && it.call.platform == call.platform)
    }

    private fun wasJustReportedMissed(call: PriorityCall): Boolean {
        val last = lastMissedAt[call.person.id to call.platform] ?: return false
        return System.currentTimeMillis() - last < DUPLICATE_MISSED_WINDOW.inWholeMilliseconds
    }

    private suspend fun dispatch(key: String, event: SessionEvent) {
        val session = sessions[key] ?: return
        val transition = session.machine.reduce(session.state, event)
        session.state = transition.state

        if (session.state == SessionState.Finished) {
            session.graceTimer?.cancel()
            session.watchdog?.cancel()
            sessions.remove(key)
        }
        transition.effects.forEach { apply(it, session) }
    }

    private suspend fun apply(effect: SessionEffect, session: Session) {
        when (effect) {
            SessionEffect.CancelGraceTimer -> session.graceTimer?.cancel()
            SessionEffect.StartAlert, SessionEffect.StopAlert -> refreshAlert()
            SessionEffect.ShowMissedCall -> announceMissed(session.call)
            is SessionEffect.Record -> {
                log.add("${session.call.platform.label}: call finished, ${effect.outcome.name.lowercase().replace('_', ' ')}")
                record(session.call, effect.outcome)
            }
        }
    }

    /** Makes the one shared alert match the sessions: playing if any call is alerting. */
    private suspend fun refreshAlert() {
        val alerting = sessions.values.lastOrNull { it.state == SessionState.Alerting }
        val wanted = when {
            alerting != null -> ActiveAlert(alerting.call.person.name, alerting.call.platform)
            isTesting -> ActiveAlert(TEST_CALLER_NAME, platform = null)
            else -> null
        }
        val previous = _activeAlert.value
        if (wanted == previous) return
        _activeAlert.value = wanted

        // A second caller joining an alert only changes the name on screen; restarting the
        // ringtone would make it stutter.
        if (wanted != null && previous != null) {
            display.showAlert(wanted)
            return
        }

        if (wanted == null) {
            player.stop()
            display.cancelAlert()
            log.add("Alert stopped")
            return
        }

        val vibrateOnly = alerting != null && soundState.isOnAnotherCall(alerting.call.platform)
        player.start(settings.settings.first(), vibrateOnly)
        display.showAlert(wanted)
        log.add(
            when {
                vibrateOnly -> "Alert started: vibration and screen only, because you are on another call"
                soundState.doNotDisturbSilencesAlert() ->
                    "Alert started, but Do Not Disturb is silencing alarms so it may only vibrate"
                else -> "Alert started: sound, vibration and screen"
            },
        )
    }

    private suspend fun announceMissed(call: PriorityCall) {
        lastMissedAt[call.person.id to call.platform] = System.currentTimeMillis()
        display.showMissedCall(call)
        player.playMissedCallChime(
            settings.settings.first(),
            vibrateOnly = soundState.isOnAnotherCall(call.platform),
        )
    }

    private suspend fun record(call: PriorityCall, outcome: CallOutcome) {
        val now = System.currentTimeMillis()
        history.add(CallRecord(0, call.person.name, call.platform, now, outcome))
        history.removeOlderThan(now - HISTORY_RETENTION_MILLIS)
    }

    companion object {
        const val TEST_CALLER_NAME = "Test alert"
        const val HISTORY_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000

        private val LONGEST_PLAUSIBLE_RING = 2.minutes
        private val TEST_ALERT_LENGTH = 20.seconds
        private val DUPLICATE_MISSED_WINDOW = 1.minutes
    }
}

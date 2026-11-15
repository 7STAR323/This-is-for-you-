package com.ares.callguard.detection

import android.telephony.TelephonyManager
import com.ares.callguard.di.ApplicationScope
import com.ares.callguard.domain.matching.ContactMatcher
import com.ares.callguard.domain.model.Platform
import com.ares.callguard.domain.repository.PeopleRepository
import com.ares.callguard.domain.session.SessionEvent
import com.ares.callguard.guard.CallSessionManager
import com.ares.callguard.guard.DetectionLog
import com.ares.callguard.guard.Guard
import com.ares.callguard.guard.PriorityCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Follows the phone's call state and turns it into the life of one priority SIM call. */
@Singleton
class SimCallTracker @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val guard: Guard,
    private val people: PeopleRepository,
    private val matcher: ContactMatcher,
    private val callLog: CallLogReader,
    private val sessions: CallSessionManager,
    private val log: DetectionLog,
) {
    private class RingingCall(
        val key: String,
        val number: String,
        val startedAtMillis: Long,
        val arrivedDuringAnotherCall: Boolean,
    )

    private val mutex = Mutex()
    private var ringing: RingingCall? = null
    private var stateBeforeRinging = TelephonyManager.EXTRA_STATE_IDLE

    suspend fun onPhoneState(state: String, incomingNumber: String?) = mutex.withLock {
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> onRinging(incomingNumber)
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                onRingingOver(nowInCall = true)
                stateBeforeRinging = state
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                onRingingOver(nowInCall = false)
                stateBeforeRinging = state
            }
        }
    }

    private suspend fun onRinging(incomingNumber: String?) {
        // The system sends this broadcast twice, once without the number; a withheld number
        // also arrives empty. Neither can be matched to anyone.
        if (incomingNumber.isNullOrBlank() || ringing != null) return
        if (!guard.isWanted()) return

        val person = matcher.matchPhoneNumber(incomingNumber, people.currentPeople())
        log.add(if (person == null) "Phone call: not one of your people" else "Phone call from ${person.name}")
        if (person == null) return

        val startedAt = System.currentTimeMillis()
        val call = RingingCall(
            key = "sim:$startedAt",
            number = incomingNumber,
            startedAtMillis = startedAt,
            arrivedDuringAnotherCall = stateBeforeRinging == TelephonyManager.EXTRA_STATE_OFFHOOK,
        )
        ringing = call
        sessions.onIncoming(PriorityCall(call.key, person, Platform.SIM, incomingNumber))
    }

    private fun onRingingOver(nowInCall: Boolean) {
        val call = ringing ?: return
        ringing = null

        // Going from ringing straight into a call means it was picked up. If another call was
        // already in progress, "in a call" tells us nothing, so the call log has to decide.
        if (nowInCall && !call.arrivedDuringAnotherCall) {
            log.add("Phone call answered")
            sessions.onOutcome(call.key, SessionEvent.Answered)
            return
        }

        sessions.onRingingStopped(call.key)
        scope.launch {
            val outcome = callLog.outcomeOf(call.number, call.startedAtMillis)
            log.add("Phone call ended: ${outcome::class.simpleName}")
            sessions.onOutcome(call.key, outcome)
        }
    }
}

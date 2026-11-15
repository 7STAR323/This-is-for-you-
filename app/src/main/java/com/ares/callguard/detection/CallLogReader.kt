package com.ares.callguard.detection

import android.content.Context
import android.provider.CallLog.Calls
import com.ares.callguard.domain.matching.PhoneNumberNormalizer
import com.ares.callguard.domain.session.SessionEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * When a SIM call stops ringing, the call state alone can't say whether it was declined or
 * missed. The phone's own call log can, a moment after the call ends.
 */
class CallLogReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val normalizer: PhoneNumberNormalizer,
) {

    suspend fun outcomeOf(number: String, ringStartedAtMillis: Long): SessionEvent {
        repeat(LOOKUP_ATTEMPTS) {
            delay(LOOKUP_INTERVAL)
            callType(number, ringStartedAtMillis)?.let { return it.toOutcome() }
        }
        // No entry (or no permission any more): an unexplained call is safer shown as missed.
        return SessionEvent.Missed
    }

    private suspend fun callType(number: String, ringStartedAtMillis: Long): Int? =
        withContext(Dispatchers.IO) {
            val wanted = normalizer.normalize(number)
            runCatching {
                context.contentResolver.query(
                    Calls.CONTENT_URI,
                    arrayOf(Calls.NUMBER, Calls.TYPE),
                    "${Calls.DATE} >= ?",
                    arrayOf((ringStartedAtMillis - CLOCK_SLACK_MILLIS).toString()),
                    "${Calls.DATE} DESC",
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val loggedNumber = cursor.getString(0) ?: continue
                        if (normalizer.normalize(loggedNumber) == wanted) return@use cursor.getInt(1)
                    }
                    null
                }
            }.getOrNull()
        }

    private fun Int.toOutcome(): SessionEvent = when (this) {
        Calls.INCOMING_TYPE, Calls.ANSWERED_EXTERNALLY_TYPE -> SessionEvent.Answered
        Calls.REJECTED_TYPE, Calls.BLOCKED_TYPE -> SessionEvent.Declined
        else -> SessionEvent.Missed
    }

    private companion object {
        const val LOOKUP_ATTEMPTS = 5
        val LOOKUP_INTERVAL = 600.milliseconds
        const val CLOCK_SLACK_MILLIS = 5_000L
    }
}

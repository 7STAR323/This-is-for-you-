package com.ares.callguard

import com.ares.callguard.alert.AlertDisplay
import com.ares.callguard.alert.AlertPlayer
import com.ares.callguard.domain.model.CallRecord
import com.ares.callguard.domain.model.GuardSettings
import com.ares.callguard.domain.model.Person
import com.ares.callguard.domain.model.Platform
import com.ares.callguard.domain.model.SeenCaller
import com.ares.callguard.domain.repository.HistoryRepository
import com.ares.callguard.domain.repository.PeopleRepository
import com.ares.callguard.domain.repository.SeenCallerRepository
import com.ares.callguard.domain.repository.SettingsRepository
import com.ares.callguard.domain.session.SessionEvent
import com.ares.callguard.guard.ActiveAlert
import com.ares.callguard.guard.PhoneSoundState
import com.ares.callguard.guard.PriorityCall
import com.ares.callguard.guard.PriorityCallSink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

class FakePeopleRepository(private var everyone: List<Person>) : PeopleRepository {
    override val people: Flow<List<Person>> get() = flowOf(everyone)
    override suspend fun currentPeople() = everyone
    override suspend fun person(id: Long) = everyone.firstOrNull { it.id == id }
    override suspend fun save(person: Person): Long = person.id.also { everyone = everyone + person }
    override suspend fun setEnabled(id: Long, enabled: Boolean) = Unit
    override suspend fun remove(id: Long) = Unit
}

class FakeSettingsRepository(initial: GuardSettings = GuardSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    val current: GuardSettings get() = state.value
    override val settings: Flow<GuardSettings> = state
    override suspend fun update(change: (GuardSettings) -> GuardSettings) = state.update(change)
}

class FakeHistoryRepository : HistoryRepository {
    val added = mutableListOf<CallRecord>()
    override val records: Flow<List<CallRecord>> get() = flowOf(added)
    override suspend fun add(record: CallRecord) { added += record }
    override suspend fun clear() = added.clear()
    override suspend fun removeOlderThan(cutoffMillis: Long) = Unit
}

class FakeSeenCallerRepository : SeenCallerRepository {
    val remembered = mutableListOf<SeenCaller>()
    override fun recent(platform: Platform): Flow<List<SeenCaller>> = flowOf(remembered)
    override suspend fun remember(caller: SeenCaller) { remembered += caller }
}

class FakePhoneSoundState(var quiet: Boolean = true, var onAnotherCall: Boolean = false) : PhoneSoundState {
    override fun isQuiet() = quiet
    override fun isOnAnotherCall(ringingPlatform: Platform) = onAnotherCall
    override fun doNotDisturbSilencesAlert() = false
}

class FakeAlertPlayer : AlertPlayer {
    var starts = 0
    var stops = 0
    var chimes = 0
    var lastStartWasVibrateOnly = false
    val isPlaying: Boolean get() = starts > stops

    override fun start(settings: GuardSettings, vibrateOnly: Boolean) {
        starts++
        lastStartWasVibrateOnly = vibrateOnly
    }
    override fun stop() { stops++ }
    override fun playMissedCallChime(settings: GuardSettings, vibrateOnly: Boolean) { chimes++ }
}

class FakeAlertDisplay : AlertDisplay {
    var showing: ActiveAlert? = null
    val missedCalls = mutableListOf<PriorityCall>()
    override fun showAlert(alert: ActiveAlert) { showing = alert }
    override fun cancelAlert() { showing = null }
    override fun showMissedCall(call: PriorityCall) { missedCalls += call }
}

/** Records what a detector reported, as short readable strings. */
class RecordingCallSink : PriorityCallSink {
    val events = mutableListOf<String>()
    private val keys = mutableListOf<String>()

    private fun label(key: String): String {
        if (key !in keys) keys += key
        return "call${keys.indexOf(key) + 1}"
    }

    override fun onIncoming(call: PriorityCall) { events += "incoming ${call.person.name} ${label(call.key)}" }
    override fun onRingingStopped(key: String) { events += "stopped ${label(key)}" }
    override fun onOutcome(key: String, outcome: SessionEvent) {
        events += "${outcome::class.simpleName!!.lowercase()} ${label(key)}"
    }
    override fun onMissedAfterTheFact(call: PriorityCall) { events += "missed-later ${call.person.name}" }
}

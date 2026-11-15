package com.ares.callguard.domain.repository

import com.ares.callguard.domain.model.CallRecord
import com.ares.callguard.domain.model.GuardSettings
import com.ares.callguard.domain.model.Person
import com.ares.callguard.domain.model.Platform
import com.ares.callguard.domain.model.SeenCaller
import kotlinx.coroutines.flow.Flow

interface PeopleRepository {
    val people: Flow<List<Person>>
    suspend fun currentPeople(): List<Person>
    suspend fun person(id: Long): Person?
    suspend fun save(person: Person): Long
    suspend fun setEnabled(id: Long, enabled: Boolean)
    suspend fun remove(id: Long)
}

interface HistoryRepository {
    val records: Flow<List<CallRecord>>
    suspend fun add(record: CallRecord)
    suspend fun clear()
    suspend fun removeOlderThan(cutoffMillis: Long)
}

interface SeenCallerRepository {
    fun recent(platform: Platform): Flow<List<SeenCaller>>
    suspend fun remember(caller: SeenCaller)
}

interface SettingsRepository {
    val settings: Flow<GuardSettings>
    suspend fun update(change: (GuardSettings) -> GuardSettings)
}

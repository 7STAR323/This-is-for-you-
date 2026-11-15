package com.ares.callguard.data

import com.ares.callguard.data.db.CallRecordEntity
import com.ares.callguard.data.db.HistoryDao
import com.ares.callguard.domain.model.CallRecord
import com.ares.callguard.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomHistoryRepository @Inject constructor(
    private val dao: HistoryDao,
) : HistoryRepository {

    override val records: Flow<List<CallRecord>> = dao.observeAll().map { rows ->
        rows.map { CallRecord(it.id, it.personName, it.platform, it.timeMillis, it.outcome) }
    }

    override suspend fun add(record: CallRecord) = dao.insert(
        CallRecordEntity(
            personName = record.personName,
            platform = record.platform,
            timeMillis = record.timeMillis,
            outcome = record.outcome,
        ),
    )

    override suspend fun clear() = dao.clear()

    override suspend fun removeOlderThan(cutoffMillis: Long) = dao.deleteOlderThan(cutoffMillis)
}

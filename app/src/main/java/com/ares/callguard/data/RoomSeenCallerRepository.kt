package com.ares.callguard.data

import com.ares.callguard.data.db.SeenCallerDao
import com.ares.callguard.data.db.SeenCallerEntity
import com.ares.callguard.domain.model.Platform
import com.ares.callguard.domain.model.SeenCaller
import com.ares.callguard.domain.repository.SeenCallerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours

class RoomSeenCallerRepository @Inject constructor(
    private val dao: SeenCallerDao,
) : SeenCallerRepository {

    override fun recent(platform: Platform): Flow<List<SeenCaller>> =
        dao.observe(platform).map { rows ->
            val cutoff = System.currentTimeMillis() - RETENTION.inWholeMilliseconds
            rows.filter { it.seenAtMillis >= cutoff }
                .map { SeenCaller(it.platform, it.displayName, it.seenAtMillis) }
        }

    override suspend fun remember(caller: SeenCaller) {
        dao.insert(SeenCallerEntity(caller.platform, caller.displayName, caller.seenAtMillis))
        dao.deleteOlderThan(caller.seenAtMillis - RETENTION.inWholeMilliseconds)
        dao.keepNewest(caller.platform, MAX_NAMES_PER_PLATFORM)
    }

    private companion object {
        // These names include people who are not on the list, so they are kept only long
        // enough to be useful when setting someone up.
        val RETENTION = 24.hours
        const val MAX_NAMES_PER_PLATFORM = 20
    }
}

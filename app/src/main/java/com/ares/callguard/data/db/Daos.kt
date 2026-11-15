package com.ares.callguard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ares.callguard.domain.model.Platform
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PeopleDao {

    @Transaction
    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<PersonWithDetails>>

    @Transaction
    @Query("SELECT * FROM people")
    abstract suspend fun getAll(): List<PersonWithDetails>

    @Transaction
    @Query("SELECT * FROM people WHERE id = :id")
    abstract suspend fun get(id: Long): PersonWithDetails?

    @Upsert
    protected abstract suspend fun upsert(person: PersonEntity): Long

    @Insert
    protected abstract suspend fun insertNumbers(numbers: List<PhoneNumberEntity>)

    @Insert
    protected abstract suspend fun insertIdentities(identities: List<PlatformIdentityEntity>)

    @Query("DELETE FROM phone_numbers WHERE personId = :personId")
    protected abstract suspend fun deleteNumbers(personId: Long)

    @Query("DELETE FROM platform_identities WHERE personId = :personId")
    protected abstract suspend fun deleteIdentities(personId: Long)

    @Query("UPDATE people SET enabled = :enabled WHERE id = :id")
    abstract suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM people WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Transaction
    open suspend fun save(
        person: PersonEntity,
        numbers: List<String>,
        identities: List<Pair<Platform, String>>,
    ): Long {
        // Upsert returns -1 when it updated an existing row.
        val personId = upsert(person).takeIf { it != -1L } ?: person.id
        deleteNumbers(personId)
        deleteIdentities(personId)
        insertNumbers(numbers.map { PhoneNumberEntity(personId = personId, number = it) })
        insertIdentities(
            identities.map { (platform, name) ->
                PlatformIdentityEntity(personId = personId, platform = platform, displayName = name)
            },
        )
        return personId
    }
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM call_history ORDER BY timeMillis DESC")
    fun observeAll(): Flow<List<CallRecordEntity>>

    @Insert
    suspend fun insert(record: CallRecordEntity)

    @Query("DELETE FROM call_history")
    suspend fun clear()

    @Query("DELETE FROM call_history WHERE timeMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}

@Dao
interface SeenCallerDao {
    @Query("SELECT * FROM seen_callers WHERE platform = :platform ORDER BY seenAtMillis DESC")
    fun observe(platform: Platform): Flow<List<SeenCallerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(caller: SeenCallerEntity)

    @Query("DELETE FROM seen_callers WHERE seenAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query(
        """
        DELETE FROM seen_callers WHERE platform = :platform AND displayName NOT IN (
            SELECT displayName FROM seen_callers WHERE platform = :platform
            ORDER BY seenAtMillis DESC LIMIT :keep
        )
        """,
    )
    suspend fun keepNewest(platform: Platform, keep: Int)
}

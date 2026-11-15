package com.ares.callguard.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.ares.callguard.domain.model.CallOutcome
import com.ares.callguard.domain.model.Platform
import com.ares.callguard.domain.model.PriorityLevel

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val photoPath: String?,
    val priority: PriorityLevel,
    val enabled: Boolean,
    /** Comma-separated [Platform] names; see [PlatformSetConverter]. */
    @ColumnInfo(defaultValue = "") val platforms: Set<Platform> = emptySet(),
)

class PlatformSetConverter {
    @TypeConverter
    fun toText(platforms: Set<Platform>): String = platforms.joinToString(",") { it.name }

    // Unknown names are skipped so a database written by a newer build still opens.
    @TypeConverter
    fun fromText(text: String): Set<Platform> =
        text.split(',').mapNotNull { name -> Platform.entries.firstOrNull { it.name == name } }.toSet()
}

@Entity(
    tableName = "phone_numbers",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("personId")],
)
data class PhoneNumberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val number: String,
)

@Entity(
    tableName = "platform_identities",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("personId")],
)
data class PlatformIdentityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val platform: Platform,
    val displayName: String,
)

data class PersonWithDetails(
    @Embedded val person: PersonEntity,
    @Relation(parentColumn = "id", entityColumn = "personId")
    val numbers: List<PhoneNumberEntity>,
    @Relation(parentColumn = "id", entityColumn = "personId")
    val identities: List<PlatformIdentityEntity>,
)

@Entity(tableName = "call_history")
data class CallRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personName: String,
    val platform: Platform,
    val timeMillis: Long,
    val outcome: CallOutcome,
)

@Entity(tableName = "seen_callers", primaryKeys = ["platform", "displayName"])
data class SeenCallerEntity(
    val platform: Platform,
    val displayName: String,
    val seenAtMillis: Long,
)

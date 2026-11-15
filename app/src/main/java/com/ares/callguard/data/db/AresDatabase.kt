package com.ares.callguard.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PersonEntity::class,
        PhoneNumberEntity::class,
        PlatformIdentityEntity::class,
        CallRecordEntity::class,
        SeenCallerEntity::class,
    ],
    version = 2,
)
@TypeConverters(PlatformSetConverter::class)
abstract class AresDatabase : RoomDatabase() {
    abstract fun peopleDao(): PeopleDao
    abstract fun historyDao(): HistoryDao
    abstract fun seenCallerDao(): SeenCallerDao

    companion object {
        /**
         * Version 1 inferred a person's platforms from what was filled in. Version 2 stores
         * them, so existing people keep exactly the platforms they had.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE people ADD COLUMN platforms TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    UPDATE people SET platforms = TRIM(
                        (CASE WHEN EXISTS (SELECT 1 FROM phone_numbers WHERE personId = people.id)
                              THEN 'SIM' ELSE '' END) ||
                        (CASE WHEN EXISTS (SELECT 1 FROM platform_identities
                                           WHERE personId = people.id AND platform = 'WHATSAPP')
                              THEN ',WHATSAPP' ELSE '' END),
                        ',')
                    """.trimIndent(),
                )
            }
        }
    }
}

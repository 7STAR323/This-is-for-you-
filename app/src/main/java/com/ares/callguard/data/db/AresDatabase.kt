package com.ares.callguard.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PersonEntity::class,
        PhoneNumberEntity::class,
        PlatformIdentityEntity::class,
        CallRecordEntity::class,
        SeenCallerEntity::class,
    ],
    version = 1,
)
abstract class AresDatabase : RoomDatabase() {
    abstract fun peopleDao(): PeopleDao
    abstract fun historyDao(): HistoryDao
    abstract fun seenCallerDao(): SeenCallerDao
}

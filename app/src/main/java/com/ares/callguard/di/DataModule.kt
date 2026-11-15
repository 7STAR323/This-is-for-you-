package com.ares.callguard.di

import android.content.Context
import android.telephony.TelephonyManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.ares.callguard.data.RoomHistoryRepository
import com.ares.callguard.data.RoomPeopleRepository
import com.ares.callguard.data.RoomSeenCallerRepository
import com.ares.callguard.data.db.AresDatabase
import com.ares.callguard.data.settings.DataStoreSettingsRepository
import com.ares.callguard.domain.matching.ContactMatcher
import com.ares.callguard.domain.matching.PhoneNumberNormalizer
import com.ares.callguard.domain.repository.HistoryRepository
import com.ares.callguard.domain.repository.PeopleRepository
import com.ares.callguard.domain.repository.SeenCallerRepository
import com.ares.callguard.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Locale
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AresDatabase =
        Room.databaseBuilder(context, AresDatabase::class.java, "ares.db")
            .addMigrations(AresDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun peopleDao(database: AresDatabase) = database.peopleDao()

    @Provides
    fun historyDao(database: AresDatabase) = database.historyDao()

    @Provides
    fun seenCallerDao(database: AresDatabase) = database.seenCallerDao()

    @Provides
    @Singleton
    fun settingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    fun phoneNumberNormalizer(@ApplicationContext context: Context): PhoneNumberNormalizer {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        // A bare number like "98765 43210" belongs to the country of the SIM that received it.
        return PhoneNumberNormalizer {
            telephony.simCountryIso.takeUnless { it.isNullOrBlank() }
                ?: telephony.networkCountryIso.takeUnless { it.isNullOrBlank() }
                ?: Locale.getDefault().country
        }
    }

    @Provides
    @Singleton
    fun contactMatcher(normalizer: PhoneNumberNormalizer) = ContactMatcher(normalizer)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun people(repository: RoomPeopleRepository): PeopleRepository

    @Binds @Singleton
    abstract fun history(repository: RoomHistoryRepository): HistoryRepository

    @Binds @Singleton
    abstract fun seenCallers(repository: RoomSeenCallerRepository): SeenCallerRepository

    @Binds @Singleton
    abstract fun settings(repository: DataStoreSettingsRepository): SettingsRepository
}

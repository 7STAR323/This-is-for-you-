package com.ares.callguard.guard

import android.content.Context
import android.content.Intent
import com.ares.callguard.di.ApplicationScope
import com.ares.callguard.domain.model.GuardHealth
import com.ares.callguard.domain.model.GuardStatus
import com.ares.callguard.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one master switch. It reports ACTIVE only when the user wants protection *and* every
 * required setting is in place, and keeps the background service in step with that.
 */
@Singleton
class Guard @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val permissions: PermissionOrchestrator,
    private val sessions: CallSessionManager,
) {
    private val recheck = MutableStateFlow(0)

    val health: StateFlow<GuardHealth?> =
        combine(settings.settings.map { it.guardWanted }.distinctUntilChanged(), recheck) { wanted, _ ->
            GuardHealth.from(wanted, permissions.missingSteps())
        }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            health.collect { current ->
                when (current?.status) {
                    GuardStatus.ACTIVE -> startService()
                    GuardStatus.NEEDS_ATTENTION -> stopService()
                    GuardStatus.INACTIVE -> {
                        sessions.cancelEverything()
                        stopService()
                    }
                    null -> Unit
                }
            }
        }
    }

    /** Whether detectors should react to calls at all. */
    suspend fun isWanted(): Boolean = settings.settings.first().guardWanted

    /** Permissions can change behind our back, so callers re-check whenever the app is seen. */
    fun recheckSetup() {
        recheck.value++
    }

    suspend fun setWanted(wanted: Boolean) {
        settings.update { it.copy(guardWanted = wanted) }
        recheckSetup()
    }

    private fun startService() {
        // Android may refuse a background start if battery restrictions were re-applied;
        // the next recheck then reports that as a missing step instead of crashing.
        runCatching { context.startForegroundService(Intent(context, GuardService::class.java)) }
    }

    private fun stopService() {
        context.stopService(Intent(context, GuardService::class.java))
    }
}

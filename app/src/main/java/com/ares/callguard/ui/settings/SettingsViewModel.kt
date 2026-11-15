package com.ares.callguard.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ares.callguard.domain.model.GuardHealth
import com.ares.callguard.domain.model.GuardSettings
import com.ares.callguard.domain.model.VibrationStyle
import com.ares.callguard.domain.repository.SettingsRepository
import com.ares.callguard.guard.CallSessionManager
import com.ares.callguard.guard.DetectionLog
import com.ares.callguard.guard.DetectionLogEntry
import com.ares.callguard.guard.Guard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val guard: Guard,
    private val sessions: CallSessionManager,
    detectionLog: DetectionLog,
) : ViewModel() {

    /** Null while loading. */
    val settings: StateFlow<GuardSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val health: StateFlow<GuardHealth?> = guard.health

    val detectionLog: StateFlow<List<DetectionLogEntry>> = detectionLog.entries

    fun setGraceSeconds(seconds: Int) = change { it.copy(graceSeconds = seconds) }

    fun setRingtone(uri: String?) = change { it.copy(ringtoneUri = uri) }

    fun setVibrationStyle(style: VibrationStyle) = change { it.copy(vibrationStyle = style) }

    fun setRaiseVolume(raise: Boolean) = change { it.copy(raiseVolumeForAlerts = raise) }

    fun setKeepAwakeStepsConfirmed(confirmed: Boolean) = change { it.copy(keepAwakeStepsConfirmed = confirmed) }

    fun finishOnboarding() = change { it.copy(onboardingSeen = true) }

    fun recheckSetup() = guard.recheckSetup()

    fun sendTestAlert() = sessions.sendTestAlert()

    private fun change(transform: (GuardSettings) -> GuardSettings) {
        viewModelScope.launch { repository.update(transform) }
    }
}

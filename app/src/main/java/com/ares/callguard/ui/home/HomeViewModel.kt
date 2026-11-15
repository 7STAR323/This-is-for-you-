package com.ares.callguard.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ares.callguard.domain.model.GuardHealth
import com.ares.callguard.domain.model.GuardStatus
import com.ares.callguard.domain.repository.PeopleRepository
import com.ares.callguard.guard.Guard
import com.ares.callguard.guard.PhoneSoundState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val health: GuardHealth,
    val peopleWhoCanReachYou: Int,
    /** Do Not Disturb is on in a form that would mute the alert's sound right now. */
    val alertMutedByDoNotDisturb: Boolean,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val guard: Guard,
    private val soundState: PhoneSoundState,
    people: PeopleRepository,
) : ViewModel() {

    /** Null while the saved state is still being read. */
    val state: StateFlow<HomeUiState?> =
        combine(guard.health.filterNotNull(), people.people) { health, everyone ->
            HomeUiState(health, everyone.count { it.enabled }, soundState.doNotDisturbSilencesAlert())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleGuard() {
        val current = state.value?.health?.status ?: return
        viewModelScope.launch { guard.setWanted(current == GuardStatus.INACTIVE) }
    }

    fun recheckSetup() = guard.recheckSetup()
}

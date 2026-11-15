package com.ares.callguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ares.callguard.domain.repository.SettingsRepository
import com.ares.callguard.guard.Guard
import com.ares.callguard.ui.AresNavigation
import com.ares.callguard.ui.components.LoadingState
import com.ares.callguard.ui.theme.AresTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsRepository,
    private val guard: Guard,
) : ViewModel() {

    /** Decided once at launch, so finishing the welcome doesn't rebuild navigation. Null while loading. */
    val showWelcome: StateFlow<Boolean?> = settings.settings
        .take(1)
        .map { !it.onboardingSeen }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun recheckSetup() = guard.recheckSetup()
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AresTheme {
                val showWelcome by viewModel.showWelcome.collectAsStateWithLifecycle()
                when (val welcome = showWelcome) {
                    null -> LoadingState()
                    else -> AresNavigation(showWelcome = welcome)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // A permission may have been granted or revoked while we were in the background.
        viewModel.recheckSetup()
    }
}

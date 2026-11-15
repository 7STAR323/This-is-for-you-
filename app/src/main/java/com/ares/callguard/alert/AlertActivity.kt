package com.ares.callguard.alert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ares.callguard.guard.ActiveAlert
import com.ares.callguard.guard.CallSessionManager
import com.ares.callguard.ui.alert.AlertScreen
import com.ares.callguard.ui.theme.AresTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AlertViewModel @Inject constructor(
    private val sessions: CallSessionManager,
) : ViewModel() {
    val alert: StateFlow<ActiveAlert?> = sessions.activeAlert

    fun stopAlert() {
        sessions.stopAlertByUser()
    }
}

/** The full-screen alert. Shown over the lock screen; closes itself when the alert ends. */
@AndroidEntryPoint
class AlertActivity : ComponentActivity() {

    private val viewModel: AlertViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        setContent {
            val alert by viewModel.alert.collectAsStateWithLifecycle()
            val current = alert
            LaunchedEffect(current == null) {
                if (current == null) finish()
            }
            if (current != null) {
                AresTheme { AlertScreen(current, onStop = viewModel::stopAlert) }
            }
        }
    }
}

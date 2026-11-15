package com.ares.callguard.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ares.callguard.R
import com.ares.callguard.domain.model.GuardHealth
import com.ares.callguard.domain.model.GuardStatus
import com.ares.callguard.ui.components.LoadingState
import com.ares.callguard.ui.setup.rememberSetupLauncher
import com.ares.callguard.ui.setup.stepsLeftMessage
import com.ares.callguard.ui.theme.Spacing

@Composable
fun HomeScreen(onAddPerson: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openSetupStep = rememberSetupLauncher(onReturned = viewModel::recheckSetup)

    val current = state
    if (current == null) {
        LoadingState()
        return
    }

    // SpaceBetween inside a minimum-height column keeps the control centred on tall screens
    // and still scrolls when large fonts make the content taller than the screen.
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Wordmark()

            Column(
                modifier = Modifier.padding(vertical = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                GuardSeal(status = current.health.status, onToggle = viewModel::toggleGuard)
                Spacer(Modifier.size(Spacing.sm))
                Text(
                    text = statusLine(current.health),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    text = peopleLine(current.peopleWhoCanReachYou),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (current.health.status == GuardStatus.NEEDS_ATTENTION) {
                    Spacer(Modifier.size(Spacing.sm))
                    SetupWarning(
                        message = stepsLeftMessage(current.health.missingSteps),
                        onClick = { openSetupStep(current.health.missingSteps.first()) },
                    )
                }
            }

            Button(
                onClick = onAddPerson,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                Spacer(Modifier.size(Spacing.sm))
                Text("Add Person")
            }
        }
    }
}

@Composable
private fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Icon(
            painterResource(R.drawable.ic_shield),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = "ARES CALL GUARD",
            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.18.em),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SetupWarning(message: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        }
    }
}

private fun statusLine(health: GuardHealth): String = when (health.status) {
    GuardStatus.ACTIVE -> "Watching for important calls"
    GuardStatus.INACTIVE -> "Not watching"
    GuardStatus.NEEDS_ATTENTION ->
        if (health.missingSteps.size == 1) "ARES needs one more step" else "ARES needs a few more steps"
}

private fun peopleLine(count: Int): String = when (count) {
    0 -> "Nobody can reach you yet"
    1 -> "1 person can reach you"
    else -> "$count people can reach you"
}

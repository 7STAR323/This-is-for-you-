package com.ares.callguard.ui.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.callguard.guard.ActiveAlert
import com.ares.callguard.ui.theme.ForestGreen
import com.ares.callguard.ui.theme.MutedChampagne
import com.ares.callguard.ui.theme.Spacing
import com.ares.callguard.ui.theme.WarmIvory

@Composable
fun AlertScreen(alert: ActiveAlert, onStop: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ForestGreen)
            .safeDrawingPadding()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = alert.personName,
            style = MaterialTheme.typography.displayLarge,
            color = WarmIvory,
            textAlign = TextAlign.Center,
        )
        Text(
            text = alert.platform?.let { "is calling on ${it.label}" } ?: "This is only a test",
            style = MaterialTheme.typography.headlineMedium,
            color = MutedChampagne,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Your important call needs attention",
            style = MaterialTheme.typography.titleLarge,
            color = WarmIvory,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = WarmIvory, contentColor = ForestGreen),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
        ) {
            Text("STOP ALERT", fontSize = 30.sp, style = MaterialTheme.typography.labelLarge)
        }
    }
}

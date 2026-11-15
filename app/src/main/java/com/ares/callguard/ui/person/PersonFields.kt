package com.ares.callguard.ui.person

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ares.callguard.domain.model.Platform
import com.ares.callguard.domain.model.PriorityLevel
import com.ares.callguard.ui.people.PlatformIcon
import com.ares.callguard.ui.theme.Spacing

/** Opens the system contact picker and returns the one phone entry the user chose. */
class PickContactPhone : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit) =
        Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data.takeIf { resultCode == Activity.RESULT_OK }
}

@Composable
fun NameField(name: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = name,
        onValueChange = onChange,
        label = { Text("Name") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PhoneNumberFields(
    numbers: List<String>,
    internationalForm: (String) -> String?,
    onChange: (Int, String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        numbers.forEachIndexed { index, number ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { onChange(index, it) },
                    label = { Text(if (numbers.size == 1) "Phone number" else "Phone number ${index + 1}") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    supportingText = {
                        val international = internationalForm(number)
                        Text(
                            when {
                                number.isBlank() -> "With or without the country code."
                                international != null -> "ARES will recognise this as $international"
                                else -> "This doesn't look like a full phone number yet."
                            },
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                if (numbers.size > 1) {
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove phone number ${index + 1}")
                    }
                }
            }
        }
        TextButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text("Add another number")
        }
    }
}

/** One switch per way of calling; a chosen app also asks for the name that app shows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlatformChooser(
    form: PersonForm,
    recentNames: Map<Platform, List<String>>,
    onToggle: (Platform, Boolean) -> Unit,
    onNameChange: (Platform, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Platform.entries.forEach { platform ->
            val chosen = platform in form.platforms
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(chosen, role = Role.Switch) { onToggle(platform, it) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                PlatformIcon(platform)
                Text(
                    if (platform == Platform.SIM) "Phone calls" else platform.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = chosen, onCheckedChange = null)
            }

            if (chosen && platform.isApp) {
                val clash = form.nameClashes[platform]
                OutlinedTextField(
                    value = form.appNames[platform].orEmpty(),
                    onValueChange = { onNameChange(platform, it) },
                    label = { Text("Name shown in ${platform.label}") },
                    singleLine = true,
                    isError = clash != null,
                    supportingText = {
                        Text(
                            if (clash != null) {
                                "$clash uses this name too, so ARES would ignore both to be safe."
                            } else {
                                "Exactly as ${platform.label} shows it when they call, emojis included."
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                val recent = recentNames[platform].orEmpty()
                if (recent.isNotEmpty()) {
                    Text(
                        "Recent ${platform.label} callers. Tap one to use it exactly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        recent.forEach { name ->
                            SuggestionChip(onClick = { onNameChange(platform, name) }, label = { Text(name) })
                        }
                    }
                }
            }
        }
        Text(
            "Apps rarely reveal a phone number, so ARES usually recognises app calls by this name. " +
                "When an app does reveal the number, the number decides.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PriorityChoice(selected: PriorityLevel, onSelect: (PriorityLevel) -> Unit) {
    Column(Modifier.selectableGroup()) {
        PriorityOption(
            title = "Ring through",
            description = "A loud alert if the call is still ringing and your phone is silent.",
            selected = selected == PriorityLevel.RING_THROUGH,
            onClick = { onSelect(PriorityLevel.RING_THROUGH) },
        )
        PriorityOption(
            title = "Missed-call alert only",
            description = "No loud alert while ringing, but you're told clearly if you miss them.",
            selected = selected == PriorityLevel.MISSED_ALERT_ONLY,
            onClick = { onSelect(PriorityLevel.MISSED_ALERT_ONLY) },
        )
    }
}

@Composable
private fun PriorityOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

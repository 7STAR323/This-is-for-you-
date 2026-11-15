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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhatsAppNameField(
    name: String,
    onChange: (String) -> Unit,
    recentNames: List<String>,
    sharedWith: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = name,
            onValueChange = onChange,
            label = { Text("Name shown in WhatsApp") },
            singleLine = true,
            supportingText = {
                Text(
                    if (sharedWith != null) {
                        "$sharedWith uses this name too. ARES can't tell them apart on WhatsApp."
                    } else {
                        "Must be exactly what WhatsApp shows when they call, emojis included."
                    },
                )
            },
            isError = sharedWith != null,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Test match", style = MaterialTheme.typography.titleSmall)
        if (recentNames.isEmpty()) {
            Text(
                "Names appear here after someone calls you on WhatsApp while ARES is on. " +
                    "Tap one to use it exactly as WhatsApp wrote it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Recent WhatsApp callers. Tap one to use it exactly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                recentNames.forEach { recent ->
                    SuggestionChip(onClick = { onChange(recent) }, label = { Text(recent) })
                }
            }
        }
    }
}

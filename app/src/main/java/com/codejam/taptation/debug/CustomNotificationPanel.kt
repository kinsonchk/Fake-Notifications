package com.codejam.taptation.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codejam.taptation.logic.NotificationContext

/**
 * Debug form for a notification whose [NotificationContext] we choose ourselves.
 *
 * [onPost] posts a real notification (tap it in the shade to exercise the full pipeline);
 * [onSimulateTap] skips the shade and dispatches the same context straight to the tap handler.
 */
@Composable
fun CustomNotificationPanel(
    onPost: (NotificationContext) -> Unit,
    onSimulateTap: (NotificationContext) -> Unit,
) {
    var packageName by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var channelName by rememberSaveable { mutableStateOf("") }
    var appCategory by rememberSaveable { mutableStateOf("") }
    var conversation by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var actions by rememberSaveable { mutableStateOf("") }

    fun spec() = NotificationContext(
        packageName = packageName.trim(),
        title = title.takeIf(String::isNotBlank),
        text = text.takeIf(String::isNotBlank),
        category = category.trim().takeIf(String::isNotEmpty),
        appCategory = appCategory.trim().toIntOrNull(),
        isConversation = conversation,
        channelName = channelName.trim().takeIf(String::isNotEmpty),
        actions = actions.split(',').map(String::trim).filter(String::isNotEmpty),
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Custom notification", style = MaterialTheme.typography.titleMedium)
            Text(
                "Posted from this app on the debug test channel. The listener swaps in the " +
                    "package, channel, conversation flag and app category below (debug builds only).",
                style = MaterialTheme.typography.bodySmall,
            )
            Field(packageName, { packageName = it }, "Package (blank = this app)", "com.instagram.android")
            Field(title, { title = it }, "Title")
            Field(text, { text = it }, "Text", singleLine = false)
            Field(category, { category = it }, "Category", "msg, social, promo, email, call, alarm, event, reminder")
            Field(channelName, { channelName = it }, "Channel name", "Direct messages")
            Field(
                appCategory, { input -> if (input.all(Char::isDigit)) appCategory = input },
                "App category (number, optional)", "0 game, 4 social, 7 productivity",
            )
            Text("Conversation", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "Unknown", true to "Yes", false to "No").forEach { (value, label) ->
                    FilterChip(
                        selected = conversation == value,
                        onClick = { conversation = value },
                        label = { Text(label) },
                    )
                }
            }
            Field(actions, { actions = it }, "Action buttons (comma-separated)", "Reply, Mark as read")
            Button(onClick = { onPost(spec()) }, modifier = Modifier.fillMaxWidth()) {
                Text("Post custom notification")
            }
            OutlinedButton(
                onClick = { onSimulateTap(spec()) },
                enabled = packageName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Simulate tap with these values") }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
    )
}

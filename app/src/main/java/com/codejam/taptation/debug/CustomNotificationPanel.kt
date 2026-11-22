package com.codejam.taptation.debug

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.codejam.taptation.logic.MediaKind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Form fields for a custom notification. [actionsText] is kept as typed (comma-separated) so a
 * trailing comma doesn't vanish while typing; the caller splits it when posting or saving.
 */
@Composable
fun CustomNotificationFields(
    draft: NotificationPreset,
    onDraftChange: (NotificationPreset) -> Unit,
    actionsText: String,
    onActionsTextChange: (String) -> Unit,
) {
    Field(draft.packageName, { onDraftChange(draft.copy(packageName = it)) },
        "Package (blank = this app)", "com.instagram.android")
    Field(draft.title, { onDraftChange(draft.copy(title = it)) }, "Title")
    Field(draft.text, { onDraftChange(draft.copy(text = it)) }, "Text", singleLine = false)
    Field(draft.category, { onDraftChange(draft.copy(category = it)) },
        "Category", "msg, social, promo, email, call, alarm, event, reminder")
    Field(draft.channelName, { onDraftChange(draft.copy(channelName = it)) }, "Channel name", "Direct messages")
    Field(
        draft.appCategory?.toString().orEmpty(),
        { input -> if (input.all(Char::isDigit)) onDraftChange(draft.copy(appCategory = input.toIntOrNull())) },
        "App category (number, optional)", "0 game, 4 social, 7 productivity",
    )
    Text("Conversation", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null to "Unknown", true to "Yes", false to "No").forEach { (value, label) ->
            FilterChip(
                selected = draft.isConversation == value,
                onClick = { onDraftChange(draft.copy(isConversation = value)) },
                label = { Text(label) },
            )
        }
    }
    Field(actionsText, onActionsTextChange, "Action buttons (comma-separated)", "Reply, Mark as read")
    Field(draft.sender, { onDraftChange(draft.copy(sender = it)) }, "Chat sender (optional)", "Alex")
    Text("Group chat", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null to "Not a chat", false to "1-to-1", true to "Group").forEach { (value, label) ->
            FilterChip(
                selected = draft.isGroup == value,
                onClick = { onDraftChange(draft.copy(isGroup = value)) },
                label = { Text(label) },
            )
        }
    }
    Text("Attachment", style = MaterialTheme.typography.bodyMedium)
    // Image, GIF, video and audio attach a real file (MessagingStyle data); a sticker has no MIME
    // type, so apps only show text: type e.g. "Sticker" in Text to test that path.
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf<MediaKind?>(null) + MediaKind.entries.filter { it != MediaKind.Sticker }).forEach { kind ->
            FilterChip(
                selected = draft.media == kind,
                onClick = { onDraftChange(draft.copy(media = kind)) },
                label = { Text(kind?.name ?: "None") },
            )
        }
    }
}

/** Splits the typed action list. */
fun parseActions(text: String): List<String> = text.split(',').map(String::trim).filter(String::isNotEmpty)

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

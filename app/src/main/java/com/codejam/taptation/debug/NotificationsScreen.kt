package com.codejam.taptation.debug

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.TaptationApplication
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.NotificationClassifier
import com.codejam.taptation.logic.NotificationContext
import com.codejam.taptation.logic.TapDispatcher
import com.codejam.taptation.service.TapListenerService
import com.codejam.taptation.service.TapLog
import com.codejam.taptation.ui.common.ClassificationBadge
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.TabScreen
import com.codejam.taptation.ui.common.TapExplanations
import com.codejam.taptation.ui.common.TapRecordRow
import com.codejam.taptation.ui.common.rememberGameRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LAST_TAPS = 20

private val DraftSaver = Saver<NotificationPreset, String>(
    save = { NotificationPresetCodec.encodeOne(it) },
    restore = { NotificationPresetCodec.decodeOne(it) },
)

/**
 * "What is the listener seeing, and why was it judged that way?" Debug builds only.
 * Composer + saved presets, the last processed taps with their reason, and observed notifications
 * with the classification they would get now.
 */
@Composable
fun NotificationsScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = rememberGameRepository()
    val presetStore = remember(context) { NotificationPresetStore.get(context) }
    val snapshot by repository.state.collectAsStateWithLifecycle()
    val presets by presetStore.presets.collectAsStateWithLifecycle()
    val observations by TapListenerService.observations.collectAsStateWithLifecycle()
    val judges = remember(context) { (context.applicationContext as TaptationApplication).judges }
    val verdicts by judges.verdicts.collectAsStateWithLifecycle()
    val config = snapshot?.config ?: GameConfig()

    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(NotificationPreset()) }
    var actionsText by rememberSaveable { mutableStateOf("") }
    var presetName by rememberSaveable { mutableStateOf("") }
    val draftIcon by rememberIcon(context, draft.iconFile)

    // What to post once POST_NOTIFICATIONS is granted.
    var pendingPost by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) pendingPost?.invoke()
        else Toast.makeText(context, "Notification permission denied", Toast.LENGTH_SHORT).show()
        pendingPost = null
    }
    fun postWithPermission(post: () -> Unit) {
        if (TestNotification.canPost(context)) {
            post()
        } else {
            // canPost() is only false on Android 13+, where this permission exists.
            pendingPost = post
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun post(preset: NotificationPreset) {
        scope.launch {
            val icon = withContext(Dispatchers.IO) { NotificationIcons.load(context, preset.iconFile) }
            postWithPermission { TestNotification.post(context, preset.toContext(), icon) }
        }
    }
    fun iconsInUse(except: String? = null): Set<String> =
        (presets.filter { it.id != except }.mapNotNull { it.iconFile } + listOfNotNull(draft.iconFile)).toSet()

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val previous = draft.iconFile
                val name = withContext(Dispatchers.IO) { NotificationIcons.import(context, uri) }
                draft = draft.copy(iconFile = name)
                if (previous != null && presets.none { it.iconFile == previous }) {
                    withContext(Dispatchers.IO) { NotificationIcons.delete(context, previous) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't use that image", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Drop images left behind by drafts that were never saved.
    LaunchedEffect(Unit) {
        val keep = iconsInUse()
        withContext(Dispatchers.IO) { NotificationIcons.deleteUnused(context, keep) }
    }

    TabScreen(contentPadding, "Notifications") {
        item {
            SectionCard("Quick test", "Plain notification from this app. Tap it: one tap. Swipe it: nothing.") {
                Button(
                    onClick = { postWithPermission { TestNotification.post(context) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Post test notification") }
            }
        }

        item {
            SectionCard(
                "Custom notification",
                "Posted from this app on the debug channel. The listener swaps in the package, channel, " +
                    "conversation flag and app category below, so it can stand in for another app.",
            ) {
                CustomNotificationFields(draft, { draft = it }, actionsText, { actionsText = it })
                IconPicker(
                    icon = draftIcon,
                    onPick = {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onClear = { draft = draft.copy(iconFile = null) },
                )
                val current = draft.copy(actions = parseActions(actionsText))
                val preview = NotificationClassifier.assess(current.toContext(context.packageName), config)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Would be:", style = MaterialTheme.typography.bodyMedium)
                    ClassificationBadge(preview.classification)
                    Text(TapExplanations.rule(preview.reason), style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { post(current) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Post custom notification")
                }
                OutlinedButton(
                    onClick = {
                        val tapped = current.toContext(context.packageName).copy(ageMillis = 0)
                        TapLog.record(tapped.packageName)
                        TapDispatcher.dispatch(tapped)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Simulate tap (no notification)") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = presetName, onValueChange = { presetName = it },
                        label = { Text("Save as") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    Button(enabled = presetName.isNotBlank(), onClick = {
                        val name = presetName.trim()
                        val id = presets.firstOrNull { it.name == name }?.id ?: UUID.randomUUID().toString()
                        presetStore.save(current.copy(id = id, name = name))
                        Toast.makeText(context, "Saved \"$name\"", Toast.LENGTH_SHORT).show()
                    }) { Text("Save") }
                }
            }
        }

        item {
            SectionCard("Saved notifications", if (presets.isEmpty()) "None yet. Fill the form and Save." else null) {
                presets.forEach { preset ->
                    PresetRow(
                        preset = preset,
                        config = config,
                        onPost = { post(preset) },
                        onEdit = {
                            draft = preset.copy(id = "", name = "")
                            actionsText = preset.actions.joinToString(", ")
                            presetName = preset.name
                        },
                        onDelete = {
                            presetStore.delete(preset.id)
                            val icon = preset.iconFile
                            if (icon != null && icon !in iconsInUse(except = preset.id)) {
                                scope.launch(Dispatchers.IO) { NotificationIcons.delete(context, icon) }
                            }
                        },
                    )
                }
            }
        }

        item {
            Text("Last $LAST_TAPS taps", style = MaterialTheme.typography.titleMedium)
            Text("What each tap was judged as and why.", style = MaterialTheme.typography.bodySmall)
        }
        val taps = snapshot?.taps.orEmpty().takeLast(LAST_TAPS).asReversed()
        if (taps.isEmpty()) item { Text("No taps yet.") }
        items(taps) { TapRecordRow(it) }

        item {
            Text("Observed notifications", style = MaterialTheme.typography.titleMedium)
            Text("Last 10 posted, memory only. \"Would be\" uses the current settings.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (observations.isEmpty()) item { Text("Nothing observed since the listener connected.") }
        // Live AI verdicts for "Let us decide" apps, as the tap would see them.
        items(observations) { ObservedRow(it.copy(judged = judges.classificationFor(it, verdicts)), config) }
    }
}

@Composable
private fun rememberIcon(context: Context, name: String?) = produceState<Bitmap?>(null, name) {
    value = withContext(Dispatchers.IO) { NotificationIcons.load(context, name) }
}

@Composable
private fun IconPicker(icon: Bitmap?, onPick: () -> Unit, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) {
            Image(icon.asImageBitmap(), contentDescription = "Notification image", modifier = Modifier.size(48.dp))
        } else {
            Text("No image", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = onPick) { Text(if (icon == null) "Choose image" else "Change image") }
        if (icon != null) TextButton(onClick = onClear) { Text("Remove") }
    }
}

@Composable
private fun PresetRow(
    preset: NotificationPreset,
    config: GameConfig,
    onPost: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val icon by rememberIcon(context, preset.iconFile)
    val preview = NotificationClassifier.assess(preset.toContext(context.packageName), config)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icon?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(preset.name, style = MaterialTheme.typography.titleSmall)
                    Text(preset.packageName.ifBlank { "this app" }, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ClassificationBadge(preview.classification)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPost) { Text("Post") }
                OutlinedButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun ObservedRow(context: NotificationContext, config: GameConfig) {
    val assessment = NotificationClassifier.assess(context, config)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClassificationBadge(assessment.classification)
                Text(context.packageName, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Text("Would be: ${TapExplanations.rule(assessment.reason)}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "title=${context.title ?: "missing"}\n" +
                    "category=${context.category ?: "missing"} • channel=${context.channelName ?: "missing"} • " +
                    "conversation=${context.isConversation ?: "unknown"}" +
                    context.actions.takeIf { it.isNotEmpty() }?.joinToString(prefix = "\nactions=").orEmpty(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

package com.codejam.taptation.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.codejam.taptation.logic.DisableGate
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.rememberNow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ActivationControls(repository: GameRepository, config: GameConfig) {
    val now = rememberNow(repository)
    val active = config.isActive(now)
    val scope = rememberCoroutineScope()
    var challenge by remember { mutableStateOf<DisableGate.Challenge?>(null) }
    var busy by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("App active", style = MaterialTheme.typography.titleMedium)
                Text(if (active) "Monitoring and catches are on." else "Monitoring and catches are paused.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = active, enabled = !busy, modifier = Modifier.semantics { contentDescription = "App active" }, onCheckedChange = { enable ->
                busy = true
                if (enable) repository.enable().whenComplete { _, _ -> scope.launch { busy = false } }
                else repository.beginDisable().whenComplete { next, _ -> scope.launch {
                    busy = false; challenge = next
                } }
            })
        }
        config.disabledUntilMillis?.takeIf { !active }?.let {
            Text("Automatically resumes at ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it))}")
        }
        }
    }
    challenge?.let { pending ->
        DisableConfirmation(pending, busy, onCancel = { repository.cancelDisable(); challenge = null },
            onConfirm = { minutes ->
                busy = true
                repository.confirmDisable(pending.id, minutes).whenComplete { _, _ -> scope.launch {
                    busy = false; challenge = null
                } }
            })
    }
}

@Composable
fun DisableConfirmation(challenge: DisableGate.Challenge, busy: Boolean,
    onCancel: () -> Unit, onConfirm: (Int?) -> Unit,
) {
    var now by remember(challenge.id) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var duration by remember(challenge.id) { mutableStateOf<Int?>(15) }
    LaunchedEffect(challenge.id) {
        while (now < challenge.readyAtMillis) { delay(250); now = SystemClock.elapsedRealtime() }
    }
    val seconds = ((challenge.readyAtMillis - now).coerceAtLeast(0) + 999) / 1000
    AlertDialog(onDismissRequest = { if (!busy) onCancel() },
        title = { Text("Disable with intention") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Monitoring, new activity recording and catches will stop. Your existing history stays.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15 to "15 minutes", 60 to "1 hour", null to "Until enabled").forEach { (value, label) ->
                        FilterChip(selected = duration == value, onClick = { duration = value }, label = { Text(label) })
                    }
                }
                Text(if (seconds > 0) "Take a moment. Confirm in $seconds seconds." else "Ready to confirm your choice.")
            }
        },
        confirmButton = { TextButton(enabled = seconds == 0L && !busy, onClick = { onConfirm(duration) }) { Text("Confirm disable") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onCancel) { Text("Keep active") } })
}

@Composable
fun CatchPermissionCard() {
    val context = LocalContext.current
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { overlayGranted = Settings.canDrawOverlays(context) }
    SectionCard("Caught feedback", "Choose where to go after a catch.") {
        Text(if (overlayGranted) "Caught overlays are enabled." else "Allow display over other apps for the two-button caught overlay.")
        if (!overlayGranted) Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.fromParts("package", context.packageName, null)))
        }) { Text("Allow caught overlay") }
        if (Build.VERSION.SDK_INT >= 33) TextButton(onClick = {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }) { Text("Enable notification fallback") }
        Text("Without overlay access, a catch notification opens the same choice in Taptation.")
    }
}

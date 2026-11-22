package com.codejam.taptation.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.codejam.taptation.service.PermissionHelper
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.openOverlaySettings
import com.codejam.taptation.ui.home.GrassMeadow

private const val PREFS = "first_run"
private const val TUTORIAL_COMPLETE = "tutorial_complete"

internal fun isTutorialComplete(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(TUTORIAL_COMPLETE, false)

internal fun completeTutorial(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putBoolean(TUTORIAL_COMPLETE, true).apply()
}

@Composable
internal fun OnboardingScreen(startAtSetup: Boolean, onComplete: () -> Unit) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(if (startAtSetup) 3 else 0) }
    var notificationAccess by remember { mutableStateOf(PermissionHelper.isNotificationAccessGranted(context)) }
    var overlayAccess by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    fun refresh() {
        notificationAccess = PermissionHelper.isNotificationAccessGranted(context)
        overlayAccess = Settings.canDrawOverlays(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    Column(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (page == 3) "A couple of settings" else "A little more room",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            if (page < 3) {
                Text("Step ${page + 1} of 3", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (page == 0) GrassMeadow(health = 100)
                SectionCard(
                    title = when (page) {
                        0 -> "Make space for what matters."
                        1 -> "Your important apps stay safe."
                        else -> "A tap can be a moment to pause."
                    },
                    subtitle = when (page) {
                        0 -> "Some distracting notification taps trigger a reminder and lower your meadow’s health. Important notifications are excluded."
                        1 -> "Choose the apps and people you want to protect. Taptation can also skip urgent and important notifications."
                        else -> "Taptation checks distracting notification taps. If a catch happens, your meadow loses health and a reminder shows what it cost before you continue."
                    },
                ) {
                    if (page == 1) Text("You can choose safe apps later in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text("Taptation needs notification access to detect notification taps. Showing catch feedback over other apps is optional but recommended.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth())
                SectionCard("Notification access", "Lets Taptation notice when you open a notification. Message text is not stored.") {
                    AccessStatus(if (notificationAccess) "Enabled" else "Needed", notificationAccess)
                    if (!notificationAccess) Button(
                        onClick = { PermissionHelper.openNotificationAccessSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Open notification access settings") }
                }
                SectionCard("Display over other apps",
                    "Recommended. Lets catch feedback appear while you’re using another app. Without it, a notification opens the choice instead.") {
                    AccessStatus(if (overlayAccess) "Enabled" else "Recommended", overlayAccess, required = false)
                    if (!overlayAccess) Button(
                        onClick = { openOverlaySettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Allow display over other apps") }
                }
                Text("Return here after changing either setting. Taptation will check again automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth())
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(4) { index ->
                    Surface(
                        color = if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.width(if (index == page) 22.dp else 8.dp).height(8.dp),
                    ) {}
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page > 0 && page < 3) {
                    OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Back") }
                }
                val canFinish = page == 3 && notificationAccess
                Button(
                    onClick = {
                        if (page < 3) page++ else if (canFinish) onComplete()
                    },
                    enabled = page < 3 || canFinish,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(when (page) {
                    0, 1 -> "Next"
                    2 -> "Set up notifications"
                    else -> "Finish setup"
                }) }
            }
        }
    }
}

@Composable
private fun AccessStatus(label: String, enabled: Boolean, required: Boolean = true) {
    Text(
        "●  $label",
        style = MaterialTheme.typography.bodyMedium,
        color = when {
            enabled -> MaterialTheme.colorScheme.primary
            required -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

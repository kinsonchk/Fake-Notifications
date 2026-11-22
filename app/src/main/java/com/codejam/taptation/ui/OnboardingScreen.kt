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
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.BuildConfig
import com.codejam.taptation.service.PermissionHelper
import com.codejam.taptation.ui.common.CriteriaField
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.openOverlaySettings
import com.codejam.taptation.ui.common.rememberGameRepository
import com.codejam.taptation.ui.home.GrassMeadow

private const val PREFS = "first_run"
private const val TUTORIAL_COMPLETE = "tutorial_complete"
private const val PAGE_WELCOME = 0
private const val PAGE_PERSONAL_CRITERIA = 1
private const val PAGE_SAFE_APPS = 2
private const val PAGE_CATCHES = 3
private const val PAGE_SETUP = 4
private const val TUTORIAL_PAGE_COUNT = PAGE_SETUP

internal fun isTutorialComplete(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(TUTORIAL_COMPLETE, false)

internal fun completeTutorial(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putBoolean(TUTORIAL_COMPLETE, true).apply()
}

@Composable
internal fun OnboardingScreen(startAtSetup: Boolean, onComplete: () -> Unit) {
    val context = LocalContext.current
    val repository = rememberGameRepository()
    val gameState by repository.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(if (startAtSetup) PAGE_SETUP else PAGE_WELCOME) }
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
                when (page) {
                    PAGE_SETUP -> "A couple of settings"
                    PAGE_PERSONAL_CRITERIA -> "What matters to you?"
                    PAGE_SAFE_APPS -> "Set rules for your apps"
                    PAGE_CATCHES -> "Pause, then choose what to do next."
                    else -> "Give your attention a little space"
                },
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            if (page < PAGE_SETUP) {
                Text("Step ${page + 1} of $TUTORIAL_PAGE_COUNT", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (page == PAGE_WELCOME) GrassMeadow(health = 100)
                if (page == PAGE_PERSONAL_CRITERIA) {
                    SectionCard(
                        title = "Set your personal notification priorities",
                        subtitle = "These preferences guide automatic checks. They only affect apps set to Let us decide, and you can change them later in Settings.",
                    ) {
                        val config = gameState?.config
                        if (config == null) {
                            Text("Loading your saved preferences…", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            CriteriaField(
                                label = "What messages would you not want Taptation to interrupt?",
                                placeholder = "Family messages, work shifts, delivery updates, appointment changes…",
                                saved = config.essentialCriteria,
                            ) { value -> repository.updateConfig { it.copy(essentialCriteria = value.trim()) } }
                            CriteriaField(
                                label = "What notifications would distract you?",
                                placeholder = "Likes, follows, game rewards, sales, suggested posts…",
                                saved = config.nonEssentialCriteria,
                            ) { value -> repository.updateConfig { it.copy(nonEssentialCriteria = value.trim()) } }
                        }
                        Text(
                            if (BuildConfig.CF_ACCOUNT_ID.isNotBlank() && BuildConfig.CF_API_TOKEN.isNotBlank()) {
                                "Your answers are saved on this device. When AI judging runs, Taptation sends your preferences and notification details to Cloudflare. Notification details are not saved by Taptation."
                            } else {
                                "Your answers are saved on this device. Automatic AI checks are not available in this build, so apps set to Let us decide will not be penalized automatically."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    SectionCard(
                        title = when (page) {
                            PAGE_WELCOME -> "Make space for what matters."
                            PAGE_SAFE_APPS -> "Choose which apps are safe or always caught if you want instead of the default behavior"
                            else -> "A tap can be a moment to pause."
                        },
                        subtitle = when (page) {
                            PAGE_WELCOME -> "Some distracting notification taps trigger a reminder and lower your meadow’s health. Important notifications are excluded."
                            PAGE_SAFE_APPS -> "Always important keeps an app safe. Always bait catches each eligible tap. Apps left on Let us decide are checked automatically; we explain that next."
                            else -> "For apps on Let us decide, AI checks whether each notification is important or distracting. Important ones stay safe; distracting ones use your catch chance. Always bait will catches all notification tapped, while Always important stays safe. If caught, your meadow loses health and a reminder explains the cost."
                        },
                    ) {
                        if (page == PAGE_SAFE_APPS) Text("You can change an app’s rule any time in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                repeat(PAGE_SETUP + 1) { index ->
                    Surface(
                        color = if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.width(if (index == page) 22.dp else 8.dp).height(8.dp),
                    ) {}
                }
            }
            if (page == PAGE_PERSONAL_CRITERIA) {
                TextButton(onClick = { page = PAGE_SAFE_APPS }) { Text("Skip for now") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page > PAGE_WELCOME && page < PAGE_SETUP) {
                    OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Back") }
                }
                val canFinish = page == PAGE_SETUP && notificationAccess
                Button(
                    onClick = {
                        if (page < PAGE_SETUP) page++ else if (canFinish) onComplete()
                    },
                    enabled = page < PAGE_SETUP || canFinish,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(when (page) {
                    PAGE_WELCOME, PAGE_PERSONAL_CRITERIA, PAGE_SAFE_APPS -> "Next"
                    PAGE_CATCHES -> "Set up notifications"
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

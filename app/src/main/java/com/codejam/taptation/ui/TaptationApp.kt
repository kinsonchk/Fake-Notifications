package com.codejam.taptation.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.codejam.taptation.R
import com.codejam.taptation.debug.DebugScreen
import com.codejam.taptation.debug.NotificationsScreen
import com.codejam.taptation.ui.home.HomeScreen
import com.codejam.taptation.ui.settings.SettingsScreen
import com.codejam.taptation.ui.stats.StatsScreen
import com.codejam.taptation.TaptationApplication
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.ui.common.CatchChoiceDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Bottom tabs. Debug-only tabs are hidden in release builds. */
enum class Tab(val label: String, @param:DrawableRes val icon: Int, val debugOnly: Boolean) {
    Home("Home", R.drawable.ic_tab_home, debugOnly = false),
    Stats("Stats", R.drawable.ic_tab_stats, debugOnly = false),
    Notifications("Notifications", R.drawable.ic_tab_notifications, debugOnly = true),
    Settings("Settings", R.drawable.ic_tab_settings, debugOnly = false),
    Debug("Debug", R.drawable.ic_tab_debug, debugOnly = true),
}

// Home visual metric and onboarding remain a product decision; caught choices are shared UI.
@Composable
fun TaptationApp(debug: Boolean) {
    val tabs = Tab.entries.filter { debug || !it.debugOnly }
    val compact = LocalConfiguration.current.screenWidthDp < 380
    var selected by rememberSaveable { mutableStateOf(Tab.Home) }
    var postponedCatchId by remember { mutableStateOf<String?>(null) }
    val app = LocalContext.current.applicationContext as TaptationApplication
    val snapshot by app.game.state.collectAsStateWithLifecycle()
    val requests by app.feedback.homeRequests.collectAsStateWithLifecycle()
    var handledRequest by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(requests) { if (requests > handledRequest) { selected = Tab.Home; handledRequest = requests } }
    LaunchedEffect(snapshot?.pendingCatch?.id) { postponedCatchId = null }
    // Keeps each tab's scroll position and typed text while another tab is shown.
    val tabState = rememberSaveableStateHolder()

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
            Column {
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tabs.forEach { tab ->
                    val active = tab == selected
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .selectable(selected = active, role = Role.Tab, onClick = { selected = tab })
                        .heightIn(min = 52.dp).padding(horizontal = 2.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(painterResource(tab.icon), contentDescription = null, modifier = Modifier.size(20.dp),
                            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (compact && tab == Tab.Notifications) "Notifs" else tab.label,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                }
            }
            }
        },
    ) { padding ->
        tabState.SaveableStateProvider(selected.name) {
            when (selected) {
                Tab.Home -> HomeScreen(padding, onSettings = { selected = Tab.Settings })
                Tab.Stats -> StatsScreen(padding)
                Tab.Notifications -> NotificationsScreen(padding)
                Tab.Settings -> SettingsScreen(padding)
                Tab.Debug -> DebugScreen(padding)
            }
        }
    }
    snapshot?.pendingCatch?.takeIf { it.id == postponedCatchId }?.let {
        Surface(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(12.dp),
            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer, shadowElevation = 4.dp) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(androidx.compose.ui.res.stringResource(R.string.caught_choice_pending),
                    Modifier.weight(1f), color = MaterialTheme.colorScheme.onTertiaryContainer)
                androidx.compose.material3.TextButton(onClick = { postponedCatchId = null }) {
                    Text(androidx.compose.ui.res.stringResource(R.string.caught_choose_now))
                }
            }
        }
    }
    snapshot?.pendingCatch?.takeIf { it.id != postponedCatchId }?.let { pending ->
        CatchChoiceDialog(continueExtraDamage = pending.continueExtraDamage,
            onTaptation = { app.feedback.choose(pending, true) },
            onOriginal = { app.feedback.choose(pending, false) },
            onLater = { postponedCatchId = pending.id })
    }
    }
}

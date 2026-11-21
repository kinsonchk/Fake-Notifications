package com.codejam.taptation.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.codejam.taptation.R
import com.codejam.taptation.debug.DebugScreen
import com.codejam.taptation.debug.NotificationsScreen
import com.codejam.taptation.ui.home.HomeScreen
import com.codejam.taptation.ui.settings.SettingsScreen
import com.codejam.taptation.ui.stats.StatsScreen

/** Bottom tabs. Debug-only tabs are hidden in release builds. */
enum class Tab(val label: String, @param:DrawableRes val icon: Int, val debugOnly: Boolean) {
    Home("Home", R.drawable.ic_tab_home, debugOnly = false),
    Stats("Stats", R.drawable.ic_tab_stats, debugOnly = false),
    Notifications("Notifications", R.drawable.ic_tab_notifications, debugOnly = true),
    Settings("Settings", R.drawable.ic_tab_settings, debugOnly = false),
    Debug("Debug", R.drawable.ic_tab_debug, debugOnly = true),
}

// TODO(Person 3): replace the Home status card with the tree visual, add onboarding and lockout.
@Composable
fun TaptationApp(debug: Boolean) {
    val tabs = Tab.entries.filter { debug || !it.debugOnly }
    var selected by rememberSaveable { mutableStateOf(Tab.Home) }
    // Keeps each tab's scroll position and typed text while another tab is shown.
    val tabState = rememberSaveableStateHolder()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { selected = tab },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        tabState.SaveableStateProvider(selected.name) {
            when (selected) {
                Tab.Home -> HomeScreen(padding)
                Tab.Stats -> StatsScreen(padding)
                Tab.Notifications -> NotificationsScreen(padding)
                Tab.Settings -> SettingsScreen(padding)
                Tab.Debug -> DebugScreen(padding)
            }
        }
    }
}

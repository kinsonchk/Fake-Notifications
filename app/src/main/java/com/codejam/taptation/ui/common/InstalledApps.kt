package com.codejam.taptation.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.TaptationApplication

/** Loading (null apps), loaded, or failed; [reload] asks the backend to read the apps again. */
data class InstalledAppsState(val apps: List<InstalledApp>?, val error: String?, val reload: () -> Unit)

/**
 * The backend's launchable-app list (service/AppDirectory: not Taptation, sorted by name, with the
 * notification permission). Prefer [com.codejam.taptation.TaptationApplication.rulePicker] for the
 * rules page: it already applies the eligibility rule.
 */
@Composable
fun rememberInstalledAppsState(): InstalledAppsState {
    val directory = (LocalContext.current.applicationContext as TaptationApplication).apps
    val list by directory.apps.collectAsStateWithLifecycle()
    return InstalledAppsState(list.apps, list.error, directory::reload)
}

package com.codejam.taptation.ui.common

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Loading, loaded, or failed; [reload] queries the package manager again. */
data class InstalledAppsState(val apps: List<InstalledApp>?, val error: String?, val reload: () -> Unit)

/**
 * Launchable apps on the phone (not our own), sorted by name. The manifest's `<queries>` entry for
 * MAIN/LAUNCHER makes them visible on Android 11+. Loaded off the main thread.
 */
@Composable
fun rememberInstalledAppsState(): InstalledAppsState {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { version++ }
    LaunchedEffect(context.packageManager, version) {
        apps = null
        error = null
        try {
            apps = withContext(Dispatchers.IO) {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                queryLauncherApps(context.packageManager, intent).asSequence()
                    .mapNotNull { info ->
                        val app = info.activityInfo?.applicationInfo ?: return@mapNotNull null
                        if (app.packageName == context.packageName) return@mapNotNull null
                        InstalledApp(app.packageName, app.loadLabel(context.packageManager).toString())
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.localizedMessage ?: "Could not read installed apps."
        }
    }
    return InstalledAppsState(apps, error) { version++ }
}

@Suppress("DEPRECATION")
private fun queryLauncherApps(packageManager: PackageManager, intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        packageManager.queryIntentActivities(intent, 0)
    }

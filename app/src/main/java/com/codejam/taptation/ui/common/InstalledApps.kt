package com.codejam.taptation.ui.common

import android.Manifest
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

/** [notificationsEnabled]: allowed to post notifications (always true before Android 13, where there is no public check). */
data class InstalledApp(val packageName: String, val label: String, val notificationsEnabled: Boolean)

/** Loading, loaded, or failed; [reload] queries the package manager again. */
data class InstalledAppsState(val apps: List<InstalledApp>?, val error: String?, val reload: () -> Unit)

/**
 * Launchable apps on the phone (not our own), sorted by name. The manifest's `<queries>` entry for
 * MAIN/LAUNCHER makes them visible on Android 11+. Loaded off the main thread.
 */
@Composable
fun rememberInstalledApps(): InstalledAppsState {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf(0) }
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
                        InstalledApp(app.packageName, app.loadLabel(context.packageManager).toString(),
                            notificationsAllowed(context.packageManager, app.packageName))
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

/**
 * Android 13+ backs the per-app notification switch in system settings with the POST_NOTIFICATIONS
 * runtime permission (older apps get it implicitly), so its grant state says whether the app can notify.
 * Before 13 another app's setting can't be read, so every app counts.
 */
private fun notificationsAllowed(packageManager: PackageManager, packageName: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return packageManager.checkPermission(Manifest.permission.POST_NOTIFICATIONS, packageName) ==
        PackageManager.PERMISSION_GRANTED
}

@Suppress("DEPRECATION")
private fun queryLauncherApps(packageManager: PackageManager, intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        packageManager.queryIntentActivities(intent, 0)
    }

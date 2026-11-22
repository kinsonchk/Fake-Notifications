package com.codejam.taptation.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import com.codejam.taptation.logic.AppList
import com.codejam.taptation.logic.InstalledApp
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The phone's apps, for every screen and the caught overlay: the launchable app list (with
 * whether each may post notifications), display names and icons. The only place that queries the
 * package manager for this; screens collect [apps] and call [reload], they never query Android.
 */
class AppDirectory(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val loader = Executors.newSingleThreadExecutor()
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = ConcurrentHashMap<String, Bitmap>()
    private val _apps = MutableStateFlow(AppList())

    /** Launchable apps except Taptation, sorted by name. Loaded off the main thread by [reload]. */
    val apps: StateFlow<AppList> = _apps.asStateFlow()

    /** Before Android 13 another app's notification permission can't be read, so all apps are listed. */
    val notificationFilterSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Re-reads the installed apps (cheap to call on every screen resume). Keeps the old list while loading. */
    fun reload() {
        loader.execute {
            _apps.value = try {
                AppList(apps = loadLaunchableApps())
            } catch (e: RuntimeException) {
                _apps.value.copy(error = e.localizedMessage ?: "Couldn't read the installed apps.")
            }
        }
    }

    /** Display name, or a readable guess from the package name when the app isn't visible. */
    fun label(packageName: String): String = labels.getOrPut(packageName) {
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
                .takeIf { it.isNotBlank() } ?: fallbackLabel(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            fallbackLabel(packageName)
        } catch (_: SecurityException) {
            fallbackLabel(packageName)
        }
    }

    /** 96 px icon, or null when the app isn't visible or installed. Call off the main thread. */
    fun icon(packageName: String): Bitmap? = icons[packageName] ?: try {
        packageManager.getApplicationIcon(packageName).toBitmap(96, 96).also { icons[packageName] = it }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    private fun loadLaunchableApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return queryLauncherApps(intent).asSequence()
            .mapNotNull { info ->
                val app = info.activityInfo?.applicationInfo ?: return@mapNotNull null
                if (app.packageName == appContext.packageName) return@mapNotNull null
                val label = app.loadLabel(packageManager).toString().also { labels[app.packageName] = it }
                InstalledApp(app.packageName, label, notificationsAllowed(app.packageName),
                    app.category.takeIf { it != ApplicationInfo.CATEGORY_UNDEFINED })
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
            .toList()
    }

    private fun notificationsAllowed(packageName: String): Boolean =
        !notificationFilterSupported ||
            packageManager.checkPermission(Manifest.permission.POST_NOTIFICATIONS, packageName) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun queryLauncherApps(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            packageManager.queryIntentActivities(intent, 0)
        }

    companion object {
        private val noise = setOf("android", "app", "apps", "mobile", "lite", "client", "release", "google", "com", "org", "net")

        /** Readable guess from a package name when the phone can't give the real label. */
        fun fallbackLabel(packageName: String): String {
            val parts = packageName.split('.').filter { it.isNotBlank() }
            val pick = parts.asReversed().firstOrNull { it.lowercase(Locale.ROOT) !in noise } ?: parts.lastOrNull() ?: packageName
            return pick.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }
}

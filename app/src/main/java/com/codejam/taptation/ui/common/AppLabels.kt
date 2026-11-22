package com.codejam.taptation.ui.common

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app the user can pick: its package name stays internal, people see [label] and the icon. */
data class InstalledApp(val packageName: String, val label: String)

/** App names and icons from the phone, so no screen shows a package name. */
class AppLabels(context: Context) {
    private val packageManager = context.applicationContext.packageManager
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = ConcurrentHashMap<String, ImageBitmap>()

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

    /** Icon bitmap, or null when the app isn't visible or installed. Call off the main thread. */
    fun icon(packageName: String): ImageBitmap? = icons[packageName] ?: try {
        packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap().also { icons[packageName] = it }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    companion object {
        private val noise = setOf("android", "app", "apps", "mobile", "lite", "client", "release", "google", "com", "org", "net")

        /** Readable guess from a package name when the phone can't give the real label. */
        fun fallbackLabel(packageName: String): String {
            val parts = packageName.split('.').filter { it.isNotBlank() }
            val pick = parts.asReversed().firstOrNull { it.lowercase(Locale.ROOT) !in noise } ?: parts.lastOrNull() ?: packageName
            return pick.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }
        }

        /** Case-insensitive match on the app name only; blank query keeps everything. */
        fun filter(apps: List<InstalledApp>, query: String): List<InstalledApp> {
            val needle = query.trim()
            return if (needle.isEmpty()) apps else apps.filter { it.label.contains(needle, ignoreCase = true) }
        }
    }
}

@Composable
fun rememberAppLabels(): AppLabels {
    val context = LocalContext.current
    return remember(context) { AppLabels(context) }
}

/** Real icon when available, otherwise a tile with the app's first letter. */
@Composable
fun AppIcon(packageName: String, labels: AppLabels, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) { labels.icon(packageName) }
    }
    val shape = RoundedCornerShape(size / 3.2f)
    Box(modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        val bitmap = icon
        if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(size))
        else Surface(Modifier.size(size), shape = shape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Text(labels.label(packageName).take(1).uppercase(), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = (size.value * .42f).sp))
            }
        }
    }
}

sealed interface AppListState {
    data object Loading : AppListState
    data class Loaded(val apps: List<InstalledApp>) : AppListState
    data class Failed(val message: String) : AppListState
}

/** Launchable apps, loaded off the main thread. Reloads each time the screen resumes; [retry] after a failure. */
class AppListController internal constructor(private val reload: () -> Unit, val state: AppListState) {
    fun retry() = reload()
}

@Composable
fun rememberInstalledApps(): AppListController {
    val context = LocalContext.current
    var version by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<AppListState>(AppListState.Loading) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { version++ }
    androidx.compose.runtime.LaunchedEffect(version) {
        if (state !is AppListState.Loaded) state = AppListState.Loading
        state = try {
            AppListState.Loaded(withContext(Dispatchers.IO) { loadInstalledApps(context) })
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppListState.Failed(failure.localizedMessage ?: "Couldn't read the installed apps.")
        }
    }
    return remember(version, state) { AppListController({ version++ }, state) }
}

@Suppress("DEPRECATION")
private fun loadInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val matches: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
    else pm.queryIntentActivities(intent, 0)
    return matches.asSequence().mapNotNull { info ->
        val app = info.activityInfo?.applicationInfo ?: return@mapNotNull null
        if (app.packageName == context.packageName) null
        else InstalledApp(app.packageName, app.loadLabel(pm).toString())
    }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.ROOT) }.toList()
}

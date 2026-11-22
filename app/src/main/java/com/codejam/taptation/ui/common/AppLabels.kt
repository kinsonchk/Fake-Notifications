package com.codejam.taptation.ui.common

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.TaptationApplication
import com.codejam.taptation.service.AppDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The backend's app model (logic/InstalledApp); kept under this name so screens don't change imports. */
typealias InstalledApp = com.codejam.taptation.logic.InstalledApp

/**
 * App names and icons for screens, from the backend's [AppDirectory] (the only place that asks the
 * package manager). No screen shows a package name.
 */
class AppLabels(context: Context) {
    private val directory = (context.applicationContext as TaptationApplication).apps

    fun label(packageName: String): String = directory.label(packageName)

    /** Icon bitmap, or null when the app isn't visible or installed. Call off the main thread. */
    fun icon(packageName: String): ImageBitmap? = directory.icon(packageName)?.asImageBitmap()

    companion object {
        /** Readable guess from a package name when the phone can't give the real label. */
        fun fallbackLabel(packageName: String): String = AppDirectory.fallbackLabel(packageName)

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

/** Launchable apps from the backend; [retry] asks it to read them again. */
class AppListController internal constructor(private val reload: () -> Unit, val state: AppListState) {
    fun retry() = reload()
}

/** The backend's installed-app list as a screen state (the backend reloads it on every resume). */
@Composable
fun rememberInstalledApps(): AppListController {
    val directory = (LocalContext.current.applicationContext as TaptationApplication).apps
    val list by directory.apps.collectAsStateWithLifecycle()
    val apps = list.apps
    val error = list.error
    val state = when {
        apps != null -> AppListState.Loaded(apps)
        error != null -> AppListState.Failed(error)
        else -> AppListState.Loading
    }
    return remember(state) { AppListController(directory::reload, state) }
}

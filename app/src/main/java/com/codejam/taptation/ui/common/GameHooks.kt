package com.codejam.taptation.ui.common

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.codejam.taptation.TaptationApplication
import com.codejam.taptation.logic.GameRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** The app-wide repository created in [TaptationApplication]. */
@Composable
fun rememberGameRepository(): GameRepository {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as TaptationApplication).game }
}

/**
 * Wall-clock time that ticks every second while the screen is started, for countdowns only.
 * Recovery doesn't depend on it: GameRepository refreshes itself when the next step is due, and
 * TaptationApplication refreshes it whenever an activity resumes.
 */
@Composable
fun rememberNow(repository: GameRepository): Long {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(repository, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }
    return now
}

fun Context.isDebuggable(): Boolean = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

fun formatClock(millis: Long): String = clockFormat.format(Instant.ofEpochMilli(millis))

/** "45 s", "12 min 5 s", "3 h 5 min" (seconds dropped from an hour up). */
fun formatDuration(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return when {
        seconds < 60 -> "$seconds s"
        seconds < 3600 -> if (seconds % 60 == 0L) "${seconds / 60} min" else "${seconds / 60} min ${seconds % 60} s"
        else -> "${seconds / 3600} h ${seconds % 3600 / 60} min"
    }
}

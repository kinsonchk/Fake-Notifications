package com.codejam.taptation.ui.common

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** Observes Android's animation preference, including changes made while this screen is open. */
@Composable
fun rememberMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var enabled by remember { mutableStateOf(animationsEnabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = animationsEnabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

/** Shared timing for Compose and native overlay: red is an event flash, not a new health value. */
fun damageFlashMix(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return if (p < .28f) kotlin.math.sin(p / .28f * Math.PI / 2).toFloat()
    else (.5 * (1 + kotlin.math.cos((p - .28f) / .72f * Math.PI))).toFloat()
}

fun damageDropProgress(progress: Float): Float {
    val p = (progress / .65f).coerceIn(0f, 1f)
    return 1f - (1f - p) * (1f - p) * (1f - p)
}

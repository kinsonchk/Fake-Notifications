package com.codejam.taptation.ui.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/**
 * Opens Android's "Display over other apps" page for this app, or its App info page when that
 * screen is missing (some OEM builds). Returns false and shows a short message if neither opens.
 */
fun openOverlaySettings(context: Context): Boolean {
    val pkg = Uri.fromParts("package", context.packageName, null)
    val opened = listOf(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg),
    ).any { intent ->
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.isSuccess
    }
    if (!opened) {
        Toast.makeText(context, "Couldn't open settings. Allow \"Display over other apps\" in Android Settings > Apps.",
            Toast.LENGTH_LONG).show()
    }
    return opened
}

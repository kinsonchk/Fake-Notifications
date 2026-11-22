package com.codejam.taptation.ui.common

import android.content.Context
import android.widget.Toast
import com.codejam.taptation.service.PermissionHelper

/**
 * Opens Android's "Display over other apps" page (backend: PermissionHelper.openOverlaySettings),
 * and tells the user what to do when the phone has no such screen.
 */
fun openOverlaySettings(context: Context): Boolean {
    val opened = PermissionHelper.openOverlaySettings(context)
    if (!opened) {
        Toast.makeText(context, "Couldn't open settings. Allow \"Display over other apps\" in Android Settings > Apps.",
            Toast.LENGTH_LONG).show()
    }
    return opened
}

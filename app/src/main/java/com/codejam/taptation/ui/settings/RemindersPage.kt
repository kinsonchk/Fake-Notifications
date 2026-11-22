package com.codejam.taptation.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.codejam.taptation.service.PermissionHelper
import com.codejam.taptation.ui.common.BackLink
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.StatusPill
import com.codejam.taptation.ui.common.TabScreen

/** What Android currently allows for catch reminders; refreshed whenever the screen resumes. */
data class ReminderPermissions(val overlay: Boolean, val notifications: Boolean, val notificationAccess: Boolean) {
    /** How many things need the user's attention. */
    val issues: Int get() = listOf(overlay, notifications).count { !it }

    companion object {
        fun read(context: Context) = ReminderPermissions(
            overlay = Settings.canDrawOverlays(context),
            notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            notificationAccess = PermissionHelper.isNotificationAccessGranted(context),
        )
    }
}

@Composable
fun rememberReminderPermissions(): ReminderPermissions {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(ReminderPermissions.read(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissions = ReminderPermissions.read(context) }
    return permissions
}

private const val PREFS = "catch_reminders"
private const val ASKED = "asked_notifications"

/**
 * Catch reminders: each permission shows whether it is Allowed, why it is needed, and the right next
 * step. Android only shows the notification prompt a couple of times, so after one ask we send the
 * user to the app's notification settings instead of a button that may do nothing.
 */
@Composable
fun RemindersPage(contentPadding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    var live by remember { mutableStateOf(ReminderPermissions.read(context)) }
    var asked by remember { mutableStateOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ASKED, false)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { live = ReminderPermissions.read(context) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ASKED, true).apply()
        asked = true
        live = ReminderPermissions.read(context)
    }

    TabScreen(contentPadding, "Catch reminders.", eyebrow = "Your rules, your pace") {
        item { BackLink("Settings", onBack) }
        item {
            Text("When you're caught, a reminder shows what it cost and lets you choose where to go next.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionCard("Show reminders over other apps", "Lets the reminder appear right away, on top of the app you opened.") {
                PermissionStatus(live.overlay)
                if (!live.overlay) Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Allow display over other apps") }
            }
        }
        item {
            SectionCard("Allow reminder notifications", "Used when a reminder can't appear over other apps.") {
                PermissionStatus(live.notifications)
                if (!live.notifications) {
                    val canAsk = Build.VERSION.SDK_INT >= 33 && !asked
                    if (canAsk) Button(onClick = { request.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Allow notifications") }
                    else OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open Android settings") }
                }
            }
        }
        item {
            SectionCard("Notification access", "Lets Taptation notice when you open a notification. Message text is not stored.") {
                PermissionStatus(live.notificationAccess)
                if (!live.notificationAccess) {
                    OutlinedButton(onClick = { PermissionHelper.openNotificationAccessSettings(context) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open Android settings") }
                    Text("If the switch is greyed out, open this app's info, tap ⋮ and choose Allow restricted settings.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("Some apps and system screens may hide reminders.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionStatus(allowed: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusPill(if (allowed) "✓  Allowed" else "Not allowed", good = allowed)
    }
}

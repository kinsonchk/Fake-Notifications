package com.codejam.taptation.ui.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/** What Android currently allows for catch reminders, as this page shows it (from SystemStatus). */
data class ReminderPermissions(val overlay: Boolean, val notifications: Boolean, val notificationAccess: Boolean) {
    /** How many things need the user's attention. */
    val issues: Int get() = listOf(overlay, notifications).count { !it }

}

/** The backend's permission state in this page's shape. */
@Composable
fun rememberReminderPermissions(): ReminderPermissions {
    val system = (LocalContext.current.applicationContext as com.codejam.taptation.TaptationApplication).system
    val status by system.permissions.collectAsStateWithLifecycle()
    return ReminderPermissions(status.overlay, status.postNotifications, status.notificationAccess)
}


/**
 * Catch reminders: each permission shows whether it is Allowed, why it is needed, and the right next
 * step. Android only shows the notification prompt a couple of times, so after one ask we send the
 * user to the app's notification settings instead of a button that may do nothing.
 */
@Composable
fun RemindersPage(contentPadding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    // Permission state and "already asked" come from the backend (service/SystemStatus), which
    // re-reads them whenever an activity resumes.
    val system = (context.applicationContext as com.codejam.taptation.TaptationApplication).system
    val status by system.permissions.collectAsStateWithLifecycle()
    val live = ReminderPermissions(status.overlay, status.postNotifications, status.notificationAccess)
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        system.markNotificationPromptShown()
    }

    TabScreen(contentPadding, "Catch reminders.") {
        item { BackLink("Settings", onBack) }
        item {
            Text("When you're caught, a reminder shows what it cost and lets you choose where to go next.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionCard("Show reminders over other apps", "Lets the reminder appear right away, on top of the app you opened.") {
                PermissionStatus(live.overlay)
                if (!live.overlay) Button(onClick = {
                    com.codejam.taptation.ui.common.openOverlaySettings(context)
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Allow display over other apps") }
            }
        }
        item {
            SectionCard("Allow reminder notifications", "Used when a reminder can't appear over other apps.") {
                PermissionStatus(live.notifications)
                if (!live.notifications) {
                    val canAsk = status.notificationPromptAvailable
                    if (canAsk) Button(onClick = { request.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Allow notifications") }
                    else OutlinedButton(onClick = { PermissionHelper.openAppNotificationSettings(context) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open Android settings") }
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
        StatusPill(if (allowed) "Allowed" else "Not allowed", good = allowed)
    }
}

package com.codejam.taptation.debug

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.logic.TapDispatcher
import com.codejam.taptation.service.PermissionHelper
import com.codejam.taptation.service.TapListenerService
import com.codejam.taptation.service.TapLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

/** Plain screen to prove the notification service works on a real phone. Not the real UI. */
@Composable
fun DebugScreen(contentPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current

    var accessGranted by remember { mutableStateOf(PermissionHelper.isNotificationAccessGranted(context)) }
    var batteryUnrestricted by remember { mutableStateOf(PermissionHelper.isIgnoringBatteryOptimizations(context)) }
    // The user returns from system settings here, so refresh both on every resume.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        accessGranted = PermissionHelper.isNotificationAccessGranted(context)
        batteryUnrestricted = PermissionHelper.isIgnoringBatteryOptimizations(context)
    }

    val connected by TapListenerService.connected.collectAsStateWithLifecycle()
    val taps by TapLog.entries.collectAsStateWithLifecycle()

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) TestNotification.post(context)
        else Toast.makeText(context, "Notification permission denied", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Notification service debug", style = MaterialTheme.typography.titleLarge)
        }

        item {
            StatusRow("Notification access", if (accessGranted) "granted" else "NOT granted")
            StatusRow("Service connected", if (connected) "yes" else "no")
            StatusRow("Battery", if (batteryUnrestricted) "unrestricted" else "optimized")
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { PermissionHelper.openNotificationAccessSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open notification access settings") }

                Button(
                    onClick = {
                        if (TestNotification.canPost(context)) {
                            TestNotification.post(context)
                        } else {
                            // canPost() is only false on Android 13+, where this permission exists.
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Post test notification") }

                OutlinedButton(
                    onClick = {
                        TapLog.record("com.test.fake")
                        TapDispatcher.dispatch("com.test.fake")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Simulate tap") }

                OutlinedButton(
                    onClick = { PermissionHelper.openBatteryOptimizationSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open battery settings") }

                Text(
                    "Set this app to Unrestricted (App info > App battery usage) so the " +
                        "system doesn't kill the listener in the background.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item { LogicDebugPanel() }

        item {
            HorizontalDivider()
            Text(
                "Last ${TapLog.CAPACITY} taps",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (taps.isEmpty()) {
            item { Text("No taps yet.", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(taps) { tap ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(tap.packageName, modifier = Modifier.weight(1f))
                    Text(timeFormat.format(Instant.ofEpochMilli(tap.timestampMillis)))
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

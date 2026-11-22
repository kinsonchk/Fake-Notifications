package com.codejam.taptation.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.ContextWrapper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.logic.NotificationContext
import com.codejam.taptation.logic.TapOutcome
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Real TYPE_APPLICATION_OVERLAY, real saved catch, explicit original-action callback. */
class CaughtOverlayTest {
    @Test fun backgroundCatchShowsChoicesAndAppliesOnlyTheSavedContinuingPenalty() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val original = instrumentation.targetContext
        val prefix = "overlay-test-${java.util.UUID.randomUUID()}-"
        val isolated = object : ContextWrapper(original) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(prefix + name, mode)
        }
        val automation = instrumentation.uiAutomation
        val priorOverlay = Settings.canDrawOverlays(original)
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        val info = automation.serviceInfo
        val oldFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        val opened = AtomicBoolean(false)
        lateinit var feedback: CatchFeedback
        lateinit var repository: GameRepository
        var initialized = false
        try {
            shell("appops set ${original.packageName} SYSTEM_ALERT_WINDOW allow")
            assertTrue(Settings.canDrawOverlays(original))
            instrumentation.runOnMainSync {
                feedback = CatchFeedback(isolated)
                feedback.surface = com.codejam.taptation.ui.overlay.CatchOverlay(isolated)
                initialized = true
                repository = GameRepository(isolated, onCaught = feedback::rememberDestination)
                feedback.attach(repository)
                feedback.setForeground(false)
            }
            repository.refresh().get(5, TimeUnit.SECONDS)
            repository.setDebugCatchProbability(1.0).get(5, TimeUnit.SECONDS)
            val result = repository.processTap(NotificationContext(original.packageName, title = "Demo",
                text = "Routine update", category = "promo", isConversation = false, ageMillis = 0,
                openOriginal = { opened.set(true) })).get(5, TimeUnit.SECONDS)
            assertEquals(TapOutcome.Caught, result.outcome)
            fun node(text: String, prefix: Boolean = false): AccessibilityNodeInfo? = automation.windows.asSequence()
                .mapNotNull { it.root }.flatMap { it.findAccessibilityNodeInfosByText(text).asSequence() }
                .firstOrNull { if (prefix) it.text?.toString()?.startsWith(text) == true else it.text?.toString() == text }
            val deadline = SystemClock.elapsedRealtime() + 10_000
            var originalButton: AccessibilityNodeInfo? = null
            while (originalButton == null && SystemClock.elapsedRealtime() < deadline) {
                originalButton = node("Continue to", prefix = true)
                if (originalButton == null) SystemClock.sleep(100)
            }
            assertNotNull("Native caught overlay was not shown", originalButton)
            assertNotNull(node("Go to Taptation"))
            assertNotNull("Caught heading missing", node("Caught"))
            assertNotNull("Correction action missing", node("This should be important"))
            assertNotNull("Cost should be stated in text", node("−10"))
            automation.takeScreenshot()?.let { bitmap ->
                val directory = java.io.File(original.getExternalFilesDir(null), "ui-previews").apply { mkdirs() }
                java.io.File(directory, "caught-native.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            assertTrue(originalButton!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            val openedDeadline = SystemClock.elapsedRealtime() + 5_000
            while (!opened.get() && SystemClock.elapsedRealtime() < openedDeadline) SystemClock.sleep(50)
            assertTrue("Original action wasn't invoked", opened.get())
            assertNull(repository.state.value!!.pendingCatch)
            assertEquals(85, repository.state.value!!.health)
            assertEquals(1L, repository.state.value!!.totalCatches)
        } finally {
            if (initialized) instrumentation.runOnMainSync { feedback.close() }
            original.deleteSharedPreferences(prefix + "taptation_game")
            shell("appops set ${original.packageName} SYSTEM_ALERT_WINDOW ${if (priorOverlay) "allow" else "default"}")
            info.flags = oldFlags
            automation.serviceInfo = info
        }
    }
}

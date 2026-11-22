package com.codejam.taptation.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.ContextWrapper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.codejam.taptation.R
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.logic.NotificationContext
import com.codejam.taptation.logic.TapOutcome
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** At 0 health a bait tap is always caught (even with a 0% chance) and the overlay still shows. */
class ZeroHealthCatchTest {
    @Test fun zeroHealthBaitTapIsCaughtAndShowsTheOverlay() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val original = instrumentation.targetContext
        val prefix = "zero-health-test-${java.util.UUID.randomUUID()}-"
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
                initialized = true
                repository = GameRepository(isolated, onCaught = feedback::rememberDestination)
                feedback.attach(repository)
                feedback.setForeground(false)
            }
            repository.refresh().get(5, TimeUnit.SECONDS)
            repository.setDebugCatchProbability(0.0).get(5, TimeUnit.SECONDS)
            repository.setHealthForDebug(0).get(5, TimeUnit.SECONDS)
            val result = repository.processTap(NotificationContext(original.packageName, title = "Demo",
                text = "Routine update", category = "promo", isConversation = false, ageMillis = 0,
                openOriginal = { opened.set(true) })).get(5, TimeUnit.SECONDS)
            assertEquals(TapOutcome.Caught, result.outcome)
            fun node(text: String, prefix: Boolean = false): AccessibilityNodeInfo? = automation.windows.asSequence()
                .mapNotNull { it.root }.flatMap { it.findAccessibilityNodeInfosByText(text).asSequence() }
                .firstOrNull { if (prefix) it.text?.toString()?.startsWith(text) == true else it.text?.toString() == text }
            val extra = result.state.pendingCatch!!.continueExtraDamage
            assertEquals("no health left to lose by continuing", 0, extra)
            // "Continue to <app>" with no "· −N health" suffix: nothing left to lose.
            val continuePrefix = original.getString(R.string.caught_original_app, "").trimEnd()
            val deadline = SystemClock.elapsedRealtime() + 10_000
            var originalButton: AccessibilityNodeInfo? = null
            while (originalButton == null && SystemClock.elapsedRealtime() < deadline) {
                originalButton = node(continuePrefix, prefix = true)
                if (originalButton == null) SystemClock.sleep(100)
            }
            assertNotNull("Native caught overlay was not shown", originalButton)
            assertFalse("Continuing must not show a penalty at 0 health", originalButton!!.text.toString().contains("health"))
            assertNotNull(node(original.getString(R.string.caught_our_app)))
            assertTrue(originalButton!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            val openedDeadline = SystemClock.elapsedRealtime() + 5_000
            while (!opened.get() && SystemClock.elapsedRealtime() < openedDeadline) SystemClock.sleep(50)
            assertTrue("Original action wasn't invoked", opened.get())
            assertNull(repository.state.value!!.pendingCatch)
            assertEquals(0, repository.state.value!!.health)
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

package com.codejam.taptation.ai

import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.codejam.taptation.TaptationApplication
import com.codejam.taptation.debug.TestNotification
import com.codejam.taptation.logic.NotificationContext
import com.codejam.taptation.service.TapListenerService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end on a device: debug notifications standing in for other apps are posted, and the
 * listener sends them to the Clef judge, whose verdicts are then ready for the tap path.
 * Grants notification access itself; needs network and the cloudflare.* keys.
 */
@RunWith(AndroidJUnit4::class)
class JudgeWiringTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as TaptationApplication

    @Before
    fun setUp() {
        assumeTrue("No Cloudflare key in local.properties", ClefJudge().isConfigured)
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        shell("cmd notification allow_listener ${context.packageName}/${TapListenerService::class.java.name}")
        waitFor("listener connected") { TapListenerService.connected.value.takeIf { it } }
        waitFor("game state loaded") { app.game.state.value }
    }

    @After
    fun tearDown() {
        // keepNotifications=true leaves them in the shade, e.g. to tap one by hand or with adb.
        if (InstrumentationRegistry.getArguments().getString("keepNotifications") != "true") {
            NotificationManagerCompat.from(context).cancelAll()
        }
    }

    @Test
    fun postedNotificationsGetAVerdict() {
        val importantTitle = "TD Bank ${SystemClock.elapsedRealtime()}"
        val baitTitle = "Instagram ${SystemClock.elapsedRealtime()}"
        val important = NotificationContext("com.td", title = importantTitle,
            text = "Your verification code is 482913. Don't share it with anyone.")
        val bait = NotificationContext("com.instagram.android", title = baitTitle,
            text = "alex.r and 12 others liked your photo", category = "social")
        // only=important|bait posts just one, so the shade doesn't group them (handy for tapping by hand).
        val only = InstrumentationRegistry.getArguments().getString("only")
        if (only != "bait") TestNotification.post(context, important)
        if (only != "important") TestNotification.post(context, bait)

        if (only != "bait") assertEquals(Verdict.Important, verdictFor(importantTitle))
        if (only != "important") assertEquals(Verdict.Bait, verdictFor(baitTitle))

    }

    private fun verdictFor(title: String): Verdict {
        val key = waitFor("listener saw $title") {
            TapListenerService.observations.value.firstOrNull { it.title == title }?.key
        }
        return waitFor("verdict for $title", timeoutMillis = 20_000) { app.judges.verdicts.value[key] }
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).close()
        SystemClock.sleep(300)
    }

    private fun <T : Any> waitFor(what: String, timeoutMillis: Long = 10_000, value: () -> T?): T {
        val end = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < end) {
            value()?.let { return it }
            SystemClock.sleep(100)
        }
        throw AssertionError("Timed out waiting for $what")
    }
}

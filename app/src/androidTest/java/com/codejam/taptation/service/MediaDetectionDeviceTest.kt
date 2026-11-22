package com.codejam.taptation.service

import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.codejam.taptation.debug.TestNotification
import com.codejam.taptation.logic.MediaKind
import com.codejam.taptation.logic.NotificationContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The listener reads chat details from real posted notifications: the attachment kind (a
 * MessagingStyle GIF attachment, a "📷 Photo" placeholder) and the sender and group flag.
 */
class MediaDetectionDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before fun grantAccess() {
        listOf("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
            "cmd notification allow_listener ${context.packageName}/${TapListenerService::class.java.name}",
        ).forEach { command ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
                .use { it.readBytes() }
        }
        waitFor("listener connected") { TapListenerService.connected.value.takeIf { it } }
    }

    @After fun cleanUp() = NotificationManagerCompat.from(context).cancelAll()

    @Test fun attachmentKindReachesTheNotificationContext() {
        val stamp = SystemClock.elapsedRealtime()
        TestNotification.post(context, NotificationContext("com.whatsapp", title = "Mom $stamp",
            text = "", category = "msg", media = MediaKind.Gif))
        TestNotification.post(context, NotificationContext("com.whatsapp", title = "Dad $stamp",
            text = "📷 Photo", category = "msg"))
        assertEquals(MediaKind.Gif, observed("Mom $stamp").media)
        assertEquals(MediaKind.Image, observed("Dad $stamp").media)
    }

    @Test fun senderAndGroupReachTheNotificationContext() {
        val stamp = SystemClock.elapsedRealtime()
        TestNotification.post(context, NotificationContext("com.discord", title = "sarah_k $stamp",
            text = "are you coming?", sender = "sarah_k $stamp", isGroup = false))
        TestNotification.post(context, NotificationContext("com.whatsapp", title = "Famille $stamp",
            text = "on y va?", sender = "Alex", isGroup = true))
        observed("sarah_k $stamp").let { assertEquals("sarah_k $stamp", it.sender); assertEquals(false, it.isGroup) }
        // In a group the notification's title is the group; the sender comes from the message.
        val group = waitFor("group chat") { TapListenerService.observations.value.firstOrNull { it.sender == "Alex" } }
        assertEquals(true, group.isGroup)
    }

    private fun observed(title: String): NotificationContext =
        waitFor("listener saw $title") { TapListenerService.observations.value.firstOrNull { it.title == title } }

    private fun <T : Any> waitFor(what: String, value: () -> T?): T {
        val end = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < end) {
            value()?.let { return it }
            SystemClock.sleep(100)
        }
        throw AssertionError("Timed out waiting for $what")
    }
}

package com.codejam.taptation.service

import android.app.Notification
import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSignalsTest {

    @Test
    fun importanceName_mapsFrameworkConstants() {
        assertEquals("HIGH", NotificationSignals.importanceName(NotificationManager.IMPORTANCE_HIGH))
        assertEquals("LOW", NotificationSignals.importanceName(NotificationManager.IMPORTANCE_LOW))
        assertEquals("UNKNOWN(42)", NotificationSignals.importanceName(42))
    }

    @Test
    fun styleName_keepsShortClassName() {
        assertEquals("MessagingStyle", NotificationSignals.styleName("android.app.Notification\$MessagingStyle"))
        assertEquals("BigTextStyle", NotificationSignals.styleName("android.app.Notification\$BigTextStyle"))
        assertNull(NotificationSignals.styleName(null))
        assertNull(NotificationSignals.styleName(""))
    }

    @Test
    fun describe_listsEverySignal() {
        val signals = NotificationSignals(
            category = Notification.CATEGORY_MESSAGE,
            channelId = "dm",
            channelName = "Direct Messages",
            importance = NotificationManager.IMPORTANCE_HIGH,
            isConversation = true,
            isGroupConversation = false,
            style = "MessagingStyle",
            flags = Notification.FLAG_AUTO_CANCEL,
            hasContentIntent = true,
            hasGroup = true,
            postTimeMillis = 1_000L,
            media = com.codejam.taptation.logic.MediaKind.Gif,
        )
        assertEquals(
            "cat=msg channelId=dm channel=\"Direct Messages\" importance=HIGH conversation=true " +
                "groupConversation=false style=MessagingStyle media=gif autoCancel=true ongoing=false " +
                "summary=false group=true contentIntent=true postTime=1000 age=1500ms",
            signals.describe(nowMillis = 2_500L),
        )
    }

    @Test
    fun describe_marksMissingValues() {
        val line = NotificationSignals(
            category = null, channelId = null, channelName = null, importance = null,
            isConversation = null, isGroupConversation = null, style = null,
            flags = 0, hasContentIntent = false, hasGroup = false, postTimeMillis = 0L,
        ).describe(nowMillis = 0L)
        assertTrue(line, line.startsWith("cat=none channelId=none channel=? importance=? conversation=? groupConversation=?"))
        assertTrue(line, line.contains("autoCancel=false"))
    }
}

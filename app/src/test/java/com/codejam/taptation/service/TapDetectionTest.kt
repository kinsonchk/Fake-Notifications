package com.codejam.taptation.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapDetectionTest {

    private val own = "com.codejam.taptation"
    private val other = "com.example.social"

    @Test
    fun click_isTap() {
        assertTrue(TapDetection.isTap(NotificationListenerService.REASON_CLICK))
    }

    @Test
    fun nonClickReasons_areNotTaps() {
        listOf(
            NotificationListenerService.REASON_CANCEL,
            NotificationListenerService.REASON_CANCEL_ALL,
            NotificationListenerService.REASON_APP_CANCEL,
            NotificationListenerService.REASON_APP_CANCEL_ALL,
            NotificationListenerService.REASON_GROUP_SUMMARY_CANCELED,
            NotificationListenerService.REASON_SNOOZED,
            NotificationListenerService.REASON_TIMEOUT,
        ).forEach { reason ->
            assertFalse("reason $reason", TapDetection.isTap(reason))
        }
    }

    @Test
    fun plainTapFromOtherApp_isDispatched() {
        assertTrue(dispatch(flags = 0, pkg = other, debug = false))
    }

    @Test
    fun swipe_isNotDispatched() {
        assertFalse(
            TapDetection.shouldDispatch(
                NotificationListenerService.REASON_CANCEL, 0, null, other, own, includeOwnPackage = true,
            )
        )
    }

    @Test
    fun groupSummary_isNotDispatched() {
        assertFalse(dispatch(flags = Notification.FLAG_GROUP_SUMMARY, pkg = other, debug = true))
    }

    @Test
    fun ongoing_isNotDispatched() {
        assertFalse(dispatch(flags = Notification.FLAG_ONGOING_EVENT, pkg = other, debug = true))
    }

    @Test
    fun ownTestNotification_onlyCountsInDebug() {
        assertFalse(dispatch(flags = 0, pkg = own, debug = false, channel = TapDetection.OWN_TEST_CHANNEL_ID))
        assertTrue(dispatch(flags = 0, pkg = own, debug = true, channel = TapDetection.OWN_TEST_CHANNEL_ID))
    }

    @Test
    fun ownNonTestNotification_neverCounts() {
        // Loop guard: tapping our own "you got caught" alert must not cause another catch.
        listOf("catches", "default", null).forEach { channel ->
            assertFalse("channel $channel", dispatch(flags = 0, pkg = own, debug = true, channel = channel))
            assertFalse("channel $channel", dispatch(flags = 0, pkg = own, debug = false, channel = channel))
        }
    }

    @Test
    fun otherApp_countsOnAnyChannel() {
        assertTrue(dispatch(flags = 0, pkg = other, debug = false, channel = TapDetection.OWN_TEST_CHANNEL_ID))
        assertTrue(dispatch(flags = 0, pkg = other, debug = false, channel = null))
    }

    @Test
    fun swipeAndClearAll_areDismissals() {
        listOf(NotificationListenerService.REASON_CANCEL, NotificationListenerService.REASON_CANCEL_ALL).forEach {
            assertEquals(TapDetection.RemovalKind.Dismiss, kind(it, pkg = other))
        }
        assertEquals(TapDetection.RemovalKind.Tap, kind(NotificationListenerService.REASON_CLICK, pkg = other))
    }

    @Test
    fun otherRemovalReasons_areIgnored() {
        listOf(
            NotificationListenerService.REASON_APP_CANCEL,
            NotificationListenerService.REASON_APP_CANCEL_ALL,
            NotificationListenerService.REASON_GROUP_SUMMARY_CANCELED,
            NotificationListenerService.REASON_SNOOZED,
            NotificationListenerService.REASON_TIMEOUT,
            NotificationListenerService.REASON_LISTENER_CANCEL,
        ).forEach { assertEquals("reason $it", TapDetection.RemovalKind.Ignored, kind(it, pkg = other)) }
    }

    @Test
    fun dismissFilters_matchTapFilters() {
        val cancel = NotificationListenerService.REASON_CANCEL
        assertEquals(TapDetection.RemovalKind.Ignored, kind(cancel, pkg = other, flags = Notification.FLAG_GROUP_SUMMARY))
        assertEquals(TapDetection.RemovalKind.Ignored, kind(cancel, pkg = other, flags = Notification.FLAG_ONGOING_EVENT))
        assertEquals(TapDetection.RemovalKind.Ignored, kind(cancel, pkg = own, channel = "catches"))
        assertEquals(TapDetection.RemovalKind.Dismiss, kind(cancel, pkg = own, channel = TapDetection.OWN_TEST_CHANNEL_ID))
    }

    @Test
    fun tapLog_keepsNewestTwenty() {
        repeat(TapLog.CAPACITY + 5) { TapLog.record("pkg$it", timestampMillis = it.toLong()) }
        val entries = TapLog.entries.value
        assertEquals(TapLog.CAPACITY, entries.size)
        assertEquals("pkg${TapLog.CAPACITY + 4}", entries.first().packageName)
    }

    private fun kind(reason: Int, pkg: String, flags: Int = 0, channel: String? = "any") =
        TapDetection.removalKind(reason, flags, channel, pkg, own, includeOwnPackage = true)

    private fun dispatch(flags: Int, pkg: String, debug: Boolean, channel: String? = "any") =
        TapDetection.shouldDispatch(
            reason = NotificationListenerService.REASON_CLICK,
            flags = flags,
            channelId = channel,
            packageName = pkg,
            ownPackageName = own,
            includeOwnPackage = debug,
        )

    @Test
    fun onlyCountableNotificationsAreWorthJudging() {
        fun countable(flags: Int = 0, pkg: String = other, channel: String? = "chat", debug: Boolean = false) =
            TapDetection.isCountable(flags, channel, pkg, own, debug)
        assertTrue(countable())
        assertFalse(countable(flags = Notification.FLAG_ONGOING_EVENT))
        assertFalse(countable(flags = Notification.FLAG_GROUP_SUMMARY))
        assertFalse(countable(pkg = own, debug = true))
        assertTrue(countable(pkg = own, channel = TapDetection.OWN_TEST_CHANNEL_ID, debug = true))
        assertFalse(countable(pkg = own, channel = TapDetection.OWN_TEST_CHANNEL_ID, debug = false))
    }
}

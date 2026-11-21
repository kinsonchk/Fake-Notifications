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
                NotificationListenerService.REASON_CANCEL, 0, other, own, includeOwnPackage = true,
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
    fun ownPackage_onlyCountsInDebug() {
        assertFalse(dispatch(flags = 0, pkg = own, debug = false))
        assertTrue(dispatch(flags = 0, pkg = own, debug = true))
    }

    @Test
    fun tapLog_keepsNewestTwenty() {
        repeat(TapLog.CAPACITY + 5) {
            TapLog.record(
                TappedNotification(
                    packageName = "pkg$it", title = null, text = null, category = null,
                    channelId = null, key = "key$it", postTimeMillis = 0, tapTimeMillis = it.toLong(),
                )
            )
        }
        val entries = TapLog.entries.value
        assertEquals(TapLog.CAPACITY, entries.size)
        assertEquals("pkg${TapLog.CAPACITY + 4}", entries.first().packageName)
    }

    private fun dispatch(flags: Int, pkg: String, debug: Boolean) =
        TapDetection.shouldDispatch(
            reason = NotificationListenerService.REASON_CLICK,
            flags = flags,
            packageName = pkg,
            ownPackageName = own,
            includeOwnPackage = debug,
        )
}

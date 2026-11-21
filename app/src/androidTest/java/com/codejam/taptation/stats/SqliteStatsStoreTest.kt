package com.codejam.taptation.stats

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.codejam.taptation.logic.Classification
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqliteStatsStoreTest {
    // name = null: in-memory database, nothing left on the device.
    private val store = SqliteStatsStore(InstrumentationRegistry.getInstrumentation().targetContext, name = null)

    @After fun close() = store.close()

    @Test fun roundTripsEveryFieldAndFiltersByTime() {
        val tap = StatsEvent(1_000, "com.instagram.android", StatsEventType.TAP, 2_500, Classification.Bait, caught = true)
        val dismiss = StatsEvent(2_000, "com.google.android.gm", StatsEventType.DISMISS, null, Classification.Important)
        val later = StatsEvent(5_000, "x", StatsEventType.TAP, 1, Classification.Unknown)
        store.insert(tap)
        store.insertAll(listOf(dismiss, later))

        assertEquals(listOf(tap, dismiss), store.eventsBetween(0, 5_000))
        assertEquals(3, store.count())
        store.clear()
        assertEquals(0, store.count())
    }
}

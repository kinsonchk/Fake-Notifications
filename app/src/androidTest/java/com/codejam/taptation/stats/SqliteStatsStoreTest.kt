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
        val tap = StatsEvent(1_000, "com.instagram.android", StatsEventType.TAP, 2_500, Classification.Bait, caught = true, category = "social")
        val dismiss = StatsEvent(2_000, "com.google.android.gm", StatsEventType.DISMISS, null, Classification.Important)
        val later = StatsEvent(5_000, "x", StatsEventType.TAP, 1, Classification.Unknown)
        store.insert(tap)
        store.insertAll(listOf(dismiss, later))

        assertEquals(listOf(tap, dismiss), store.eventsBetween(0, 5_000))
        assertEquals(3, store.count())
        store.clear()
        assertEquals(0, store.count())
    }

    @Test fun versionOneDatabaseUpgradesWithoutDroppingHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-test-${java.util.UUID.randomUUID()}.db"
        try {
            context.getDatabasePath(name).parentFile!!.mkdirs()
            android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
                db.execSQL("CREATE TABLE events (_id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER NOT NULL, package TEXT NOT NULL, type TEXT NOT NULL, age_ms INTEGER, classification TEXT NOT NULL, caught INTEGER NOT NULL)")
                db.execSQL("INSERT INTO events(timestamp, package, type, classification, caught) VALUES(1000, 'old.app', 'TAP', 'Bait', 1)")
                db.version = 1
            }
            SqliteStatsStore(context, name).use { upgraded ->
                assertEquals(1, upgraded.count())
                val old = upgraded.eventsBetween(0, 2000).single()
                assertEquals("old.app", old.packageName)
                assertEquals(null, old.category)
                assertEquals(true, old.caught)
                val fresh = StatsEvent(1500, "new.app", StatsEventType.DISMISS, 20, Classification.Bait, category = "email")
                upgraded.insert(fresh)
                assertEquals(fresh, upgraded.eventsBetween(1500, 2000).single())
            }
        } finally { context.deleteDatabase(name) }
    }
}

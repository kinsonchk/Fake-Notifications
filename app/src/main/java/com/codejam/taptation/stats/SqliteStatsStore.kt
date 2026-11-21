package com.codejam.taptation.stats

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.codejam.taptation.logic.Classification

/**
 * One row per event in a plain SQLite table (no Room, so no extra dependencies).
 * Pass [name] = null for an in-memory database (instrumented tests).
 */
class SqliteStatsStore(context: Context, name: String? = DATABASE_NAME) :
    SQLiteOpenHelper(context.applicationContext, name, null, VERSION), StatsStore {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                package TEXT NOT NULL,
                type TEXT NOT NULL,
                age_ms INTEGER,
                classification TEXT NOT NULL,
                caught INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX events_timestamp ON $TABLE(timestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 only. Add ALTER TABLE steps here when the schema changes; never drop user data.
    }

    override fun insert(event: StatsEvent) {
        writableDatabase.insertOrThrow(TABLE, null, event.toValues())
    }

    override fun insertAll(events: List<StatsEvent>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            events.forEach { db.insertOrThrow(TABLE, null, it.toValues()) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun eventsBetween(fromMillis: Long, toMillis: Long): List<StatsEvent> =
        readableDatabase.query(
            TABLE,
            arrayOf("timestamp", "package", "type", "age_ms", "classification", "caught"),
            "timestamp >= ? AND timestamp < ?",
            arrayOf(fromMillis.toString(), toMillis.toString()),
            null, null, "timestamp ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    // Skip rows a future version wrote with values this one doesn't know.
                    val type = runCatching { StatsEventType.valueOf(cursor.getString(2)) }.getOrNull() ?: continue
                    val classification = runCatching { Classification.valueOf(cursor.getString(4)) }.getOrNull()
                        ?: continue
                    add(
                        StatsEvent(
                            timestampMillis = cursor.getLong(0),
                            packageName = cursor.getString(1),
                            type = type,
                            ageMillis = if (cursor.isNull(3)) null else cursor.getLong(3),
                            classification = classification,
                            caught = cursor.getInt(5) != 0,
                        ),
                    )
                }
            }
        }

    override fun count(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { it.moveToFirst(); it.getInt(0) }

    override fun clear() {
        writableDatabase.delete(TABLE, null, null)
    }

    private fun StatsEvent.toValues() = ContentValues().apply {
        put("timestamp", timestampMillis)
        put("package", packageName)
        put("type", type.name)
        if (ageMillis != null) put("age_ms", ageMillis) else putNull("age_ms")
        put("classification", classification.name)
        put("caught", if (caught) 1 else 0)
    }

    companion object {
        const val DATABASE_NAME = "stats.db"
        private const val VERSION = 1
        private const val TABLE = "events"
    }
}

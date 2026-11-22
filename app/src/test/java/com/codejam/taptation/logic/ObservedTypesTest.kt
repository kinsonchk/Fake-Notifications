package com.codejam.taptation.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservedTypesTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day + 5_000

    @Test fun countsNamedTypesPerAppMostFrequentFirst() {
        var seen = ObservedTypes()
        repeat(3) { seen = seen.record("app.insta", "Likes and comments", now) }
        seen = seen.record("app.insta", "Direct messages", now)
        seen = seen.record("app.insta", "Likes and comments", now - 2 * day)
        val types = seen.typesFor("app.insta", now)
        assertEquals(listOf("Likes and comments", "Direct messages"), types.map { it.channel })
        assertEquals(listOf(4, 1), types.map { it.count })
    }

    @Test fun unnamedTypesCountTheAppButAreNeverListed() {
        val seen = ObservedTypes().record("app.x", null, now).record("app.x", "  ", now)
        assertTrue(seen.hasSeen("app.x"))
        assertTrue(seen.typesFor("app.x", now).isEmpty())
        assertEquals(2, seen.appCounts(now)["app.x"])
    }

    @Test fun oldSightingsFallOutOfTheWindow() {
        val seen = ObservedTypes().record("a", "News", now - 10 * day).record("a", "Offers", now)
        assertEquals(listOf("Offers"), seen.typesFor("a", now, days = 7).map { it.channel })
        assertEquals(listOf("News", "Offers"), seen.typesFor("a", now, days = 14).map { it.channel }.sorted())
        assertEquals(1, seen.appCounts(now)["a"])
    }

    @Test fun recordIgnoresBlankPackageAndKeepsOriginalUntouched() {
        val empty = ObservedTypes()
        assertTrue(empty.record(" ", "News", now).isEmpty)
        val one = empty.record("a", "News", now)
        assertTrue(empty.isEmpty)
        assertFalse(one.isEmpty)
    }

    @Test fun encodeAndDecodeRoundTrip() {
        val seen = ObservedTypes().record("a.b", "Likes", now).record("a.b", "Likes", now - day)
            .record("c.d", "Chat\tname", now).record("c.d", null, now)
        val copy = ObservedTypes.decode(seen.encode())
        assertEquals(seen.typesFor("a.b", now), copy.typesFor("a.b", now))
        assertEquals(seen.appCounts(now), copy.appCounts(now))
        assertEquals(listOf("Chat name"), copy.typesFor("c.d", now).map { it.channel })
    }

    @Test fun decodeSkipsMalformedLinesAndEmptyInput() {
        assertTrue(ObservedTypes.decode(null).isEmpty)
        assertTrue(ObservedTypes.decode("").isEmpty)
        val copy = ObservedTypes.decode("garbage\nonly\ttwo\n\ta\t1:1\npkg\tNews\tx:y,5:q\npkg\tOffers\t7:2")
        assertEquals(listOf("Offers"), copy.typesFor("pkg", 7 * day + 1, days = 3).map { it.channel })
        assertNull(copy.typesFor("pkg", 7 * day + 1, days = 3).firstOrNull { it.channel == "News" })
    }

    @Test fun memoryStaysBounded() {
        var seen = ObservedTypes()
        repeat(500) { seen = seen.record("app$it", "c", now + it) }
        assertTrue(seen.appCounts(now + 1000).size <= 300)
        repeat(100) { seen = seen.record("one", "type$it", now) }
        assertTrue(seen.typesFor("one", now).size <= 40)
    }
}

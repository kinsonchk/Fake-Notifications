package com.codejam.taptation.stats

import com.codejam.taptation.logic.Classification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsRepositoryTest {
    private var now = 100 * StatsRepository.DAY_MILLIS
    private val store = InMemoryStatsStore()
    private val repository = StatsRepository(store, { now }, Dispatchers.Unconfined)

    @Test fun recordsAndSummarizesTheLastWeek() = runBlocking {
        repository.recordTap("insta", 2_000, Classification.Bait, wasCaught = true)
        repository.recordDismiss("insta", 4_000, Classification.Bait)
        repository.recordDismiss("mail", 1_000, Classification.Important)

        val week = repository.weekly()
        assertEquals(1, week.taps)
        assertEquals(1, week.dismissals)
        assertEquals(1, week.caught)
        assertEquals(50, week.tapRatePercent)
        assertEquals(2, repository.weekly(includeImportant = true).dismissals)
        assertEquals(3L, repository.changes.value)
    }

    @Test fun weeklyAndMonthlyCoverDifferentRanges() = runBlocking {
        repository.recordTap("old", 1_000, Classification.Bait, wasCaught = false)
        now += 10 * StatsRepository.DAY_MILLIS
        repository.recordTap("new", 1_000, Classification.Bait, wasCaught = false)
        assertEquals(1, repository.weekly().taps)
        assertEquals(2, repository.monthly().taps)
    }

    @Test fun fakeHistoryGoesThroughTheStoreAndClears() = runBlocking {
        val history = FakeStatsHistory.generate(now, days = 30)
        assertTrue(history.size in 30 * 10..30 * 30)
        assertTrue(history.all { it.timestampMillis in now - 30 * StatsRepository.DAY_MILLIS until now })
        repository.insertForDebug(history)
        assertEquals(history.size, repository.count())
        val month = repository.monthly(includeImportant = true)
        assertEquals(history.size, month.taps + month.dismissals)
        repository.clearForDebug()
        assertEquals(0, repository.count())
    }
}

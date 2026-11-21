package com.codejam.taptation.stats

/** Where stats events live. Calls block: [StatsRepository] runs them on its own thread. */
interface StatsStore {
    fun insert(event: StatsEvent)
    fun insertAll(events: List<StatsEvent>)
    /** Events with fromMillis <= timestamp < toMillis, oldest first. */
    fun eventsBetween(fromMillis: Long, toMillis: Long): List<StatsEvent>
    fun count(): Int
    fun clear()
}

/** For JVM tests and as a fallback; lost when the process dies. */
class InMemoryStatsStore : StatsStore {
    private val events = mutableListOf<StatsEvent>()

    @Synchronized override fun insert(event: StatsEvent) { events += event }
    @Synchronized override fun insertAll(events: List<StatsEvent>) { this.events += events }
    @Synchronized override fun eventsBetween(fromMillis: Long, toMillis: Long): List<StatsEvent> =
        events.filter { it.timestampMillis in fromMillis until toMillis }.sortedBy { it.timestampMillis }
    @Synchronized override fun count(): Int = events.size
    @Synchronized override fun clear() = events.clear()
}

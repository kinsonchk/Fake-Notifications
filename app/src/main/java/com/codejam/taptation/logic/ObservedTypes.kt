package com.codejam.taptation.logic

/**
 * Notification types (channel names) the listener has seen per app, with small per-day counts.
 *
 * Holds only app package names and the channel names the apps themselves chose. Titles and text
 * are never stored. Immutable: [record] returns a new instance, so it can be published as state.
 */
class ObservedTypes private constructor(private val data: Map<String, Map<String, Map<Long, Int>>>) {
    constructor() : this(emptyMap())

    /** One notification type of an app, e.g. "Likes and comments". */
    data class Type(val channel: String, val count: Int, val lastDay: Long)

    val isEmpty: Boolean get() = data.isEmpty()

    /** Records one posted notification. A blank or missing [channel] still counts the app as seen. */
    fun record(packageName: String, channel: String?, nowMillis: Long): ObservedTypes {
        if (packageName.isBlank()) return this
        val name = channel?.trim().orEmpty().take(MAX_NAME)
        val day = dayOf(nowMillis)
        val channels = data[packageName].orEmpty()
        val days = channels[name].orEmpty()
        val updatedDays = (days + (day to (days[day] ?: 0) + 1)).filterKeys { it > day - KEEP_DAYS }
        var updated = channels + (name to updatedDays)
        if (updated.size > MAX_CHANNELS_PER_APP) {
            updated = updated.entries.sortedByDescending { entry -> entry.value.keys.maxOrNull() ?: 0L }
                .take(MAX_CHANNELS_PER_APP).associate { it.key to it.value }
        }
        var next = data + (packageName to updated)
        if (next.size > MAX_APPS) {
            next = next.entries.sortedByDescending { app -> app.value.values.maxOfOrNull { it.keys.maxOrNull() ?: 0L } ?: 0L }
                .take(MAX_APPS).associate { it.key to it.value }
        }
        return ObservedTypes(next)
    }

    /** Named types for [packageName] seen in the last [days] days, most frequent first. */
    fun typesFor(packageName: String, nowMillis: Long, days: Int = 7): List<Type> {
        val earliest = dayOf(nowMillis) - days + 1
        return data[packageName].orEmpty().mapNotNull { (channel, perDay) ->
            if (channel.isEmpty()) return@mapNotNull null
            val recent = perDay.filterKeys { it >= earliest }
            if (recent.isEmpty()) null else Type(channel, recent.values.sum(), recent.keys.max())
        }.sortedWith(compareByDescending<Type> { it.count }.thenBy { it.channel.lowercase() })
    }

    /** Notifications seen from each app in the last [days] days (including unnamed types). */
    fun appCounts(nowMillis: Long, days: Int = 7): Map<String, Int> {
        val earliest = dayOf(nowMillis) - days + 1
        return data.mapValues { (_, channels) ->
            channels.values.sumOf { perDay -> perDay.filterKeys { it >= earliest }.values.sum() }
        }.filterValues { it > 0 }
    }

    fun hasSeen(packageName: String): Boolean = data.containsKey(packageName)

    /** Compact text for SharedPreferences: one line per type, `package<TAB>channel<TAB>day:count,...`. */
    fun encode(): String = data.entries.sortedBy { it.key }.flatMap { (pkg, channels) ->
        channels.entries.sortedBy { it.key }.map { (channel, perDay) ->
            listOf(pkg, channel, perDay.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" })
                .joinToString("\t") { it.replace("\t", " ").replace("\n", " ") }
        }
    }.joinToString("\n")

    companion object {
        const val KEEP_DAYS = 14
        private const val MAX_APPS = 300
        private const val MAX_CHANNELS_PER_APP = 40
        private const val MAX_NAME = 80
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        fun dayOf(millis: Long): Long = Math.floorDiv(millis, DAY_MILLIS)

        /** Reads [encode] output; malformed lines are skipped rather than failing the app. */
        fun decode(text: String?): ObservedTypes {
            if (text.isNullOrBlank()) return ObservedTypes()
            val data = linkedMapOf<String, MutableMap<String, Map<Long, Int>>>()
            text.lineSequence().forEach { line ->
                val parts = line.split('\t')
                if (parts.size != 3 || parts[0].isBlank()) return@forEach
                val perDay = parts[2].split(',').mapNotNull { item ->
                    val pair = item.split(':')
                    val day = pair.getOrNull(0)?.toLongOrNull()
                    val count = pair.getOrNull(1)?.toIntOrNull()
                    if (pair.size == 2 && day != null && count != null && count > 0) day to count else null
                }.toMap()
                if (perDay.isNotEmpty()) data.getOrPut(parts[0]) { linkedMapOf() }[parts[1]] = perDay
            }
            return ObservedTypes(data)
        }
    }
}

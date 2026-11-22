package com.codejam.taptation.ai

import com.codejam.taptation.logic.AppRule
import com.codejam.taptation.logic.Classification
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.NotificationContext
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Judges "Let us decide" notifications when they are posted, so a verdict is ready by the time the
 * user taps. Judging runs on its own thread (never the listener's main thread or the game worker).
 * Verdicts are kept in memory by notification key only; content is never stored or logged here.
 */
class JudgeQueue(
    private val judge: NotificationJudge,
    private val executor: Executor = Executors.newSingleThreadExecutor(),
) {
    private class Entry(val fingerprint: Int)

    // Same bound as the listener's context cache.
    private val pending = object : LinkedHashMap<String, Entry>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > MAX_ENTRIES
    }
    private val _verdicts = MutableStateFlow<Map<String, Verdict>>(emptyMap())

    /** Finished verdicts by notification key, for the tap path and the debug screen. */
    val verdicts: StateFlow<Map<String, Verdict>> = _verdicts.asStateFlow()

    /**
     * Called for every posted notification. Judges only apps on "Let us decide" and only when the
     * title/text changed (each new chat message re-posts the same key).
     */
    fun onPosted(context: NotificationContext, config: GameConfig) {
        val key = context.key ?: return
        if (!judge.isConfigured || config.ruleFor(context.packageName) != AppRule.LetUsDecide) return
        val fingerprint = fingerprint(context)
        synchronized(pending) {
            if (pending[key]?.fingerprint == fingerprint) return
            pending[key] = Entry(fingerprint)
            _verdicts.value -= key
        }
        executor.execute {
            val verdict = judge.judge(context, config)
            synchronized(pending) {
                // Drop stale answers: the notification was removed or its content changed meanwhile.
                if (pending[key]?.fingerprint != fingerprint) return@execute
                _verdicts.value = (_verdicts.value + (key to verdict)).entries
                    .toList().takeLast(MAX_ENTRIES).associate { it.toPair() }
            }
        }
    }

    /**
     * What "Let us decide" should use for [context]. Null without a judge (keep the old fallback);
     * Unknown while pending, after a failure, or when the notification was never judged.
     */
    fun classificationFor(
        context: NotificationContext,
        verdicts: Map<String, Verdict> = this.verdicts.value,
    ): Classification? {
        if (!judge.isConfigured) return null
        return when (context.key?.let { verdicts[it] }) {
            Verdict.Important -> Classification.Important
            Verdict.Bait -> Classification.Bait
            Verdict.Unsure, null -> Classification.Unknown
        }
    }

    fun onRemoved(key: String) = synchronized(pending) {
        pending.remove(key)
        _verdicts.value -= key
    }

    fun clear() = synchronized(pending) {
        pending.clear()
        _verdicts.value = emptyMap()
    }

    private fun fingerprint(context: NotificationContext): Int =
        listOf(context.packageName, context.title, context.text).hashCode()

    companion object {
        const val MAX_ENTRIES = 500
    }
}

package com.codejam.taptation.ai

import com.codejam.taptation.logic.AppRule
import com.codejam.taptation.logic.Classification
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.NotificationContext
import com.codejam.taptation.logic.Strictness
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Judges "Let us decide" notifications ahead of the tap, so a verdict is ready when the user taps.
 *
 * Keeps every countable notification in the shade as a candidate (memory only, by key) and asks the
 * judge again whenever its content or the judging settings change: the app's rule, the user's
 * criteria or strictness ([onConfigChanged]). A request that became outdated while queued is
 * skipped before it reaches the network, and a late answer for old input is dropped. Judging runs
 * on its own thread, never the listener's main thread or the game worker. Content is never logged.
 */
class JudgeQueue(
    private val judge: NotificationJudge,
    private val executor: Executor = Executors.newSingleThreadExecutor(),
) {
    /** [requested] identifies the input of the last request (content + judging settings). */
    private class Candidate(val context: NotificationContext, var requested: Int? = null)

    private val lock = Any()
    private var config: GameConfig? = null
    private val _verdicts = MutableStateFlow<Map<String, Verdict>>(emptyMap())

    // Bounded like the listener's caches; an evicted candidate takes its verdict with it.
    private val candidates = object : LinkedHashMap<String, Candidate>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Candidate>): Boolean =
            (size > MAX_ENTRIES).also { if (it) _verdicts.value -= eldest.key }
    }

    /** Finished verdicts by notification key, for the tap path and the debug screen. */
    val verdicts: StateFlow<Map<String, Verdict>> = _verdicts.asStateFlow()

    val isConfigured: Boolean get() = judge.isConfigured

    /**
     * A countable notification was posted or updated (or found in the shade after a reconnect).
     * [config] may be null while the game is still loading; it is judged once [onConfigChanged] runs.
     */
    fun onPosted(context: NotificationContext, config: GameConfig?) {
        val key = context.key ?: return
        synchronized(lock) {
            if (config != null) this.config = config
            candidates[key] = Candidate(context, candidates[key]?.takeIf { it.context.sameContent(context) }?.requested)
            request(key)
        }
    }

    /** Settings changed: re-judge whatever the change affects. Cheap when nothing relevant changed. */
    fun onConfigChanged(config: GameConfig) = synchronized(lock) {
        this.config = config
        candidates.keys.toList().forEach(::request)
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
        return context.key?.let { verdicts[it] }.toClassification()
    }

    /**
     * Judges [context] right now on the calling thread (blocking network call), without caching.
     * For debug "Simulate tap". Null without a judge or when the app isn't on "Let us decide".
     */
    fun judgeNow(context: NotificationContext, config: GameConfig): Classification? {
        if (!judge.isConfigured || config.ruleFor(context.packageName) != AppRule.LetUsDecide) return null
        return judge.judge(context, config).toClassification()
    }

    fun onRemoved(key: String) = synchronized(lock) {
        candidates.remove(key)
        _verdicts.value -= key
    }

    fun clear() = synchronized(lock) {
        candidates.clear()
        _verdicts.value = emptyMap()
    }

    /** Caller holds [lock]. */
    private fun request(key: String) {
        val config = config ?: return
        val candidate = candidates[key] ?: return
        if (!judge.isConfigured || config.ruleFor(candidate.context.packageName) != AppRule.LetUsDecide) return
        val input = inputOf(candidate.context, config)
        if (candidate.requested == input) return
        candidate.requested = input
        _verdicts.value -= key
        executor.execute {
            // Skip requests that became outdated while queued, before spending a network call.
            val context = synchronized(lock) { candidates[key]?.takeIf { it.requested == input }?.context }
                ?: return@execute
            val verdict = judge.judge(context, config)
            synchronized(lock) {
                if (candidates[key]?.requested == input) _verdicts.value += key to verdict
            }
        }
    }

    companion object {
        const val MAX_ENTRIES = 500

        /** The settings a verdict depends on; a change in any of them re-judges. */
        fun judgingSettings(config: GameConfig): Triple<String, String, Strictness> =
            Triple(config.essentialCriteria, config.nonEssentialCriteria, config.strictness)

        private fun inputOf(context: NotificationContext, config: GameConfig): Int =
            listOf(context.packageName, context.title, context.text, context.media, context.sender, context.isGroup,
                judgingSettings(config)).hashCode()

        private fun NotificationContext.sameContent(other: NotificationContext) =
            packageName == other.packageName && title == other.title && text == other.text && media == other.media &&
                sender == other.sender && isGroup == other.isGroup

        private fun Verdict?.toClassification(): Classification = when (this) {
            Verdict.Important -> Classification.Important
            Verdict.Bait -> Classification.Bait
            Verdict.Unsure, null -> Classification.Unknown
        }
    }
}

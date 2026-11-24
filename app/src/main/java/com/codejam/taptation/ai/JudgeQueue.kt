package com.codejam.taptation.ai

import com.codejam.taptation.logic.AppRule
import com.codejam.taptation.logic.Classification
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.NotificationContext
import com.codejam.taptation.logic.Strictness
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.CompletableFuture
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
    private class Candidate(
        var context: NotificationContext,
        var requested: List<Any?>? = null,
        var pendingResult: CompletableFuture<JudgeResult>? = null,
    )

    private val lock = Any()
    private var config: GameConfig? = null
    private val _verdicts = MutableStateFlow<Map<String, Verdict>>(emptyMap())
    private val reasons = mutableMapOf<String, String>()
    // Requests a tap is waiting on, with the content to judge: they still run if the notification
    // was removed (the tap removes it) or updated before the executor got to them.
    private val awaited = HashMap<CompletableFuture<JudgeResult>, NotificationContext>()
    private val tapTimeouts: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "ai-tap-grace").apply { isDaemon = true }
    }

    // Bounded like the listener's caches; an evicted candidate takes its verdict with it.
    private val candidates = object : LinkedHashMap<String, Candidate>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Candidate>): Boolean =
            (size > MAX_ENTRIES).also {
                if (it) {
                    _verdicts.value -= eldest.key
                    reasons.remove(eldest.key)
                }
            }
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
            val previous = candidates[key]
            if (previous != null && previous.context.sameContent(context)) {
                previous.context = context
                candidates[key] = previous
            } else {
                candidates[key] = Candidate(context)
            }
            request(key)
        }
    }

    /**
     * Ranking data (channel, conversation flag) changed for a notification. Re-judges it only if it
     * is already a candidate, so non-countable notifications never spend quota.
     */
    fun onUpdated(context: NotificationContext) {
        val key = context.key ?: return
        synchronized(lock) {
            val candidate = candidates[key] ?: return
            candidate.context = context
            request(key)
        }
    }

    /** Settings changed: re-judge whatever the change affects. Cheap when nothing relevant changed. */
    fun onConfigChanged(config: GameConfig) = synchronized(lock) {
        this.config = config
        candidates.keys.toList().forEach(::request)
    }

    /**
     * What "Let us decide" should use for [context]. An unavailable judge, pending request, or
     * failed request is Unknown (no penalty); a completed model score is Important or Bait.
     */
    fun classificationFor(
        context: NotificationContext,
        verdicts: Map<String, Verdict> = this.verdicts.value,
    ): Classification? {
        if (!judge.isConfigured) return Classification.Unknown
        return context.key?.let { verdicts[it] }.toClassification()
    }

    /**
     * Current non-waiting result used for dismissals and debug previews. [verdicts] lets a screen
     * pass the snapshot it collected, so it recomposes when a verdict lands.
     */
    fun contextWithCurrentResult(
        context: NotificationContext,
        verdicts: Map<String, Verdict> = _verdicts.value,
    ): NotificationContext {
        if (!judge.isConfigured) return context.copy(
            judged = Classification.Unknown,
            judgingReason = "ai_not_configured",
        )
        synchronized(lock) {
            val key = context.key
            val verdict = key?.let { verdicts[it] }
            return context.copy(
                judged = verdict.toClassification(),
                judgingReason = key?.let(reasons::get) ?: "ai_pending",
            )
        }
    }

    /**
     * Resolves a tapped notification using its in-flight pre-judgment, with a bounded grace period.
     * Called from the listener callback, but never blocks it; the returned future dispatches game
     * logic only after the decision arrives or the timeout expires.
     */
    fun contextForTap(context: NotificationContext): CompletableFuture<NotificationContext> {
        if (!judge.isConfigured) return CompletableFuture.completedFuture(context.copy(
            judged = Classification.Unknown,
            judgingReason = "ai_not_configured",
        ))
        val pending = synchronized(lock) {
            val key = context.key
            val verdict = key?.let { _verdicts.value[it] }
            if (verdict != null) {
                return CompletableFuture.completedFuture(context.copy(
                    judged = verdict.toClassification(),
                    judgingReason = key?.let(reasons::get) ?: "ai_unsure",
                ))
            }
            key?.let { k -> candidates[k]?.let { c -> c.pendingResult?.also { if (!it.isDone) awaited[it] = c.context } } }
        } ?: return CompletableFuture.completedFuture(context.copy(
            judged = Classification.Unknown,
            judgingReason = "ai_pending",
        ))

        val timeout = CompletableFuture<JudgeResult>()
        tapTimeouts.schedule({
            timeout.complete(JudgeResult(Verdict.Unsure, "ai_pending_timeout"))
        }, TAP_GRACE_MILLIS, TimeUnit.MILLISECONDS)
        return pending.applyToEither(timeout) { result ->
            context.copy(judged = result.classification, judgingReason = result.reason)
        }
    }

    /**
     * Judges [context] right now on the calling thread (blocking network call), without caching.
     * For debug "Simulate tap". Null without a judge or when the app isn't on "Let us decide".
     */
    fun judgeNow(context: NotificationContext, config: GameConfig): Classification? {
        return judgeNowWithReason(context, config)?.classification
    }

    fun judgeNowWithReason(context: NotificationContext, config: GameConfig): JudgeResult? =
        if (!judge.isConfigured || config.ruleFor(context.packageName) != AppRule.LetUsDecide) null
        else judge.judgeWithReason(context, config)

    fun onRemoved(key: String) = synchronized(lock) {
        candidates.remove(key)
        _verdicts.value -= key
        reasons.remove(key)
    }

    fun clear() = synchronized(lock) {
        // Taps already waiting keep their requests (see awaited); only the shade's state is dropped.
        candidates.clear()
        _verdicts.value = emptyMap()
        reasons.clear()
    }

    /** Caller holds [lock]. */
    private fun request(key: String) {
        val config = config ?: return
        val candidate = candidates[key] ?: return
        if (!judge.isConfigured || config.ruleFor(candidate.context.packageName) != AppRule.LetUsDecide) return
        val input = inputOf(candidate.context, config)
        if (candidate.requested == input) return
        candidate.requested = input
        val pendingResult = CompletableFuture<JudgeResult>()
        candidate.pendingResult = pendingResult
        _verdicts.value -= key
        reasons[key] = "ai_pending"
        executor.execute {
            // Skip requests that became outdated while queued, before spending a network call,
            // unless a tap is waiting on this one.
            val context = synchronized(lock) {
                candidates[key]?.takeIf { it.requested == input }?.context ?: awaited.remove(pendingResult)
            }
            if (context == null) {
                pendingResult.complete(JudgeResult(Verdict.Unsure, "ai_outdated"))
                return@execute
            }
            val result = try { judge.judgeWithReason(context, config) }
                catch (e: RuntimeException) { JudgeResult(Verdict.Unsure, "ai_request_failed") }
                finally { synchronized(lock) { awaited.remove(pendingResult) } }
            synchronized(lock) {
                val current = candidates[key]?.takeIf { it.requested == input }
                if (current != null) {
                    _verdicts.value += key to result.verdict
                    reasons[key] = result.reason
                }
            }
            // Complete the particular request's waiters even if this notification was updated
            // meanwhile; its tapped context may still refer to the content this request saw.
            pendingResult.complete(result)
        }
    }

    companion object {
        const val MAX_ENTRIES = 500
        const val TAP_GRACE_MILLIS = 1_000L

        /** The settings a verdict depends on; a change in any of them re-judges. */
        fun judgingSettings(config: GameConfig): Triple<String, String, Strictness> =
            Triple(config.essentialCriteria, config.nonEssentialCriteria, config.strictness)

        /** Every field sent to the model, plus the judging settings; compared by value, not hash. */
        private fun inputOf(context: NotificationContext, config: GameConfig): List<Any?> =
            context.judgedFields() + judgingSettings(config)

        private fun NotificationContext.judgedFields(): List<Any?> =
            listOf(packageName, title, text, media, sender, isGroup, channelName, category, isConversation, actions)

        private fun NotificationContext.sameContent(other: NotificationContext) = judgedFields() == other.judgedFields()

        private fun Verdict?.toClassification(): Classification = when (this) {
            Verdict.Important -> Classification.Important
            Verdict.Bait -> Classification.Bait
            Verdict.Unsure, null -> Classification.Unknown
        }
    }
}

package com.codejam.taptation.ai

import android.util.Log
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.NotificationContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Judges with Cloudflare's Clef decision models: one typed choice question ([ClefQuestion]) whose
 * answer is a probability per option, turned into a verdict with strictness-based thresholds.
 * Blocking network call: run it on a worker thread. Logs only the failure kind, never content.
 */
class ClefJudge(
    private val workersAi: WorkersAi = WorkersAi(),
    /** "clef" (27B, more accurate) or "clef-flash" (cheaper). */
    private val variant: String = DEFAULT_VARIANT,
) : NotificationJudge {
    override val isConfigured: Boolean get() = workersAi.isConfigured

    /** Unsure when no key is set, the notification has no title/text, or the call fails; a judge error must never cost health. */
    override fun judge(context: NotificationContext, config: GameConfig): Verdict =
        judgeWithReason(context, config).verdict

    override fun judgeWithReason(context: NotificationContext, config: GameConfig): JudgeResult {
        if (!isConfigured) return JudgeResult(Verdict.Unsure, "ai_not_configured")
        if (!JudgePrompt.hasContent(context)) return JudgeResult(Verdict.Unsure, "ai_missing_content")
        return try {
            val baitProbability = baitProbability(context, config)
            require(baitProbability.isFinite() && baitProbability in 0.0..1.0) { "Invalid bait probability" }
            val verdict = ClefQuestion.verdict(baitProbability, config.strictness)
            val reason = when (verdict) {
                Verdict.Important -> "ai_important"
                Verdict.Bait -> "ai_bait"
                Verdict.Unsure -> "ai_unsure_range"
            }
            JudgeResult(verdict, reason)
        } catch (e: Exception) {
            Log.w(WorkersAi.TAG, "Clef judge failed: ${e.javaClass.simpleName}")
            JudgeResult(Verdict.Unsure, "ai_request_failed")
        }
    }

    /** Like [judge] but throws on network/HTTP/format errors, so tests can tell them apart. */
    fun judgeOrThrow(context: NotificationContext, config: GameConfig): Verdict =
        ClefQuestion.verdict(baitProbability(context, config), config.strictness)

    /** The model's probability (0..1) that the notification is bait. */
    fun baitProbability(context: NotificationContext, config: GameConfig): Double {
        val criteria = JSONObject()
        ClefQuestion.criteria(config).forEach { (id, description) -> criteria.put(id, description) }
        val body = JSONObject()
            .put("model", variant)
            .put("state", toJson(ClefQuestion.state(context)))
            .put("questions", JSONObject().put(QUESTION_ID, JSONObject()
                .put("type", "choice")
                .put("instructions", ClefQuestion.INSTRUCTIONS)
                .put("criteria", criteria)))
        val answer = workersAi.run("@cf/cloudflare/$variant", body)
            .getJSONObject("answers").getJSONObject(QUESTION_ID)
        return answer.getJSONObject("probabilities").getDouble(ClefQuestion.BAIT)
    }

    private fun toJson(state: Map<String, Any>): JSONObject = JSONObject().apply {
        state.forEach { (key, value) -> put(key, if (value is List<*>) JSONArray(value) else value) }
    }

    companion object {
        const val DEFAULT_VARIANT = "clef"
        private const val QUESTION_ID = "verdict"
    }
}

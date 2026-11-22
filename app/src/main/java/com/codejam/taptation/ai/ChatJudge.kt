package com.codejam.taptation.ai

import android.util.Log
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.NotificationContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Asks a Workers AI chat model for a one-word verdict ([JudgePrompt]). Blocking network call: run
 * it on a worker thread. Logs only the failure kind, never content.
 */
class ChatJudge(
    private val workersAi: WorkersAi = WorkersAi(),
    private val model: String = DEFAULT_MODEL,
) : NotificationJudge {
    override val isConfigured: Boolean get() = workersAi.isConfigured

    /** Unsure when no key is set, the notification has no title/text, or the call fails; a judge error must never cost health. */
    override fun judge(context: NotificationContext, config: GameConfig): Verdict {
        if (!isConfigured || !JudgePrompt.hasContent(context)) return Verdict.Unsure
        return try {
            judgeOrThrow(context, config)
        } catch (e: Exception) {
            Log.w(WorkersAi.TAG, "Chat judge failed: ${e.javaClass.simpleName}")
            Verdict.Unsure
        }
    }

    /** Like [judge] but throws on network/HTTP/format errors, so tests can tell them apart. */
    fun judgeOrThrow(context: NotificationContext, config: GameConfig): Verdict {
        val body = JSONObject()
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", JudgePrompt.system(config)))
                .put(JSONObject().put("role", "user").put("content", JudgePrompt.user(context))))
            .put("max_tokens", 5)
            .put("temperature", 0)
        return JudgePrompt.parse(answerText(workersAi.run(model, body)))
    }

    /** Most models answer in result.response; OpenAI-style ones use result.choices. */
    private fun answerText(result: JSONObject): String {
        result.optString("response").takeIf { it.isNotEmpty() }?.let { return it }
        return result.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }

    companion object {
        const val DEFAULT_MODEL = "@cf/meta/llama-3.1-8b-instruct-fp8-fast"
    }
}

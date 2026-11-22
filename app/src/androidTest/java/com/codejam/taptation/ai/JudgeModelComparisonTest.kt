package com.codejam.taptation.ai

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.NotificationContext
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs every judge case on several Workers AI models and logs a score table (tag FakeNotifs). A
 * report, not a pass/fail check; opt-in because it spends free quota:
 * ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.codejam.taptation.ai.JudgeModelComparisonTest -Pandroid.testInstrumentationRunnerArguments.judgeBenchmark=true
 */
@RunWith(AndroidJUnit4::class)
class JudgeModelComparisonTest {
    // Not listed: @cf/google/gemma-4-26b-a4b-it thinks before answering (reasoning_content), so a
    // one-word reply needs hundreds of tokens: slow and ~20x the Neurons. It scored 0/22 at max_tokens 5.
    private val judges: List<Pair<String, (NotificationContext, GameConfig) -> Verdict>> = listOf(
        "llama-3.1-8b-fp8-fast" to ChatJudge()::judgeOrThrow,
        "llama-3.2-3b" to ChatJudge(model = "@cf/meta/llama-3.2-3b-instruct")::judgeOrThrow,
        "clef" to ClefJudge(variant = "clef")::judgeOrThrow,
        "clef-flash" to ClefJudge(variant = "clef-flash")::judgeOrThrow,
    )
    private val cases = JudgeCases.obvious + JudgeCases.harder + JudgeCases.criteria + JudgeCases.ambiguous

    @Before
    fun optIn() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Pass judgeBenchmark=true to run", args.getString("judgeBenchmark") == "true")
        assumeTrue("No Cloudflare key in local.properties", WorkersAi().isConfigured)
    }

    @Test
    fun compareModels() {
        val summary = judges.map { (name, judge) ->
            var right = 0; var scored = 0; var falseBait = 0; var missedBait = 0; var errors = 0
            val times = mutableListOf<Long>()
            for (case in cases) {
                val start = SystemClock.elapsedRealtime()
                val verdict = try {
                    judge(case.context, case.config).name
                } catch (e: Exception) {
                    errors++; "ERROR ${e.javaClass.simpleName}: ${e.message}"
                }
                times += SystemClock.elapsedRealtime() - start
                if (case.expected != null) {
                    scored++
                    if (verdict == case.expected.name) right++
                    else if (verdict == "Bait") falseBait++
                    else if (case.expected == Verdict.Bait) missedBait++
                }
                log("$name | ${case.name} | $verdict | expected ${case.expected ?: "-"} | ${times.last()} ms")
            }
            val median = times.sorted()[times.size / 2]
            "$name: $right/$scored right, $falseBait important-as-bait, $missedBait bait missed, $errors errors, median $median ms"
        }
        summary.forEach { log("SUMMARY $it") }
    }

    private fun log(line: String) = Log.i("FakeNotifs", "judge bench $line")
}

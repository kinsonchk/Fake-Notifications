package com.codejam.taptation.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Live calls to Cloudflare's Clef decision model; skipped without cloudflare.* keys in local.properties. */
@RunWith(AndroidJUnit4::class)
class ClefJudgeTest {
    private val judge = ClefJudge()

    @Before
    fun requireKey() = assumeTrue("No Cloudflare key in local.properties", judge.isConfigured)

    @Test fun obviousCases() = check(JudgeCases.obvious)

    @Test fun harderCases() = check(JudgeCases.harder)

    @Test fun userCriteriaChangeTheAnswer() = check(JudgeCases.criteria)

    @Test
    fun badTokenAnswersUnsure() {
        val badKey = ClefJudge(WorkersAi(apiToken = "not-a-real-token"))
        assertEquals(Verdict.Unsure, badKey.judge(JudgeCases.obvious.first().context, JudgeCases.config))
    }

    /** Verdict plus the bait probability, e.g. "Bait (bait 0.96)", so the log shows the margin. */
    private fun check(cases: List<JudgeCase>) = expectAll("clef", cases) {
        val p = judge.baitProbability(it.context, it.config)
        "${ClefQuestion.verdict(p, it.config.strictness)} (bait ${String.format(Locale.ROOT, "%.2f", p)})"
    }
}

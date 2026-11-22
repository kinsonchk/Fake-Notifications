package com.codejam.taptation.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Live calls to the default Workers AI chat model; skipped without cloudflare.* keys in local.properties. */
@RunWith(AndroidJUnit4::class)
class ChatJudgeTest {
    private val judge = ChatJudge()

    @Before
    fun requireKey() = assumeTrue("No Cloudflare key in local.properties", judge.isConfigured)

    @Test fun obviousCases() = check(JudgeCases.obvious)

    @Test fun harderCases() = check(JudgeCases.harder)

    @Test fun userCriteriaChangeTheAnswer() = check(JudgeCases.criteria)

    @Test
    fun missingKeyAnswersUnsureWithoutCalling() {
        val noKey = ChatJudge(WorkersAi(accountId = "", apiToken = ""))
        assertEquals(Verdict.Unsure, noKey.judge(JudgeCases.obvious.first().context, JudgeCases.config))
    }

    @Test
    fun badTokenAnswersUnsure() {
        val badKey = ChatJudge(WorkersAi(apiToken = "not-a-real-token"))
        assertEquals(Verdict.Unsure, badKey.judge(JudgeCases.obvious.first().context, JudgeCases.config))
    }

    private fun check(cases: List<JudgeCase>) =
        expectAll("chat", cases) { judge.judgeOrThrow(it.context, it.config).name }
}

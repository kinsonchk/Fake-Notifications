package com.codejam.taptation.ai

import com.codejam.taptation.logic.AppRule
import com.codejam.taptation.logic.Classification
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.NotificationContext
import com.codejam.taptation.logic.Strictness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JudgeQueueTest {
    private class FakeJudge(var answer: Verdict = Verdict.Bait, override val isConfigured: Boolean = true) : NotificationJudge {
        var calls = 0
        override fun judge(context: NotificationContext, config: GameConfig): Verdict { calls++; return answer }
    }

    private val tasks = ArrayDeque<Runnable>()
    private fun runAll() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    private val config = GameConfig()
    private fun post(title: String = "Hi", text: String = "there", pkg: String = "com.app") =
        NotificationContext(pkg, key = "k1", title = title, text = text)

    @Test
    fun verdictIsReadyAfterTheJudgeAnswers() {
        val queue = JudgeQueue(FakeJudge(Verdict.Important)) { tasks += it }
        queue.onPosted(post(), config)
        assertEquals(Classification.Unknown, queue.classificationFor(post()))
        runAll()
        assertEquals(Classification.Important, queue.classificationFor(post()))
    }

    @Test
    fun sameContentIsJudgedOnceAndNewContentAgain() {
        val judge = FakeJudge()
        val queue = JudgeQueue(judge) { tasks += it }
        queue.onPosted(post(), config); runAll()
        queue.onPosted(post(), config); runAll()
        assertEquals(1, judge.calls)
        queue.onPosted(post(text = "new message"), config); runAll()
        assertEquals(2, judge.calls)
    }

    @Test
    fun outdatedRequestsNeverReachTheJudge() {
        val judge = FakeJudge(Verdict.Bait)
        val queue = JudgeQueue(judge) { tasks += it }
        repeat(5) { queue.onPosted(post(text = "update $it"), config) }
        runAll()
        assertEquals(1, judge.calls)
        assertEquals(Classification.Bait, queue.classificationFor(post(text = "update 4")))
    }

    @Test
    fun removedOrClearedNotificationsAreNotJudged() {
        val judge = FakeJudge()
        val queue = JudgeQueue(judge) { tasks += it }
        queue.onPosted(post(), config)
        queue.onRemoved("k1")
        runAll()
        assertEquals(0, judge.calls)
        assertEquals(Classification.Unknown, queue.classificationFor(post()))
    }

    @Test
    fun appSwitchedToLetUsDecideGetsJudged() {
        val judge = FakeJudge(Verdict.Bait)
        val queue = JudgeQueue(judge) { tasks += it }
        queue.onPosted(post(), config.copy(appRules = mapOf("com.app" to AppRule.AlwaysImportant))); runAll()
        assertEquals(0, judge.calls)
        queue.onConfigChanged(config); runAll()
        assertEquals(1, judge.calls)
        assertEquals(Classification.Bait, queue.classificationFor(post()))
    }

    @Test
    fun alwaysBaitSkipsAiAndAutomaticAppsUseIt() {
        val judge = FakeJudge(Verdict.Bait)
        val queue = JudgeQueue(judge) { tasks += it }
        queue.onPosted(post(), config.copy(appRules = mapOf("com.app" to AppRule.AlwaysBait)))
        runAll()
        assertEquals(0, judge.calls)

        queue.onConfigChanged(config)
        runAll()
        assertEquals(1, judge.calls)
    }

    @Test
    fun changedCriteriaOrStrictnessRejudge() {
        val judge = FakeJudge(Verdict.Bait)
        val queue = JudgeQueue(judge) { tasks += it }
        queue.onPosted(post(), config); runAll()
        queue.onConfigChanged(config.copy(level = config.level)); runAll()
        assertEquals(1, judge.calls)
        queue.onConfigChanged(config.copy(strictness = Strictness.Strict)); runAll()
        queue.onConfigChanged(config.copy(strictness = Strictness.Strict, essentialCriteria = "family")); runAll()
        assertEquals(3, judge.calls)
    }

    @Test
    fun postedBeforeTheGameLoadedIsJudgedOnFirstConfig() {
        val judge = FakeJudge(Verdict.Important)
        val queue = JudgeQueue(judge) { tasks += it }
        queue.onPosted(post(), null); runAll()
        assertEquals(0, judge.calls)
        queue.onConfigChanged(config); runAll()
        assertEquals(Classification.Important, queue.classificationFor(post()))
    }

    @Test
    fun withoutAKeyNotificationsHaveNoAutomaticPenalty() {
        val judge = FakeJudge(isConfigured = false)
        val queue = JudgeQueue(judge) { tasks += it }
        queue.onPosted(post(), config); runAll()
        assertEquals(0, judge.calls)
        assertEquals(Classification.Unknown, queue.classificationFor(post()))
        assertNull(queue.judgeNow(post(), config))
    }
}

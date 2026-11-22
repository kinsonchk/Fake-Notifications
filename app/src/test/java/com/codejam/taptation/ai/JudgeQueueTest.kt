package com.codejam.taptation.ai

import com.codejam.taptation.logic.AppRule
import com.codejam.taptation.logic.Classification
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.NotificationContext
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
    fun answerForOldContentIsDropped() {
        val queue = JudgeQueue(FakeJudge(Verdict.Bait)) { tasks += it }
        queue.onPosted(post(text = "old"), config)
        queue.onPosted(post(text = "new"), config)
        tasks.removeFirst().run() // the old content's answer arrives first
        assertEquals(Classification.Unknown, queue.classificationFor(post(text = "new")))
        runAll()
        assertEquals(Classification.Bait, queue.classificationFor(post(text = "new")))
    }

    @Test
    fun onlyLetUsDecideAppsAreJudged() {
        val judge = FakeJudge()
        val queue = JudgeQueue(judge) { tasks += it }
        queue.onPosted(post(), config.copy(appRules = mapOf("com.app" to AppRule.AlwaysImportant))); runAll()
        assertEquals(0, judge.calls)
    }

    @Test
    fun removedNotificationForgetsItsVerdict() {
        val queue = JudgeQueue(FakeJudge(Verdict.Bait)) { tasks += it }
        queue.onPosted(post(), config); runAll()
        queue.onRemoved("k1")
        assertEquals(Classification.Unknown, queue.classificationFor(post()))
    }

    @Test
    fun withoutAKeyNothingIsJudgedAndTheFallbackStays() {
        val judge = FakeJudge(isConfigured = false)
        val queue = JudgeQueue(judge) { tasks += it }
        queue.onPosted(post(), config); runAll()
        assertEquals(0, judge.calls)
        assertNull(queue.classificationFor(post()))
    }
}

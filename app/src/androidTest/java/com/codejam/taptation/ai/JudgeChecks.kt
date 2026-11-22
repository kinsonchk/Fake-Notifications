package com.codejam.taptation.ai

import android.os.SystemClock
import android.util.Log
import org.junit.Assert.assertTrue

/** Runs [cases] through [judge], logs each verdict and time (tag FakeNotifs), fails listing every miss. */
fun expectAll(label: String, cases: List<JudgeCase>, judge: (JudgeCase) -> String) {
    val misses = cases.mapNotNull { case ->
        val start = SystemClock.elapsedRealtime()
        val verdict = judge(case)
        val millis = SystemClock.elapsedRealtime() - start
        Log.i("FakeNotifs", "judge test $label ${case.name}: $verdict (expected ${case.expected}) in $millis ms")
        if (verdict.startsWith(case.expected?.name ?: "")) null
        else "${case.name}: got $verdict, expected ${case.expected}"
    }
    assertTrue("Wrong verdicts:\n" + misses.joinToString("\n"), misses.isEmpty())
}

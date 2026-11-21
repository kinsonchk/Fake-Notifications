package com.codejam.taptation.ui.common

import com.codejam.taptation.logic.TapOutcome
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapExplanationsTest {
    @Test fun everyClassifierRuleHasAnExplanation() {
        // Rule codes are the string arguments of important()/bait()/unknown() in NotificationClassifier.
        val source = File("src/main/java/com/codejam/taptation/logic/NotificationClassifier.kt").readText()
        val codes = Regex("""(?:important|bait|unknown)\("([a-z_]+)"\)""").findAll(source).map { it.groupValues[1] }.toSet()
        assertTrue("no rule codes found", codes.isNotEmpty())
        codes.forEach { assertNotEquals("missing explanation for $it", it, TapExplanations.rule(it)) }
    }

    @Test fun olderTapsWithoutRuleSayNotRecorded() {
        assertEquals("Not recorded (tap saved by an older version)", TapExplanations.rule(""))
    }

    @Test fun outcomeTextExplainsMissedRollsAndCatches() {
        assertEquals("Roll missed: no penalty this time", TapExplanations.outcome(TapOutcome.NoPenalty, "roll_passed"))
        assertEquals("Caught: -20 health", TapExplanations.outcome(TapOutcome.Caught, "caught", 20))
        assertEquals("During lockout: no extra penalty", TapExplanations.outcome(TapOutcome.NoPenalty, "lockout"))
    }
}

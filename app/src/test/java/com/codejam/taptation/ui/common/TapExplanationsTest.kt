package com.codejam.taptation.ui.common

import com.codejam.taptation.logic.Classification
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

    @Test fun durationsUnderAnHourKeepSeconds() {
        assertEquals("45 s", formatDuration(45_000))
        assertEquals("11 min 23 s", formatDuration((11 * 60 + 23) * 1000L))
        assertEquals("2 min", formatDuration(120_000))
        assertEquals("3 h 5 min", formatDuration((3 * 3600 + 5 * 60 + 9) * 1000L))
    }

    @Test fun staleTapsAreExplained() {
        assertEquals("Too old: opened after your time limit, so it can't catch you",
            TapExplanations.outcome(TapOutcome.NoPenalty, "stale"))
    }

    @Test fun olderTapsWithoutRuleSayNotRecorded() {
        assertEquals("Not recorded (tap saved by an older version)", TapExplanations.rule(""))
    }

    @Test fun outcomeTextAvoidsInternalWords() {
        assertEquals("No catch this time", TapExplanations.outcome(TapOutcome.NoPenalty, "roll_passed"))
        assertEquals("Caught · −20 health", TapExplanations.outcome(TapOutcome.Caught, "caught", 20))
        assertEquals("No additional penalty during the cooldown", TapExplanations.outcome(TapOutcome.NoPenalty, "lockout"))
        listOf("roll_passed", "lockout", "stale", "dead", "disabled", "other").forEach { reason ->
            val text = TapExplanations.outcome(TapOutcome.NoPenalty, reason).lowercase()
            assertTrue(text, "lockout" !in text && "roll" !in text && "whitelist" !in text)
        }
        listOf("whitelisted", "vip_title", "configured_bait", "bait_channel").forEach {
            assertTrue(TapExplanations.rule(it), "whitelist" !in TapExplanations.rule(it).lowercase() && "vip" !in TapExplanations.rule(it).lowercase())
        }
    }

    @Test fun catchReasonNamesTheAppAndTypeWithoutPackageNames() {
        assertEquals("Instagram · Suggested for you is set as distracting",
            TapExplanations.catchReason("bait_channel", "Instagram", "Suggested for you"))
        assertEquals("Instagram is set as distracting", TapExplanations.catchReason("configured_bait", "Instagram", null))
        assertEquals("Instagram sent a promo or social notification", TapExplanations.catchReason("bait_category", "Instagram", " "))
        assertEquals("Mail · Offers counted as distracting", TapExplanations.catchReason("something_new", "Mail", "Offers"))
        assertEquals("Mail counted as distracting", TapExplanations.catchReason("", "Mail", null))
    }
}

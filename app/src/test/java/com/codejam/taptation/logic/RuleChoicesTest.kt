package com.codejam.taptation.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleChoicesTest {
    private val app = RuleTarget("com.insta")
    private val type = RuleTarget("com.insta", "Suggested for you")

    @Test fun wholeAppDistractingAndSafeAreExclusive() {
        val distracting = RuleChoices.apply(GameConfig(), app, RuleBehavior.Distracting)
        assertEquals(setOf("com.insta"), distracting.baitPackages)
        assertEquals(RuleBehavior.Distracting, RuleChoices.behaviorOf(distracting, app))
        val safe = RuleChoices.apply(distracting, app, RuleBehavior.AlwaysSafe)
        assertEquals(setOf("com.insta"), safe.whitelistedPackages)
        assertTrue(safe.baitPackages.isEmpty())
        assertEquals(RuleBehavior.AlwaysSafe, RuleChoices.behaviorOf(safe, app))
    }

    @Test fun defaultRemovesBothRulesForThatTargetOnly() {
        val config = GameConfig(baitPackages = setOf("com.insta", "com.other"), whitelistedPackages = setOf("com.cal"))
        val cleared = RuleChoices.apply(config, app, RuleBehavior.Default)
        assertEquals(setOf("com.other"), cleared.baitPackages)
        assertEquals(setOf("com.cal"), cleared.whitelistedPackages)
        assertEquals(RuleBehavior.Default, RuleChoices.behaviorOf(cleared, app))
    }

    @Test fun typeRulesUseChannelsAndMatchCaseInsensitively() {
        val config = RuleChoices.apply(GameConfig(), type, RuleBehavior.AlwaysSafe)
        assertEquals(mapOf("com.insta" to setOf("Suggested for you")), config.importantChannels)
        assertEquals(RuleBehavior.AlwaysSafe, RuleChoices.behaviorOf(config, RuleTarget("com.insta", "suggested FOR you ")))
        val flipped = RuleChoices.apply(config, type, RuleBehavior.Distracting)
        assertTrue(flipped.importantChannels.isEmpty())
        assertEquals(mapOf("com.insta" to setOf("Suggested for you")), flipped.baitChannels)
        assertTrue(RuleChoices.apply(flipped, type, RuleBehavior.Default).baitChannels.isEmpty())
    }

    @Test fun typeRuleDoesNotTouchTheWholeAppRule() {
        val config = GameConfig(baitPackages = setOf("com.insta"))
        val next = RuleChoices.apply(config, type, RuleBehavior.AlwaysSafe)
        assertEquals(setOf("com.insta"), next.baitPackages)
        assertEquals(RuleBehavior.Distracting, RuleChoices.behaviorOf(next, app))
    }

    @Test fun savedRulesListDistractingAppsAndTypeRulesInOrder() {
        var config = GameConfig(whitelistedPackages = setOf("com.cal"))
        config = RuleChoices.apply(config, RuleTarget("com.b"), RuleBehavior.Distracting)
        config = RuleChoices.apply(config, RuleTarget("com.a", "News"), RuleBehavior.AlwaysSafe)
        config = RuleChoices.apply(config, RuleTarget("com.a", "Offers"), RuleBehavior.Distracting)
        assertEquals(
            listOf(SavedRule(RuleTarget("com.b"), RuleBehavior.Distracting),
                SavedRule(RuleTarget("com.a", "Offers"), RuleBehavior.Distracting),
                SavedRule(RuleTarget("com.a", "News"), RuleBehavior.AlwaysSafe)),
            RuleChoices.savedRules(config))
    }

    @Test fun savedRuleCanBeRemovedAndRestoredForUndo() {
        val rule = SavedRule(type, RuleBehavior.Distracting)
        val config = RuleChoices.apply(GameConfig(), rule.target, rule.behavior)
        val removed = RuleChoices.apply(config, rule.target, RuleBehavior.Default)
        assertTrue(RuleChoices.savedRules(removed).isEmpty())
        assertEquals(config, RuleChoices.apply(removed, rule.target, rule.behavior))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankPackageIsRejected() { RuleChoices.apply(GameConfig(), RuleTarget(" "), RuleBehavior.Distracting) }

    @Test fun realClassifierHonoursTheChosenTypeRule() {
        val config = RuleChoices.apply(GameConfig(), type, RuleBehavior.AlwaysSafe)
        val context = NotificationContext("com.insta", title = "t", text = "x", category = "social", channelName = "Suggested for you")
        assertEquals("important_channel", NotificationClassifier.assessByRules(context, config).reason)
        val other = context.copy(channelName = "Direct messages")
        assertEquals("bait_category", NotificationClassifier.assessByRules(other, config).reason)
    }
}

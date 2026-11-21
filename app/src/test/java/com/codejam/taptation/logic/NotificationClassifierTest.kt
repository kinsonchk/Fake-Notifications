package com.codejam.taptation.logic

import org.junit.Assert.*
import org.junit.Test

class NotificationClassifierTest {
    private val normal = NotificationContext("observed.app", title = "Alex",
        text = "New update", category = "email", isConversation = false, ageMillis = 1000)
    private fun assess(context: NotificationContext = normal, config: GameConfig = GameConfig()) =
        NotificationClassifier.assess(context, config)

    @Test fun whitelistPrecedesEveryOtherRule() {
        assertEquals("whitelisted", assess(normal.copy(category = "promo"),
            GameConfig(whitelistedPackages = setOf(normal.packageName))).reason)
    }

    @Test fun protectedCategoriesBeatExplicitBaitRulesEvenWithoutContent() {
        listOf("call", "alarm", "reminder", "event", " CALL ").forEach { category ->
            val result = assess(NotificationContext("observed.app", category = category),
                GameConfig(baitPackages = setOf("observed.app")))
            assertEquals("important_category", result.reason)
            assertEquals(Classification.Important, result.classification)
        }
    }

    @Test fun vipNamesMatchWholeNamesInTitleAndBeatSocial() {
        val config = GameConfig(vipNames = setOf("Ann", "Zoë Smith"))
        assertEquals("vip_title", assess(normal.copy(title = "Message from ANN", category = "social"), config).reason)
        assertEquals("vip_title", assess(normal.copy(title = "Zoë Smith: sent a message"), config).reason)
        assertEquals(Classification.Bait, assess(normal.copy(title = "Joanne"), config).classification)
        assertEquals(Classification.Bait, assess(normal.copy(text = "Ann liked a post"), config).classification)
    }

    @Test fun urgentTermsInTitleOrBodyBeatPromoButDoNotMatchInsideWords() {
        listOf(normal.copy(title = "URGENT: please call", category = "promo"),
            normal.copy(text = "Your sign-in code: 123456", category = "social")).forEach {
            assertEquals("important_keyword", assess(it).reason)
        }
        assertEquals(Classification.Bait, assess(normal.copy(text = "Decode this codec")).classification)
    }

    @Test fun promoAndSocialAreBaitWhenSignalsAreComplete() {
        listOf("promo", "social").forEach {
            assertEquals("bait_category", assess(normal.copy(category = it)).reason)
        }
    }

    @Test fun ordinaryMessageAndEmailDefaultToBait() {
        listOf("msg", "message", "email").forEach {
            assertEquals(Classification.Bait, assess(normal.copy(category = it)).classification)
        }
    }

    @Test fun missingRedactedOrUnrecognizedSignalsFailSafe() {
        listOf(normal.copy(title = null), normal.copy(text = null), normal.copy(text = ""),
            normal.copy(category = "vendor_custom"), normal.copy(category = null),
            normal.copy(category = "msg", isConversation = null), normal.copy(ageMillis = -1)).forEach {
            assertEquals(it.toString(), Classification.Unknown, assess(it).classification)
        }
    }

    @Test fun configuredBaitNeverOverridesUrgencyOrMissingContent() {
        val config = GameConfig(baitPackages = setOf(normal.packageName), baitKeywords = setOf("update"))
        assertEquals(Classification.Important, assess(normal.copy(text = "urgent update"), config).classification)
        assertEquals(Classification.Unknown, assess(normal.copy(text = null), config).classification)
    }

    @Test fun outlookMeetingVipAndUrgencyBypassButOrdinaryMailIsBait() {
        val config = GameConfig(appProfiles = mapOf(normal.packageName to AppProfile.Outlook), vipNames = setOf("Alex"))
        assertEquals("vip_title", assess(normal, config).reason)
        assertEquals("important_category", assess(normal.copy(category = "event"), config).reason)
        assertEquals("important_keyword", assess(normal.copy(title = "Meeting invitation", category = "email"), config).reason)
        assertEquals(Classification.Bait, assess(normal.copy(title = "Newsletter"), config).classification)
    }

    @Test fun instagramLikesFollowsAndNonVipDmsAreBait() {
        val config = GameConfig(appProfiles = mapOf(normal.packageName to AppProfile.Instagram), vipNames = setOf("Sam"))
        listOf(normal.copy(title = "New like", category = "social"),
            normal.copy(title = "New follower", category = "social"),
            normal.copy(category = "msg", isConversation = true)).forEach {
            assertEquals(Classification.Bait, assess(it, config).classification)
        }
        assertEquals("vip_title", assess(normal.copy(title = "Sam: new DM", category = "msg", isConversation = true), config).reason)
    }

    @Test fun discordConversationsAndMentionsBeatSocialButServerChatterIsBait() {
        val config = GameConfig(appProfiles = mapOf(normal.packageName to AppProfile.Discord))
        val social = normal.copy(category = "social")
        assertEquals("discord_conversation", assess(social.copy(isConversation = true), config).reason)
        assertEquals("discord_mention", assess(social.copy(text = "@alex please check this"), config).reason)
        assertEquals("discord_mention", assess(social.copy(text = "Hi @everyone"), config).reason)
        assertEquals(Classification.Bait, assess(social, config).classification)
        assertEquals(Classification.Unknown, assess(social.copy(isConversation = null), config).classification)
    }

    @Test fun unknownAppConversationDoesNotAssumeSocialMeansBait() {
        assertEquals(Classification.Unknown, assess(normal.copy(category = "social", isConversation = true)).classification)
    }

    @Test fun observedChannelsAreExactAndScopedToTheirPackage() {
        val config = GameConfig(
            importantChannels = mapOf(normal.packageName to setOf("Observed Important Channel")),
            baitChannels = mapOf(normal.packageName to setOf("Observed Chatter Channel")),
        )
        val important = normal.copy(category = "social", channelName = "observed important channel")
        assertEquals("important_channel", assess(important, config).reason)
        assertEquals(Classification.Bait, assess(important.copy(packageName = "different.app"), config).classification)
        assertEquals("bait_channel", assess(normal.copy(category = null, channelName = "Observed Chatter Channel"), config).reason)
        assertEquals(Classification.Unknown, assess(normal.copy(category = null, channelName = "Observed Chatter Channel extra"), config).classification)
        assertEquals(Classification.Important, assess(important.copy(text = "urgent"), config).classification)
    }

    @Test fun noAgeBasedPenaltyThresholdIsInvented() {
        assertEquals(assess(normal).classification, assess(normal.copy(ageMillis = Long.MAX_VALUE)).classification)
    }

    @Test fun newSettingsAndAgeRoundTripWithoutSavingRawMetadata() {
        val config = GameConfig(vipNames = setOf("Alex"), importantKeywords = setOf("urgent", "code"),
            appProfiles = mapOf("real.package" to AppProfile.Discord),
            importantChannels = mapOf("real.package" to setOf("verified dm")),
            baitChannels = mapOf("real.package" to setOf("verified chatter")))
        val record = TapRecord(123, "real.package", Classification.Important, TapOutcome.Bypass,
            100, 100, 0, "vip_title", 5000)
        val state = GameState(config = config, taps = listOf(record))
        assertEquals(state, GameStateCodec.decode(GameStateCodec.encode(state)))
    }

    @Test fun versionOneStoreMigratesWithoutLosingHealthOrCounters() {
        // Fixed snapshot produced with the original v1 layout, before VIP/channel fields existed.
        val fixture = "AAAAAQAAAEkAAAAAAAAD6AAAAAAAAAPoAAAAAAAAAAD//////////wAAAAAAAAACP7MzMzMzMzMABE1pbGQAAAAKAAAAFAAAACMAAAAFAAAAHgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        val state = GameStateCodec.decode(fixture)
        assertEquals(73, state.health)
        assertEquals(2L, state.totalCatches)
        assertEquals(1000L, state.recoveryAnchorMillis)
        assertEquals(setOf("urgent", "code", "meeting invite", "meeting invitation"), state.config.importantKeywords)
        assertTrue(state.config.vipNames.isEmpty())
        assertEquals(state, GameStateCodec.decode(GameStateCodec.encode(state)))
    }

    @Test fun engineImportantBypassNeverRollsDamagesOrResetsCleanTimer() {
        var now = 1000L
        var rolls = 0
        val store = object : GameStore {
            var value: GameState? = null
            override fun load() = value
            override fun save(state: GameState) { value = state }
        }
        val engine = GameEngine(store, { now }, { rolls++; 0.0 })
        engine.setHealthForDebug(50)
        now += GameEngine.HOUR_MILLIS / 2
        val result = engine.onTap(normal.copy(text = "urgent update"))
        assertEquals(TapOutcome.Bypass, result.outcome)
        assertEquals(0, rolls)
        assertEquals(1000L, result.state.recoveryAnchorMillis)
        now += GameEngine.HOUR_MILLIS / 2
        assertEquals(55, engine.getHealth())
    }

    @Test fun packageAndAgeWithoutMetadataFailsSafeAndLogsAge() {
        val engine = GameEngine(object : GameStore {
            override fun load(): GameState? = null
            override fun save(state: GameState) {}
        }, { 1000 }, { error("Missing metadata must not roll") })
        assertEquals(TapOutcome.NoPenalty, engine.onTap("unknown.app", 500).outcome)
        assertEquals(500L, engine.state.taps.single().ageMillis)
    }
}

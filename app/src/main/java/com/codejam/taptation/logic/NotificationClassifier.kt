package com.codejam.taptation.logic

import java.util.Locale

data class Assessment(val classification: Classification, val reason: String)

/** Pure, ordered rules. No guessed package IDs or hardcoded third-party channel names. */
object NotificationClassifier {
    fun assess(context: NotificationContext, config: GameConfig): Assessment {
        fun important(reason: String) = Assessment(Classification.Important, reason)
        fun bait(reason: String) = Assessment(Classification.Bait, reason)
        fun unknown(reason: String) = Assessment(Classification.Unknown, reason)
        val category = context.category?.trim()?.lowercase(Locale.ROOT)
        val title = context.title.orEmpty()
        val content = "$title ${context.text.orEmpty()}"
        if (context.packageName in config.whitelistedPackages) return important("whitelisted")
        if (category in setOf("call", "alarm", "reminder", "event")) return important("important_category")
        if (config.vipNames.any { containsPhrase(title, it) }) return important("vip_title")
        if (config.importantKeywords.any { containsPhrase(content, it) }) return important("important_keyword")
        if (channelMatches(context, config.importantChannels)) return important("important_channel")

        // Discord exceptions must precede social/promo bait decisions.
        val profile = config.appProfiles[context.packageName]
        if (profile == AppProfile.Discord) {
            if (context.isConversation == true) return important("discord_conversation")
            if (mention.containsMatchIn(content)) return important("discord_mention")
        }

        // Can't rule out VIP/urgency when content was unavailable or redacted.
        if (context.title.isNullOrBlank() || context.text.isNullOrBlank()) return unknown("missing_content")
        if (context.ageMillis != null && context.ageMillis < 0) return unknown("invalid_age")
        if (channelMatches(context, config.baitChannels)) return bait("bait_channel")
        if (profile == AppProfile.Discord && context.isConversation == null) {
            return unknown("uncertain_discord_conversation")
        }
        if (context.isConversation == true && profile == null) return unknown("unrecognized_conversation")
        if (category in setOf("msg", "message") && context.isConversation == null && profile == null) {
            return unknown("missing_conversation_signal")
        }
        if (category in setOf("promo", "social")) return bait("bait_category")
        if (context.packageName in config.baitPackages ||
            context.appCategory in config.baitAppCategories ||
            config.baitKeywords.any { containsPhrase(content, it) }
        ) return bait("configured_bait")
        if (category in setOf("msg", "message", "email")) return bait("ordinary_notification")
        // A verified app profile allows ordinary complete-content notifications to default to bait.
        // Unrecognized category values still fail safe, even for a configured profile.
        if (profile != null && category.isNullOrBlank() && context.isConversation != null) {
            return bait("profile_default")
        }
        return unknown("unrecognized_signals")
    }

    private fun channelMatches(context: NotificationContext, rules: Map<String, Set<String>>): Boolean =
        !context.channelName.isNullOrBlank() && rules[context.packageName].orEmpty()
            .any { it.trim().equals(context.channelName.trim(), ignoreCase = true) }

    /** Whole name/phrase boundaries: Ann doesn't match Joanne; code doesn't match decode. */
    private fun containsPhrase(content: String, phrase: String): Boolean = Regex(
        "(?<![\\p{L}\\p{N}_])${Regex.escape(phrase.trim())}(?![\\p{L}\\p{N}_])",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(content)

    private val mention = Regex("(?<![\\p{L}\\p{N}_])@[\\p{L}\\p{N}_]+")
}

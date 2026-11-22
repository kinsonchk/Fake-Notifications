package com.codejam.taptation.logic

import java.util.Locale

data class Assessment(val classification: Classification, val reason: String)

object NotificationClassifier {
    private fun important(reason: String) = Assessment(Classification.Important, reason)
    private fun bait(reason: String) = Assessment(Classification.Bait, reason)
    private fun unknown(reason: String) = Assessment(Classification.Unknown, reason)

    /**
     * What a tap or dismissal of [context] counts as: stale notifications (older than the freshness
     * window) are Unknown without classification; otherwise [assess]. Taps and dismissals share it
     * so the stats compare like with like.
     */
    fun assessTap(context: NotificationContext, config: GameConfig): Assessment =
        if (config.isStale(context)) unknown("stale") else assess(context, config)

    /** The tap path's entry point: the user's choice for the app (Settings > Notification rules) decides first. */
    fun assess(context: NotificationContext, config: GameConfig): Assessment =
        when (config.ruleFor(context.packageName)) {
            AppRule.AlwaysImportant -> important("app_always_important")
            AppRule.AlwaysBait -> bait("app_always_bait")
            AppRule.LetUsDecide -> letUsDecide(context, config)
        }

    /**
     * Apps left on "Let us decide" follow the AI judge's verdict ([NotificationContext.judged], made
     * when the notification was posted from the user's criteria and strictness). If no result is
     * available (unconfigured, pending or failed), the tap has no penalty.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun letUsDecide(context: NotificationContext, config: GameConfig): Assessment =
        when (context.judged) {
            null -> unknown(context.judgingReason ?: "ai_not_configured")
            Classification.Important -> important("ai_important")
            Classification.Bait -> bait("ai_bait")
            Classification.Unknown -> unknown(context.judgingReason ?: "ai_unsure")
        }

    /**
     * The earlier ordered rules (whitelist, categories, VIP, keywords, channels, app profiles). Not on the
     * tap path any more; kept for the future "Let us decide" judge. No guessed package IDs or channel names.
     */
    fun assessByRules(context: NotificationContext, config: GameConfig): Assessment {
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

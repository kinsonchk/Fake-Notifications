package com.codejam.taptation.ui.common

import com.codejam.taptation.logic.Classification
import com.codejam.taptation.logic.TapOutcome

/** Plain-language text for the codes in TapRecord/TapResult. Pure, so it is unit-tested. */
object TapExplanations {
    /** Why the classifier picked Important, Bait or Unknown ([com.codejam.taptation.logic.TapRecord.rule]). */
    fun rule(code: String): String = when (code) {
        "whitelisted" -> "Always-safe app"
        "important_category" -> "Call, alarm, reminder or event"
        "vip_title" -> "From someone on your always-get-through list"
        "important_keyword" -> "Contains an urgent word"
        "important_channel" -> "Notification type marked always safe"
        "discord_conversation" -> "Discord direct conversation"
        "discord_mention" -> "Discord @mention"
        "missing_content" -> "Title or text missing, so urgent can't be ruled out"
        "invalid_age" -> "Notification age is invalid"
        "stale" -> "Older than your time limit, so it was skipped"
        "disabled" -> "Taptation was paused; nothing was checked"
        "bait_channel" -> "Notification type marked distracting"
        "uncertain_discord_conversation" -> "Discord, but the conversation flag is unknown"
        "unrecognized_conversation" -> "Conversation from an app without a profile"
        "missing_conversation_signal" -> "Message without a conversation signal"
        "bait_category" -> "Promo or social category"
        "configured_bait" -> "Distracting app, word or app category"
        "ordinary_notification" -> "Ordinary message or email"
        "profile_default" -> "Normal notification from a profiled app"
        "unrecognized_signals" -> "No rule matched"
        "" -> "Not recorded (tap saved by an older version)"
        else -> code
    }

    /**
     * One sentence for the caught pop-up: which app (and notification type) counted as distracting.
     * Uses names only; never notification text.
     */
    fun catchReason(rule: String, appLabel: String, channel: String?): String {
        val type = channel?.takeIf { it.isNotBlank() }
        return when (rule) {
            "bait_channel" -> if (type != null) "$appLabel · $type is set as distracting" else "$appLabel has a distracting notification type"
            "configured_bait" -> "$appLabel is set as distracting"
            "bait_category" -> "$appLabel sent a promo or social notification"
            "ordinary_notification" -> "$appLabel sent an ordinary message or email"
            "profile_default" -> "$appLabel notifications count as distracting"
            else -> if (type != null) "$appLabel · $type counted as distracting" else "$appLabel counted as distracting"
        }
    }

    fun classification(classification: Classification): String = when (classification) {
        Classification.Important -> "Important"
        Classification.Bait -> "Distracting"
        Classification.Unknown -> "Unsure"
    }

    /** What happened to the tap ([com.codejam.taptation.logic.TapRecord.reason] + outcome). */
    fun outcome(outcome: TapOutcome, reason: String, damage: Int = 0): String = when (outcome) {
        TapOutcome.Caught -> if (damage > 0) "Caught · −$damage health" else "Caught"
        TapOutcome.Bypass -> "Safe: important notifications are never penalized"
        TapOutcome.NoPenalty -> when (reason) {
            "roll_passed" -> "No catch this time"
            "lockout" -> "No additional penalty during the cooldown"
            "dead" -> "Tree is already dead"
            "stale" -> "Too old: opened after your time limit, so it can't catch you"
            "disabled" -> "Taptation was paused: nothing was checked"
            else -> "Not penalized: unsure notifications are never penalized"
        }
    }
}

package com.codejam.taptation.ui.common

import com.codejam.taptation.logic.Classification
import com.codejam.taptation.logic.TapOutcome

/** Plain-language text for the codes in TapRecord/TapResult. Pure, so it is unit-tested. */
object TapExplanations {
    /** Why the classifier picked Important, Bait or Unknown ([com.codejam.taptation.logic.TapRecord.rule]). */
    fun rule(code: String): String = when (code) {
        "app_always_important" -> "App set to Always important"
        "app_always_bait" -> "App set to Always bait"
        "let_us_decide" -> "Let us decide: counted as non-essential for now"
        "whitelisted" -> "App is whitelisted"
        "important_category" -> "Call, alarm, reminder or event"
        "vip_title" -> "Title matches a VIP name"
        "important_keyword" -> "Contains an important keyword"
        "important_channel" -> "Channel marked important for this app"
        "discord_conversation" -> "Discord direct conversation"
        "discord_mention" -> "Discord @mention"
        "missing_content" -> "Title or text missing, so urgent can't be ruled out"
        "invalid_age" -> "Notification age is invalid"
        "stale" -> "Notification is older than the freshness window; classification skipped"
        "disabled" -> "App is disabled; processing skipped"
        "bait_channel" -> "Channel marked bait for this app"
        "uncertain_discord_conversation" -> "Discord, but the conversation flag is unknown"
        "unrecognized_conversation" -> "Conversation from an app without a profile"
        "missing_conversation_signal" -> "Message without a conversation signal"
        "bait_category" -> "Promo or social category"
        "configured_bait" -> "Bait app, bait keyword or bait app category"
        "ordinary_notification" -> "Ordinary message or email"
        "profile_default" -> "Normal notification from a profiled app"
        "unrecognized_signals" -> "No rule matched"
        "" -> "Not recorded (tap saved by an older version)"
        else -> code
    }

    fun classification(classification: Classification): String = when (classification) {
        Classification.Important -> "Important"
        Classification.Bait -> "Bait"
        Classification.Unknown -> "Unsure"
    }

    /** What happened to the tap ([com.codejam.taptation.logic.TapRecord.reason] + outcome). */
    fun outcome(outcome: TapOutcome, reason: String, damage: Int = 0): String = when (outcome) {
        TapOutcome.Caught -> if (damage > 0) "Caught: -$damage health" else "Caught"
        TapOutcome.Bypass -> "Safe: important notifications are never penalized"
        TapOutcome.NoPenalty -> when (reason) {
            "roll_passed" -> "Roll missed: no penalty this time"
            "lockout" -> "During lockout: no extra penalty"
            "dead" -> "Tree is already dead"
            "stale" -> "Too old: tapped after the freshness window, bait rules don't apply"
            "disabled" -> "App disabled: no monitoring or penalty"
            else -> "Not penalized: unsure notifications are never penalized"
        }
    }
}

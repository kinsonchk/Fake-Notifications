package com.codejam.taptation.service

import android.app.Notification
import androidx.core.app.NotificationCompat

/**
 * Who sent a chat notification and whether it is a group chat ([com.codejam.taptation.logic.NotificationContext.sender]
 * / isGroup). Chat apps that use MessagingStyle say both; for the others the title of a 1-to-1
 * chat is the sender (seen for Discord and Instagram DMs) and group lines read "Name: message".
 * The sender is personal data: never log it.
 */
object ConversationDetection {
    data class Conversation(val sender: String?, val isGroup: Boolean?)

    /** What the chat layout says, if the notification uses one. Not called in JVM tests; see [resolve]. */
    fun from(notification: Notification): Conversation {
        val extras = notification.extras
        val style = try {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        } catch (e: RuntimeException) {
            null // Malformed extras from another app must not break the listener.
        }
        return resolve(
            isMessagingStyle = style != null,
            messageSender = style?.messages?.lastOrNull()?.person?.name?.toString(),
            styleIsGroup = style?.isGroupConversation,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras?.getCharSequence(Notification.EXTRA_TEXT))?.toString(),
            category = notification.category,
        )
    }

    /**
     * A notification is a chat when it uses MessagingStyle or has the message category. Sender:
     * the message's sender, else the "Name:" prefix of the last text line in a group, else the
     * title. Group: MessagingStyle's flag; without it unknown.
     */
    fun resolve(
        isMessagingStyle: Boolean,
        messageSender: String?,
        styleIsGroup: Boolean?,
        title: String?,
        text: String?,
        category: String?,
    ): Conversation {
        val isChat = isMessagingStyle || category == Notification.CATEGORY_MESSAGE
        if (!isChat) return Conversation(sender = null, isGroup = null)
        val isGroup = if (isMessagingStyle) styleIsGroup else null
        val prefix = text?.lines()?.lastOrNull { it.isNotBlank() }?.let(::namePrefix)
        val sender = messageSender?.trim()?.takeIf(String::isNotEmpty)
            ?: prefix?.takeIf { isGroup != false }
            ?: title?.trim()?.takeIf { it.isNotEmpty() && isGroup != true }
        return Conversation(sender, isGroup)
    }

    /** "Alex: see you at 8" -> "Alex"; short names only, so "Note: bring snacks" style text is rare noise. */
    private fun namePrefix(line: String): String? {
        val index = line.indexOf(": ")
        if (index !in 1..30) return null
        return line.substring(0, index).trim().takeIf { name -> name.isNotEmpty() && name.count { it == ' ' } <= 3 }
    }
}

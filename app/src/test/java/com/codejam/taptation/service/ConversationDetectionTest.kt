package com.codejam.taptation.service

import com.codejam.taptation.service.ConversationDetection.Conversation
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationDetectionTest {
    private fun resolve(
        messaging: Boolean = false, messageSender: String? = null, group: Boolean? = null,
        title: String? = null, text: String? = null, category: String? = null,
    ) = ConversationDetection.resolve(messaging, messageSender, group, title, text, category)

    @Test fun messagingStyleSenderAndGroupFlagWin() {
        assertEquals(Conversation("Alex", true),
            resolve(messaging = true, messageSender = "Alex", group = true, title = "Famille", text = "on y va?"))
        assertEquals(Conversation("Mom", false),
            resolve(messaging = true, messageSender = "Mom", group = false, title = "Mom", text = "call me"))
    }

    @Test fun directMessageTitleIsTheSender() {
        // Discord and Instagram DMs: title = the user.
        assertEquals(Conversation("sarah_k", false),
            resolve(messaging = true, group = false, title = "sarah_k", text = "are you coming tonight?"))
        assertEquals(Conversation("sarah_k", null), resolve(title = "sarah_k", text = "hey", category = "msg"))
    }

    @Test fun groupLinesGiveTheSenderPrefix() {
        assertEquals(Conversation("Alex", true),
            resolve(messaging = true, group = true, title = "Famille Girard", text = "Alex: 📷 Photo"))
        // A group without a sender anywhere: don't take the group name for a person.
        assertEquals(Conversation(null, true), resolve(messaging = true, group = true, title = "Famille Girard", text = "14 new messages"))
    }

    @Test fun nonChatNotificationsHaveNoSender() {
        assertEquals(Conversation(null, null), resolve(title = "Instagram", text = "alex.r liked your photo", category = "social"))
        assertEquals(Conversation(null, null), resolve(title = "Flash sale", text = "Deal: 70% off", category = "promo"))
    }
}

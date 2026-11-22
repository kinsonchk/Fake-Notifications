package com.codejam.taptation.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class NotificationPresetCodecTest {
    private val full = NotificationPreset(
        id = "1", name = "Boss", packageName = "com.slack", title = "Alex", text = "urgent: call me",
        category = "msg", channelName = "Direct messages", appCategory = 7, isConversation = true,
        actions = listOf("Reply", "Mark as read"),
        iconFile = "123e4567-e89b-12d3-a456-426614174000.png",
    )

    @Test fun roundTripKeepsEveryField() {
        val list = listOf(full, NotificationPreset(id = "2", name = "Empty"))
        assertEquals(list, NotificationPresetCodec.decode(NotificationPresetCodec.encode(list)))
        assertEquals(full, NotificationPresetCodec.decodeOne(NotificationPresetCodec.encodeOne(full)))
    }

    @Test fun corruptDataIsRejected() {
        assertThrows(Exception::class.java) { NotificationPresetCodec.decode("broken") }
        val bytes = java.util.Base64.getDecoder().decode(NotificationPresetCodec.encode(listOf(full)))
        bytes[3] = 9
        assertThrows(IllegalArgumentException::class.java) {
            NotificationPresetCodec.decode(java.util.Base64.getEncoder().encodeToString(bytes))
        }
    }

    @Test fun blankFieldsBecomeMissingSignals() {
        val context = NotificationPreset(packageName = " ", title = " ", actions = listOf(" ", "Reply"))
            .toContext("com.codejam.taptation")
        assertEquals("com.codejam.taptation", context.packageName)
        assertNull(context.title)
        assertNull(context.category)
        assertEquals(listOf("Reply"), context.actions)
    }

    @Test fun typedActionsAreSplitOnCommas() {
        assertEquals(listOf("Reply", "Mark as read"), parseActions("Reply, Mark as read, "))
    }

    @Test fun mediaRoundTripsAndVersion1PresetsStillLoad() {
        val gif = NotificationPreset(id = "1", name = "gif", title = "Mom", media = com.codejam.taptation.logic.MediaKind.Gif,
            sender = "Alex", isGroup = true)
        assertEquals(listOf(gif), NotificationPresetCodec.decode(NotificationPresetCodec.encode(listOf(gif))))

        // A preset saved by the v1 codec (no media field).
        val buffer = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(buffer).use { out ->
            out.writeInt(1); out.writeInt(1)
            listOf("1", "old", "", "Hi", "", "", "").forEach(out::writeUTF)
            out.writeBoolean(false); out.writeBoolean(false); out.writeInt(0); out.writeBoolean(false)
        }
        val old = NotificationPresetCodec.decode(java.util.Base64.getEncoder().encodeToString(buffer.toByteArray())).single()
        assertEquals(NotificationPreset(id = "1", name = "old", title = "Hi"), old)
    }
}

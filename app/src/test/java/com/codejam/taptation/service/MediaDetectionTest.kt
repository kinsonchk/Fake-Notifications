package com.codejam.taptation.service

import com.codejam.taptation.logic.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaDetectionTest {
    @Test fun mimeTypeWinsOverEverythingElse() {
        assertEquals(MediaKind.Gif, MediaDetection.detect("image/gif", hasPicture = false, text = "lol"))
        assertEquals(MediaKind.Image, MediaDetection.detect("image/jpeg", hasPicture = false, text = null))
        assertEquals(MediaKind.Video, MediaDetection.detect("video/mp4", hasPicture = true, text = "GIF"))
        assertEquals(MediaKind.Audio, MediaDetection.detect("audio/ogg", hasPicture = false, text = null))
        assertNull(MediaDetection.fromMimeType("text/plain"))
    }

    @Test fun bigPictureMeansImage() {
        assertEquals(MediaKind.Image, MediaDetection.detect(null, hasPicture = true, text = "New post"))
    }

    @Test fun appPlaceholdersAreRecognised() {
        mapOf(
            "📷 Photo" to MediaKind.Image,
            "Alex: 📷 Photo" to MediaKind.Image,
            "GIF" to MediaKind.Gif,
            "Alex: GIF" to MediaKind.Gif,
            "sent you a GIF" to MediaKind.Gif,
            "sarah_k sent you a photo." to MediaKind.Image,
            "🎥 Video" to MediaKind.Video,
            "🎤 Voice message (0:12)" to MediaKind.Audio,
            "Sticker" to MediaKind.Sticker,
            "Marc a envoyé une photo" to MediaKind.Image,
            "Message vocal" to MediaKind.Audio,
            "Photos" to MediaKind.Image,
        ).forEach { (text, kind) -> assertEquals(text, kind, MediaDetection.fromText(text)) }
    }

    @Test fun ordinaryMessagesMentioningMediaAreNotAttachments() {
        listOf(
            "Did you see the photo I posted yesterday?",
            "Can you send me the video from the party",
            "Your verification code is 482913",
            "",
        ).forEach { assertNull(it, MediaDetection.fromText(it)) }
        assertNull(MediaDetection.fromText(null))
    }

    @Test fun lastLineOfAMultiLineTextCounts() {
        assertEquals(MediaKind.Image, MediaDetection.fromText("Hey\n📷 Photo"))
    }
}

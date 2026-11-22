package com.codejam.taptation.service

import android.app.Notification
import android.os.Build
import androidx.core.app.NotificationCompat
import com.codejam.taptation.logic.MediaKind

/**
 * Detects whether a notification announces a photo, GIF, video, sticker or voice message.
 *
 * Signals, most reliable first: the MIME type a MessagingStyle message attaches (Google Messages
 * and others), a BigPictureStyle picture, then the placeholder text many apps show instead
 * ("📷 Photo", "GIF", "sent you a photo", "a envoyé une photo"). Only the kind is returned: the
 * attachment itself is usually not readable by a listener, and is never opened. Best effort:
 * apps that show none of these signals are not detected.
 */
object MediaDetection {
    /** Reads the notification's signals. Not called in JVM tests; see [detect]. */
    fun from(notification: Notification): MediaKind? {
        val extras = notification.extras
        val mimeType = try {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                ?.messages?.lastOrNull()?.dataMimeType
        } catch (e: RuntimeException) {
            null // Malformed extras from another app must not break the listener.
        }
        val hasPicture = extras != null && (extras.containsKey(Notification.EXTRA_PICTURE) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && extras.containsKey(Notification.EXTRA_PICTURE_ICON)))
        val text = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        return detect(mimeType, hasPicture, text)
    }

    fun detect(mimeType: String?, hasPicture: Boolean, text: String?): MediaKind? =
        fromMimeType(mimeType) ?: (if (hasPicture) MediaKind.Image else null) ?: fromText(text)

    fun fromMimeType(mimeType: String?): MediaKind? {
        val type = mimeType?.trim()?.lowercase() ?: return null
        return when {
            type == "image/gif" -> MediaKind.Gif
            type.startsWith("image/") -> MediaKind.Image
            type.startsWith("video/") -> MediaKind.Video
            type.startsWith("audio/") -> MediaKind.Audio
            else -> null
        }
    }

    /** Placeholder text: the whole (last) line is a media word, or "sent (you) a <media>". */
    fun fromText(text: String?): MediaKind? {
        val line = text?.lines()?.lastOrNull { it.isNotBlank() }?.trim() ?: return null
        leadingEmoji.entries.firstOrNull { (emoji, _) -> line.startsWith(emoji) || line.contains(": $emoji") }
            ?.let { return it.value }
        // "Alex: Photo" in group chats: drop a short sender prefix.
        val body = line.substringAfter(": ", line).takeIf { line.indexOf(": ") in 1..30 } ?: line
        val normalized = body.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
        words.entries.firstOrNull { (word, _) -> normalized == word || normalized == word + "s" }?.let { return it.value }
        sentPhrase.find(normalized)?.groupValues?.get(1)?.let { noun -> return words[noun] ?: words[noun.removeSuffix("s")] }
        return null
    }

    private val leadingEmoji = linkedMapOf(
        "📷" to MediaKind.Image, "📸" to MediaKind.Image, "🖼" to MediaKind.Image,
        "🎥" to MediaKind.Video, "📹" to MediaKind.Video, "🎬" to MediaKind.Video,
        "🎤" to MediaKind.Audio, "🎙" to MediaKind.Audio,
    )

    private val words = linkedMapOf(
        "gif" to MediaKind.Gif,
        "photo" to MediaKind.Image, "image" to MediaKind.Image, "picture" to MediaKind.Image, "pic" to MediaKind.Image,
        "video" to MediaKind.Video, "vidéo" to MediaKind.Video,
        "sticker" to MediaKind.Sticker, "autocollant" to MediaKind.Sticker,
        "voice message" to MediaKind.Audio, "voice note" to MediaKind.Audio, "audio" to MediaKind.Audio,
        "message vocal" to MediaKind.Audio,
    )

    private val sentPhrase = Regex(
        "\\b(?:sent(?: you)?|a envoyé|t a envoyé)(?: a| an| un| une)? " +
            "(gif|photo|image|picture|pic|video|vidéo|sticker|autocollant|voice message|voice note|message vocal)s?\\b",
    )
}

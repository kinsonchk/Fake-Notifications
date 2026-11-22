package com.codejam.taptation.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import com.codejam.taptation.logic.MediaKind
import java.io.File

/**
 * Placeholder attachments for debug notifications, so a test message carries a real MessagingStyle
 * attachment (MIME type + content URI) exactly like a chat app's photo or GIF. Debug builds only:
 * the FileProvider is declared in src/debug/AndroidManifest.xml.
 */
object DebugMedia {
    fun mimeType(kind: MediaKind): String? = when (kind) {
        MediaKind.Image -> "image/png"
        MediaKind.Gif -> "image/gif"
        MediaKind.Video -> "video/mp4"
        MediaKind.Audio -> "audio/ogg"
        MediaKind.Sticker -> null // No MIME type for stickers: apps announce them in text only.
    }

    /** A small generated picture (one file per kind), shareable with the system UI. */
    fun uriFor(context: Context, kind: MediaKind): Uri {
        val directory = File(context.cacheDir, "debug-media").apply { mkdirs() }
        val file = File(directory, "attachment-${kind.name.lowercase()}")
        if (!file.exists()) {
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(46, 104, 75)) }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        return FileProvider.getUriForFile(context, context.packageName + ".debugmedia", file)
    }
}

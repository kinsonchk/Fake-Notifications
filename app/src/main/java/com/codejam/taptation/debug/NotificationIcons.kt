package com.codejam.taptation.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Images picked for custom notifications, copied into app-private storage and scaled down so a
 * preset keeps working after the original picture is gone. Call from a background thread.
 */
object NotificationIcons {
    private const val MAX_SIZE = 256
    private val validName = Regex("[0-9a-f-]{36}\\.png")

    /** Copies [uri] as a PNG of at most 256 px and returns its file name. */
    fun import(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not a readable image" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIZE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Not a readable image")
        val scale = MAX_SIZE.toFloat() / max(decoded.width, decoded.height)
        val bitmap = if (scale >= 1f) decoded else Bitmap.createScaledBitmap(
            decoded, (decoded.width * scale).roundToInt().coerceAtLeast(1),
            (decoded.height * scale).roundToInt().coerceAtLeast(1), true,
        )
        val name = "${UUID.randomUUID()}.png"
        File(folder(context), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return name
    }

    fun load(context: Context, name: String?): Bitmap? =
        file(context, name)?.takeIf(File::exists)?.let { BitmapFactory.decodeFile(it.path) }

    fun delete(context: Context, name: String?) {
        file(context, name)?.delete()
    }

    /** Deletes images no preset (or the current draft) refers to, e.g. after replacing a draft image. */
    fun deleteUnused(context: Context, inUse: Set<String>) {
        folder(context).listFiles()?.filter { it.name !in inUse }?.forEach(File::delete)
    }

    private fun file(context: Context, name: String?): File? =
        name?.takeIf(validName::matches)?.let { File(folder(context), it) }

    private fun folder(context: Context): File = File(context.filesDir, "notification_icons").apply { mkdirs() }
}

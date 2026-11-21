package com.codejam.taptation.debug

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.codejam.taptation.logic.NotificationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A custom debug notification, typed by the user. Also used as the composer's draft (empty
 * [id]/[name]). Only holds what the user entered, never content copied from real notifications.
 *
 * @property iconFile file name inside [NotificationIcons]' folder, shown as the large icon.
 */
data class NotificationPreset(
    val id: String = "",
    val name: String = "",
    val packageName: String = "",
    val title: String = "",
    val text: String = "",
    val category: String = "",
    val channelName: String = "",
    val appCategory: Int? = null,
    val isConversation: Boolean? = null,
    val actions: List<String> = emptyList(),
    val iconFile: String? = null,
) {
    /** The context the listener will report; blank package means "this app". */
    fun toContext(ownPackageName: String? = null): NotificationContext = NotificationContext(
        packageName = packageName.trim().ifEmpty { ownPackageName.orEmpty() },
        title = title.takeIf(String::isNotBlank),
        text = text.takeIf(String::isNotBlank),
        category = category.trim().takeIf(String::isNotEmpty),
        appCategory = appCategory,
        isConversation = isConversation,
        channelName = channelName.trim().takeIf(String::isNotEmpty),
        actions = actions.map(String::trim).filter(String::isNotEmpty),
    )
}

/** Versioned, pure-JVM encoding (same style as GameStateCodec) so it is unit-tested. */
object NotificationPresetCodec {
    private const val VERSION = 1

    fun encode(presets: List<NotificationPreset>): String {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(VERSION)
            out.writeInt(presets.size)
            presets.forEach { out.preset(it) }
        }
        return Base64.getEncoder().encodeToString(buffer.toByteArray())
    }

    fun decode(encoded: String): List<NotificationPreset> =
        DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use { input ->
            require(input.readInt() == VERSION) { "Unsupported preset version" }
            val count = input.readInt().also { require(it in 0..1000) { "Invalid preset count" } }
            List(count) { input.preset() }.also {
                require(input.available() == 0) { "Trailing preset data" }
            }
        }

    /** One preset as a single string, used to keep the composer draft across tab switches. */
    fun encodeOne(preset: NotificationPreset): String = encode(listOf(preset))
    fun decodeOne(encoded: String): NotificationPreset = decode(encoded).single()

    private fun DataOutputStream.preset(p: NotificationPreset) {
        listOf(p.id, p.name, p.packageName, p.title, p.text, p.category, p.channelName).forEach(::writeUTF)
        writeNullable(p.appCategory) { writeInt(it) }
        writeNullable(p.isConversation) { writeBoolean(it) }
        writeInt(p.actions.size)
        p.actions.forEach(::writeUTF)
        writeNullable(p.iconFile) { writeUTF(it) }
    }

    private fun DataInputStream.preset(): NotificationPreset {
        val strings = List(7) { readUTF() }
        return NotificationPreset(
            id = strings[0], name = strings[1], packageName = strings[2], title = strings[3],
            text = strings[4], category = strings[5], channelName = strings[6],
            appCategory = readNullable { readInt() },
            isConversation = readNullable { readBoolean() },
            actions = List(readInt().also { require(it in 0..20) }) { readUTF() },
            iconFile = readNullable { readUTF() },
        )
    }

    private fun <T : Any> DataOutputStream.writeNullable(value: T?, write: DataOutputStream.(T) -> Unit) {
        writeBoolean(value != null)
        value?.let { write(it) }
    }

    private fun <T : Any> DataInputStream.readNullable(readValue: DataInputStream.() -> T): T? =
        if (readBoolean()) readValue() else null
}

/** Saved presets, in their own SharedPreferences file (separate from the game store). */
class NotificationPresetStore private constructor(private val prefs: SharedPreferences) {
    private val _presets = MutableStateFlow(load())
    val presets: StateFlow<List<NotificationPreset>> = _presets.asStateFlow()

    /** Adds [preset], or replaces the one with the same id. */
    fun save(preset: NotificationPreset) {
        require(preset.id.isNotBlank() && preset.name.isNotBlank())
        val list = presets.value
        val index = list.indexOfFirst { it.id == preset.id }
        persist(if (index < 0) list + preset else list.toMutableList().also { it[index] = preset })
    }

    fun delete(id: String) = persist(presets.value.filterNot { it.id == id })

    private fun persist(list: List<NotificationPreset>) {
        prefs.edit().putString(KEY, NotificationPresetCodec.encode(list)).apply()
        _presets.value = list
    }

    private fun load(): List<NotificationPreset> = try {
        prefs.getString(KEY, null)?.let(NotificationPresetCodec::decode).orEmpty()
    } catch (e: Exception) {
        Log.w("FakeNotifs", "Saved notification presets unreadable, starting empty", e)
        emptyList()
    }

    companion object {
        private const val KEY = "presets"

        @Volatile private var instance: NotificationPresetStore? = null

        fun get(context: Context): NotificationPresetStore = instance ?: synchronized(this) {
            instance ?: NotificationPresetStore(
                context.applicationContext.getSharedPreferences("notification_presets", Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}

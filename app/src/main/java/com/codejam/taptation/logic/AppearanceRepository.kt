package com.codejam.taptation.logic

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Appearance { Dark, Light }

/** Saved app appearance, independent of game settings and the phone's system theme. */
class AppearanceRepository(context: Context, scope: CoroutineScope, preferencesName: String = "appearance_v1") {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val choices = Channel<Appearance>(Channel.CONFLATED)
    private val _mode = MutableStateFlow<Appearance?>(null)
    val mode = _mode.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            try {
                val name = preferences.getString("mode", Appearance.Dark.name)
                _mode.value = Appearance.entries.find { it.name == name } ?: Appearance.Dark
            } catch (_: Exception) {
                _mode.value = Appearance.Dark
                _error.value = "Your appearance setting could not be loaded. Choose a theme to save it again."
            }
            // One consumer preserves the latest intent, including choices made during initial loading.
            for (choice in choices) {
                try {
                    check(preferences.edit().putString("mode", choice.name).commit())
                    _mode.value = choice
                    _error.value = null
                } catch (_: Exception) { _error.value = "Your theme could not be saved. Please try again." }
            }
        }
    }

    fun setAppearance(appearance: Appearance) { choices.trySend(appearance) }
}

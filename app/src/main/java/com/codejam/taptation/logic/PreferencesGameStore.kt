package com.codejam.taptation.logic

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log

/** One atomic preferences value keeps health, settings and statistics consistent. Worker only. */
class PreferencesGameStore(context: Context) : GameStore {
    private val preferences = context.getSharedPreferences("taptation_game", Context.MODE_PRIVATE)

    /**
     * An unreadable snapshot (unknown layout, corruption) would otherwise stop the engine from ever
     * starting, with no way to recover from the app. Keep a copy under [UNREADABLE_KEY] for
     * debugging and start a fresh game instead.
     */
    @SuppressLint("UseKtx") // The KTX edit helper discards commit()'s success flag.
    override fun load(): GameState? {
        val snapshot = preferences.getString(SNAPSHOT_KEY, null) ?: return null
        return try {
            GameStateCodec.decode(snapshot)
        } catch (e: Exception) {
            Log.e("FakeNotifs", "Unreadable game snapshot, starting fresh (copy kept as $UNREADABLE_KEY)", e)
            preferences.edit().putString(UNREADABLE_KEY, snapshot).remove(SNAPSHOT_KEY).commit()
            null
        }
    }

    @SuppressLint("UseKtx") // The KTX edit helper discards commit()'s success flag.
    override fun save(state: GameState) {
        check(preferences.edit().putString(SNAPSHOT_KEY, GameStateCodec.encode(state)).commit()) {
            "Could not save game state"
        }
    }

    private companion object {
        const val SNAPSHOT_KEY = "snapshot"
        const val UNREADABLE_KEY = "snapshot_unreadable"
    }
}

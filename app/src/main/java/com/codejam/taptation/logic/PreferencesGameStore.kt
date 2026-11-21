package com.codejam.taptation.logic

import android.content.Context
import android.annotation.SuppressLint

/** One atomic preferences value keeps health, settings and statistics consistent. Worker only. */
class PreferencesGameStore(context: Context) : GameStore {
    private val preferences = context.getSharedPreferences("taptation_game", Context.MODE_PRIVATE)

    override fun load(): GameState? = preferences.getString("snapshot", null)?.let(GameStateCodec::decode)

    @SuppressLint("UseKtx") // The KTX edit helper discards commit()'s success flag.
    override fun save(state: GameState) {
        check(preferences.edit().putString("snapshot", GameStateCodec.encode(state)).commit()) {
            "Could not save game state"
        }
    }
}

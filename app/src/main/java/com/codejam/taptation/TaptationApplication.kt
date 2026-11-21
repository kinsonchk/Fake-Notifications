package com.codejam.taptation

import android.app.Application
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.logic.TapDispatcher

class TaptationApplication : Application() {
    lateinit var game: GameRepository
        private set

    override fun onCreate() {
        super.onCreate()
        game = GameRepository(this)
        TapDispatcher.handler = game
    }
}

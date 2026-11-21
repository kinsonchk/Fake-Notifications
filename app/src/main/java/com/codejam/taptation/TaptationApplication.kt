package com.codejam.taptation

import android.app.Application
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.logic.TapDispatcher
import com.codejam.taptation.stats.SqliteStatsStore
import com.codejam.taptation.stats.StatsRepository

class TaptationApplication : Application() {
    lateinit var game: GameRepository
        private set

    /** Taps and dismissals over time (docs/decision-stats-handler.md). Read-only for the UI. */
    lateinit var stats: StatsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        stats = StatsRepository(SqliteStatsStore(this))
        game = GameRepository(this, stats)
        TapDispatcher.handler = game
    }
}

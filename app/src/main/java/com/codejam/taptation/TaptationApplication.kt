package com.codejam.taptation

import android.app.Application
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.logic.TapDispatcher
import com.codejam.taptation.stats.SqliteStatsStore
import com.codejam.taptation.stats.StatsRepository
import com.codejam.taptation.service.CatchFeedback

class TaptationApplication : Application() {
    lateinit var game: GameRepository
        private set

    /** Taps and dismissals over time (docs/decision-stats-handler.md). Read-only for the UI. */
    lateinit var stats: StatsRepository
        private set
    lateinit var feedback: CatchFeedback
        private set

    override fun onCreate() {
        super.onCreate()
        stats = StatsRepository(SqliteStatsStore(this))
        feedback = CatchFeedback(this)
        game = GameRepository(this, stats, feedback::rememberDestination)
        feedback.attach(game)
        TapDispatcher.handler = game
    }
}

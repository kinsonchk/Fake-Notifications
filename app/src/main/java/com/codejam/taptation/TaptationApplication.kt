package com.codejam.taptation

import android.app.Application
import com.codejam.taptation.ai.ClefJudge
import com.codejam.taptation.ai.JudgeQueue
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.logic.TapDispatcher
import com.codejam.taptation.service.CatchFeedback
import com.codejam.taptation.stats.SqliteStatsStore
import com.codejam.taptation.stats.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

class TaptationApplication : Application() {
    lateinit var game: GameRepository
        private set

    /** Taps and dismissals over time (docs/decision-stats-handler.md). Read-only for the UI. */
    lateinit var stats: StatsRepository
        private set

    lateinit var feedback: CatchFeedback
        private set

    /** AI verdicts for "Let us decide" (Cloudflare Clef); inactive without cloudflare.* keys. */
    lateinit var judges: JudgeQueue
        private set

    override fun onCreate() {
        super.onCreate()
        stats = StatsRepository(SqliteStatsStore(this))
        judges = JudgeQueue(ClefJudge())
        feedback = CatchFeedback(this)
        game = GameRepository(this, stats, feedback::rememberDestination)
        feedback.attach(game)
        TapDispatcher.handler = game
        // Re-judge notifications in the shade when app rules, criteria or strictness change, and
        // judge the ones posted while the game was still loading.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            game.state.mapNotNull { it?.config }
                .distinctUntilChangedBy { it.appRules to JudgeQueue.judgingSettings(it) }
                .collect(judges::onConfigChanged)
        }
    }
}

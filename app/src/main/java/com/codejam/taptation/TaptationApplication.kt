package com.codejam.taptation

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.codejam.taptation.ai.ClefJudge
import com.codejam.taptation.ai.JudgeQueue
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.logic.meadow.MeadowRepository
import com.codejam.taptation.logic.RulePicker
import com.codejam.taptation.logic.TapDispatcher
import com.codejam.taptation.service.AppDirectory
import com.codejam.taptation.service.CatchFeedback
import com.codejam.taptation.service.SystemStatus
import com.codejam.taptation.service.TapListenerService
import com.codejam.taptation.stats.SqliteStatsStore
import com.codejam.taptation.stats.StatsRepository
import com.codejam.taptation.ui.overlay.CatchOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Composition root: creates every backend object once per process and is the only place that wires
 * a UI implementation into the backend (the caught overlay). Screens reach the backend through
 * these properties (see "UI ↔ backend contract" in CLAUDE.md and docs/ui-backend-contract.md).
 */
class TaptationApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var game: GameRepository
        private set

    /** Taps and dismissals over time (docs/decision-stats-handler.md). Read-only for the UI. */
    lateinit var stats: StatsRepository
        private set

    /** The caught flow: the [CatchFeedback.prompt] every caught screen renders, and its actions. */
    lateinit var feedback: CatchFeedback
        private set

    /** AI verdicts for "Let us decide" (Cloudflare Clef); inactive without cloudflare.* keys. */
    lateinit var judges: JudgeQueue
        private set

    /** Installed apps, display names and icons. Screens never query the package manager. */
    lateinit var apps: AppDirectory
        private set

    /** Android permissions and the monitoring status, refreshed whenever an activity resumes. */
    lateinit var system: SystemStatus
        private set

    lateinit var meadow: MeadowRepository
        private set

    /** What the Notification rules page lists: eligible apps with their rule, and orphaned rules. */
    lateinit var rulePicker: StateFlow<RulePicker>
        private set

    override fun onCreate() {
        super.onCreate()
        stats = StatsRepository(SqliteStatsStore(this))
        judges = JudgeQueue(ClefJudge())
        apps = AppDirectory(this)
        feedback = CatchFeedback(this, apps)
        feedback.surface = CatchOverlay(this)
        game = GameRepository(this, stats, feedback::rememberDestination)
        feedback.attach(game)
        TapDispatcher.handler = game
        system = SystemStatus(this, TapListenerService.connected, game.state, scope)
        meadow = MeadowRepository(this, game.state, system.monitoring, scope)
        rulePicker = combine(apps.apps, game.state) { list, state ->
            RulePicker.build(list, state?.config, apps.notificationFilterSupported)
        }.stateIn(scope, SharingStarted.Eagerly, RulePicker(notificationFilterSupported = apps.notificationFilterSupported))
        // Re-judge notifications in the shade when app rules, criteria or strictness change, and
        // judge the ones posted while the game was still loading.
        scope.launch {
            game.state.mapNotNull { it?.config }
                .distinctUntilChangedBy { it.appRules to JudgeQueue.judgingSettings(it) }
                .collect(judges::onConfigChanged)
        }
        // The user may come back from system settings or after the phone slept: re-read permissions
        // and installed apps, and catch up on recovery. Screens don't need to trigger any of this.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { system.refresh(); apps.reload(); game.refresh(); meadow.refresh() }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}

package com.codejam.taptation.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.TaptationApplication
import com.codejam.taptation.logic.AppRule
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.GameEngine
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.logic.RulePicker
import com.codejam.taptation.service.PermissionHelper
import com.codejam.taptation.ui.common.AppRuleRow
import com.codejam.taptation.ui.common.CriteriaField
import com.codejam.taptation.ui.common.RuleLegend
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.color
import com.codejam.taptation.ui.common.openOverlaySettings
import com.codejam.taptation.ui.common.rememberAppLabels
import com.codejam.taptation.ui.common.rememberGameRepository
import com.codejam.taptation.logic.meadow.MeadowState
import com.codejam.taptation.logic.meadow.ValleyStage
import com.codejam.taptation.ui.home.MeadowCanvas
import com.codejam.taptation.ui.home.MeadowEffects
import com.codejam.taptation.ui.home.MeadowScenery
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PREFS = "first_run"
private const val TUTORIAL_COMPLETE = "tutorial_complete"
private const val PAGE_WELCOME = 0
private const val PAGE_PERSONAL_CRITERIA = 1
private const val PAGE_CATCHES = 2
private const val PAGE_APP_RULES = 3
private const val PAGE_SETUP = 4
private const val TUTORIAL_PAGE_COUNT = PAGE_SETUP

internal fun isTutorialComplete(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(TUTORIAL_COMPLETE, false)

internal fun completeTutorial(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putBoolean(TUTORIAL_COMPLETE, true).apply()
}

@Composable
internal fun OnboardingScreen(startAtSetup: Boolean, onComplete: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TaptationApplication
    val repository = rememberGameRepository()
    val gameState by repository.state.collectAsStateWithLifecycle()
    // Permission state comes from the backend, which re-reads it whenever the activity resumes.
    val permissions by app.system.permissions.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(if (startAtSetup) PAGE_SETUP else PAGE_WELCOME) }
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    Column(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when (page) {
                    PAGE_SETUP -> "A couple of settings"
                    PAGE_PERSONAL_CRITERIA -> "What matters to you?"
                    PAGE_APP_RULES -> "Set rules for your apps"
                    PAGE_CATCHES -> "Pause, then choose what to do next."
                    else -> "Give your attention a little space"
                },
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            if (page < PAGE_SETUP) {
                Text("Step ${page + 1} of $TUTORIAL_PAGE_COUNT", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            }
            when (page) {
                PAGE_WELCOME -> WelcomePage()
                PAGE_PERSONAL_CRITERIA -> CriteriaPage(gameState?.config, repository)
                PAGE_CATCHES -> CatchesPage(gameState?.config ?: GameConfig())
                PAGE_APP_RULES -> AppRulesPage(repository)
                else -> SetupPage(permissions.notificationAccess, permissions.overlay)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                repeat(PAGE_SETUP + 1) { index ->
                    Surface(
                        color = if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.width(if (index == page) 22.dp else 8.dp).height(8.dp),
                    ) {}
                }
            }
            if (page == PAGE_PERSONAL_CRITERIA) {
                TextButton(onClick = { page = PAGE_CATCHES }) { Text("Skip for now") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page > PAGE_WELCOME && page < PAGE_SETUP) {
                    OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Back") }
                }
                val canFinish = page == PAGE_SETUP && permissions.notificationAccess
                Button(
                    onClick = {
                        if (page < PAGE_SETUP) page++ else if (canFinish) onComplete()
                    },
                    enabled = page < PAGE_SETUP || canFinish,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(when (page) {
                    PAGE_APP_RULES -> "Set up notifications"
                    PAGE_SETUP -> "Finish setup"
                    else -> "Next"
                }) }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Text("Taptation helps you stay away from distracting notifications and break the habit of checking them the moment they arrive.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth())
    IllustratedCard("Grow your meadow",
        "Leave distracting notifications for later and your meadow stays healthy. Calm stretches reveal more of the valley.",
        illustration = { WelcomeMeadow() }) {}
    IllustratedCard("Distracting taps can catch you",
        "Some taps on distracting notifications trigger a reminder and lower your meadow’s health.",
        illustration = { MiniNotification("$SAMPLE_APP · Distracting", rule = AppRule.AlwaysBait) }) {}
    IllustratedCard("Important ones are never penalized",
        "Family, work, rides or banking can be set to Always important.",
        illustration = { MiniNotification("WhatsApp · Always important", rule = AppRule.AlwaysImportant,
            title = "Mom: Can you call me back?", app = "WhatsApp") }) {}
}

/** The same valley meadow as Home, fully grown, to show where calm time leads; display only. */
@Composable
private fun WelcomeMeadow() {
    val effects = remember { MeadowEffects() }
    val world = remember {
        val grown = ValleyStage.Valley.minutes * MeadowState.MINUTE
        MeadowState(healthyMillis = grown, cleanMillis = grown, revealedStage = ValleyStage.Valley.ordinal)
    }
    MeadowCanvas(health = 100, world = world, effects = effects, scenery = MeadowScenery.Valley)
}

@Composable
private fun CriteriaPage(config: GameConfig?, repository: GameRepository) {
    val aiConfigured = (LocalContext.current.applicationContext as TaptationApplication).judges.isConfigured
    Text("Your answers guide automatic checks for apps set to Let us decide. You can change them later in Settings.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth())
    if (config == null) {
        Text("Loading your saved preferences…", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth())
    } else {
        IllustratedCard("Never interrupt",
            "Messages Taptation should always let through.",
            illustration = { MiniNotification("WhatsApp · Important", rule = AppRule.AlwaysImportant,
                title = "Mom: Can you call me back?", app = "WhatsApp") }) {
            CriteriaField(
                label = "What messages would you not want Taptation to interrupt?",
                placeholder = "Family messages, work shifts, delivery updates, appointment changes…",
                saved = config.essentialCriteria,
            ) { value -> repository.setCriteria(essential = value.trim()) }
        }
        IllustratedCard("Distracting",
            "Notifications that pull you away from what you were doing.",
            illustration = { MiniNotification("$SAMPLE_APP · Distracting", rule = AppRule.AlwaysBait) }) {
            CriteriaField(
                label = "What notifications would distract you?",
                placeholder = "Likes, follows, game rewards, sales, suggested posts…",
                saved = config.nonEssentialCriteria,
            ) { value -> repository.setCriteria(nonEssential = value.trim()) }
        }
    }
    Text(
            if (aiConfigured) {
                "Your answers are saved on this device. When AI judging runs, Taptation sends your preferences and notification details to Cloudflare. Notification details are not saved by Taptation."
            } else {
                "Your answers are saved on this device. Automatic AI checks are not available in this build, so apps set to Let us decide will not be penalized automatically."
            },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** What happens after a tap, in order: the check, the chance, the reminder over the app, the choice. */
@Composable
private fun CatchesPage(config: GameConfig) {
    // An example catch from full health, with the user's real difficulty and damage.
    val health = 100
    val chance = (config.difficulty.probability * 100).roundToInt()
    val healthAfter = (health - config.currentDamage).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth()) {
        TimelineStep(1, "You tap a notification") {
            MiniNotification("$SAMPLE_APP · Let us decide")
        }
        TimelineStep(2, "AI checks it") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VerdictTile("Important", "Opens normally", AppRule.AlwaysImportant.color(), Modifier.weight(1f))
                VerdictTile("Distracting", "$chance% catch chance on ${config.difficulty.name}",
                    AppRule.AlwaysBait.color(), Modifier.weight(1f), outlined = true)
            }
        }
        TimelineStep(3, "If you’re caught, a reminder pops up over the app") {
            OverlayIllustration(health = health, healthAfter = healthAfter)
            Text("Your meadow loses ${health - healthAfter} health.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TimelineStep(4, "You choose where to go", last = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniButton("Go to Taptation", filled = true, Modifier.weight(1f), caption = "No extra cost")
                MiniButton("Continue to the app", filled = false, Modifier.weight(1f),
                    caption = "−${GameEngine.CONTINUE_EXTRA_DAMAGE} more health")
            }
        }
    }
    Text("Apps set to Always important or Always distracting skip the AI check. You’ll choose those next.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth())
}

@Composable
private fun TimelineStep(number: Int, title: String, last: Boolean = false, content: @Composable () -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().drawBehind {
        if (!last) drawLine(line, Offset(14.dp.toPx(), 30.dp.toPx()), Offset(14.dp.toPx(), size.height - 2.dp.toPx()), 2.dp.toPx())
    }, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(28.dp)) {
            Box(Modifier.size(28.dp).border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .background(MaterialTheme.colorScheme.background, CircleShape), contentAlignment = Alignment.Center) {
                Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f).padding(top = 3.dp, bottom = if (last) 0.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun VerdictTile(title: String, body: String, accent: Color, modifier: Modifier, outlined: Boolean = false) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (outlined) accent else MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = accent)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The rules explained, then a few of the user's apps with the same picker as Settings. */
@Composable
private fun AppRulesPage(repository: GameRepository) {
    val app = LocalContext.current.applicationContext as TaptationApplication
    val picker by app.rulePicker.collectAsStateWithLifecycle()
    val error by repository.error.collectAsStateWithLifecycle()
    val labels = rememberAppLabels()
    val scope = rememberCoroutineScope()
    var showAll by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(setOf<String>()) }

    Text("Every app starts on Let us decide. Change any app now, or later in Settings.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth())
    RuleLegend(Modifier.fillMaxWidth())
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val suggested = picker.suggestions()
        val rows = when {
            showAll -> picker.search(search)
            suggested.isNotEmpty() -> suggested
            else -> picker.search("").take(RulePicker.ONBOARDING_SUGGESTIONS)
        }
        Text(when {
            showAll -> "All your apps"
            suggested.isNotEmpty() -> "Your social, video and game apps"
            else -> "Your apps"
        }, style = MaterialTheme.typography.titleMedium)
        if (showAll) OutlinedTextField(search, onValueChange = { search = it }, label = { Text("Find an app") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp))
        when {
            picker.apps == null && picker.error != null -> {
                Text("Couldn’t load installed apps.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = app.apps::reload) { Text("Try again") }
            }
            picker.apps == null -> Text("Loading your apps…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            rows.isEmpty() -> Text(if (showAll && search.isNotBlank()) "No matching apps. Try a different name."
                else "No apps with notifications turned on yet. You can set rules later in Settings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rows.forEach { row ->
            val pkg = row.app.packageName
            AppRuleRow(row.app, row.rule, labels, pkg !in saving) { choice ->
                saving = saving + pkg
                repository.setAppRule(pkg, choice).whenComplete { _, _ -> scope.launch { saving = saving - pkg } }
            }
        }
        error?.let { Text("Some changes could not be saved. Please try again.", color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall) }
        if (showAll || picker.apps.orEmpty().size > rows.size) {
            TextButton(onClick = { showAll = !showAll; search = "" }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (showAll) "Show fewer apps" else "Show all apps")
            }
        }
    }
}

@Composable
private fun SetupPage(notificationAccess: Boolean, overlayAccess: Boolean) {
    val context = LocalContext.current
    Text("One is required, one is recommended.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth())
    IllustratedCard("Notification access",
        "Required. Lets Taptation notice when you open a notification. Message text is not stored.",
        illustration = { TapIllustration() }) {
        AccessStatus(if (notificationAccess) "Enabled" else "Needed", notificationAccess)
        if (!notificationAccess) Button(
            onClick = { PermissionHelper.openNotificationAccessSettings(context) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Open notification access settings") }
    }
    IllustratedCard("Show reminders over other apps",
        "Recommended. The reminder appears on top of the app you opened. Without it, you get a notification instead.",
        illustration = { OverlayIllustration(compact = true) }) {
        AccessStatus(if (overlayAccess) "Enabled" else "Recommended", overlayAccess, required = false)
        if (!overlayAccess) OutlinedButton(
            onClick = { openOverlaySettings(context) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Allow display over other apps") }
    }
    Text("Come back here after changing a setting. Taptation checks again automatically.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth())
}

/** Status chip: enabled, needed (required and missing) or recommended (optional and missing). */
@Composable
private fun AccessStatus(label: String, enabled: Boolean, required: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val (background, foreground, dot) = when {
        enabled -> Triple(colors.primaryContainer, colors.onPrimaryContainer, colors.primary)
        required -> Triple(colors.errorContainer, colors.onErrorContainer, colors.error)
        else -> Triple(colors.tertiaryContainer, colors.onTertiaryContainer, colors.tertiary)
    }
    Surface(color = background, contentColor = foreground, shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

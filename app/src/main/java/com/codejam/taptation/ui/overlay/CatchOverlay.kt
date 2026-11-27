package com.codejam.taptation.ui.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.codejam.taptation.R
import com.codejam.taptation.logic.CatchDetails
import com.codejam.taptation.logic.CatchPrompt
import com.codejam.taptation.logic.meadow.MeadowCameo
import com.codejam.taptation.ui.caught.FishingSceneView
import com.codejam.taptation.service.CatchActions
import com.codejam.taptation.service.CatchSurface
import com.codejam.taptation.ui.common.TapExplanations
import com.codejam.taptation.ui.common.damageDropProgress
import com.codejam.taptation.ui.common.damageFlashMix
import com.codejam.taptation.ui.theme.*

/**
 * The caught prompt drawn over other apps (TYPE_APPLICATION_OVERLAY), with plain Views because
 * Compose needs an activity. Pure presentation: the backend (service/CatchFeedback) decides when to
 * show it and builds the [CatchPrompt]; buttons only call [CatchActions]. Main thread only.
 */
class CatchOverlay(private val context: Context,
    private val sceneFor: (String) -> MeadowCameo = { MeadowCameo.Fish },
    private val isDark: () -> Boolean = { true }) : CatchSurface {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val animators = mutableListOf<ValueAnimator>()
    private var overlay: View? = null

    override fun show(prompt: CatchPrompt, actions: CatchActions): Boolean {
        hide()
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val dark = isDark()
        // Same tokens as the Compose theme (ui/theme): ink ramp, bobber orange for the catch, meadow green for health.
        val background = (if (dark) DarkBackground else LightBackground).toArgb()
        val ink = (if (dark) DarkText else LightText).toArgb()
        val muted = (if (dark) DarkTextSecondary else LightTextSecondary).toArgb()
        val raised = (if (dark) DarkRaised else LightRaised).toArgb()
        val border = (if (dark) DarkLine else LightLine).toArgb()
        val health = (if (dark) HealthDark else HealthLight).toArgb()
        val catchColor = (if (dark) BobberDark else BobberLight).toArgb()
        val plex = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.ibm_plex_sans) ?: Typeface.DEFAULT
        val plexMedium = if (android.os.Build.VERSION.SDK_INT >= 28) Typeface.create(plex, 500, false) else plex
        val details = prompt.details
        val appLabel = prompt.appLabel
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            this.background = GradientDrawable().apply {
                setColor(background); cornerRadius = dp(24).toFloat(); setStroke(dp(1), border)
            }
            elevation = dp(8).toFloat()
        }
        panel.addView(TextView(context).apply {
            setText(R.string.caught_title); textSize = 30f; setTextColor(ink); typeface = plexMedium
        })
        if (details != null && appLabel != null) {
            panel.addView(TextView(context).apply {
                text = TapExplanations.catchReason(details.rule, appLabel, details.channel)
                textSize = 14f; setTextColor(muted); typeface = plex; setPadding(0, dp(8), 0, dp(4))
            })
        } else {
            panel.addView(TextView(context).apply {
                setText(R.string.caught_message_no_extra); textSize = 14f; setTextColor(muted); typeface = plex; setPadding(0, dp(8), 0, dp(4))
            })
        }
        panel.addView(FishingSceneView(context).apply {
            layoutParams=LinearLayout.LayoutParams(-1,dp(160))
            bind(prompt.catchId,sceneFor(prompt.catchId),appLabel,dark,prompt.animate)
        })
        if (details != null && details.damage > 0) {
            panel.addView(healthBlock(details, prompt.animate, ink, muted, health, catchColor, raised, plex, plexMedium) { dp(it) })
        }
        panel.addView(TextView(context).apply {setText(R.string.caught_destination_prompt);textSize=13f;setTextColor(muted);typeface=plex;setPadding(0,dp(12),0,dp(4))})
        fun choice(label: String, primary: Boolean, action: () -> Unit) = Button(context).apply {
            text = label; isAllCaps = false; textSize = 15f; typeface = plexMedium
            setTextColor(if (primary) background else ink)
            backgroundTintList = null; stateListAnimator = null
            this.background = GradientDrawable().apply {
                setColor(if (primary) ink else raised); cornerRadius = dp(12).toFloat()
                if (!primary) setStroke(dp(1), border)
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            minHeight = dp(48); setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { action() }
        }
        (panel.getChildAt(panel.childCount - 1)).let { last ->
            (last.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = dp(8)
        }
        panel.addView(choice(context.getString(R.string.caught_our_app), true) { actions.choose(ourApp = true) })
        val destination = appLabel ?: context.getString(R.string.caught_original_destination)
        val continueLabel = if (prompt.continueExtraDamage > 0)
            context.getString(R.string.caught_original_app_with_penalty, destination, prompt.continueExtraDamage)
        else context.getString(R.string.caught_original_app, destination)
        panel.addView(choice(continueLabel, false) { actions.choose(ourApp = false) }.apply {
            contentDescription=continueLabel
            if(prompt.continueExtraDamage>0){
                val label=context.getString(R.string.caught_original_app,destination)
                val caption=context.getString(R.string.caught_continue_cost,prompt.continueExtraDamage)
                text=android.text.SpannableString("$label\n$caption").apply{
                    setSpan(android.text.style.RelativeSizeSpan(.86f),label.length+1,length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(android.text.style.ForegroundColorSpan(muted),label.length+1,length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        })
        val params = WindowManager.LayoutParams(
            minOf(context.resources.displayMetrics.widthPixels - dp(32), dp(480)), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER; dimAmount = 0.45f }
        val scroll = android.widget.ScrollView(context).apply {
            isFillViewport = false
            addView(panel)
        }
        windows.addView(scroll, params)
        overlay = scroll
        return true
    }

    override fun hide() {
        animators.forEach { it.cancel() }
        animators.clear()
        overlay?.let { runCatching { windows.removeView(it) } }
        overlay = null
    }

    /** Already-applied health loss: "Health  90 / 100", a bar that shrinks, the cost in text. Static when motion is off. */
    private fun healthBlock(details: CatchDetails, animate: Boolean, ink: Int, muted: Int,
        health: Int, catchColor: Int, track: Int, plex: Typeface, plexMedium: Typeface, dp: (Int) -> Int,
    ): View {
        val lost = details.damage
        val mono = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.ibm_plex_mono_regular) ?: Typeface.MONOSPACE
        val number = TextView(context).apply { textSize = 14f; setTextColor(ink); typeface = mono }
        val floating = TextView(context).apply {
            text = "−$lost"; textSize = 14f; setTextColor(catchColor); typeface = mono; alpha = 0f
        }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(android.widget.ImageView(context).apply {
            setImageResource(R.drawable.ic_heart); setColorFilter(muted)
        }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) })
        row.addView(TextView(context).apply { text = "Health"; textSize = 15f; setTextColor(ink); typeface = plexMedium })
        row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(number)
        val frame = FrameLayout(context).apply {
            contentDescription = "Health ${details.healthAfter} out of 100, down $lost"
            addView(row, FrameLayout.LayoutParams(-1, -2))
            addView(floating, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = -dp(18) })
        }
        val bar = HealthBarView(context, track, health)
        fun setValue(k: Float) {
            val value = details.healthBefore + (details.healthAfter - details.healthBefore) * k
            number.text = "${Math.round(value)} / 100"; bar.fraction = value / 100f
        }
        setValue(0f)
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            fun run(duration: Long, delay: Long, interpolator: android.animation.TimeInterpolator, update: (Float) -> Unit) {
                animators += ValueAnimator.ofFloat(0f, 1f).apply {
                    this.duration = duration; startDelay = delay; this.interpolator = interpolator
                    addUpdateListener { update(it.animatedValue as Float) }
                    start()
                }
            }
            run(1150, 0, android.view.animation.LinearInterpolator()) {
                setValue(damageDropProgress(it))
                bar.fillColor = androidx.core.graphics.ColorUtils.blendARGB(health, catchColor, damageFlashMix(it))
            }
            run(900, 150, android.view.animation.LinearInterpolator()) {
                floating.alpha = (if (it < .25f) it / .25f else 1f - (it - .25f) / .75f).coerceIn(0f, 1f)
                floating.translationY = -dp(16) * it
            }
        } else {
            setValue(1f)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(4) }
            addView(frame, LinearLayout.LayoutParams(-1, -2))
            addView(bar, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(10) })
            addView(TextView(context).apply {
                text = "−$lost from this catch"; textSize = 13f; setTextColor(muted); typeface = plex; setPadding(0, dp(8), 0, 0)
            })
        }
    }
}

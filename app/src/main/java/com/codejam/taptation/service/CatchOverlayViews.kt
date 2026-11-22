package com.codejam.taptation.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** The Home tree in miniature for the native caught overlay; one leaf drops as [fall] goes 0 to 1. */
class LeafBadgeView(context: Context, private val leaves: Int, private val earth: Int) : View(context) {
    var fall: Float = 0f
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private fun r(l: Float, t: Float, rr: Float, b: Float) = rect.apply { set(l, t, rr, b) }
    private val leafPath = Path().apply {
        moveTo(0f, 0f); cubicTo(2f, -13f, 13f, -14f, 21f, -9f); cubicTo(21f, 0f, 12f, 3f, 0f, 0f); close()
    }

    override fun onDraw(canvas: Canvas) {
        val unit = minOf(width / 260f, height / 240f)
        canvas.save()
        canvas.translate((width - 260 * unit) / 2, (height - 240 * unit) / 2)
        canvas.scale(unit, unit)
        paint.style = Paint.Style.FILL
        paint.color = leaves; paint.alpha = 31
        canvas.drawOval(r(43f, 207f, 217f, 231f), paint)
        paint.color = earth
        canvas.drawRoundRect(r(123f, 116f, 137f, 215f), 7f, 7f, paint)
        paint.strokeWidth = 8f
        canvas.drawLine(130f, 154f, 101f, 112f, paint)
        canvas.drawLine(130f, 167f, 160f, 118f, paint)
        fun oval(oval: RectF, alpha: Int, degrees: Float = 0f, px: Float = 0f, py: Float = 0f) {
            paint.color = leaves; paint.alpha = alpha
            canvas.save(); canvas.rotate(degrees, px, py); canvas.drawOval(oval, paint); canvas.restore()
        }
        oval(r(50f, 48f, 144f, 156f), 222, -25f, 97f, 102f)
        oval(r(120f, 48f, 214f, 156f), 191, 25f, 167f, 103f)
        oval(r(90f, 22f, 186f, 140f), 255)
        oval(r(77f, 98f, 197f, 164f), 255)
        paint.color = earth; paint.alpha = 255
        canvas.save()
        canvas.translate(196f + 10f * fall, 96f + 114f * fall)
        canvas.rotate(160f * fall)
        canvas.scale(1.6f, 1.6f)
        canvas.drawPath(leafPath, paint)
        canvas.restore()
        canvas.restore()
    }
}

/** Rounded health bar for the native caught overlay; [fraction] is 0..1. */
class HealthBarView(context: Context, private val track: Int, fill: Int) : View(context) {
    var fillColor: Int = fill
        set(value) { field = value; invalidate() }
    var fraction: Float = 1f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val radius = height / 2f
        paint.color = track
        canvas.drawRoundRect(rect.apply { set(0f, 0f, width.toFloat(), height.toFloat()) }, radius, radius, paint)
        if (fraction > 0f) {
            paint.color = fillColor
            canvas.drawRoundRect(rect.apply { set(0f, 0f, maxOf(height.toFloat(), width * fraction), height.toFloat()) }, radius, radius, paint)
        }
    }
}

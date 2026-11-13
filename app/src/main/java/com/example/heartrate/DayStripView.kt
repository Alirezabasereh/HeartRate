package com.example.heartrate

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/**
 * Top strip of the Steps page: one bar per day, dotted target lines on the right,
 * and the selected day highlighted in a white pill with a dark date circle.
 */
class DayStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private var days: List<DayData> = emptyList()
    private var goal = 10_000
    private var selected = -1
    var onDaySelected: ((Int) -> Unit)? = null

    private val green = ContextCompat.getColor(context, R.color.step_green)
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.step_dash)
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * d
        pathEffect = DashPathEffect(floatArrayOf(2f * d, 4f * d), 0f)
    }
    private val axisText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.step_green_text)
        textSize = 12f * d
        textAlign = Paint.Align.RIGHT
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
    }
    private val grayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.bar_gray)
    }
    private val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
    }
    private val dayText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * d
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private val axisWidth = 58f * d

    fun setData(days: List<DayData>, goal: Int, selected: Int) {
        this.days = days
        this.goal = goal
        this.selected = selected
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (days.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val n = days.size
        val plotW = w - axisWidth
        val colW = plotW / n
        val baseY = h - 46f * d
        val topY = 18f * d
        val maxH = baseY - topY
        val scale = maxOf(goal, days.maxOf { it.steps }).toFloat()
        val nf = NumberFormat.getIntegerInstance(Locale.US)

        // Dotted target lines + labels
        for ((value, text) in listOf(goal to nf.format(goal), goal / 2 to nf.format(goal / 2))) {
            val y = baseY - value / scale * maxH
            canvas.drawLine(0f, y, w, y, dashPaint)
            canvas.drawText(text, w - 6f * d, y - 4f * d, axisText)
        }

        for (i in 0 until n) {
            val cx = colW * i + colW / 2f
            val steps = days[i].steps
            val barH = if (steps > 0) (steps / scale * maxH).coerceAtLeast(6f * d) else 0f
            val isSel = i == selected

            if (isSel) {
                rect.set(cx - 15f * d, 6f * d, cx + 15f * d, baseY + 12f * d)
                canvas.drawRoundRect(rect, 15f * d, 15f * d, pillPaint)
            }

            if (steps > 0) {
                rect.set(cx - 5f * d, baseY - barH, cx + 5f * d, baseY)
                canvas.drawRoundRect(rect, 5f * d, 5f * d, if (isSel) greenPaint else grayPaint)
            } else {
                canvas.drawCircle(cx, baseY - 3f * d, 3f * d, grayPaint)
            }

            val cal = Calendar.getInstance().apply { timeInMillis = days[i].dayStart }
            val label = cal.get(Calendar.DAY_OF_MONTH).toString()
            val labelY = h - 16f * d
            if (isSel) {
                canvas.drawCircle(cx, labelY - 5f * d, 15f * d, circlePaint)
                dayText.color = ContextCompat.getColor(context, R.color.white)
            } else {
                dayText.color = if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
                    ContextCompat.getColor(context, R.color.sunday_red)
                else ContextCompat.getColor(context, R.color.text_secondary)
            }
            canvas.drawText(label, cx, labelY, dayText)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (days.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val colW = (width - axisWidth) / days.size
                val idx = (event.x / colW).toInt()
                if (event.x < width - axisWidth && idx in days.indices) {
                    selected = idx
                    invalidate()
                    onDaySelected?.invoke(idx)
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()
}

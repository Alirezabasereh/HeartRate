package com.example.heartrate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/** 24 green bars (steps per hour) with 0 / 6 / 12 / 18 / 24(h) axis labels. */
class HourlyBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private var data = FloatArray(24)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.step_green)
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.bar_gray)
        strokeWidth = 1f * d
        style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(2f * d, 3f * d), 0f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 12f * d
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    fun setData(hourly: FloatArray) {
        data = hourly
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val labelH = 24f * d
        val baseY = h - labelH
        val slot = w / 24f
        val barW = slot * 0.5f
        val max = (data.maxOrNull() ?: 0f).coerceAtLeast(10f)

        canvas.drawLine(0f, baseY, w, baseY, axisPaint)

        for (i in 0 until 24) {
            val v = data[i]
            if (v < 0.5f) continue
            val bh = (v / max * (baseY - 4f * d)).coerceAtLeast(3f * d)
            val cx = slot * i + slot / 2f
            rect.set(cx - barW / 2f, baseY - bh, cx + barW / 2f, baseY)
            canvas.drawRoundRect(rect, barW / 3f, barW / 3f, barPaint)
        }

        val y = h - 4f * d
        for (hour in intArrayOf(0, 6, 12, 18)) {
            canvas.drawText("$hour", slot * hour + slot / 2f, y, textPaint)
        }
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("24(h)", w, y, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
    }
}

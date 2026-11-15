package com.example.heartrate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * نوار بازه‌ی کوچک مثل «Healthy range» در Samsung Health:
 * یک مسیر خاکستری، بخش رنگی ناحیه‌ی فعلی، و یک خط نشانگر روی مقدار.
 */
class RangeBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.range_track)
    }
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.range_marker)
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private var bpm = 0

    fun setBpm(value: Int) {
        bpm = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bpm <= 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f

        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, trackPaint)

        val zone = HeartZone.of(bpm)
        val (start, end) = when (zone) {
            HeartZone.LOW -> 0f to HeartZone.LOW_END
            HeartZone.NORMAL -> HeartZone.LOW_END to HeartZone.HIGH_START
            HeartZone.HIGH -> HeartZone.HIGH_START to 1f
        }
        zonePaint.color = ContextCompat.getColor(context, zone.colorRes)
        rect.set(start * w, 0f, end * w, h)
        canvas.drawRoundRect(rect, r, r, zonePaint)

        // خط نشانگر مقدار
        val x = (HeartZone.fraction(bpm) * w).coerceIn(markerPaint.strokeWidth, w - markerPaint.strokeWidth)
        canvas.drawLine(x, 0f, x, h, markerPaint)
    }
}

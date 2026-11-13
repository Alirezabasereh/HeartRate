package com.example.heartrate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/** Rounded horizontal progress bar (track + fill). */
class PillProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.range_track)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.step_green)
    }
    private val rect = RectF()
    private var progress = 0f

    fun setColors(track: Int, fill: Int) {
        trackPaint.color = track
        fillPaint.color = fill
        invalidate()
    }

    /** 0..1 (values above 1 are clamped). */
    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, trackPaint)
        if (progress > 0f) {
            rect.set(0f, 0f, (w * progress).coerceAtLeast(h), h)
            canvas.drawRoundRect(rect, r, r, fillPaint)
        }
    }
}

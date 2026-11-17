package com.example.heartrate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/** Small circular progress ring with the percentage written in the middle. */
class ProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * d
        color = ContextCompat.getColor(context, R.color.ring_track_coral)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * d
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.coral)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = 14f * d
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val oval = RectF()
    private var progress = 0f

    /** 0..1 */
    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = arcPaint.strokeWidth / 2f + 1f * d
        oval.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(oval, 0f, 360f, false, trackPaint)
        if (progress > 0f) {
            canvas.drawArc(oval, -90f, 360f * progress, false, arcPaint)
        }
        val y = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("${(progress * 100).toInt()}%", width / 2f, y, textPaint)
    }
}

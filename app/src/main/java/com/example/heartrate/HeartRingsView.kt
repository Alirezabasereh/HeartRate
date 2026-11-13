package com.example.heartrate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

/** Three concentric heart-shaped progress rings (steps / active minutes / calories). */
class HeartRingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val colors = intArrayOf(
        ContextCompat.getColor(context, R.color.step_green),
        ContextCompat.getColor(context, R.color.ring_blue),
        ContextCompat.getColor(context, R.color.ring_pink)
    )
    private val progress = floatArrayOf(0f, 0f, 0f)
    private val scales = floatArrayOf(0.95f, 0.69f, 0.43f)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val unit = Path()
    private val scaleMatrix = Matrix()
    private val ring = Path()
    private val segment = Path()

    init {
        // Unit heart (about -1..1), starting at the top notch and running clockwise.
        unit.moveTo(0f, -0.45f)
        unit.cubicTo(0.2f, -0.95f, 1.0f, -0.95f, 1.0f, -0.3f)
        unit.cubicTo(1.0f, 0.25f, 0.4f, 0.6f, 0f, 1.0f)
        unit.cubicTo(-0.4f, 0.6f, -1.0f, 0.25f, -1.0f, -0.3f)
        unit.cubicTo(-1.0f, -0.95f, -0.2f, -0.95f, 0f, -0.45f)
        unit.close()
    }

    /** Each value 0..1. */
    fun setProgress(steps: Float, minutes: Float, calories: Float) {
        progress[0] = steps.coerceIn(0f, 1f)
        progress[1] = minutes.coerceIn(0f, 1f)
        progress[2] = calories.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val half = min(width, height) / 2f * 0.88f
        val cx = width / 2f
        val cy = height / 2f + half * 0.04f
        val strokeW = half * 0.17f
        trackPaint.strokeWidth = strokeW
        progressPaint.strokeWidth = strokeW

        for (i in 0 until 3) {
            val s = half * scales[i] - strokeW / 2f
            scaleMatrix.reset()
            scaleMatrix.setScale(s, s)
            scaleMatrix.postTranslate(cx, cy)
            ring.reset()
            unit.transform(scaleMatrix, ring)

            trackPaint.color = Color.argb(60, Color.red(colors[i]), Color.green(colors[i]), Color.blue(colors[i]))
            canvas.drawPath(ring, trackPaint)

            if (progress[i] > 0f) {
                progressPaint.color = colors[i]
                if (progress[i] >= 0.999f) {
                    canvas.drawPath(ring, progressPaint)
                } else {
                    val pm = PathMeasure(ring, false)
                    segment.reset()
                    pm.getSegment(0f, pm.length * progress[i], segment, true)
                    canvas.drawPath(segment, progressPaint)
                }
            }
        }
    }
}

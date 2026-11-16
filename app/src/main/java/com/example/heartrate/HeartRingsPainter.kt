package com.example.heartrate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * Draws the three-ring heart: a fat, rounded heart with three thick concentric bands
 * (green = steps, blue = active time, pink = calories), thin white gaps between them and a
 * white heart-shaped hole in the middle. Each band shows its progress in the full colour on a
 * light tint of the same colour; at 100% it looks like the solid rings.
 * Used by the in-app view and by the widget bitmaps.
 */
class HeartRingsPainter(context: Context) {

    private val colors = intArrayOf(
        ContextCompat.getColor(context, R.color.step_green),
        ContextCompat.getColor(context, R.color.ring_blue),
        ContextCompat.getColor(context, R.color.ring_pink)
    )

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }
    private val unit = Path()
    private val scaleMatrix = Matrix()
    private val ring = Path()
    private val segment = Path()

    init {
        // Unit heart: x in -1..1, y from -0.80 (top of the lobes) to 0.86 (rounded bottom),
        // soft shallow notch at the top. Starts at the notch and runs clockwise.
        unit.moveTo(0f, -0.66f)
        unit.cubicTo(0.12f, -0.76f, 0.32f, -0.82f, 0.55f, -0.80f)
        unit.cubicTo(0.85f, -0.78f, 1.0f, -0.52f, 1.0f, -0.22f)
        unit.cubicTo(0.98f, 0.22f, 0.42f, 0.84f, 0f, 0.86f)
        unit.cubicTo(-0.42f, 0.84f, -0.98f, 0.22f, -1.0f, -0.22f)
        unit.cubicTo(-1.0f, -0.52f, -0.85f, -0.78f, -0.55f, -0.80f)
        unit.cubicTo(-0.32f, -0.82f, -0.12f, -0.76f, 0f, -0.66f)
        unit.close()
    }

    /** [progress] = steps, minutes, calories (each 0..1). */
    fun draw(canvas: Canvas, width: Float, height: Float, progress: FloatArray) {
        val half = min(width / 2f, height / 2f / 0.83f) * 0.94f
        val cx = width / 2f
        val cy = height / 2f
        val band = half * 0.16f      // thickness of each coloured band
        val gap = half * 0.03f       // white gap on each side between bands
        trackPaint.strokeWidth = band
        progressPaint.strokeWidth = band
        gapPaint.strokeWidth = band + 2f * gap

        for (i in 0 until 3) {
            // Centre line of band i. Horizontal and vertical steps are equal so the bands and
            // the white gaps keep the same thickness all around the heart.
            scaleMatrix.reset()
            scaleMatrix.setTranslate(0f, -0.03f)
            scaleMatrix.postScale(half * (0.92f - 0.19f * i), half * (0.75f - 0.19f * i) / 0.83f)
            scaleMatrix.postTranslate(cx, cy)
            ring.reset()
            unit.transform(scaleMatrix, ring)

            if (i > 0) canvas.drawPath(ring, gapPaint)

            val c = colors[i]
            trackPaint.color = Color.argb(60, Color.red(c), Color.green(c), Color.blue(c))
            canvas.drawPath(ring, trackPaint)

            val p = progress[i].coerceIn(0f, 1f)
            if (p > 0f) {
                progressPaint.color = c
                if (p >= 0.999f) {
                    canvas.drawPath(ring, progressPaint)
                } else {
                    val pm = PathMeasure(ring, false)
                    segment.reset()
                    pm.getSegment(0f, pm.length * p, segment, true)
                    canvas.drawPath(segment, progressPaint)
                }
            }
        }
    }
}

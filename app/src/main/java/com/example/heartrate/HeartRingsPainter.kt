package com.example.heartrate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.SweepGradient
import kotlin.math.min

/**
 * Draws the three-ring heart exactly like the reference artwork: three thick concentric bands
 * (green = steps, blue = active time, pink = calories) separated by thin white gaps, with a small
 * white heart in the middle. The shape comes from [HeartGeometry] (true offset curves), so the
 * bands and gaps have the same thickness everywhere.
 *
 * Each band shows its progress in the full colour (a soft gradient along the arc) on a light tint
 * of the same colour; at 100% it looks like the solid rings.
 * Used by the in-app view and by the widget bitmaps.
 */
class HeartRingsPainter(@Suppress("UNUSED_PARAMETER") context: Context) {

    // Gradient start/end colours of each band (sampled from the reference artwork)
    private val startColors = intArrayOf(
        Color.rgb(0, 212, 98), Color.rgb(76, 158, 255), Color.rgb(255, 81, 157)
    )
    private val endColors = intArrayOf(
        Color.rgb(34, 219, 132), Color.rgb(122, 183, 255), Color.rgb(255, 130, 188)
    )
    // Light tint shown for the part of the band that is not filled yet
    private val trackColors = intArrayOf(
        Color.rgb(207, 248, 230), Color.rgb(224, 240, 255), Color.rgb(253, 229, 244)
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

    /** Smooth closed paths of the three centre lines in unit coordinates. */
    private val unitPaths: Array<Path> = Array(3) { buildPath(HeartGeometry.LOOPS[it]) }
    private val scaleMatrix = Matrix()
    private val gradientMatrix = Matrix()
    private val ring = Path()
    private val segment = Path()

    private fun buildPath(pts: FloatArray): Path {
        val n = pts.size / 2
        val path = Path()
        // Quadratic curves through the midpoints of the measured points keep the outline smooth.
        fun x(i: Int) = pts[((i % n) + n) % n * 2]
        fun y(i: Int) = pts[((i % n) + n) % n * 2 + 1]
        path.moveTo(x(0), y(0))
        for (i in 1..n) {
            val mx = (x(i) + x(i + 1)) / 2f
            val my = (y(i) + y(i + 1)) / 2f
            path.quadTo(x(i), y(i), mx, my)
        }
        path.close()
        return path
    }

    /** [progress] = steps, minutes, calories (each 0..1). */
    fun draw(canvas: Canvas, width: Float, height: Float, progress: FloatArray) {
        val half = min(width / 2f, height / 2f / HeartGeometry.HALF_HEIGHT) * 0.97f
        val cx = width / 2f
        val cy = height / 2f

        for (i in 0 until 3) {
            scaleMatrix.reset()
            scaleMatrix.setScale(half, half)
            scaleMatrix.postTranslate(cx, cy)
            ring.reset()
            unitPaths[i].transform(scaleMatrix, ring)

            val stroke = HeartGeometry.BANDS[i] * half
            trackPaint.strokeWidth = stroke
            progressPaint.strokeWidth = stroke

            trackPaint.color = trackColors[i]
            canvas.drawPath(ring, trackPaint)

            val p = progress[i].coerceIn(0f, 1f)
            if (p > 0f) {
                // Soft colour sweep along the arc, starting at the top notch.
                val gradient = SweepGradient(cx, cy, intArrayOf(startColors[i], endColors[i]), null)
                gradientMatrix.setRotate(-90f, cx, cy)
                gradient.setLocalMatrix(gradientMatrix)
                progressPaint.shader = gradient

                if (p >= 0.999f) {
                    canvas.drawPath(ring, progressPaint)
                } else {
                    val pm = PathMeasure(ring, false)
                    segment.reset()
                    pm.getSegment(0f, pm.length * p, segment, true)
                    canvas.drawPath(segment, progressPaint)
                }
                progressPaint.shader = null
            }
        }
    }
}

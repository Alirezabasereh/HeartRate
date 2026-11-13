package com.example.heartrate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View

/**
 * یک View ساده که یک خط نوار قلب (ECG) را به‌صورت متحرک و تکرارشونده ترسیم می‌کند.
 * سرعت حرکت نوار با متد setSpeed(bpm) قابل تنظیم است تا با ضربان واقعی هماهنگ شود.
 */
class EcgView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val linePaint = Paint().apply {
        color = Color.parseColor("#FF6B8A")
        style = Paint.Style.STROKE
        strokeWidth = 7f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val patternWidth = 320f
    private var phase = 0f
    private var pxPerFrame = 6f
    private var running = false
    private val handler = Handler(Looper.getMainLooper())

    private val tickRunnable = object : Runnable {
        override fun run() {
            phase += pxPerFrame
            invalidate()
            if (running) handler.postDelayed(this, 16L)
        }
    }

    fun start() {
        if (running) return
        running = true
        handler.post(tickRunnable)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tickRunnable)
    }

    /** هرچه ضربان بالاتر باشد، نوار سریع‌تر حرکت می‌کند. */
    fun setSpeedFromBpm(bpm: Int) {
        val clamped = bpm.coerceIn(40, 180)
        pxPerFrame = 3f + (clamped - 40) / 140f * 9f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val midY = height / 2f
        val amp = height * 0.42f

        val path = Path()
        var startX = -(phase % patternWidth)
        var first = true

        while (startX < w) {
            val points = cyclePoints(startX, midY, amp)
            for ((px, py) in points) {
                if (first) {
                    path.moveTo(px, py)
                    first = false
                } else {
                    path.lineTo(px, py)
                }
            }
            startX += patternWidth
        }

        canvas.drawPath(path, linePaint)
    }

    private fun cyclePoints(startX: Float, midY: Float, amp: Float): List<Pair<Float, Float>> {
        val shape = listOf(
            0f to 0f,
            40f to 0f,
            55f to -amp * 0.12f,
            70f to 0f,
            100f to 0f,
            108f to amp * 0.15f,
            118f to -amp,
            128f to amp * 0.55f,
            140f to 0f,
            170f to 0f,
            190f to -amp * 0.22f,
            210f to 0f,
            320f to 0f
        )
        return shape.map { (dx, dy) -> (startX + dx) to (midY + dy) }
    }
}

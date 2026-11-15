package com.example.heartrate

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/** Three concentric heart-shaped progress rings (steps / active minutes / calories). */
class HeartRingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val painter = HeartRingsPainter(context)
    private val shown = floatArrayOf(0f, 0f, 0f)
    private var animator: ValueAnimator? = null

    /** Each value 0..1; the rings glide to the new values. */
    fun setProgress(steps: Float, minutes: Float, calories: Float) {
        val target = floatArrayOf(
            steps.coerceIn(0f, 1f), minutes.coerceIn(0f, 1f), calories.coerceIn(0f, 1f)
        )
        val from = shown.copyOf()
        if (from.contentEquals(target)) return
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedFraction
                for (i in 0 until 3) shown[i] = from[i] + (target[i] - from[i]) * f
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        painter.draw(canvas, width.toFloat(), height.toFloat(), shown)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}

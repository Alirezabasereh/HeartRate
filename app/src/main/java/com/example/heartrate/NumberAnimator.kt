package com.example.heartrate

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import kotlin.math.abs

/**
 * Shows a number that counts up (or down) one unit at a time instead of jumping.
 * E.g. 106 -> 110 shows 107, 108, 109, 110 quickly. Large jumps are capped so they stay fast.
 * The first value (and any call with animate = false) is shown immediately.
 */
class NumberAnimator(
    private val msPerUnit: Long = 70L,
    private val onValue: (Int) -> Unit
) {
    private var initialized = false
    private var shown = 0
    private var target = 0
    private var animator: ValueAnimator? = null

    /** The value currently on screen (mid-count while animating). */
    val current: Int get() = shown

    fun set(value: Int, animate: Boolean = true) {
        if (!initialized || !animate) {
            animator?.cancel()
            initialized = true
            shown = value
            target = value
            onValue(value)
            return
        }
        if (value == target) return
        target = value
        animator?.cancel()

        val from = shown
        animator = ValueAnimator.ofInt(from, value).apply {
            duration = (abs(value - from) * msPerUnit).coerceIn(150L, 900L)
            interpolator = LinearInterpolator()
            addUpdateListener {
                shown = it.animatedValue as Int
                onValue(shown)
            }
            start()
        }
    }
}

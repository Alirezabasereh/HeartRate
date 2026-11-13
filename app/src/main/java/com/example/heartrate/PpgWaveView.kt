package com.example.heartrate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Smooth gray wave scrolling left, like the Samsung Health measuring screen.
 * The sensor only reports a BPM number, so the waveform is synthetic (simple pulse
 * model); its beat rate follows the real BPM.
 */
class PpgWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.wave_gray)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val sampleCount = 260
    private val samples = FloatArray(sampleCount)
    private var head = 0

    private val sampleMs = 1000f / 60f
    private var accMs = 0f
    private var lastNanos = 0L

    private var bpm = 72f
    private var beatPhase = 0f
    private var beatAmp = 1f
    private var driftPhase = 0f
    private var amp = IDLE_AMP
    private var targetAmp = IDLE_AMP

    private var running = false
    private var primed = false
    private val random = java.util.Random()
    private val path = Path()

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            val dtMs = ((now - lastNanos) / 1_000_000f).coerceIn(0f, 100f)
            lastNanos = now
            accMs += dtMs
            while (accMs >= sampleMs) {
                pushSample()
                accMs -= sampleMs
            }
            invalidate()
            postOnAnimation(this)
        }
    }

    fun start() {
        if (running) return
        if (!primed) {
            repeat(sampleCount) { pushSample() }
            primed = true
        }
        running = true
        lastNanos = System.nanoTime()
        postOnAnimation(frame)
    }

    fun stop() {
        running = false
        removeCallbacks(frame)
    }

    /** ضربان فعلی؛ سرعت ضرب‌های موج را تعیین می‌کند. */
    fun setBpm(value: Int) {
        bpm = value.toFloat().coerceIn(40f, 180f)
    }

    /** وقتی انگشت روی سنسور نیست موج تقریباً صاف می‌شود. */
    fun setSignalActive(active: Boolean) {
        targetAmp = if (active) 1f else IDLE_AMP
    }

    private fun pushSample() {
        amp += (targetAmp - amp) * 0.03f

        beatPhase += bpm / 60f * (sampleMs / 1000f)
        if (beatPhase >= 1f) {
            beatPhase -= 1f
            beatAmp = 0.75f + random.nextFloat() * 0.25f // قد هر ضرب کمی متفاوت
        }
        driftPhase += (sampleMs / 1000f) * 0.35f

        val p = beatPhase
        val pulse = skewGauss(p, 0.17f, 0.045f, 0.085f) +
                0.38f * skewGauss(p, 0.46f, 0.06f, 0.08f) // قله‌ی اصلی + قله‌ی کوچک دوم
        val drift = 0.12f * sin(driftPhase * 2f * PI.toFloat())

        samples[head] = (pulse * beatAmp + drift) * amp
        head = (head + 1) % sampleCount
    }

    private fun skewGauss(x: Float, center: Float, sigmaLeft: Float, sigmaRight: Float): Float {
        val d = x - center
        val s = if (d < 0f) sigmaLeft else sigmaRight
        return exp(-(d * d) / (2f * s * s))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val baseY = h * 0.78f
        val scaleY = h * 0.60f
        val stepX = w / (sampleCount - 1)

        path.reset()
        var prevX = 0f
        var prevY = baseY - samples[head] * scaleY
        path.moveTo(prevX, prevY)
        for (i in 1 until sampleCount) {
            val x = i * stepX
            val y = baseY - samples[(head + i) % sampleCount] * scaleY
            path.quadTo(prevX, prevY, (prevX + x) / 2f, (prevY + y) / 2f)
            prevX = x
            prevY = y
        }
        path.lineTo(prevX, prevY)
        canvas.drawPath(path, linePaint)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val IDLE_AMP = 0.12f
    }
}

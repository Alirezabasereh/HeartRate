package com.example.heartrate

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.animation.PropertyValuesHolder
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.abs

/**
 * Flow:
 *  1. Wait (no time limit) until the finger is on the sensor and the first valid value arrives.
 *  2. Count 5 seconds from that first value. The ring advances in 20% steps (one per second);
 *     the number inside counts up quickly to each step and the ring follows the same speed.
 *     The live BPM appears together with the ring, from the first reading.
 *  3. If the finger is lifted at any point, the count is cancelled and — once the finger is back —
 *     starts again from zero.
 *  4. When the 5 seconds complete, save the median and open that record's page.
 */
class MeasurementActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var listening = false

    private lateinit var heartIcon: ImageView
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var progressRow: View
    private lateinit var progressRing: ProgressRingView
    private lateinit var liveBpmText: TextView
    private lateinit var waveView: PpgWaveView

    private var ringAnimator: ValueAnimator? = null
    private var ringValue = 0f
    private var currentStep = 0
    private var pulseAnimator: ObjectAnimator? = null
    private var pulseBpm = 0
    private var countDownTimer: CountDownTimer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val readings = mutableListOf<Int>()
    private var measuring = false
    private var finished = false
    private var lastValidReadingAt = 0L

    /** Silence from the sensor for this long while measuring is treated as "finger lifted". */
    private val watchdog = object : Runnable {
        override fun run() {
            if (!measuring || finished) return
            if (SystemClock.elapsedRealtime() - lastValidReadingAt > NO_SIGNAL_TIMEOUT_MS) {
                onFingerLost()
            } else {
                handler.postDelayed(this, 500L)
            }
        }
    }

    companion object {
        private const val REQUEST_BODY_SENSORS = 100
        private const val MEASUREMENT_DURATION_MS = 5_000L
        private const val NO_SIGNAL_TIMEOUT_MS = 6_000L
        private const val STEP_COUNT = 5            // 5 steps of 20%
        private const val STEP_MS = 1_000L          // one step per second
        private const val RING_COUNT_MS = 450L      // how fast the number/ring counts to the next step
        private const val MIN_VALID_BPM = 30
        private const val MAX_VALID_BPM = 220
        private const val TIPS_PREFS = "tips"
        private const val KEY_TIP_INDEX = "tip_index"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_measurement)

        heartIcon = findViewById(R.id.heartIcon)
        titleText = findViewById(R.id.titleText)
        subtitleText = findViewById(R.id.subtitleText)
        progressRow = findViewById(R.id.progressRow)
        progressRing = findViewById(R.id.progressRing)
        liveBpmText = findViewById(R.id.liveBpmText)
        waveView = findViewById(R.id.waveView)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        showNextTip()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        heartRateSensor = findHeartRateSensor()

        waveView.setSignalActive(false)
        waveView.start()
        startPulseAnimation(800L)

        if (heartRateSensor == null) {
            showFailure(R.string.no_sensor)
            return
        }
        checkPermissionAndStart()
    }

    /** A different "Did you know?" fact every time the screen opens. */
    private fun showNextTip() {
        val tips = resources.getStringArray(R.array.heart_tips)
        if (tips.isEmpty()) return
        val prefs = getSharedPreferences(TIPS_PREFS, MODE_PRIVATE)
        val index = if (prefs.contains(KEY_TIP_INDEX)) {
            (prefs.getInt(KEY_TIP_INDEX, 0) + 1) % tips.size
        } else {
            java.util.Random().nextInt(tips.size)
        }
        prefs.edit().putInt(KEY_TIP_INDEX, index).apply()
        findViewById<TextView>(R.id.tipText).text = tips[index]
    }

    private fun findHeartRateSensor(): Sensor? {
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let { return it }
        return sensorManager.getSensorList(Sensor.TYPE_ALL).firstOrNull {
            val name = it.name.lowercase(Locale.US)
            val type = it.stringType.lowercase(Locale.US)
            name.contains("heart") || name.contains("hrm") ||
                    type.contains("heart") || type.contains("hrm")
        }
    }

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.BODY_SENSORS), REQUEST_BODY_SENSORS
            )
        } else {
            startListening()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BODY_SENSORS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                showFailure(R.string.no_permission)
            }
        }
    }

    private fun startListening() {
        if (listening || finished) return
        listening = sensorManager.registerListener(
            this, heartRateSensor, SensorManager.SENSOR_DELAY_FASTEST
        )
        if (listening) subtitleText.setText(R.string.place_finger) else showFailure(R.string.no_sensor)
    }

    private fun stopListening() {
        if (listening) {
            sensorManager.unregisterListener(this)
            listening = false
        }
    }

    // ---------- Measurement state ----------

    /** First valid reading after waiting (or after the finger was lifted): count 5 s from now. */
    private fun startMeasuring() {
        measuring = true
        subtitleText.setText(R.string.finger_detected)

        ringAnimator?.cancel()
        ringValue = 0f
        currentStep = 0
        progressRing.setProgress(0f)
        updateLiveBpm()
        // Ring and live BPM fade in inside the space that was reserved for them.
        progressRow.animate().cancel()
        progressRow.alpha = 0f
        progressRow.visibility = View.VISIBLE
        progressRow.animate().alpha(1f).setDuration(250L).start()

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(MEASUREMENT_DURATION_MS, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsed = MEASUREMENT_DURATION_MS - millisUntilFinished
                val step = minOf(STEP_COUNT, (elapsed / STEP_MS).toInt() + 1)
                if (step > currentStep) {
                    currentStep = step
                    animateRingTo(step.toFloat() / STEP_COUNT)
                }
            }

            override fun onFinish() {
                if (currentStep < STEP_COUNT) animateRingTo(1f)
                finishMeasurement()
            }
        }.start()

        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, 500L)
    }

    /** Number and ring advance together, quickly, from the current value to [target]. */
    private fun animateRingTo(target: Float) {
        ringAnimator?.cancel()
        ringAnimator = ValueAnimator.ofFloat(ringValue, target).apply {
            duration = RING_COUNT_MS
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener {
                ringValue = it.animatedValue as Float
                progressRing.setProgress(ringValue)
            }
            start()
        }
    }

    private fun updateLiveBpm() {
        currentMedian()?.let { liveBpmText.text = "$it" }
    }

    /** Finger lifted: cancel the count, discard what was read, and ask to put the finger back. */
    private fun onFingerLost() {
        if (!measuring || finished) return
        measuring = false
        countDownTimer?.cancel()
        handler.removeCallbacks(watchdog)
        ringAnimator?.cancel()
        readings.clear()
        ringValue = 0f
        currentStep = 0
        progressRing.setProgress(0f)
        liveBpmText.setText(R.string.live_bpm_placeholder)
        progressRow.animate().cancel()
        progressRow.visibility = View.INVISIBLE // space stays reserved, nothing shifts
        subtitleText.setText(R.string.hold_steady)
        waveView.setSignalActive(false)
    }

    private fun currentMedian(): Int? {
        if (readings.isEmpty()) return null
        val sorted = readings.sorted()
        return sorted[sorted.size / 2]
    }

    private fun showFailure(messageRes: Int) {
        waveView.stop()
        pulseAnimator?.cancel()
        titleText.setText(R.string.measure_failed)
        subtitleText.setText(messageRes)
    }

    private fun finishMeasurement() {
        if (finished) return
        finished = true
        measuring = false
        handler.removeCallbacks(watchdog)

        stopListening()
        ringAnimator?.cancel()
        waveView.stop()
        pulseAnimator?.cancel()

        val finalBpm = currentMedian() ?: 0
        if (finalBpm > 0) {
            val timestamp = System.currentTimeMillis()
            HeartRateStorage.saveRecord(this, finalBpm, timestamp)
            startActivity(RecordDetailActivity.intent(this, HeartRecord(finalBpm, timestamp)))
        }
        finish()
    }

    // ---------- Sensor callbacks ----------

    override fun onSensorChanged(event: SensorEvent?) {
        if (finished) return
        event ?: return
        val bpm = event.values.getOrNull(0)?.toInt() ?: return

        // No contact reported by the sensor.
        if (event.accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT || bpm <= 0) {
            if (measuring) onFingerLost()
            return
        }
        if (bpm < MIN_VALID_BPM || bpm > MAX_VALID_BPM) return

        lastValidReadingAt = SystemClock.elapsedRealtime()
        readings.add(bpm)
        if (!measuring) startMeasuring() else updateLiveBpm()

        waveView.setSignalActive(true)
        waveView.setBpm(bpm)
        updatePulseAnimation(bpm)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (finished) return
        when {
            accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT -> if (measuring) onFingerLost()
            // Sensor reports a usable signal: the finger is on it, so change the prompt right away.
            accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_LOW -> subtitleText.setText(R.string.finger_detected)
        }
    }

    // ---------- Heart icon animation ----------

    private fun startPulseAnimation(periodMs: Long) {
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            heartIcon,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.15f, 1f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.15f, 1f)
        ).apply {
            duration = periodMs
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun updatePulseAnimation(bpm: Int) {
        if (abs(bpm - pulseBpm) < 3) return
        pulseBpm = bpm
        startPulseAnimation((60_000L / bpm).coerceIn(300L, 1500L))
    }

    /** Leaving the screen mid-measurement cancels it so the sensor isn't left running. */
    override fun onStop() {
        super.onStop()
        if (!finished && !isChangingConfigurations) {
            countDownTimer?.cancel()
            handler.removeCallbacks(watchdog)
            stopListening()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        countDownTimer?.cancel()
        handler.removeCallbacksAndMessages(null)
        ringAnimator?.cancel()
        pulseAnimator?.cancel()
        waveView.stop()
    }
}

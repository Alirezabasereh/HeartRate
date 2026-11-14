package com.example.heartrate

import android.Manifest
import android.animation.ObjectAnimator
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
 *  2. Count 10 seconds from that first value, showing a progress ring and (from 40%) the live BPM.
 *  3. If the finger is lifted at any point, the count is cancelled and — once the finger is back —
 *     starts again from zero.
 *  4. When the 10 seconds complete, save the median and open that record's page.
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
        private const val MEASUREMENT_DURATION_MS = 10_000L
        private const val NO_SIGNAL_TIMEOUT_MS = 6_000L
        private const val LIVE_BPM_FROM_PROGRESS = 0.4f
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

    /** First valid reading after waiting (or after the finger was lifted): count 10 s from now. */
    private fun startMeasuring() {
        measuring = true
        subtitleText.setText(R.string.stay_still)
        progressRow.visibility = View.VISIBLE
        progressRing.setProgress(0f)
        liveBpmText.setText(R.string.live_bpm_placeholder)

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(MEASUREMENT_DURATION_MS, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val progress = 1f - millisUntilFinished / MEASUREMENT_DURATION_MS.toFloat()
                progressRing.setProgress(progress)
                if (progress >= LIVE_BPM_FROM_PROGRESS) {
                    currentMedian()?.let { liveBpmText.text = "$it" }
                }
            }

            override fun onFinish() {
                progressRing.setProgress(1f)
                finishMeasurement()
            }
        }.start()

        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, 500L)
    }

    /** Finger lifted: cancel the count, discard what was read, and ask to put the finger back. */
    private fun onFingerLost() {
        if (!measuring || finished) return
        measuring = false
        countDownTimer?.cancel()
        handler.removeCallbacks(watchdog)
        readings.clear()
        progressRing.setProgress(0f)
        liveBpmText.setText(R.string.live_bpm_placeholder)
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
        if (!measuring) startMeasuring()

        waveView.setSignalActive(true)
        waveView.setBpm(bpm)
        updatePulseAnimation(bpm)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (finished) return
        if (accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT) {
            if (measuring) onFingerLost()
        } else if (measuring) {
            subtitleText.setText(R.string.stay_still)
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
        pulseAnimator?.cancel()
        waveView.stop()
    }
}

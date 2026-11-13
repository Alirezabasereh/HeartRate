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
 *  1. Wait (no time limit) until the sensor delivers its first valid heart-rate value.
 *  2. From that first value, keep reading for exactly 10 seconds.
 *  3. Save the median of the readings and open that record's page.
 */
class MeasurementActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var listening = false

    private lateinit var heartIcon: ImageView
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var waveView: PpgWaveView

    private var pulseAnimator: ObjectAnimator? = null
    private var pulseBpm = 0
    private var countDownTimer: CountDownTimer? = null
    private val readings = mutableListOf<Int>()
    private var gotFirstReading = false
    private var measurementFinished = false

    companion object {
        private const val REQUEST_BODY_SENSORS = 100
        private const val MEASUREMENT_DURATION_MS = 10_000L
        private const val MIN_VALID_BPM = 30
        private const val MAX_VALID_BPM = 220
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_measurement)

        heartIcon = findViewById(R.id.heartIcon)
        titleText = findViewById(R.id.titleText)
        subtitleText = findViewById(R.id.subtitleText)
        waveView = findViewById(R.id.waveView)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

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

    /**
     * The standard heart-rate sensor first; if the device doesn't expose it under
     * TYPE_HEART_RATE, look for any sensor whose name/type mentions heart rate.
     */
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
                this,
                arrayOf(Manifest.permission.BODY_SENSORS),
                REQUEST_BODY_SENSORS
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

    /** Starts listening only; the 10-second timer starts at the first valid reading. */
    private fun startListening() {
        if (listening || measurementFinished) return
        listening = sensorManager.registerListener(
            this, heartRateSensor, SensorManager.SENSOR_DELAY_FASTEST
        )
        if (listening) {
            subtitleText.setText(R.string.place_finger)
        } else {
            showFailure(R.string.no_sensor)
        }
    }

    private fun stopListening() {
        if (listening) {
            sensorManager.unregisterListener(this)
            listening = false
        }
    }

    private fun startMeasurementTimer() {
        countDownTimer = object : CountDownTimer(MEASUREMENT_DURATION_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                finishMeasurement()
            }
        }.start()
    }

    private fun showFailure(messageRes: Int) {
        waveView.stop()
        pulseAnimator?.cancel()
        titleText.setText(R.string.measure_failed)
        subtitleText.setText(messageRes)
    }

    private fun finishMeasurement() {
        if (measurementFinished) return
        measurementFinished = true

        stopListening()
        waveView.stop()
        pulseAnimator?.cancel()

        // Median is robust against a few noisy values at the start/end.
        val finalBpm = if (readings.isNotEmpty()) {
            val sorted = readings.sorted()
            sorted[sorted.size / 2]
        } else 0

        if (finalBpm > 0) {
            val timestamp = System.currentTimeMillis()
            HeartRateStorage.saveRecord(this, finalBpm, timestamp)
            // Land on the new record's page; Back from there returns to the main screen.
            startActivity(RecordDetailActivity.intent(this, HeartRecord(finalBpm, timestamp)))
        }
        finish()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (measurementFinished) return
        event ?: return
        if (event.accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT) return

        val bpm = event.values.getOrNull(0)?.toInt() ?: return
        if (bpm < MIN_VALID_BPM || bpm > MAX_VALID_BPM) return

        readings.add(bpm)

        if (!gotFirstReading) {
            gotFirstReading = true
            subtitleText.setText(R.string.stay_still)
            startMeasurementTimer() // 10 seconds counted from the first detected beat
        }

        waveView.setSignalActive(true)
        waveView.setBpm(bpm)
        updatePulseAnimation(bpm)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (measurementFinished) return
        when (accuracy) {
            SensorManager.SENSOR_STATUS_NO_CONTACT,
            SensorManager.SENSOR_STATUS_UNRELIABLE -> {
                subtitleText.setText(R.string.hold_steady)
                waveView.setSignalActive(false)
            }
            else -> if (gotFirstReading) subtitleText.setText(R.string.stay_still)
        }
    }

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
        if (!measurementFinished && !isChangingConfigurations) {
            countDownTimer?.cancel()
            stopListening()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        countDownTimer?.cancel()
        pulseAnimator?.cancel()
        waveView.stop()
    }
}

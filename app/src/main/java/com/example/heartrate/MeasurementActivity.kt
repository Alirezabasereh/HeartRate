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
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs

class MeasurementActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null

    private lateinit var heartIcon: ImageView
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var waveView: PpgWaveView

    private var pulseAnimator: ObjectAnimator? = null
    private var pulseBpm = 0
    private var countDownTimer: CountDownTimer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val readings = mutableListOf<Int>()
    private var gotFirstReading = false
    private var measurementFinished = false

    companion object {
        private const val REQUEST_BODY_SENSORS = 100
        private const val MEASUREMENT_DURATION_MS = 10_000L
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
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        waveView.setSignalActive(false)
        waveView.start()
        startPulseAnimation(800L)

        if (heartRateSensor == null) {
            showFailure(R.string.no_sensor)
            return
        }

        checkPermissionAndStart()
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
            beginMeasurement()
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
                beginMeasurement()
            } else {
                showFailure(R.string.no_permission)
            }
        }
    }

    private fun beginMeasurement() {
        sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
        subtitleText.setText(R.string.place_finger)

        countDownTimer = object : CountDownTimer(MEASUREMENT_DURATION_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                finishMeasurement()
            }
        }.start()
    }

    /** خطا: موج و ضربان قلب متوقف می‌شوند و پیام در زیرعنوان نشان داده می‌شود. */
    private fun showFailure(messageRes: Int) {
        waveView.stop()
        pulseAnimator?.cancel()
        titleText.setText(R.string.measure_failed)
        subtitleText.setText(messageRes)
    }

    private fun finishMeasurement() {
        if (measurementFinished) return
        measurementFinished = true

        sensorManager.unregisterListener(this)
        waveView.stop()
        pulseAnimator?.cancel()

        val finalBpm = if (readings.isNotEmpty()) readings.average().toInt() else 0

        if (finalBpm > 0) {
            titleText.text = "$finalBpm ${getString(R.string.bpm_unit)}"
            subtitleText.setText(R.string.record_saved)
            HeartRateStorage.saveRecord(this, finalBpm, System.currentTimeMillis())
        } else {
            titleText.setText(R.string.no_data)
            subtitleText.setText(R.string.try_again)
        }

        handler.postDelayed({ finish() }, 2000)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (measurementFinished) return
        event ?: return
        val bpm = event.values.getOrNull(0)?.toInt() ?: return

        if (bpm > 0) {
            readings.add(bpm)
            if (!gotFirstReading) {
                gotFirstReading = true
                subtitleText.setText(R.string.stay_still)
            }
            waveView.setSignalActive(true)
            waveView.setBpm(bpm)
            updatePulseAnimation(bpm)
        }
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

    /** انیمیشن قلب فقط وقتی ضربان چند واحد عوض شده دوباره راه می‌افتد (برای جلوگیری از پرش). */
    private fun updatePulseAnimation(bpm: Int) {
        if (abs(bpm - pulseBpm) < 3) return
        pulseBpm = bpm
        startPulseAnimation((60_000L / bpm).coerceIn(300L, 1500L))
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        countDownTimer?.cancel()
        pulseAnimator?.cancel()
        handler.removeCallbacksAndMessages(null)
        waveView.stop()
    }
}

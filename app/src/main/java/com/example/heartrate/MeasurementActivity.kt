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

class MeasurementActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null

    private lateinit var heartIcon: ImageView
    private lateinit var bpmText: TextView
    private lateinit var countdownText: TextView
    private lateinit var statusText: TextView
    private lateinit var ecgView: EcgView

    private var pulseAnimator: ObjectAnimator? = null
    private var countDownTimer: CountDownTimer? = null
    private val readings = mutableListOf<Int>()
    private var measurementFinished = false

    companion object {
        private const val REQUEST_BODY_SENSORS = 100
        private const val MEASUREMENT_DURATION_MS = 10_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_measurement)

        heartIcon = findViewById(R.id.heartIcon)
        bpmText = findViewById(R.id.bpmText)
        countdownText = findViewById(R.id.countdownText)
        statusText = findViewById(R.id.statusText)
        ecgView = findViewById(R.id.ecgView)

        findViewById<View>(R.id.cancelButton).setOnClickListener { finish() }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        ecgView.start()
        startPulseAnimation(800L)

        if (heartRateSensor == null) {
            statusText.text = "سنسور ضربان قلب روی این دستگاه پیدا نشد."
            countdownText.text = "ناموفق"
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
                statusText.text = "بدون اجازه‌ی دسترسی به سنسور بدن، اندازه‌گیری ممکن نیست."
                countdownText.text = "ناموفق"
            }
        }
    }

    private fun beginMeasurement() {
        sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
        statusText.text = "انگشت خود را روی سنسور (کنار دوربین پشت) نگه دارید"

        countDownTimer = object : CountDownTimer(MEASUREMENT_DURATION_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                countdownText.text = "$secondsLeft ثانیه مانده"
            }

            override fun onFinish() {
                finishMeasurement()
            }
        }.start()
    }

    private fun finishMeasurement() {
        if (measurementFinished) return
        measurementFinished = true

        sensorManager.unregisterListener(this)
        ecgView.stop()
        pulseAnimator?.cancel()

        val finalBpm = if (readings.isNotEmpty()) readings.average().toInt() else 0

        if (finalBpm > 0) {
            bpmText.text = "$finalBpm"
            countdownText.text = "تمام شد ✓"
            statusText.text = "رکورد با موفقیت ثبت شد"
            HeartRateStorage.saveRecord(this, finalBpm, System.currentTimeMillis())
        } else {
            countdownText.text = "داده‌ی کافی دریافت نشد"
            statusText.text = "لطفاً دوباره امتحان کنید"
        }

        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 2000)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (measurementFinished) return
        event ?: return
        val bpm = event.values.getOrNull(0)?.toInt() ?: return

        if (bpm > 0) {
            readings.add(bpm)
            bpmText.text = "$bpm"
            ecgView.setSpeedFromBpm(bpm)
            restartPulseAnimation(bpm)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (measurementFinished) return
        statusText.text = when (accuracy) {
            SensorManager.SENSOR_STATUS_UNRELIABLE -> "دقت نامعتبر — انگشت را ثابت نگه دارید"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "دقت: پایین"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "دقت: متوسط"
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "دقت: بالا"
            else -> statusText.text.toString()
        }
    }

    private fun startPulseAnimation(periodMs: Long) {
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            heartIcon,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.25f, 1f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.25f, 1f)
        ).apply {
            duration = periodMs
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun restartPulseAnimation(bpm: Int) {
        val periodMs = (60_000L / bpm).coerceIn(300L, 1500L)
        startPulseAnimation(periodMs)
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        countDownTimer?.cancel()
        pulseAnimator?.cancel()
        ecgView.stop()
    }
}

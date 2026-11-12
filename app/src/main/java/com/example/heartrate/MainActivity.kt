package com.example.heartrate

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null

    private lateinit var bpmText: TextView
    private lateinit var statusText: TextView
    private lateinit var accuracyText: TextView

    companion object {
        private const val REQUEST_BODY_SENSORS = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bpmText = findViewById(R.id.bpmText)
        statusText = findViewById(R.id.statusText)
        accuracyText = findViewById(R.id.accuracyText)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // این همان سنسور فیزیکی HRM روی S7 Edge است که از طریق API استاندارد
        // اندروید در دسترس قرار می‌گیرد (نیازی به کتابخانه اختصاصی سامسونگ نیست)
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if (heartRateSensor == null) {
            statusText.text = "سنسور ضربان قلب روی این دستگاه پیدا نشد."
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
            statusText.text = "انگشت خود را روی سنسور (کنار دوربین پشت) نگه دارید..."
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
                statusText.text = "انگشت خود را روی سنسور (کنار دوربین پشت) نگه دارید..."
            } else {
                statusText.text = "بدون اجازه دسترسی به سنسور بدن، اندازه‌گیری ممکن نیست."
            }
        }
    }

    override fun onResume() {
        super.onResume()
        heartRateSensor?.let {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                // SENSOR_DELAY_NORMAL برای این نوع سنسور کافی است؛ خودِ سخت‌افزار
                // با نرخ داخلی خودش داده تولید می‌کند
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val bpm = event.values.getOrNull(0) ?: return

        if (bpm <= 0f) {
            bpmText.text = "--"
            statusText.text = "در حال خواندن... انگشت خود را ثابت نگه دارید"
        } else {
            bpmText.text = bpm.toInt().toString()
            statusText.text = "ضربان قلب زنده"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        accuracyText.text = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "دقت: بالا"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "دقت: متوسط"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "دقت: پایین"
            SensorManager.SENSOR_STATUS_ACCURACY_UNRELIABLE -> "دقت: نامعتبر (انگشت را ثابت کنید)"
            else -> "دقت: نامشخص"
        }
    }
}

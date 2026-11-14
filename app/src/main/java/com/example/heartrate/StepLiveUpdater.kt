package com.example.heartrate

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

/**
 * Keeps a screen's step numbers live while it is visible:
 *  - listens to the step counter and records snapshots (throttled),
 *  - fires [onChange] on new data,
 *  - at midnight records a last snapshot for the old day, then calls [onNewDay] and [onChange]
 *    so the screen switches to the new (reset) day even if it stayed open.
 */
class StepLiveUpdater(
    private val context: Context,
    private val onNewDay: () -> Unit = {},
    private val onChange: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val handler = Handler(Looper.getMainLooper())
    private var latest = -1f
    private var lastRecordMs = 0L

    private val midnight = object : Runnable {
        override fun run() {
            if (latest >= 0f) StepTracker.record(context, latest)
            onNewDay()
            onChange()
            scheduleMidnight()
        }
    }

    fun start() {
        latest = -1f
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        scheduleMidnight()
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(midnight)
        if (latest >= 0f) StepTracker.record(context, latest)
        latest = -1f
    }

    private fun scheduleMidnight() {
        handler.removeCallbacks(midnight)
        handler.postDelayed(midnight, StepTracker.millisUntilNextDay() + 1_500L)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val v = event?.values?.getOrNull(0) ?: return
        latest = v
        val now = System.currentTimeMillis()
        if (now - lastRecordMs > 2_000L) {
            lastRecordMs = now
            StepTracker.record(context, v, now)
            onChange()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

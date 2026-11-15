package com.example.heartrate

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import java.util.Calendar

/** Reads the current value of the step counter once and records it. */
object StepSampler {

    fun sampleOnce(context: Context, pending: BroadcastReceiver.PendingResult?) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) {
            pending?.finish()
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var done = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (done) return
                val v = event?.values?.getOrNull(0) ?: return
                done = true
                StepTracker.record(context, v, force = true)
                StepWidgets.updateAll(context)
                sm.unregisterListener(this)
                handler.removeCallbacksAndMessages(null)
                pending?.finish()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val ok = sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        if (!ok) {
            pending?.finish()
            return
        }
        // Safety net: don't keep the receiver alive forever if the sensor stays silent.
        handler.postDelayed({
            if (!done) {
                done = true
                sm.unregisterListener(listener)
                pending?.finish()
            }
        }, 8_000L)
    }
}

/** 15-minute alarm that keeps the step history up to date while the app is closed. */
object StepScheduler {

    private const val REQUEST_CODE = 4101

    fun schedule(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(app, StepAlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            app, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // First run at the next quarter of an hour, then every 15 minutes.
        val first = Calendar.getInstance().apply {
            val minute = get(Calendar.MINUTE)
            add(Calendar.MINUTE, 15 - minute % 15)
            set(Calendar.SECOND, 30)
            set(Calendar.MILLISECOND, 0)
        }
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, first.timeInMillis, AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi
        )
    }
}

class StepAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            StepScheduler.schedule(app)
        }
        // Make sure continuous background counting is running (it restarts the service if the
        // system killed it), and take one snapshot as a safety net.
        StepTrackingService.start(app)
        StepSampler.sampleOnce(app, goAsync())
    }
}

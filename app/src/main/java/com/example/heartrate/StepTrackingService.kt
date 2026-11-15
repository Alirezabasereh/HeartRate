package com.example.heartrate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.text.NumberFormat
import java.util.Locale

/**
 * Keeps the step counter sensor registered all the time (like Samsung Health does), so steps are
 * recorded and the home-screen widgets update live even when the app is closed.
 * Runs as a foreground service with a small, silent notification.
 */
class StepTrackingService : Service(), SensorEventListener {

    companion object {
        private const val CHANNEL_ID = "step_tracking"
        private const val NOTIFICATION_ID = 7001
        private const val RECORD_INTERVAL_MS = 1_500L

        /** Starts the service (if background tracking is enabled and the device has the sensor). */
        fun start(context: Context) {
            val app = context.applicationContext
            if (!StepTracker.isBackgroundTracking(app)) return
            val intent = Intent(app, StepTrackingService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
            } catch (e: Exception) {
                // Background start not allowed right now; the next alarm/app launch retries.
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            app.stopService(Intent(app, StepTrackingService::class.java))
        }
    }

    private lateinit var sensorManager: SensorManager
    private val handler = Handler(Looper.getMainLooper())
    private var latest = -1f
    private var lastRecordMs = 0L
    private var trailingScheduled = false
    private var lastNotifiedSteps = -1

    private val trailing = Runnable {
        trailingScheduled = false
        recordLatest()
    }

    private val midnight = object : Runnable {
        override fun run() {
            recordLatest()
            StepWidgets.updateAll(this@StepTrackingService, force = true)
            updateNotification()
            scheduleMidnight()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // Must be called promptly after startForegroundService().
        startForeground(NOTIFICATION_ID, buildNotification(StepTracker.today(this).steps))

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) {
            stopSelf()
            return
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        scheduleMidnight()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        val v = event?.values?.getOrNull(0) ?: return
        latest = v
        val sinceLast = System.currentTimeMillis() - lastRecordMs
        if (sinceLast >= RECORD_INTERVAL_MS) {
            recordLatest()
        } else if (!trailingScheduled) {
            // Make sure the last step of a burst is not left out until the next event.
            trailingScheduled = true
            handler.postDelayed(trailing, RECORD_INTERVAL_MS - sinceLast)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun recordLatest() {
        if (latest < 0f) return
        val now = System.currentTimeMillis()
        lastRecordMs = now
        StepTracker.record(this, latest, now)
        StepWidgets.updateAll(this)
        updateNotification()
    }

    private fun scheduleMidnight() {
        handler.removeCallbacks(midnight)
        handler.postDelayed(midnight, StepTracker.millisUntilNextDay() + 1_500L)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val steps = StepTracker.today(this).steps
        if (steps == lastNotifiedSteps) return
        lastNotifiedSteps = steps
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(steps))
        } catch (e: Exception) {
            // Notification permission/limits must never crash the counter.
        }
    }

    private fun buildNotification(steps: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_steps)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(
                getString(R.string.notif_text, NumberFormat.getIntegerInstance(Locale.US).format(steps))
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        handler.removeCallbacksAndMessages(null)
        if (latest >= 0f) StepTracker.record(this, latest, force = true) else StepTracker.flush(this)
        super.onDestroy()
    }
}

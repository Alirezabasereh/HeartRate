package com.example.heartrate

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/** Home screen: steps summary, steps card and heart-rate card. */
class HomeActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var latestCounter = -1f
    private var lastRecordMs = 0L

    private lateinit var summaryStepsText: TextView
    private lateinit var summaryMinsText: TextView
    private lateinit var summaryKcalText: TextView
    private lateinit var heartRings: HeartRingsView
    private lateinit var stepsCard: View
    private lateinit var stepsValueText: TextView
    private lateinit var stepsGoalText: TextView
    private lateinit var stepsPercentText: TextView
    private lateinit var stepsProgress: PillProgressView
    private lateinit var homeBpmText: TextView
    private lateinit var homeHeartDateText: TextView
    private lateinit var homeZoneText: TextView
    private lateinit var homeRangeBar: RangeBarView

    private val numberFormat = NumberFormat.getIntegerInstance(Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        summaryStepsText = findViewById(R.id.summaryStepsText)
        summaryMinsText = findViewById(R.id.summaryMinsText)
        summaryKcalText = findViewById(R.id.summaryKcalText)
        heartRings = findViewById(R.id.heartRings)
        stepsCard = findViewById(R.id.stepsCard)
        stepsValueText = findViewById(R.id.stepsValueText)
        stepsGoalText = findViewById(R.id.stepsGoalText)
        stepsPercentText = findViewById(R.id.stepsPercentText)
        stepsProgress = findViewById(R.id.stepsProgress)
        homeBpmText = findViewById(R.id.homeBpmText)
        homeHeartDateText = findViewById(R.id.homeHeartDateText)
        homeZoneText = findViewById(R.id.homeZoneText)
        homeRangeBar = findViewById(R.id.homeRangeBar)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        val openSteps = View.OnClickListener {
            startActivity(Intent(this, StepsActivity::class.java))
        }
        findViewById<View>(R.id.summaryCard).setOnClickListener(openSteps)
        stepsCard.setOnClickListener(openSteps)
        findViewById<View>(R.id.heartCard).setOnClickListener {
            startActivity(Intent(this, HeartRateActivity::class.java))
        }
        findViewById<View>(R.id.homeMeasureButton).setOnClickListener {
            startActivity(Intent(this, MeasurementActivity::class.java))
        }
        findViewById<View>(R.id.menuButton).setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add(0, 1, 0, R.string.step_settings)
            popup.setOnMenuItemClickListener {
                StepSettingsDialog.show(this) { refresh() }
                true
            }
            popup.show()
        }
    }

    override fun onResume() {
        super.onResume()
        StepScheduler.schedule(this)
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        refresh()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        if (latestCounter >= 0f) StepTracker.record(this, latestCounter)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val v = event?.values?.getOrNull(0) ?: return
        latestCounter = v
        val now = System.currentTimeMillis()
        if (now - lastRecordMs > 2_000L) {
            lastRecordMs = now
            StepTracker.record(this, v, now)
            refreshSteps()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun refresh() {
        refreshSteps()
        refreshHeart()
    }

    private fun refreshSteps() {
        if (stepSensor == null) {
            stepsValueText.text = "--"
            stepsGoalText.setText(R.string.no_step_sensor)
            return
        }

        val goal = StepTracker.getGoal(this)
        val steps = StepTracker.today(this).steps
        val stats = StepTracker.stats(this, steps)
        val progress = steps.toFloat() / goal

        summaryStepsText.text = numberFormat.format(steps)
        summaryMinsText.text = numberFormat.format(stats.activeMinutes)
        summaryKcalText.text = numberFormat.format(stats.calories)
        heartRings.setProgress(
            progress,
            stats.activeMinutes.toFloat() / StepTracker.ACTIVE_MINUTES_TARGET,
            stats.calories.toFloat() / StepTracker.CALORIES_TARGET
        )

        stepsValueText.text = numberFormat.format(steps)
        stepsGoalText.text = getString(R.string.goal_format, numberFormat.format(goal))
        stepsPercentText.text = "${(progress * 100).toInt()}%"
        stepsProgress.setProgress(progress)

        // White card until the goal is reached, then the green "goal reached" card.
        val reached = steps >= goal
        stepsCard.setBackgroundResource(if (reached) R.drawable.bg_card_green else R.drawable.bg_card_single)
        val white = ContextCompat.getColor(this, R.color.white)
        stepsValueText.setTextColor(if (reached) white else ContextCompat.getColor(this, R.color.text_primary))
        stepsGoalText.setTextColor(if (reached) white else ContextCompat.getColor(this, R.color.text_secondary))
        if (reached) {
            stepsProgress.setColors(0x66FFFFFF, white)
        } else {
            stepsProgress.setColors(
                ContextCompat.getColor(this, R.color.range_track),
                ContextCompat.getColor(this, R.color.step_green)
            )
        }
    }

    private fun refreshHeart() {
        val latest = HeartRateStorage.getRecords(this).firstOrNull()
        if (latest == null) {
            homeBpmText.text = "--"
            homeHeartDateText.setText(R.string.no_measurement_yet)
            homeZoneText.text = ""
            homeRangeBar.setBpm(0)
        } else {
            val zone = HeartZone.of(latest.bpm)
            homeBpmText.text = "${latest.bpm}"
            homeHeartDateText.text = getString(
                R.string.last_measured,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.US)
                    .format(Date(latest.timestamp))
            )
            homeZoneText.setText(zone.labelRes)
            homeZoneText.setTextColor(ContextCompat.getColor(this, zone.colorRes))
            homeRangeBar.setBpm(latest.bpm)
        }
    }
}

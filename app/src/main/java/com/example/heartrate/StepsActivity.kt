package com.example.heartrate

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/** Steps page: 7-day strip, selected-day details, hourly chart and weekly summary. */
class StepsActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var latestCounter = -1f
    private var lastRecordMs = 0L

    private lateinit var dayStrip: DayStripView
    private lateinit var dateText: TextView
    private lateinit var stepsValueText: TextView
    private lateinit var goalBar: PillProgressView
    private lateinit var targetText: TextView
    private lateinit var distanceText: TextView
    private lateinit var kcalText: TextView
    private lateinit var hourlyChart: HourlyBarChartView
    private lateinit var detailsCard: LinearLayout
    private lateinit var weekCard: LinearLayout

    private var days: List<DayData> = emptyList()
    private var selected = -1
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_steps)

        dayStrip = findViewById(R.id.dayStrip)
        dateText = findViewById(R.id.dateText)
        stepsValueText = findViewById(R.id.stepsValueText)
        goalBar = findViewById(R.id.goalBar)
        targetText = findViewById(R.id.targetText)
        distanceText = findViewById(R.id.distanceText)
        kcalText = findViewById(R.id.kcalText)
        hourlyChart = findViewById(R.id.hourlyChart)
        detailsCard = findViewById(R.id.detailsCard)
        weekCard = findViewById(R.id.weekCard)

        goalBar.setColors(
            ContextCompat.getColor(this, R.color.range_track),
            ContextCompat.getColor(this, R.color.step_green)
        )

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.menuButton).setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add(0, 1, 0, R.string.step_settings)
            popup.setOnMenuItemClickListener {
                StepSettingsDialog.show(this) { reload() }
                true
            }
            popup.show()
        }
        dayStrip.onDaySelected = { index ->
            selected = index
            showSelectedDay()
        }
    }

    override fun onResume() {
        super.onResume()
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        reload()
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
            reload()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Re-reads the stored data; keeps the selected day (defaults to today). */
    private fun reload() {
        days = StepTracker.lastDays(this, 7)
        if (selected !in days.indices) selected = days.lastIndex
        dayStrip.setData(days, StepTracker.getGoal(this), selected)
        showSelectedDay()
        showWeekSummary()
    }

    private fun showSelectedDay() {
        val day = days.getOrNull(selected) ?: return
        val goal = StepTracker.getGoal(this)
        val stats = StepTracker.stats(this, day.steps)

        dayStrip.setData(days, goal, selected)
        dateText.text = SimpleDateFormat("EEE, d MMMM", Locale.US).format(day.dayStart)
        stepsValueText.text = numberFormat.format(day.steps)
        goalBar.setProgress(day.steps.toFloat() / goal)
        targetText.text = getString(R.string.target_format, numberFormat.format(goal))
        distanceText.text = String.format(Locale.US, "%.2f", stats.distanceKm)
        kcalText.text = numberFormat.format(stats.calories)
        hourlyChart.setData(day.hourly)

        fillRows(
            detailsCard, listOf(
                getString(R.string.row_active_time) to getString(R.string.mins_value, stats.activeMinutes),
                getString(R.string.row_avg_speed) to getString(
                    R.string.speed_value, String.format(Locale.US, "%.1f", stats.avgSpeedKmh)
                )
            )
        )
    }

    private fun showWeekSummary() {
        val goal = StepTracker.getGoal(this)
        val total = days.sumOf { it.steps }
        val best = days.maxByOrNull { it.steps }
        val reached = days.count { it.steps >= goal }
        fillRows(
            weekCard, listOf(
                getString(R.string.row_daily_avg) to
                        getString(R.string.steps_value, numberFormat.format(total / days.size.coerceAtLeast(1))),
                getString(R.string.row_best_day) to
                        if (best != null && best.steps > 0)
                            getString(R.string.steps_value, numberFormat.format(best.steps))
                        else "--",
                getString(R.string.row_goal_days) to getString(R.string.days_value, reached)
            )
        )
    }

    private fun fillRows(container: LinearLayout, rows: List<Pair<String, String>>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        rows.forEachIndexed { i, (label, value) ->
            val row = inflater.inflate(R.layout.item_info_row, container, false)
            row.findViewById<TextView>(R.id.infoLabel).text = label
            row.findViewById<TextView>(R.id.infoValue).text = value
            if (i == rows.lastIndex) row.findViewById<View>(R.id.infoDivider).visibility = View.GONE
            container.addView(row)
        }
    }
}

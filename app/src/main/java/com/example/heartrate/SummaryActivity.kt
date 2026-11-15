package com.example.heartrate

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.NumberFormat
import java.util.Locale

/**
 * "Daily activity": overall status for today (rings + three metrics) plus a
 * 7-day performance summary for steps and heart rate.
 */
class SummaryActivity : AppCompatActivity() {

    private lateinit var stepUpdater: StepLiveUpdater
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.US)

    private var stepsTarget = ""
    private var activeTarget = ""
    private var kcalTarget = ""
    private val stepsValueAnim = NumberAnimator { findViewById<TextView>(R.id.sumStepsValue).text = numberFormat.format(it) }
    private val activeValueAnim = NumberAnimator(250L) { findViewById<TextView>(R.id.sumActiveValue).text = "$it min" }
    private val kcalValueAnim = NumberAnimator(120L) { findViewById<TextView>(R.id.sumKcalValue).text = numberFormat.format(it) }
    private val stepsPctAnim = NumberAnimator(200L) { setSub(R.id.sumStepsSub, it, stepsTarget) }
    private val activePctAnim = NumberAnimator(200L) { setSub(R.id.sumActiveSub, it, activeTarget) }
    private val kcalPctAnim = NumberAnimator(200L) { setSub(R.id.sumKcalSub, it, kcalTarget) }

    private lateinit var rings: HeartRingsView
    private lateinit var statusText: TextView
    private lateinit var weekCard: LinearLayout
    private lateinit var heartCard: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary)

        rings = findViewById(R.id.summaryRings)
        statusText = findViewById(R.id.summaryStatusText)
        weekCard = findViewById(R.id.weekCard)
        heartCard = findViewById(R.id.heartCard)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.menuButton).setOnClickListener { anchor ->
            val popup = android.widget.PopupMenu(this, anchor)
            popup.menu.add(0, 1, 0, R.string.step_settings)
            popup.setOnMenuItemClickListener {
                startActivity(android.content.Intent(this, SettingsActivity::class.java))
                true
            }
            popup.show()
        }
        findViewById<View>(R.id.sumStepsColumn).setOnClickListener {
            startActivity(Intent(this, StepsActivity::class.java))
        }
        heartCard.setOnClickListener {
            startActivity(Intent(this, HeartRateActivity::class.java))
        }

        stepUpdater = StepLiveUpdater(this) { render() }
    }

    override fun onResume() {
        super.onResume()
        stepUpdater.start()
        render()
    }

    override fun onPause() {
        super.onPause()
        stepUpdater.stop()
    }

    private fun render() {
        renderToday()
        renderWeek()
        renderHeart()
    }

    private fun renderToday() {
        val goal = StepTracker.getGoal(this)
        val activeGoal = StepTracker.getActiveGoal(this)
        val calorieGoal = StepTracker.getCalorieGoal(this)
        val steps = StepTracker.today(this).steps
        val stats = StepTracker.stats(this, steps)

        val stepsP = steps.toFloat() / goal
        val minP = stats.activeMinutes.toFloat() / activeGoal
        val kcalP = stats.calories.toFloat() / calorieGoal
        rings.setProgress(stepsP, minP, kcalP)

        statusText.text = when {
            stepUpdater.sensor == null -> getString(R.string.no_step_sensor)
            steps >= goal -> getString(R.string.summary_goal_reached)
            else -> getString(R.string.summary_steps_left, numberFormat.format(goal - steps))
        }

        stepsTarget = getString(R.string.target_steps_format, numberFormat.format(goal))
        activeTarget = getString(R.string.target_min_format, activeGoal)
        kcalTarget = getString(R.string.target_kcal_format, numberFormat.format(calorieGoal))

        stepsValueAnim.set(steps)
        activeValueAnim.set(stats.activeMinutes)
        kcalValueAnim.set(stats.calories)
        stepsPctAnim.set((stepsP * 100).toInt())
        activePctAnim.set((minP * 100).toInt())
        kcalPctAnim.set((kcalP * 100).toInt())

        // A changed goal changes the target text even when the percentage stays the same.
        setSub(R.id.sumStepsSub, stepsPctAnim.current, stepsTarget)
        setSub(R.id.sumActiveSub, activePctAnim.current, activeTarget)
        setSub(R.id.sumKcalSub, kcalPctAnim.current, kcalTarget)
    }

    private fun setSub(subId: Int, percent: Int, target: String) {
        findViewById<TextView>(subId).text = getString(R.string.of_target_format, percent, target)
    }

    private fun renderWeek() {
        val goal = StepTracker.getGoal(this)
        val days = StepTracker.lastDays(this, 7)
        val total = days.sumOf { it.steps }
        val stats = StepTracker.stats(this, total)
        val best = days.maxByOrNull { it.steps }
        val reached = days.count { it.steps >= goal }

        fillRows(
            weekCard, listOf(
                getString(R.string.row_total_steps) to numberFormat.format(total),
                getString(R.string.row_daily_avg) to
                        getString(R.string.steps_value, numberFormat.format(total / days.size.coerceAtLeast(1))),
                getString(R.string.row_distance) to
                        getString(R.string.km_value, String.format(Locale.US, "%.1f", stats.distanceKm)),
                getString(R.string.row_calories) to
                        getString(R.string.kcal_value, numberFormat.format(stats.calories)),
                getString(R.string.row_active_total) to getString(R.string.mins_value, stats.activeMinutes),
                getString(R.string.row_best_day) to
                        if (best != null && best.steps > 0)
                            getString(R.string.steps_value, numberFormat.format(best.steps)) else "--",
                getString(R.string.row_goal_days) to getString(R.string.days_value, reached)
            )
        )
    }

    private fun renderHeart() {
        val all = HeartRateStorage.getRecords(this)
        val since = StepTracker.startOfDay(System.currentTimeMillis()) - 6L * 24 * 60 * 60 * 1000
        val week = all.filter { it.timestamp >= since }
        val latest = all.firstOrNull()

        val rows = mutableListOf<Pair<String, String>>()
        if (latest != null) {
            rows.add(
                getString(R.string.row_latest) to getString(
                    R.string.bpm_zone_value, latest.bpm, getString(HeartZone.of(latest.bpm).labelRes)
                )
            )
        }
        if (week.isEmpty()) {
            rows.add(getString(R.string.no_hr_week) to "")
        } else {
            val bpms = week.map { it.bpm }
            rows.add(getString(R.string.row_hr_avg) to getString(R.string.bpm_value, bpms.average().toInt()))
            rows.add(getString(R.string.row_hr_min) to getString(R.string.bpm_value, bpms.minOrNull() ?: 0))
            rows.add(getString(R.string.row_hr_max) to getString(R.string.bpm_value, bpms.maxOrNull() ?: 0))
            rows.add(getString(R.string.row_hr_count) to "${bpms.size}")
        }
        fillRows(heartCard, rows)
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

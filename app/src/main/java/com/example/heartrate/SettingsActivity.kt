package com.example.heartrate

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.NumberFormat
import java.util.Locale

/** Goals, body measurements and background counting, shown as sliders in the app's card style. */
class SettingsActivity : AppCompatActivity() {

    private class Spec(
        val titleRes: Int,
        val iconRes: Int,
        val colorRes: Int,
        val min: Int,
        val max: Int,
        val step: Int,
        val defaultValue: Int,
        val format: (Int) -> String
    )

    private class Row(val spec: Spec, val seek: SeekBar)

    private val nf = NumberFormat.getIntegerInstance(Locale.US)
    private val rows = mutableListOf<Row>()

    private var goal = 0
    private var activeGoal = 0
    private var calorieGoal = 0
    private var heightCm = 0
    private var weightKg = 0

    private lateinit var rings: HeartRingsView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        goal = StepTracker.getGoal(this)
        activeGoal = StepTracker.getActiveGoal(this)
        calorieGoal = StepTracker.getCalorieGoal(this)
        heightCm = StepTracker.getHeightCm(this)
        weightKg = StepTracker.getWeightKg(this)

        rings = findViewById(R.id.settingsRings)
        rings.setProgress(1f, 1f, 1f)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        val goalsCard = findViewById<LinearLayout>(R.id.goalsCard)
        val bodyCard = findViewById<LinearLayout>(R.id.bodyCard)

        addRow(
            goalsCard,
            Spec(R.string.setting_steps, R.drawable.ic_dot_steps, R.color.step_green,
                1_000, 30_000, 500, StepTracker.DEFAULT_GOAL) { nf.format(it) },
            goal, last = false
        ) { goal = it }
        addRow(
            goalsCard,
            Spec(R.string.setting_active, R.drawable.ic_dot_time, R.color.ring_blue,
                10, 240, 5, StepTracker.DEFAULT_ACTIVE_GOAL) { getString(R.string.mins_value, it) },
            activeGoal, last = false
        ) { activeGoal = it }
        addRow(
            goalsCard,
            Spec(R.string.setting_calories, R.drawable.ic_dot_kcal, R.color.ring_pink,
                50, 2_000, 10, StepTracker.DEFAULT_CALORIE_GOAL) { getString(R.string.kcal_value, nf.format(it)) },
            calorieGoal, last = true
        ) { calorieGoal = it }

        addRow(
            bodyCard,
            Spec(R.string.setting_height, R.drawable.ic_dot_height, R.color.badge_orange,
                100, 220, 1, StepTracker.DEFAULT_HEIGHT_CM) { getString(R.string.cm_value, it) },
            heightCm, last = false
        ) { heightCm = it }
        addRow(
            bodyCard,
            Spec(R.string.setting_weight, R.drawable.ic_dot_weight, R.color.icon_gray,
                30, 200, 1, StepTracker.DEFAULT_WEIGHT_KG) { getString(R.string.kg_value, it) },
            weightKg, last = true
        ) { weightKg = it }

        // Background counting switch
        val backgroundCard = findViewById<LinearLayout>(R.id.backgroundCard)
        val switchRow = layoutInflater.inflate(R.layout.item_setting_switch, backgroundCard, false)
        val switch = switchRow.findViewById<SwitchMaterial>(R.id.backgroundSwitch)
        switch.isChecked = StepTracker.isBackgroundTracking(this)
        switch.setOnCheckedChangeListener { _, checked ->
            StepTracker.setBackgroundTracking(this, checked)
            if (checked) StepTrackingService.start(this) else StepTrackingService.stop(this)
        }
        switchRow.setOnClickListener { switch.toggle() }
        backgroundCard.addView(switchRow)

        findViewById<View>(R.id.resetButton).setOnClickListener {
            for (row in rows) row.seek.progress = progressFor(row.spec, row.spec.defaultValue)
            goal = StepTracker.DEFAULT_GOAL
            activeGoal = StepTracker.DEFAULT_ACTIVE_GOAL
            calorieGoal = StepTracker.DEFAULT_CALORIE_GOAL
            heightCm = StepTracker.DEFAULT_HEIGHT_CM
            weightKg = StepTracker.DEFAULT_WEIGHT_KG
            save()
        }

        updateEstimate()
    }

    private fun progressFor(spec: Spec, value: Int): Int =
        ((value.coerceIn(spec.min, spec.max) - spec.min + spec.step / 2) / spec.step)

    private fun addRow(
        container: LinearLayout,
        spec: Spec,
        initial: Int,
        last: Boolean,
        onChange: (Int) -> Unit
    ) {
        val view = layoutInflater.inflate(R.layout.item_setting_slider, container, false)
        view.findViewById<ImageView>(R.id.settingIcon).setImageResource(spec.iconRes)
        view.findViewById<TextView>(R.id.settingTitle).setText(spec.titleRes)
        val valueText = view.findViewById<TextView>(R.id.settingValue)
        val seek = view.findViewById<SeekBar>(R.id.settingSeek)
        if (last) view.findViewById<View>(R.id.settingDivider).visibility = View.GONE

        val color = ContextCompat.getColor(this, spec.colorRes)
        seek.max = (spec.max - spec.min) / spec.step
        seek.progress = progressFor(spec, initial)
        seek.progressTintList = ColorStateList.valueOf(color)
        seek.thumbTintList = ColorStateList.valueOf(color)
        seek.progressBackgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.range_track))
        valueText.text = spec.format(spec.min + seek.progress * spec.step)

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = spec.min + progress * spec.step
                valueText.text = spec.format(value)
                if (fromUser) {
                    onChange(value)
                    save()
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        rows.add(Row(spec, seek))
        container.addView(view)
    }

    private fun save() {
        StepTracker.saveSettings(this, goal, activeGoal, calorieGoal, heightCm, weightKg)
        updateEstimate()
    }

    /** What the current step goal means in distance, calories and time with the chosen body values. */
    private fun updateEstimate() {
        val stats = StepTracker.statsFor(goal, heightCm, weightKg)
        findViewById<TextView>(R.id.estimateSteps).text = getString(R.string.steps_value, nf.format(goal))
        findViewById<TextView>(R.id.estimateKm).text =
            getString(R.string.estimate_km, String.format(Locale.US, "%.1f", stats.distanceKm))
        findViewById<TextView>(R.id.estimateKcal).text =
            getString(R.string.estimate_kcal, nf.format(stats.calories))
        findViewById<TextView>(R.id.estimateMin).text =
            getString(R.string.estimate_min, stats.activeMinutes)
    }

    override fun onPause() {
        super.onPause()
        StepTracker.saveSettings(this, goal, activeGoal, calorieGoal, heightCm, weightKg)
        StepWidgets.updateAll(this, force = true)
    }
}

package com.example.heartrate

import android.app.Activity
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog

/** Dialog for the daily step goal and the body measurements used for estimates. */
object StepSettingsDialog {

    fun show(activity: Activity, onSaved: () -> Unit) {
        val d = activity.resources.displayMetrics.density
        val goal = field(activity, R.string.hint_goal, StepTracker.getGoal(activity))
        val height = field(activity, R.string.hint_height, StepTracker.getHeightCm(activity))
        val weight = field(activity, R.string.hint_weight, StepTracker.getWeightKg(activity))

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * d).toInt()
            setPadding(pad, (8 * d).toInt(), pad, 0)
            addView(goal)
            addView(height)
            addView(weight)
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.settings_title)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                StepTracker.saveSettings(
                    activity,
                    goal.text.toString().toIntOrNull() ?: StepTracker.getGoal(activity),
                    height.text.toString().toIntOrNull() ?: StepTracker.getHeightCm(activity),
                    weight.text.toString().toIntOrNull() ?: StepTracker.getWeightKg(activity)
                )
                onSaved()
            }
            .show()
    }

    private fun field(activity: Activity, hintRes: Int, value: Int) = EditText(activity).apply {
        setHint(hintRes)
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(value.toString())
        setSelectAllOnFocus(true)
    }
}

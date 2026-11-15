package com.example.heartrate

import android.app.Activity
import android.content.Intent

/**
 * Kept only so an old copy of this file left in a repository cannot break the build.
 * Settings are now a full page: [SettingsActivity].
 */
object StepSettingsDialog {
    fun show(activity: Activity, onSaved: () -> Unit = {}) {
        activity.startActivity(Intent(activity, SettingsActivity::class.java))
    }
}

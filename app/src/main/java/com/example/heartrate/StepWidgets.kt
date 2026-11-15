package com.example.heartrate

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.RemoteViews
import java.text.NumberFormat
import java.util.Locale

/** Builds and refreshes the three home-screen widgets from the stored step data. */
object StepWidgets {

    private val nf: NumberFormat get() = NumberFormat.getIntegerInstance(Locale.US)

    private class Snapshot(
        val steps: Int, val goal: Int,
        val minutes: Int, val minutesGoal: Int,
        val kcal: Int, val kcalGoal: Int
    )

    private fun snapshot(context: Context): Snapshot {
        val steps = StepTracker.today(context).steps
        val stats = StepTracker.stats(context, steps)
        return Snapshot(
            steps, StepTracker.getGoal(context),
            stats.activeMinutes, StepTracker.getActiveGoal(context),
            stats.calories, StepTracker.getCalorieGoal(context)
        )
    }

    private var lastKey: String? = null

    /** Refreshes all placed widgets; skipped when nothing changed since the last refresh. */
    @Synchronized
    fun updateAll(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val s = snapshot(app)
        val key = "${s.steps}/${s.goal}/${s.minutes}/${s.minutesGoal}/${s.kcal}/${s.kcalGoal}"
        if (!force && key == lastKey) return
        lastKey = key
        val manager = AppWidgetManager.getInstance(app)
        update(app, manager, DailyActivityWidget::class.java) { buildDaily(app) }
        update(app, manager, StepsWidget::class.java) { buildSteps(app) }
        update(app, manager, DailyActivityLargeWidget::class.java) { buildLarge(app) }
    }

    private fun update(
        context: Context,
        manager: AppWidgetManager,
        cls: Class<*>,
        build: () -> RemoteViews
    ) {
        val ids = manager.getAppWidgetIds(ComponentName(context, cls))
        if (ids.isNotEmpty()) manager.updateAppWidget(ids, build())
    }

    // ---------- Daily activity 2x1: just the heart rings ----------
    fun buildDaily(context: Context): RemoteViews {
        val s = snapshot(context)
        val rv = RemoteViews(context.packageName, R.layout.widget_daily_activity)
        rv.setImageViewBitmap(R.id.widgetRings, ringsBitmap(context, s))
        rv.setOnClickPendingIntent(R.id.widgetRoot, open(context, SummaryActivity::class.java, 1))
        return rv
    }

    // ---------- Steps 2x1: number + green bar ----------
    fun buildSteps(context: Context): RemoteViews {
        val s = snapshot(context)
        val rv = RemoteViews(context.packageName, R.layout.widget_steps)
        rv.setTextViewText(R.id.widgetSteps, nf.format(s.steps))
        val pct = (s.steps * 100L / s.goal).toInt().coerceIn(0, 100)
        rv.setProgressBar(R.id.widgetBar, 100, pct, false)
        rv.setOnClickPendingIntent(R.id.widgetRoot, open(context, StepsActivity::class.java, 2))
        return rv
    }

    // ---------- Daily activity 4x1: three metric rows + rings ----------
    fun buildLarge(context: Context): RemoteViews {
        val s = snapshot(context)
        val rv = RemoteViews(context.packageName, R.layout.widget_daily_activity_large)
        rv.setTextViewText(R.id.wStepsValue, nf.format(s.steps))
        rv.setTextViewText(R.id.wStepsGoal, context.getString(R.string.widget_goal_slash, nf.format(s.goal)))
        rv.setTextViewText(R.id.wMinsValue, nf.format(s.minutes))
        rv.setTextViewText(R.id.wMinsGoal, context.getString(R.string.widget_goal_min, nf.format(s.minutesGoal)))
        rv.setTextViewText(R.id.wKcalValue, nf.format(s.kcal))
        rv.setTextViewText(R.id.wKcalGoal, context.getString(R.string.widget_goal_kcal, nf.format(s.kcalGoal)))
        rv.setImageViewBitmap(R.id.widgetRings, ringsBitmap(context, s))
        rv.setOnClickPendingIntent(R.id.widgetRoot, open(context, SummaryActivity::class.java, 3))
        return rv
    }

    private fun ringsBitmap(context: Context, s: Snapshot): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        HeartRingsPainter(context).draw(
            Canvas(bmp), size.toFloat(), size.toFloat(),
            floatArrayOf(
                s.steps.toFloat() / s.goal,
                s.minutes.toFloat() / s.minutesGoal,
                s.kcal.toFloat() / s.kcalGoal
            )
        )
        return bmp
    }

    private fun open(context: Context, cls: Class<*>, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

/** Shared behaviour: draw from stored data right away, then take a fresh sensor sample. */
abstract class BaseStepWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        StepWidgets.updateAll(context, force = true)
        StepTrackingService.start(context)
    }

    override fun onEnabled(context: Context) {
        // Makes sure the 15-minute snapshots run even if the app itself is never opened.
        StepScheduler.schedule(context)
        StepTrackingService.start(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            StepSampler.sampleOnce(context.applicationContext, goAsync())
        }
    }
}

class DailyActivityWidget : BaseStepWidget()
class StepsWidget : BaseStepWidget()
class DailyActivityLargeWidget : BaseStepWidget()

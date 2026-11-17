package com.example.heartrate

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.widget.RemoteViews
import java.util.Calendar

/**
 * Analog clock widgets (white face, four styles).
 *
 * Hour and minute hands are drawn by the system's AnalogClock, so they keep correct time on their
 * own. The second hand is native on Android 12+; on older versions it is a ViewFlipper that
 * advances once per second by itself and is re-synchronised whenever the screen turns on.
 */
object ClockWidgets {

    private val providers = listOf(
        ClockPlainWidget::class.java,
        ClockTicksWidget::class.java,
        ClockNumbersWidget::class.java,
        ClockFlowerWidget::class.java
    )

    /** Re-syncs the second hand of every placed clock (only needed before Android 12). */
    fun updateAll(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        for (cls in providers) {
            val ids = manager.getAppWidgetIds(ComponentName(app, cls))
            if (ids.isEmpty()) continue
            val layout = layoutFor(cls)
            manager.updateAppWidget(ids, build(app, layout))
        }
    }

    private fun layoutFor(cls: Class<*>): Int = when (cls) {
        ClockPlainWidget::class.java -> R.layout.widget_clock_1
        ClockTicksWidget::class.java -> R.layout.widget_clock_2
        ClockNumbersWidget::class.java -> R.layout.widget_clock_3
        else -> R.layout.widget_clock_4
    }

    fun build(context: Context, layoutRes: Int): RemoteViews {
        val rv = RemoteViews(context.packageName, layoutRes)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Put the flipper on the current second so the hand starts in the right place.
            rv.setDisplayedChild(R.id.secFlipper, Calendar.getInstance().get(Calendar.SECOND))
        }
        // Tapping the clock opens the phone's clock app.
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            context, layoutRes, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.clockRoot, pi)
        return rv
    }
}

abstract class BaseClockWidget(private val layoutRes: Int) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, ClockWidgets.build(context, layoutRes))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Time or time zone changed: redraw so the second hand is back in sync.
        if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, this::class.java))
            if (ids.isNotEmpty()) onUpdate(context, manager, ids)
        }
    }
}

class ClockPlainWidget : BaseClockWidget(R.layout.widget_clock_1)
class ClockTicksWidget : BaseClockWidget(R.layout.widget_clock_2)
class ClockNumbersWidget : BaseClockWidget(R.layout.widget_clock_3)
class ClockFlowerWidget : BaseClockWidget(R.layout.widget_clock_4)

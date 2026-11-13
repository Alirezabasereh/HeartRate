package com.example.heartrate

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class StepStats(
    val steps: Int,
    val distanceKm: Float,
    val calories: Int,
    val activeMinutes: Int,
    val avgSpeedKmh: Float
)

class DayData(val dayStart: Long, val steps: Int, val hourly: FloatArray)

/**
 * Daily/hourly step storage built on the phone's TYPE_STEP_COUNTER sensor.
 *
 * The sensor is hardware-based: it keeps counting even while the app is closed and
 * reports a cumulative total since the last reboot. We take snapshots of that total
 * (when the app is open, every hour via an alarm, and after boot) and spread the
 * difference between two snapshots over the hours it spans.
 */
object StepTracker {

    private const val PREFS = "step_prefs"
    private const val KEY_DAYS = "days"
    private const val KEY_LAST_COUNTER = "last_counter"
    private const val KEY_LAST_TS = "last_ts"
    private const val KEY_LAST_ELAPSED = "last_elapsed"
    private const val KEY_GOAL = "goal"
    private const val KEY_HEIGHT = "height_cm"
    private const val KEY_WEIGHT = "weight_kg"
    private const val KEEP_DAYS = 60

    const val ACTIVE_MINUTES_TARGET = 60
    const val CALORIES_TARGET = 500

    // ---------- Settings ----------

    fun getGoal(context: Context): Int = prefs(context).getInt(KEY_GOAL, 10_000)
    fun getHeightCm(context: Context): Int = prefs(context).getInt(KEY_HEIGHT, 170)
    fun getWeightKg(context: Context): Int = prefs(context).getInt(KEY_WEIGHT, 70)

    fun saveSettings(context: Context, goal: Int, heightCm: Int, weightKg: Int) {
        prefs(context).edit()
            .putInt(KEY_GOAL, goal.coerceIn(100, 100_000))
            .putInt(KEY_HEIGHT, heightCm.coerceIn(100, 230))
            .putInt(KEY_WEIGHT, weightKg.coerceIn(20, 250))
            .apply()
    }

    // ---------- Recording ----------

    /** Adds a snapshot of the cumulative step counter. */
    @Synchronized
    fun record(context: Context, counter: Float, now: Long = System.currentTimeMillis()) {
        val p = prefs(context)
        val elapsed = SystemClock.elapsedRealtime()
        val bootTime = now - elapsed
        val lastTs = p.getLong(KEY_LAST_TS, 0L)
        val lastCounter = p.getFloat(KEY_LAST_COUNTER, -1f)
        val lastElapsed = p.getLong(KEY_LAST_ELAPSED, 0L)

        val prevTs: Long
        val delta: Float
        if (lastTs == 0L || lastCounter < 0f) {
            // First snapshot ever: if the phone booted today, every counted step is from today.
            if (bootTime >= startOfDay(now)) {
                prevTs = bootTime
                delta = counter
            } else {
                prevTs = now
                delta = 0f
            }
        } else {
            val rebooted = counter < lastCounter || elapsed < lastElapsed
            if (rebooted) {
                prevTs = max(bootTime, lastTs)
                delta = counter
            } else {
                prevTs = lastTs
                delta = counter - lastCounter
            }
        }

        val days = loadDays(p)
        if (delta > 0f) addSteps(days, prevTs, now, delta)
        prune(days, now)
        saveDays(p, days)

        p.edit()
            .putFloat(KEY_LAST_COUNTER, counter)
            .putLong(KEY_LAST_TS, now)
            .putLong(KEY_LAST_ELAPSED, elapsed)
            .apply()
    }

    private fun addSteps(days: MutableMap<String, FloatArray>, from: Long, to: Long, delta: Float) {
        if (to <= from) {
            val cal = Calendar.getInstance().apply { timeInMillis = to }
            bucket(days, cal)[cal.get(Calendar.HOUR_OF_DAY)] += delta
            return
        }
        val total = (to - from).toFloat()
        var t = from
        while (t < to) {
            val cal = Calendar.getInstance().apply { timeInMillis = t }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val arr = bucket(days, cal)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.HOUR_OF_DAY, 1)
            val end = min(cal.timeInMillis, to)
            arr[hour] += delta * (end - t) / total
            t = end
        }
    }

    private fun bucket(days: MutableMap<String, FloatArray>, cal: Calendar): FloatArray =
        days.getOrPut(dayKey(cal.timeInMillis)) { FloatArray(24) }

    // ---------- Reading ----------

    fun getDay(context: Context, dayStart: Long): DayData {
        val hourly = loadDays(prefs(context))[dayKey(dayStart)] ?: FloatArray(24)
        return DayData(dayStart, hourly.sum().toInt(), hourly)
    }

    fun today(context: Context): DayData = getDay(context, startOfDay(System.currentTimeMillis()))

    /** The last [n] days ending today (oldest first). */
    fun lastDays(context: Context, n: Int): List<DayData> {
        val days = loadDays(prefs(context))
        val cal = Calendar.getInstance().apply {
            timeInMillis = startOfDay(System.currentTimeMillis())
            add(Calendar.DAY_OF_YEAR, -(n - 1))
        }
        val out = mutableListOf<DayData>()
        repeat(n) {
            val start = cal.timeInMillis
            val hourly = days[dayKey(start)] ?: FloatArray(24)
            out.add(DayData(start, hourly.sum().toInt(), hourly))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return out
    }

    /** Distance, calories, active minutes and speed are estimates derived from the step count. */
    fun stats(context: Context, steps: Int): StepStats {
        val strideM = getHeightCm(context) * 0.415f / 100f
        val km = steps * strideM / 1000f
        val kcal = (km * getWeightKg(context) * 0.57f).toInt()
        val minutes = steps / 100
        val speed = if (minutes > 0) km / (minutes / 60f) else 0f
        return StepStats(steps, km, kcal, minutes, speed)
    }

    // ---------- Helpers ----------

    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun dayKey(millis: Long): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(millis)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadDays(p: android.content.SharedPreferences): MutableMap<String, FloatArray> {
        val out = mutableMapOf<String, FloatArray>()
        val json = p.getString(KEY_DAYS, null) ?: return out
        try {
            val obj = JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = obj.getJSONArray(k)
                val f = FloatArray(24)
                for (i in 0 until min(24, arr.length())) f[i] = arr.getDouble(i).toFloat()
                out[k] = f
            }
        } catch (e: Exception) {
            out.clear()
        }
        return out
    }

    private fun saveDays(p: android.content.SharedPreferences, days: Map<String, FloatArray>) {
        val obj = JSONObject()
        for ((k, v) in days) {
            val arr = JSONArray()
            for (x in v) arr.put(Math.round(x * 100f) / 100.0)
            obj.put(k, arr)
        }
        p.edit().putString(KEY_DAYS, obj.toString()).apply()
    }

    private fun prune(days: MutableMap<String, FloatArray>, now: Long) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -KEEP_DAYS)
        }
        val cutoff = dayKey(cal.timeInMillis)
        days.keys.removeAll { it < cutoff }
    }
}

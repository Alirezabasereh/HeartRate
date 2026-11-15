package com.example.heartrate

import android.content.Context
import android.content.SharedPreferences
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
 * The sensor is hardware-based and reports a cumulative total since the last reboot.
 * Snapshots of that total are recorded continuously by [StepTrackingService] (and by screens,
 * alarms and widgets as a safety net); the difference between two snapshots is spread over
 * the hours it spans.
 *
 * Data lives in memory and is written to disk together with the last snapshot at most every
 * [FLUSH_INTERVAL_MS] (and whenever a screen stops), so frequent snapshots stay cheap. If the
 * process dies before a flush, the next snapshot simply re-adds the missing steps.
 */
object StepTracker {

    private const val PREFS = "step_prefs"
    private const val KEY_DAYS = "days"
    private const val KEY_LAST_COUNTER = "last_counter"
    private const val KEY_LAST_TS = "last_ts"
    private const val KEY_LAST_ELAPSED = "last_elapsed"
    private const val KEY_GOAL = "goal"
    private const val KEY_ACTIVE_GOAL = "active_goal_min"
    private const val KEY_CALORIE_GOAL = "calorie_goal"
    private const val KEY_HEIGHT = "height_cm"
    private const val KEY_WEIGHT = "weight_kg"
    private const val KEY_BACKGROUND = "background_tracking"
    private const val KEEP_DAYS = 60
    private const val FLUSH_INTERVAL_MS = 10_000L

    const val DEFAULT_GOAL = 10_000
    const val DEFAULT_ACTIVE_GOAL = 60
    const val DEFAULT_CALORIE_GOAL = 500
    const val DEFAULT_HEIGHT_CM = 170
    const val DEFAULT_WEIGHT_KG = 70

    // ---------- Settings ----------

    fun getGoal(context: Context): Int = prefs(context).getInt(KEY_GOAL, DEFAULT_GOAL)
    fun getActiveGoal(context: Context): Int = prefs(context).getInt(KEY_ACTIVE_GOAL, DEFAULT_ACTIVE_GOAL)
    fun getCalorieGoal(context: Context): Int = prefs(context).getInt(KEY_CALORIE_GOAL, DEFAULT_CALORIE_GOAL)
    fun getHeightCm(context: Context): Int = prefs(context).getInt(KEY_HEIGHT, DEFAULT_HEIGHT_CM)
    fun getWeightKg(context: Context): Int = prefs(context).getInt(KEY_WEIGHT, DEFAULT_WEIGHT_KG)

    fun isBackgroundTracking(context: Context): Boolean = prefs(context).getBoolean(KEY_BACKGROUND, true)
    fun setBackgroundTracking(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BACKGROUND, enabled).apply()
    }

    fun saveSettings(
        context: Context,
        goal: Int,
        activeGoal: Int,
        calorieGoal: Int,
        heightCm: Int,
        weightKg: Int
    ) {
        prefs(context).edit()
            .putInt(KEY_GOAL, goal.coerceIn(100, 100_000))
            .putInt(KEY_ACTIVE_GOAL, activeGoal.coerceIn(5, 1_440))
            .putInt(KEY_CALORIE_GOAL, calorieGoal.coerceIn(10, 10_000))
            .putInt(KEY_HEIGHT, heightCm.coerceIn(100, 230))
            .putInt(KEY_WEIGHT, weightKg.coerceIn(20, 250))
            .apply()
    }

    // ---------- In-memory state ----------

    private var loaded = false
    private val days = mutableMapOf<String, FloatArray>()
    private var lastCounter = -1f
    private var lastTs = 0L
    private var lastElapsed = 0L
    private var lastFlush = 0L

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        val p = prefs(context)
        days.clear()
        days.putAll(loadDays(p))
        lastCounter = p.getFloat(KEY_LAST_COUNTER, -1f)
        lastTs = p.getLong(KEY_LAST_TS, 0L)
        lastElapsed = p.getLong(KEY_LAST_ELAPSED, 0L)
        loaded = true
    }

    // ---------- Recording ----------

    /** Adds a snapshot of the cumulative step counter. */
    @Synchronized
    fun record(
        context: Context,
        counter: Float,
        now: Long = System.currentTimeMillis(),
        force: Boolean = false
    ) {
        ensureLoaded(context)
        val elapsed = SystemClock.elapsedRealtime()
        val bootTime = now - elapsed

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

        if (delta > 0f) addSteps(prevTs, now, delta)
        prune(now)
        lastCounter = counter
        lastTs = now
        lastElapsed = elapsed

        if (force || now - lastFlush > FLUSH_INTERVAL_MS) flushLocked(context, now)
    }

    /** Writes everything to disk now. */
    @Synchronized
    fun flush(context: Context) {
        ensureLoaded(context)
        flushLocked(context, System.currentTimeMillis())
    }

    private fun flushLocked(context: Context, now: Long) {
        val obj = JSONObject()
        for ((k, v) in days) {
            val arr = JSONArray()
            for (x in v) arr.put(Math.round(x * 100f) / 100.0)
            obj.put(k, arr)
        }
        prefs(context).edit()
            .putString(KEY_DAYS, obj.toString())
            .putFloat(KEY_LAST_COUNTER, lastCounter)
            .putLong(KEY_LAST_TS, lastTs)
            .putLong(KEY_LAST_ELAPSED, lastElapsed)
            .apply()
        lastFlush = now
    }

    private fun addSteps(from: Long, to: Long, delta: Float) {
        if (to <= from) {
            val cal = Calendar.getInstance().apply { timeInMillis = to }
            bucket(cal)[cal.get(Calendar.HOUR_OF_DAY)] += delta
            return
        }
        val total = (to - from).toFloat()
        var t = from
        while (t < to) {
            val cal = Calendar.getInstance().apply { timeInMillis = t }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val arr = bucket(cal)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.HOUR_OF_DAY, 1)
            val end = min(cal.timeInMillis, to)
            arr[hour] += delta * (end - t) / total
            t = end
        }
    }

    private fun bucket(cal: Calendar): FloatArray =
        days.getOrPut(dayKey(cal.timeInMillis)) { FloatArray(24) }

    // ---------- Reading ----------

    @Synchronized
    fun getDay(context: Context, dayStart: Long): DayData {
        ensureLoaded(context)
        val hourly = days[dayKey(dayStart)]?.copyOf() ?: FloatArray(24)
        return DayData(dayStart, hourly.sum().toInt(), hourly)
    }

    fun today(context: Context): DayData = getDay(context, startOfDay(System.currentTimeMillis()))

    /** The last [n] days ending today (oldest first). */
    @Synchronized
    fun lastDays(context: Context, n: Int): List<DayData> {
        ensureLoaded(context)
        val cal = Calendar.getInstance().apply {
            timeInMillis = startOfDay(System.currentTimeMillis())
            add(Calendar.DAY_OF_YEAR, -(n - 1))
        }
        val out = mutableListOf<DayData>()
        repeat(n) {
            val start = cal.timeInMillis
            val hourly = days[dayKey(start)]?.copyOf() ?: FloatArray(24)
            out.add(DayData(start, hourly.sum().toInt(), hourly))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return out
    }

    /** Distance, calories, active minutes and speed are estimates derived from the step count. */
    fun stats(context: Context, steps: Int): StepStats =
        statsFor(steps, getHeightCm(context), getWeightKg(context))

    fun statsFor(steps: Int, heightCm: Int, weightKg: Int): StepStats {
        val strideM = heightCm * 0.415f / 100f
        val km = steps * strideM / 1000f
        val kcal = (km * weightKg * 0.57f).toInt()
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

    fun millisUntilNextDay(now: Long = System.currentTimeMillis()): Long {
        val next = Calendar.getInstance().apply {
            timeInMillis = startOfDay(now)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now
    }

    private fun dayKey(millis: Long): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(millis)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadDays(p: SharedPreferences): MutableMap<String, FloatArray> {
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

    private fun prune(now: Long) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -KEEP_DAYS)
        }
        val cutoff = dayKey(cal.timeInMillis)
        days.keys.removeAll { it < cutoff }
    }
}

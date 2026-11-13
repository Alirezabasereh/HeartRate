package com.example.heartrate

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * ذخیره‌ی سبک و بدون دیتابیس خارجی سوابق ضربان قلب.
 * از SharedPreferences به همراه یک آرایه‌ی JSON استفاده می‌کند.
 */
object HeartRateStorage {

    private const val PREFS_NAME = "heart_rate_prefs"
    private const val KEY_RECORDS = "records"
    private const val MAX_RECORDS = 200

    fun saveRecord(context: Context, bpm: Int, timestamp: Long) {
        val records = getRecords(context).toMutableList()
        records.add(0, HeartRecord(bpm, timestamp)) // جدیدترین رکورد اول لیست

        val trimmed = if (records.size > MAX_RECORDS) records.take(MAX_RECORDS) else records

        val arr = JSONArray()
        for (r in trimmed) {
            val obj = JSONObject()
            obj.put("bpm", r.bpm)
            obj.put("timestamp", r.timestamp)
            arr.put(obj)
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECORDS, arr.toString())
            .apply()
    }

    fun getRecords(context: Context): List<HeartRecord> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECORDS, null) ?: return emptyList()

        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<HeartRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(HeartRecord(obj.getInt("bpm"), obj.getLong("timestamp")))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_RECORDS)
            .apply()
    }
}

package com.example.heartrate

/**
 * ناحیه‌ی ضربان: کم (<60)، نرمال (60..100)، زیاد (>100)
 * و تبدیل bpm به موقعیت نشانگر روی نوار بازه (۰..۱).
 */
enum class HeartZone(val labelRes: Int, val colorRes: Int) {
    LOW(R.string.zone_low, R.color.status_alert),
    NORMAL(R.string.zone_normal, R.color.status_normal),
    HIGH(R.string.zone_high, R.color.status_alert);

    companion object {
        // نوار بازه: [۰..۰٫۲۸] کم، [۰٫۲۸..۰٫۷۲] نرمال، [۰٫۷۲..۱] زیاد
        const val LOW_END = 0.28f
        const val HIGH_START = 0.72f

        fun of(bpm: Int): HeartZone = when {
            bpm < 60 -> LOW
            bpm <= 100 -> NORMAL
            else -> HIGH
        }

        fun fraction(bpm: Int): Float {
            val f = when {
                bpm < 60 -> LOW_END * ((bpm - 40) / 20f)
                bpm <= 100 -> LOW_END + (HIGH_START - LOW_END) * ((bpm - 60) / 40f)
                else -> HIGH_START + (1f - HIGH_START) * ((bpm - 100) / 40f)
            }
            return f.coerceIn(0f, 1f)
        }
    }
}

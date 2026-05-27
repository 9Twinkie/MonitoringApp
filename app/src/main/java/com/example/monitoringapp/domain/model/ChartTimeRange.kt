package com.example.monitoringapp.domain.model

import androidx.annotation.StringRes
import com.example.monitoringapp.R

enum class ChartTimeRange(
    val minutes: Int,
    @StringRes val labelRes: Int
) {
    MIN_30(30, R.string.range_30m),
    HOUR(60, R.string.range_1h),
    HOURS_5(300, R.string.range_5h),
    DAY_1(1_440, R.string.range_1d),
    DAYS_5(7_200, R.string.range_5d),
    WEEK(10_080, R.string.range_7d),
    DAYS_15(21_600, R.string.range_15d),
    MONTH(43_200, R.string.range_30d),
    WEEKS_2(20_160, R.string.range_2w),
    MONTHS_2(86_400, R.string.range_2mo),
    MONTHS_3(129_600, R.string.range_3mo),
    HALF_YEAR(262_800, R.string.range_6mo);

    val step: String
        get() = when {
            minutes <= 60 -> "15s"
            minutes <= 360 -> "1m"
            minutes <= 1_440 -> "5m"
            minutes <= 10_080 -> "15m"
            minutes <= 43_200 -> "1h"
            else -> "6h"
        }

    val apiHours: Int? get() = if (minutes >= 60) minutes / 60 else null

    val apiMinutes: Int? get() = if (minutes < 60) minutes else null

    companion object {
        fun fromMinutes(minutes: Int): ChartTimeRange =
            entries.firstOrNull { it.minutes == minutes } ?: HOUR
    }
}

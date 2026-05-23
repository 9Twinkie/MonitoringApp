package com.example.monitoringapp.domain.model

import androidx.annotation.StringRes
import com.example.monitoringapp.R

enum class ChartTimeRange(
    val minutes: Int,
    @StringRes val labelRes: Int
) {
    HOUR(60, R.string.range_1h),
    MIN_30(30, R.string.range_30m),
    MIN_15(15, R.string.range_15m),
    MIN_5(5, R.string.range_5m),
    MIN_1(1, R.string.range_1m);

    val step: String
        get() = when {
            minutes <= 1 -> "5s"
            minutes <= 5 -> "10s"
            else -> "15s"
        }

    val apiHours: Int? get() = if (minutes >= 60) minutes / 60 else null

    val apiMinutes: Int? get() = if (minutes < 60) minutes else null
}

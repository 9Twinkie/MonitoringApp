package com.example.monitoringapp.data.mapper

import com.example.monitoringapp.domain.model.MetricPoint

object ChartRangeMapper {

    fun filterByRange(points: List<MetricPoint>, rangeMinutes: Int): List<MetricPoint> {
        if (points.isEmpty() || rangeMinutes <= 0) return points
        val cutoff = System.currentTimeMillis() - rangeMinutes * 60_000L
        return points.filter { it.timestamp >= cutoff }.sortedBy { it.timestamp }
    }
}

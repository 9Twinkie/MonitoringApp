package com.example.monitoringapp.utils

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.example.monitoringapp.R
import com.example.monitoringapp.domain.model.MetricPoint
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ChartHelper {

    private val tagSetup = R.id.chart_setup_done
    private val tagDataSig = R.id.chart_data_sig

    fun setupLineChart(chart: LineChart, context: Context, interactive: Boolean = true) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(interactive)
        chart.isDragEnabled = interactive
        chart.setScaleEnabled(interactive)
        chart.setPinchZoom(interactive)
        chart.legend.isEnabled = !interactive
        chart.axisRight.isEnabled = false
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.setDrawGridLines(false)
        chart.axisLeft.setDrawGridLines(true)
        chart.setNoDataText(context.getString(R.string.chart_loading))
    }

    fun bindMetricChart(
        chart: LineChart,
        context: Context,
        primary: List<MetricPoint>,
        threshold: Float? = null,
        emptyText: String? = null,
        interactive: Boolean = true,
        keepExistingWhenEmpty: Boolean = false
    ) {
        if (chart.getTag(tagSetup) != true) {
            setupLineChart(chart, context, interactive)
            chart.animateX(0)
            chart.animateY(0)
            chart.setTag(tagSetup, true)
        }

        if (primary.isEmpty()) {
            if (keepExistingWhenEmpty && chart.data != null) return
            chart.data = null
            chart.setNoDataText(emptyText ?: context.getString(R.string.chart_no_data))
            chart.invalidate()
            return
        }

        val signature = chartSignature(primary, threshold)
        if (chart.getTag(tagDataSig) == signature) return
        chart.setTag(tagDataSig, signature)

        val sorted = primary.sortedBy { it.timestamp }
        val minTs = sorted.first().timestamp
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val primaryColor = ContextCompat.getColor(context, R.color.chart_line_primary)

        val entries = sorted.map { point ->
            val xMinutes = (point.timestamp - minTs) / 60_000f
            Entry(xMinutes, point.value)
        }

        val dataSet = LineDataSet(entries, context.getString(R.string.chart_series_metric)).apply {
            color = primaryColor
            setCircleColor(primaryColor)
            lineWidth = 2f
            circleRadius = if (interactive) 3f else 2f
            setDrawCircleHole(false)
            mode = LineDataSet.Mode.LINEAR
            setDrawFilled(false)
            valueTextSize = 0f
        }

        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val ts = minTs + (value * 60_000).toLong()
                return timeFormat.format(Date(ts))
            }
        }

        chart.data = LineData(dataSet)

        chart.axisLeft.removeAllLimitLines()
        threshold?.let { value ->
            chart.axisLeft.addLimitLine(
                LimitLine(value).apply {
                    lineColor = Color.RED
                    lineWidth = 1.5f
                    enableDashedLine(10f, 8f, 0f)
                    label = context.getString(R.string.chart_threshold_label)
                }
            )
        }

        chart.invalidate()
    }

    private fun chartSignature(points: List<MetricPoint>, threshold: Float?): String {
        if (points.isEmpty()) return "empty"
        val sorted = points.sortedBy { it.timestamp }
        return "${sorted.size}:${sorted.first().timestamp}:${sorted.last().timestamp}:$threshold"
    }
}

package com.example.monitoringapp.utils

import android.content.Context
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
        chart.setBackgroundColor(ContextCompat.getColor(context, R.color.chart_background))
        applyChartColors(chart, context)
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
        } else {
            applyChartColors(chart, context)
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
        val maxTs = sorted.last().timestamp
        val spanMs = (maxTs - minTs).coerceAtLeast(0L)
        val timeFormat = timeFormatForSpan(spanMs)
        val primaryColor = ContextCompat.getColor(context, R.color.chart_line_primary)
        val axisTextColor = ContextCompat.getColor(context, R.color.chart_axis_text)

        val entries = sorted.map { point ->
            val xMinutes = (point.timestamp - minTs) / 60_000f
            Entry(xMinutes, point.value)
        }

        val dataSet = LineDataSet(entries, context.getString(R.string.chart_series_metric)).apply {
            color = primaryColor
            lineWidth = if (interactive) 2f else 1.5f
            mode = LineDataSet.Mode.LINEAR
            setDrawCircles(false)
            setDrawCircleHole(false)
            setDrawValues(false)
            setDrawFilled(false)
            highLightColor = primaryColor
            setDrawHighlightIndicators(interactive)
        }

        chart.xAxis.apply {
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val ts = minTs + (value * 60_000).toLong()
                    return timeFormat.format(Date(ts))
                }
            }
            textColor = axisTextColor
        }
        chart.axisLeft.textColor = axisTextColor
        chart.legend.textColor = axisTextColor

        chart.data = LineData(dataSet)

        chart.axisLeft.removeAllLimitLines()
        threshold?.let { value ->
            chart.axisLeft.addLimitLine(
                LimitLine(value).apply {
                    lineColor = ContextCompat.getColor(context, R.color.chart_threshold)
                    lineWidth = 1.5f
                    enableDashedLine(10f, 8f, 0f)
                    label = context.getString(R.string.chart_threshold_label)
                    textColor = axisTextColor
                    textSize = 10f
                }
            )
        }

        refreshChartViewport(chart)
    }

    /** Перерисовка после смены ориентации / layout (когда View только что создан). */
    private fun refreshChartViewport(chart: LineChart) {
        val redraw = Runnable {
            chart.notifyDataSetChanged()
            chart.fitScreen()
            chart.invalidate()
        }
        if (chart.width > 0 && chart.height > 0) {
            redraw.run()
        } else {
            chart.post(redraw)
        }
    }

    private fun applyChartColors(chart: LineChart, context: Context) {
        val axisText = ContextCompat.getColor(context, R.color.chart_axis_text)
        val gridColor = ContextCompat.getColor(context, R.color.chart_grid)
        chart.setNoDataTextColor(axisText)
        chart.xAxis.textColor = axisText
        chart.axisLeft.textColor = axisText
        chart.axisLeft.gridColor = gridColor
        chart.legend.textColor = axisText
    }

    private fun timeFormatForSpan(spanMs: Long): SimpleDateFormat {
        val locale = Locale.getDefault()
        return when {
            spanMs > 90L * 24 * 60 * 60 * 1000 -> SimpleDateFormat("dd.MM.yy", locale)
            spanMs > 2L * 24 * 60 * 60 * 1000 -> SimpleDateFormat("dd.MM HH:mm", locale)
            else -> SimpleDateFormat("HH:mm", locale)
        }
    }

    private fun chartSignature(points: List<MetricPoint>, threshold: Float?): String {
        if (points.isEmpty()) return "empty"
        val sorted = points.sortedBy { it.timestamp }
        return "${sorted.size}:${sorted.first().timestamp}:${sorted.last().timestamp}:$threshold"
    }
}

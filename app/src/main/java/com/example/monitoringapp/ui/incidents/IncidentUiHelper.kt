package com.example.monitoringapp.ui.incidents

import androidx.core.view.isVisible
import com.example.monitoringapp.R
import com.example.monitoringapp.databinding.LayoutFeaturedIncidentBinding
import com.example.monitoringapp.domain.model.Incident
import com.example.monitoringapp.domain.model.IncidentSeverity
import com.example.monitoringapp.domain.model.IncidentStatus
import com.example.monitoringapp.domain.model.MetricPoint
import com.example.monitoringapp.utils.ChartHelper

object IncidentUiHelper {

    fun bindFeatured(
        binding: LayoutFeaturedIncidentBinding,
        incident: Incident,
        chartPoints: List<MetricPoint> = incident.chartPoints,
        chartThreshold: Float? = null,
        chartLoading: Boolean = false,
        showActions: Boolean = true
    ) {
        binding.root.isVisible = true
        bindTexts(binding, incident, showActions)
        bindChart(binding, incident, chartPoints, chartThreshold, chartLoading)
    }

    fun bindTexts(
        binding: LayoutFeaturedIncidentBinding,
        incident: Incident,
        showActions: Boolean = true
    ) {
        binding.tvTitle.text = incident.title
        binding.tvHost.text = incident.host
        binding.tvMetric.text = incident.metricValue ?: incident.metricName ?: "—"
        binding.tvTime.text = formatTime(incident.createdAt)
        binding.tvRule.text = incident.rule ?: incident.metricName.orEmpty()
        binding.tvRule.isVisible = binding.tvRule.text.isNotBlank()
        binding.tvSeverity.setText(severityLabel(incident.severity))
        binding.tvStatus.setText(statusLabel(incident.status))
        binding.btnAccept.isVisible = showActions
        binding.btnConfirm.isVisible = showActions
        binding.btnGraphs.isVisible = showActions
    }

    fun bindChart(
        binding: LayoutFeaturedIncidentBinding,
        incident: Incident,
        chartPoints: List<MetricPoint>,
        chartThreshold: Float? = null,
        chartLoading: Boolean = false
    ) {
        val threshold = chartThreshold ?: incident.threshold ?: inferThreshold(incident)
        ChartHelper.bindMetricChart(
            chart = binding.chartMini,
            context = binding.root.context,
            primary = chartPoints,
            threshold = threshold,
            emptyText = if (chartLoading) {
                binding.root.context.getString(R.string.chart_loading)
            } else {
                binding.root.context.getString(R.string.chart_no_data)
            },
            interactive = false,
            keepExistingWhenEmpty = chartLoading
        )
    }

    private fun formatTime(createdAt: String?): String {
        if (createdAt.isNullOrBlank()) return "—"
        return createdAt.replace('T', ' ').takeLast(8).takeIf { it.contains(':') }
            ?: createdAt.takeLast(5)
    }

    private fun inferThreshold(incident: Incident): Float? {
        if (incident.metricName?.startsWith("up") == true) return 0.5f
        return null
    }

    private fun severityLabel(severity: IncidentSeverity): Int = when (severity) {
        IncidentSeverity.CRITICAL -> R.string.severity_critical
        IncidentSeverity.WARNING -> R.string.severity_warning
        else -> R.string.severity_info
    }

    private fun statusLabel(status: IncidentStatus): Int = when (status) {
        IncidentStatus.NEW -> R.string.status_new
        IncidentStatus.ACCEPTED -> R.string.status_accepted
        IncidentStatus.CONFIRMED -> R.string.status_confirmed
        IncidentStatus.CLOSED -> R.string.status_closed
        else -> R.string.status_new
    }
}

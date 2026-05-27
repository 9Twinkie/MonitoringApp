package com.example.monitoringapp.ui.incidents

import androidx.core.view.isVisible
import com.example.monitoringapp.R
import com.example.monitoringapp.databinding.LayoutFeaturedIncidentBinding
import com.example.monitoringapp.domain.model.Incident
import com.example.monitoringapp.domain.model.IncidentStatus
import com.example.monitoringapp.domain.model.MetricPoint
import com.example.monitoringapp.utils.ChartHelper
import com.example.monitoringapp.utils.IncidentDisplayHelper

object IncidentUiHelper {

    fun bindFeatured(
        binding: LayoutFeaturedIncidentBinding,
        incident: Incident,
        chartPoints: List<MetricPoint> = incident.chartPoints,
        chartThreshold: Float? = null,
        chartLoading: Boolean = false,
        currentUsername: String? = null
    ) {
        binding.root.isVisible = true
        bindTexts(binding, incident, currentUsername)
        bindChart(binding, incident, chartPoints, chartThreshold, chartLoading)
    }

    fun bindTexts(
        binding: LayoutFeaturedIncidentBinding,
        incident: Incident,
        currentUsername: String? = null
    ) {
        binding.tvTitle.text = incident.title
        binding.tvHost.text = IncidentDisplayHelper.subtitle(incident)
        binding.tvMetric.text = IncidentDisplayHelper.promql(incident)
        binding.tvTime.text = IncidentDisplayHelper.alertTime(incident)
        val valueLine = IncidentDisplayHelper.metricValueLine(incident)
        binding.tvRule.text = valueLine.orEmpty()
        binding.tvRule.isVisible = !valueLine.isNullOrBlank()
        com.example.monitoringapp.utils.SeverityUiHelper.applyBadge(binding.tvSeverity, incident.severity)
        binding.tvStatus.setText(statusLabel(incident.status))
        bindAssignedEngineer(binding, incident)
        bindClosedInfo(binding, incident)
        bindActions(binding, incident, currentUsername)
    }

    fun bindAssignedEngineer(
        binding: LayoutFeaturedIncidentBinding,
        incident: Incident
    ) {
        if (incident.status == IncidentStatus.CLOSED) {
            binding.tvAssignedEngineer.isVisible = false
            return
        }
        val username = incident.assignedEngineerUsername?.takeIf { it.isNotBlank() }
        val show = username != null || incident.status.isInProgress()
        binding.tvAssignedEngineer.isVisible = show
        if (!show) return
        binding.tvAssignedEngineer.text = if (username != null) {
            binding.root.context.getString(R.string.assigned_engineer, username)
        } else {
            binding.root.context.getString(R.string.assigned_engineer_unknown)
        }
    }

    fun bindClosedInfo(
        binding: LayoutFeaturedIncidentBinding,
        incident: Incident
    ) {
        binding.tvClosedInfo.isVisible = incident.status == IncidentStatus.CLOSED
        if (incident.status != IncidentStatus.CLOSED) return
        val parts = buildList {
            incident.closedByUsername?.takeIf { it.isNotBlank() }?.let {
                add(binding.root.context.getString(R.string.closed_by, it))
            }
            incident.assignedEngineerUsername?.takeIf { it.isNotBlank() }?.let {
                add(binding.root.context.getString(R.string.executed_by, it))
            }
            incident.closeComment?.takeIf { it.isNotBlank() }?.let {
                add(binding.root.context.getString(R.string.close_comment_label, it))
            }
        }
        binding.tvClosedInfo.text = parts.joinToString("\n")
    }

    fun bindActions(
        binding: LayoutFeaturedIncidentBinding,
        incident: Incident,
        currentUsername: String? = null
    ) {
        val actions = IncidentActionHelper.actionsFor(incident, currentUsername)
        binding.btnAccept.isVisible = actions.showAccept
        binding.btnConfirm.isVisible = actions.showComplete
        binding.btnClose.isVisible = actions.showClose
        val canOpenChart = IncidentGraphNavigator.chartQuery(incident) != null
        binding.btnGraphs.isVisible = true
        binding.btnGraphs.isEnabled = canOpenChart
        binding.btnGraphs.alpha = if (canOpenChart) 1f else 0.45f
        binding.chartMiniContainer.isClickable = canOpenChart
        binding.chartMiniContainer.isFocusable = canOpenChart
        binding.actionsRow.isVisible =
            actions.showAccept || actions.showComplete || actions.showClose || binding.btnGraphs.isVisible
    }

    fun bindChart(
        binding: LayoutFeaturedIncidentBinding,
        incident: Incident,
        chartPoints: List<MetricPoint>,
        chartThreshold: Float? = null,
        chartLoading: Boolean = false
    ) {
        val threshold = chartThreshold ?: incident.threshold ?: inferThreshold(incident)
        binding.chartMini.isClickable = false
        binding.chartMini.isFocusable = false
        val chart = binding.chartMini
        val bind = {
            ChartHelper.bindMetricChart(
                chart = chart,
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
        if (chart.width > 0 && chart.height > 0) bind() else chart.post { bind() }
    }

    private fun inferThreshold(incident: Incident): Float? {
        val query = IncidentDisplayHelper.promql(incident)
        if (query.startsWith("up", ignoreCase = true)) return 0.5f
        return null
    }

    fun statusLabelRes(status: IncidentStatus): Int = when (status) {
        IncidentStatus.NEW -> R.string.status_new
        IncidentStatus.ACCEPTED -> R.string.status_accepted
        IncidentStatus.IN_PROGRESS -> R.string.status_in_progress
        IncidentStatus.CONFIRMED -> R.string.status_completed
        IncidentStatus.CLOSED -> R.string.status_closed
        else -> R.string.status_new
    }

    private fun statusLabel(status: IncidentStatus): Int = statusLabelRes(status)
}

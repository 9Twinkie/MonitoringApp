package com.example.monitoringapp.ui.metrics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monitoringapp.data.mapper.ChartDataMapper
import com.example.monitoringapp.domain.model.ChartTimeRange
import com.example.monitoringapp.domain.model.Incident
import com.example.monitoringapp.domain.model.IncidentStatus
import com.example.monitoringapp.domain.model.MetricOverview
import com.example.monitoringapp.domain.model.MetricPoint
import com.example.monitoringapp.domain.repository.IncidentRepository
import com.example.monitoringapp.domain.repository.MetricsRepository
import com.example.monitoringapp.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MetricChartOption(
    val label: String,
    val query: String,
    val threshold: Float?,
    val incidentId: Long? = null
)

data class MetricsNavArgs(
    val incidentId: Long = -1L,
    val metricQuery: String? = null,
    val threshold: Float = -1f
)

@HiltViewModel
class MetricsViewModel @Inject constructor(
    private val metricsRepository: MetricsRepository,
    private val incidentRepository: IncidentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var navIncidentId: Long = savedStateHandle.get<Long>("incidentId") ?: -1L
    private var navQuery: String? = savedStateHandle.get<String>("metricQuery")
    private var navThreshold: Float = savedStateHandle.get<Float>("threshold") ?: -1f

    private var appliedArgsKey: String? = null
    private var chartJob: Job? = null

    private val _options = MutableStateFlow<List<MetricChartOption>>(emptyList())
    val options: StateFlow<List<MetricChartOption>> = _options.asStateFlow()

    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    private val _chartPoints = MutableStateFlow<UiState<List<MetricPoint>>>(UiState.Loading)
    val chartPoints: StateFlow<UiState<List<MetricPoint>>> = _chartPoints.asStateFlow()

    private val _selectedRange = MutableStateFlow(ChartTimeRange.HOUR)
    val selectedRange: StateFlow<ChartTimeRange> = _selectedRange.asStateFlow()

    private var activeThreshold: Float? = null

    init {
        onScreenVisible(MetricsNavArgs(navIncidentId, navQuery, navThreshold))
    }

    fun onScreenVisible(args: MetricsNavArgs) {
        val key = "${args.incidentId}|${args.metricQuery}|${args.threshold}"
        if (key == appliedArgsKey) return
        appliedArgsKey = key
        navIncidentId = args.incidentId
        navQuery = args.metricQuery
        navThreshold = args.threshold
        _selectedIndex.value = 0
        load()
    }

    fun load() {
        chartJob?.cancel()
        chartJob = viewModelScope.launch {
            if (navIncidentId > 0) {
                incidentRepository.getIncident(navIncidentId)?.let { incident ->
                    showIncident(incident)
                    return@launch
                }
            }

            val queryFromNav = navQuery
            if (!queryFromNav.isNullOrBlank()) {
                findIncidentByMetric(queryFromNav)?.let { incident ->
                    showIncident(incident)
                    return@launch
                }
                val threshold = navThreshold.takeIf { it >= 0f }
                val promql = ChartDataMapper.buildQuery(queryFromNav) ?: queryFromNav
                _options.value = listOf(
                    MetricChartOption(queryFromNav, promql, threshold)
                )
                loadChartForOption(_options.value.first())
                return@launch
            }

            val activeIncident = incidentRepository.incidentsFlow.first()
                .firstOrNull { it.status != IncidentStatus.CLOSED }
            if (activeIncident != null) {
                showIncident(activeIncident, includeOverview = true)
                return@launch
            }

            loadFromOverview()
        }
    }

    fun selectSeries(index: Int) {
        _selectedIndex.value = index
        reloadCurrentChart()
    }

    fun selectRange(range: ChartTimeRange) {
        if (_selectedRange.value == range) return
        _selectedRange.value = range
        reloadCurrentChart()
    }

    fun currentOption(): MetricChartOption? =
        _options.value.getOrNull(_selectedIndex.value)

    fun currentThreshold(): Float? = activeThreshold

    private fun reloadCurrentChart() {
        val option = currentOption() ?: return
        chartJob?.cancel()
        chartJob = viewModelScope.launch {
            loadChartForOption(option)
        }
    }

    private fun emptyRangeMessage(): String {
        val minutes = _selectedRange.value.minutes
        return if (minutes >= 60) {
            "Нет данных за последний час"
        } else {
            "Нет данных за последние $minutes мин"
        }
    }

    private suspend fun showIncident(incident: Incident, includeOverview: Boolean = false) {
        val label = incident.host.takeIf { it.isNotBlank() && it != "—" }
            ?: incident.title
        val promql = ChartDataMapper.buildQuery(incident.metricName)
            ?: incident.metricName
            ?: label
        val threshold = incident.threshold ?: navThreshold.takeIf { it >= 0f }
        activeThreshold = threshold

        val incidentOption = MetricChartOption(
            label = label,
            query = promql,
            threshold = threshold,
            incidentId = incident.id
        )
        _options.value = if (includeOverview) {
            buildOverviewOptions() + incidentOption
        } else {
            listOf(incidentOption)
        }.distinctBy { it.query }
        _selectedIndex.value = _options.value.indexOfFirst { it.incidentId == incident.id }
            .takeIf { it >= 0 } ?: 0
        loadChartForOption(_options.value[_selectedIndex.value])
    }

    private suspend fun loadFromOverview() {
        metricsRepository.getOverview().fold(
            onSuccess = { overview -> applyOverview(overview) },
            onFailure = {
                val cached = metricsRepository.getCachedOverview()
                if (cached != null) {
                    applyOverview(cached)
                } else {
                    _chartPoints.value = UiState.Error(it.message ?: "Ошибка загрузки")
                }
            }
        )
    }

    private suspend fun applyOverview(overview: MetricOverview) {
        val items = buildOptions(overview)
        _options.value = items
        if (items.isNotEmpty()) {
            loadChart(0)
        } else {
            _chartPoints.value = UiState.Error("Нет метрик")
        }
    }

    private suspend fun buildOverviewOptions(): List<MetricChartOption> {
        val overview = metricsRepository.getCachedOverview()
            ?: metricsRepository.getOverview().getOrNull()
            ?: return emptyList()
        return buildOptions(overview)
    }

    private fun loadChart(index: Int) {
        _selectedIndex.value = index
        reloadCurrentChart()
    }

    private suspend fun loadChartForOption(option: MetricChartOption) {
        val rangeMinutes = _selectedRange.value.minutes
        activeThreshold = option.threshold
        _chartPoints.value = UiState.Loading

        option.incidentId?.let { id ->
            incidentRepository.getIncident(id)?.let { incident ->
                loadIncidentChart(incident)
                return
            }
        }

        findIncidentByMetric(option.query)?.let { incident ->
            loadIncidentChart(incident)
            return
        }

        val points = metricsRepository.loadMetricRange(option.query, rangeMinutes).getOrElse { error ->
            _chartPoints.value = UiState.Error(error.message ?: "Не удалось загрузить график")
            return
        }
        if (points.isEmpty()) {
            _chartPoints.value = UiState.Error(emptyRangeMessage())
        } else {
            _chartPoints.value = UiState.Success(points)
        }
    }

    private suspend fun loadIncidentChart(incident: Incident) {
        val rangeMinutes = _selectedRange.value.minutes
        activeThreshold = incident.threshold ?: activeThreshold
        incidentRepository.loadIncidentChart(incident, rangeMinutes).fold(
            onSuccess = { chart ->
                activeThreshold = chart.threshold ?: activeThreshold
                if (chart.points.isEmpty()) {
                    val promql = ChartDataMapper.buildQuery(incident.metricName)
                        ?: incident.metricName
                    if (!promql.isNullOrBlank()) {
                        metricsRepository.loadMetricRange(promql, rangeMinutes).fold(
                            onSuccess = { points ->
                                if (points.isEmpty()) {
                                    _chartPoints.value = UiState.Error(emptyRangeMessage())
                                } else {
                                    _chartPoints.value = UiState.Success(points)
                                }
                            },
                            onFailure = {
                                _chartPoints.value = UiState.Error(
                                    it.message ?: "Не удалось загрузить график"
                                )
                            }
                        )
                    } else {
                        _chartPoints.value = UiState.Error(emptyRangeMessage())
                    }
                } else {
                    _chartPoints.value = UiState.Success(chart.points)
                }
            },
            onFailure = {
                _chartPoints.value = UiState.Error(it.message ?: "Не удалось загрузить график")
            }
        )
    }

    private suspend fun findIncidentByMetric(query: String?): Incident? {
        if (query.isNullOrBlank()) return null
        val normalized = ChartDataMapper.buildQuery(query) ?: query
        return incidentRepository.incidentsFlow.first().firstOrNull { incident ->
            val name = incident.metricName ?: return@firstOrNull false
            name == query ||
                name == normalized ||
                ChartDataMapper.buildQuery(name) == normalized
        }
    }

    private fun buildOptions(overview: MetricOverview): List<MetricChartOption> {
        val items = mutableListOf<MetricChartOption>()
        overview.targets.forEach { target ->
            val query = ChartDataMapper.buildQueryFromLabels("job=\"${target.name}\"")
                ?: "up{job=\"${target.name}\"}"
            items.add(MetricChartOption(target.name, query, 0.5f))
        }
        overview.series.forEach { series ->
            val query = ChartDataMapper.buildQuery(series.name) ?: series.name
            items.add(MetricChartOption(series.name, query, series.threshold ?: 0.5f))
        }
        return items.distinctBy { it.query }
    }
}

package com.example.monitoringapp.ui.metrics

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monitoringapp.R
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private var navIncidentId: Long = savedStateHandle.get<Long>("incidentId") ?: -1L
    private var navQuery: String? = savedStateHandle.get<String>("metricQuery")
    private var navThreshold: Float = savedStateHandle.get<Float>("threshold") ?: -1f

    private var appliedArgsKey: String? = null
    private var chartJob: Job? = null
    private var searchJob: Job? = null

    private val _queryInput = MutableStateFlow("")
    val queryInput: StateFlow<String> = _queryInput.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _chartPoints = MutableStateFlow<UiState<List<MetricPoint>>>(UiState.Loading)
    val chartPoints: StateFlow<UiState<List<MetricPoint>>> = _chartPoints.asStateFlow()

    private val _selectedRange = MutableStateFlow(
        ChartTimeRange.fromMinutes(metricsRepository.getLastChartRangeMinutes())
    )
    val selectedRange: StateFlow<ChartTimeRange> = _selectedRange.asStateFlow()

    private var activeThreshold: Float? = null
    private var suppressSearch = false

    init {
        val navArgs = MetricsNavArgs(navIncidentId, navQuery, navThreshold)
        if (hasNavIntent(navArgs)) {
            onScreenVisible(navArgs)
        } else {
            restoreSessionOrLoadDefaults()
        }
    }

    fun onScreenVisible(args: MetricsNavArgs) {
        if (hasNavIntent(args)) {
            val key = "${args.incidentId}|${args.metricQuery}|${args.threshold}"
            if (key == appliedArgsKey) return
            appliedArgsKey = key
            navIncidentId = args.incidentId
            navQuery = args.metricQuery
            navThreshold = args.threshold
            load()
            return
        }

        if (appliedArgsKey == SESSION_KEY && _chartPoints.value is UiState.Success) return

        val saved = metricsRepository.getLastChartQuery()
        if (!saved.isNullOrBlank()) {
            appliedArgsKey = SESSION_KEY
            if (_queryInput.value != saved) {
                _queryInput.value = saved
            }
            if (_chartPoints.value !is UiState.Success) {
                executeQuery(saved)
            }
            return
        }

        if (appliedArgsKey != null) return
        appliedArgsKey = "default"
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
                val promql = ChartDataMapper.buildQuery(queryFromNav) ?: queryFromNav
                setQueryAndRun(promql, navThreshold.takeIf { it >= 0f })
                return@launch
            }

            val activeIncident = incidentRepository.incidentsFlow.first()
                .firstOrNull { it.status != IncidentStatus.CLOSED }
            if (activeIncident != null) {
                showIncident(activeIncident)
                return@launch
            }

            loadFromOverview()
        }
    }

    fun onQueryInputChanged(text: String) {
        if (suppressSearch) return
        _queryInput.value = text
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            fetchSuggestions(text)
        }
    }

    fun selectSuggestion(value: String) {
        setQueryAndRun(value)
    }

    fun executeQuery(query: String? = _queryInput.value) {
        val trimmed = query?.trim().orEmpty()
        if (trimmed.isEmpty()) return
        metricsRepository.setLastChartQuery(trimmed)
        metricsRepository.setLastChartRangeMinutes(_selectedRange.value.minutes)
        appliedArgsKey = SESSION_KEY
        chartJob?.cancel()
        chartJob = viewModelScope.launch {
            loadChartForPromql(trimmed)
        }
    }

    fun selectRange(range: ChartTimeRange) {
        if (_selectedRange.value == range) return
        _selectedRange.value = range
        metricsRepository.setLastChartRangeMinutes(range.minutes)
        executeQuery()
    }

    fun currentThreshold(): Float? = activeThreshold

    private fun restoreSessionOrLoadDefaults() {
        val saved = metricsRepository.getLastChartQuery()
        if (!saved.isNullOrBlank()) {
            appliedArgsKey = SESSION_KEY
            _queryInput.value = saved
            executeQuery(saved)
            return
        }
        onScreenVisible(MetricsNavArgs())
    }

    private fun hasNavIntent(args: MetricsNavArgs): Boolean =
        args.incidentId > 0L || !args.metricQuery.isNullOrBlank()

    private fun setQueryAndRun(promql: String, threshold: Float? = null) {
        suppressSearch = true
        _queryInput.value = promql
        activeThreshold = threshold
        suppressSearch = false
        _suggestions.value = emptyList()
        executeQuery(promql)
    }

    private suspend fun fetchSuggestions(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _suggestions.value = emptyList()
            return
        }
        metricsRepository.searchMetricSuggestions(trimmed).fold(
            onSuccess = { list -> _suggestions.value = list },
            onFailure = { _suggestions.value = emptyList() }
        )
    }

    private fun emptyRangeMessage(): String {
        val range = _selectedRange.value
        return appContext.getString(
            R.string.chart_no_data_range,
            appContext.getString(range.labelRes)
        )
    }

    private suspend fun showIncident(incident: Incident) {
        val promql = sequenceOf(
            incident.metricExpr,
            ChartDataMapper.buildQuery(incident.metricName),
            incident.metricName
        ).firstOrNull { !it.isNullOrBlank() }
            ?: incident.title
        val threshold = incident.threshold ?: navThreshold.takeIf { it >= 0f }
        setQueryAndRun(promql, threshold)
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
        val first = buildOptions(overview).firstOrNull()
        if (first != null) {
            setQueryAndRun(first.query, first.threshold)
        } else {
            _chartPoints.value = UiState.Error("Нет метрик")
        }
    }

    private suspend fun loadChartForPromql(promql: String) {
        val normalized = ChartDataMapper.buildQuery(promql) ?: promql
        _chartPoints.value = UiState.Loading

        findIncidentByMetric(normalized)?.let { incident ->
            activeThreshold = incident.threshold ?: activeThreshold
            loadIncidentChart(incident)
            return
        }

        val rangeMinutes = _selectedRange.value.minutes
        metricsRepository.loadMetricRange(normalized, rangeMinutes).fold(
            onSuccess = { points ->
                if (points.isEmpty()) {
                    _chartPoints.value = UiState.Error(emptyRangeMessage())
                } else {
                    activeThreshold = inferThreshold(normalized)
                    _chartPoints.value = UiState.Success(points)
                }
            },
            onFailure = {
                _chartPoints.value = UiState.Error(it.message ?: "Не удалось загрузить график")
            }
        )
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
            val expr = incident.metricExpr
            name == query ||
                name == normalized ||
                expr == query ||
                expr == normalized ||
                ChartDataMapper.buildQuery(name) == normalized
        }
    }

    private fun inferThreshold(promql: String): Float? =
        if (promql.trim().startsWith("up", ignoreCase = true)) 0.5f else null

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

    companion object {
        private const val SESSION_KEY = "session"
    }
}

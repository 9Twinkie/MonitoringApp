package com.example.monitoringapp.ui.incidents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monitoringapp.data.remote.NotificationEventBus
import com.example.monitoringapp.domain.model.Incident
import com.example.monitoringapp.domain.model.IncidentChartData
import com.example.monitoringapp.domain.model.IncidentStatus
import com.example.monitoringapp.domain.repository.IncidentRepository
import com.example.monitoringapp.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class IncidentTab { ACTIVE, HISTORY }

data class FeaturedIncidentUi(
    val incident: Incident?,
    val chart: IncidentChartData = IncidentChartData(),
    val chartLoading: Boolean = false
) {
    fun contentKey(): String? = incident?.let {
        "${it.id}:${it.status}:${it.metricValue}:${it.title}"
    }

    fun chartKey(): String {
        if (chart.points.isNotEmpty()) {
            return "${chart.points.size}:${chart.points.first().timestamp}:" +
                "${chart.points.last().timestamp}:${chart.threshold}"
        }
        return "loading:$chartLoading"
    }
}

data class AlertsScreenState(
    val featured: FeaturedIncidentUi = FeaturedIncidentUi(null),
    val others: List<Incident> = emptyList()
) {
    val isEmpty: Boolean get() = featured.incident == null
    val listItems: List<AlertsListItem> get() = buildAlertsList(featured, others)
    val listKey: String get() = listItems.joinToString {
        when (it) {
            is AlertsListItem.Featured -> "F:${it.ui.contentKey()}:${it.ui.chartKey()}"
            AlertsListItem.Section -> "S"
            is AlertsListItem.Row -> "R:${it.incident.id}:${it.incident.status}"
        }
    }
}

@HiltViewModel
class IncidentsViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository,
    eventBus: NotificationEventBus
) : ViewModel() {

    private val _tab = MutableStateFlow(IncidentTab.ACTIVE)
    val tab: StateFlow<IncidentTab> = _tab.asStateFlow()

    private val _actionState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val actionState: StateFlow<UiState<String>> = _actionState.asStateFlow()

    private val _featuredChart = MutableStateFlow(IncidentChartData())
    private val _chartLoading = MutableStateFlow(false)

    private var chartIncidentId: Long? = null
    private var chartJob: Job? = null

    val isOffline = incidentRepository.isOfflineFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val filteredIncidents = combine(
        incidentRepository.incidentsFlow.debounce(250),
        _tab
    ) { all, tab ->
        when (tab) {
            IncidentTab.ACTIVE -> all.filter { it.status != IncidentStatus.CLOSED }
            IncidentTab.HISTORY -> all.filter { it.status == IncidentStatus.CLOSED }
        }
    }

    val alertsScreen: StateFlow<AlertsScreenState> = combine(
        filteredIncidents,
        _featuredChart,
        _chartLoading
    ) { list, chart, loading ->
        val featured = list.firstOrNull()
        AlertsScreenState(
            featured = FeaturedIncidentUi(
                incident = featured,
                chart = chart,
                chartLoading = loading
            ),
            others = if (featured != null) list.drop(1) else emptyList()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsScreenState())

    init {
        refresh()
        viewModelScope.launch {
            eventBus.messages.collect { refresh() }
        }
    }

    fun setTab(tab: IncidentTab) {
        _tab.value = tab
        chartIncidentId = null
        _featuredChart.value = IncidentChartData()
    }

    fun refresh() {
        viewModelScope.launch {
            incidentRepository.refreshIncidents()
        }
    }

    fun loadChartFor(incident: Incident) {
        if (chartIncidentId == incident.id && _featuredChart.value.points.isNotEmpty()) return
        if (chartIncidentId == incident.id && chartJob?.isActive == true) return
        chartJob?.cancel()
        chartIncidentId = incident.id
        chartJob = viewModelScope.launch {
            val showLoading = _featuredChart.value.points.isEmpty()
            if (showLoading) _chartLoading.value = true
            incidentRepository.loadIncidentChart(incident).onSuccess { chart ->
                if (chartIncidentId == incident.id) {
                    _featuredChart.value = chart
                }
            }
            if (chartIncidentId == incident.id && showLoading) {
                _chartLoading.value = false
            }
        }
    }

    fun confirm(id: Long) = perform(id) { incidentRepository.confirmIncident(id) }

    fun close(id: Long) = perform(id) { incidentRepository.closeIncident(id) }

    fun accept(id: Long) = perform(id) { incidentRepository.acceptIncident(id) }

    private fun perform(id: Long, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _actionState.value = UiState.Loading
            _actionState.value = block().fold(
                onSuccess = { UiState.Success("OK") },
                onFailure = { UiState.Error(it.message ?: "Ошибка") }
            )
        }
    }
}

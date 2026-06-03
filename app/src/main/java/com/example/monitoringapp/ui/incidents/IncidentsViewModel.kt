package com.example.monitoringapp.ui.incidents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monitoringapp.domain.model.ChartTimeRange
import com.example.monitoringapp.domain.model.Incident
import com.example.monitoringapp.domain.model.IncidentChartData
import com.example.monitoringapp.domain.model.IncidentStatus
import com.example.monitoringapp.domain.repository.AuthRepository
import com.example.monitoringapp.domain.repository.FavoriteRepository
import com.example.monitoringapp.domain.repository.IncidentRepository
import com.example.monitoringapp.utils.ApiErrorMapper
import com.example.monitoringapp.utils.DashboardEnricher
import com.example.monitoringapp.utils.TargetMatcher
import com.example.monitoringapp.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class IncidentTab { ACTIVE, IN_PROGRESS, FAVORITES, HISTORY }

private data class FeaturedChartTarget(
    val epoch: Int,
    val tab: IncidentTab,
    val incident: Incident?
)

data class FeaturedIncidentUi(
    val incident: Incident?,
    val chart: IncidentChartData = IncidentChartData(),
    val chartLoading: Boolean = false
) {
    fun contentKey(): String? = incident?.let {
        "${it.id}:${it.status}:${it.metricValue}:${it.title}:" +
            "${it.assignedEngineerUsername}:${it.canAccept}:${it.canConfirm}:${it.canClose}:" +
            "${it.closeComment}:${it.closedByUsername}:${it.prometheusAlertActive}:${it.siteAddress}"
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
}

@HiltViewModel
class IncidentsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val incidentRepository: IncidentRepository,
    private val favoriteRepository: FavoriteRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val filterHost: String? = savedStateHandle["filterHost"]
    private val filterName: String? = savedStateHandle["filterName"]

    val objectFilterLabel: String? =
        filterName?.takeIf { it.isNotBlank() } ?: filterHost?.takeIf { it.isNotBlank() }

    val currentUsername: String? get() = authRepository.getUsername()

    private val _tab = MutableStateFlow(IncidentTab.ACTIVE)
    val tab: StateFlow<IncidentTab> = _tab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _actionState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val actionState: StateFlow<UiState<String>> = _actionState.asStateFlow()

    private val _loadError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val loadError: SharedFlow<String> = _loadError.asSharedFlow()

    private val _promptCloseIncidentId = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val promptCloseIncidentId: SharedFlow<Long> = _promptCloseIncidentId.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _featuredChart = MutableStateFlow(IncidentChartData())
    private val _chartLoading = MutableStateFlow(false)
    private val _selectedIncidentId = MutableStateFlow<Long?>(null)
    /** Смена вкладки/поиска/выбора — принудительно перезагрузить график featured. */
    private val _chartEpoch = MutableStateFlow(0)

    private var chartIncidentId: Long? = null
    private var chartJob: Job? = null

    val isOffline = incidentRepository.isOfflineFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val filteredIncidents = combine(
        incidentRepository.incidentsFlow.debounce(250),
        favoriteRepository.favoritesFlow,
        _tab,
        _searchQuery
    ) { all, favorites, tab, query ->
        val scoped = all.filter { matchesObjectFilter(it) }
        val byTab = when (tab) {
            IncidentTab.ACTIVE -> scoped.filter { it.matchesActiveTab() }
            IncidentTab.IN_PROGRESS -> scoped.filter { it.matchesInProgressTab() }
            IncidentTab.FAVORITES -> scoped.filter { incident ->
                incident.status != IncidentStatus.CLOSED &&
                    favoriteRepository.isFavoriteIncident(incident, favorites)
            }
            IncidentTab.HISTORY -> scoped.filter { it.status == IncidentStatus.CLOSED }
        }
        DashboardEnricher.sortIncidents(
            byTab.filter { DashboardEnricher.incidentMatchesSearch(it, query) }
        )
    }

    val alertsScreen: StateFlow<AlertsScreenState> = combine(
        filteredIncidents,
        _selectedIncidentId,
        _featuredChart,
        _chartLoading
    ) { list, selectedId, chart, loading ->
        val featuredIncident = selectedId
            ?.let { id -> list.firstOrNull { it.id == id } }
            ?: list.firstOrNull()
        val others = if (featuredIncident != null) {
            list.filter { it.id != featuredIncident.id }
        } else {
            emptyList()
        }
        AlertsScreenState(
            featured = FeaturedIncidentUi(
                incident = featuredIncident,
                chart = if (featuredIncident?.id == chartIncidentId) chart else IncidentChartData(),
                chartLoading = loading && featuredIncident?.id == chartIncidentId
            ),
            others = others
        )
    }.distinctUntilChanged { old, new ->
        old.featured.contentKey() == new.featured.contentKey() &&
            old.featured.chartKey() == new.featured.chartKey() &&
            old.others.map { "${it.id}:${it.status}:${it.closeComment}:${it.closedByUsername}" } ==
            new.others.map { "${it.id}:${it.status}:${it.closeComment}:${it.closedByUsername}" }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = Long.MAX_VALUE),
        AlertsScreenState()
    )

    init {
        objectFilterLabel?.let { _searchQuery.value = it }
        refresh(silent = true)
        viewModelScope.launch {
            combine(filteredIncidents, _tab, _selectedIncidentId, _chartEpoch) { list, tab, selectedId, epoch ->
                FeaturedChartTarget(
                    epoch = epoch,
                    tab = tab,
                    incident = selectedId?.let { id -> list.firstOrNull { it.id == id } }
                        ?: list.firstOrNull()
                )
            }
                .distinctUntilChanged { old, new ->
                    old.epoch == new.epoch &&
                        old.tab == new.tab &&
                        old.incident?.id == new.incident?.id
                }
                .collect { target ->
                    if (target.incident != null) {
                        loadFeaturedChart(target.incident, target.epoch)
                    } else {
                        cancelChartLoad()
                    }
                }
        }
    }

    fun clearObjectFilter() {
        // Navigation args are fixed; user can clear search instead.
        _searchQuery.value = ""
    }

    private fun matchesObjectFilter(incident: Incident): Boolean {
        val host = filterHost?.takeIf { it.isNotBlank() }
        val name = filterName?.takeIf { it.isNotBlank() }
        if (host == null && name == null) return true
        return TargetMatcher.incidentMatchesTarget(
            incident,
            name ?: incident.title,
            host ?: incident.host
        )
    }

    fun setTab(tab: IncidentTab) {
        _tab.value = tab
        _selectedIncidentId.value = null
        requestChartReload()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _selectedIncidentId.value = null
        requestChartReload()
    }

    fun selectIncident(incident: Incident) {
        if (_selectedIncidentId.value == incident.id &&
            chartIncidentId == incident.id &&
            _featuredChart.value.points.isNotEmpty()
        ) {
            return
        }
        _selectedIncidentId.value = incident.id
        requestChartReload()
    }

    /** Повторная попытка, если ViewHolder пересоздан или первая загрузка не удалась. */
    fun refreshFeaturedChartIfEmpty() {
        val incident = alertsScreen.value.featured.incident ?: return
        if (_featuredChart.value.points.isEmpty() && chartJob?.isActive != true) {
            requestChartReload()
        }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _isRefreshing.value = true
            incidentRepository.refreshIncidents().onFailure { error ->
                _loadError.emit(ApiErrorMapper.toMessage(error))
            }
            if (!silent) _isRefreshing.value = false
        }
    }

    private fun requestChartReload() {
        cancelChartLoad()
        _chartEpoch.value++
    }

    private fun cancelChartLoad() {
        chartJob?.cancel()
        chartJob = null
        chartIncidentId = null
        chartLoadEpoch = -1
        _featuredChart.value = IncidentChartData()
        _chartLoading.value = false
    }

    private var chartLoadEpoch: Int = -1

    private fun loadFeaturedChart(incident: Incident, epoch: Int) {
        if (chartIncidentId == incident.id && chartJob?.isActive == true) return
        if (chartIncidentId == incident.id &&
            _featuredChart.value.points.isNotEmpty() &&
            chartLoadEpoch == epoch
        ) {
            return
        }
        if (chartIncidentId == incident.id &&
            _featuredChart.value.points.isEmpty() &&
            chartLoadEpoch == epoch &&
            chartJob == null
        ) {
            return
        }

        chartJob?.cancel()
        chartIncidentId = incident.id
        chartLoadEpoch = epoch

        if (incident.chartPoints.isNotEmpty()) {
            _featuredChart.value = IncidentChartData(
                points = incident.chartPoints,
                threshold = incident.threshold,
                promql = incident.metricName
            )
        } else {
            _featuredChart.value = IncidentChartData()
        }

        chartJob = viewModelScope.launch {
            val targetId = incident.id
            _chartLoading.value = true
            try {
                incidentRepository.loadIncidentChart(incident, ChartTimeRange.HOUR.minutes)
                    .onSuccess { chart ->
                        if (chartIncidentId != targetId) return@onSuccess
                        if (chart.points.isNotEmpty() || _featuredChart.value.points.isEmpty()) {
                            _featuredChart.value = chart
                        }
                    }
            } finally {
                if (chartIncidentId == targetId) {
                    _chartLoading.value = false
                }
            }
        }
    }

    fun accept(id: Long) = perform(
        incidentId = id,
        successMessage = "ACCEPTED",
        onSuccess = { _tab.value = IncidentTab.IN_PROGRESS }
    ) { incidentRepository.acceptIncident(id) }

    fun close(id: Long, comment: String? = null) = perform(
        incidentId = id,
        successMessage = "CLOSED",
        onSuccess = { _tab.value = IncidentTab.HISTORY }
    ) { incidentRepository.closeIncident(id, comment) }

    suspend fun getIncidentById(id: Long): Incident? = incidentRepository.getIncident(id)

    private fun perform(
        incidentId: Long,
        successMessage: String,
        onSuccess: (() -> Unit)? = null,
        block: suspend () -> Result<Unit>
    ) {
        viewModelScope.launch {
            _actionState.value = UiState.Loading
            _actionState.value = block().fold(
                onSuccess = {
                    onSuccess?.invoke()
                    _selectedIncidentId.value = incidentId
                    requestChartReload()
                    UiState.Success(successMessage)
                },
                onFailure = { UiState.Error(it.message ?: "Ошибка") }
            )
        }
    }
}

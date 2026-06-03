package com.example.monitoringapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monitoringapp.domain.model.DashboardSummary
import com.example.monitoringapp.domain.model.FavoriteTarget
import com.example.monitoringapp.domain.model.IncidentSeverity
import com.example.monitoringapp.domain.model.IncidentStatus
import com.example.monitoringapp.domain.model.MonitoringObject
import com.example.monitoringapp.domain.repository.FavoriteRepository
import com.example.monitoringapp.domain.repository.IncidentRepository
import com.example.monitoringapp.domain.repository.MetricsRepository
import com.example.monitoringapp.utils.ApiErrorMapper
import com.example.monitoringapp.utils.DashboardEnricher
import com.example.monitoringapp.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val criticalCount: Int = 0,
    val serversOnline: Int = 0,
    val serversTotal: Int = 0,
    val objects: List<MonitoringObject> = emptyList(),
    val favoritesCount: Int = 0,
    val favoritesOnly: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val metricsRepository: MetricsRepository,
    private val incidentRepository: IncidentRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    val isOffline = incidentRepository.isOfflineFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _rawSummary = MutableStateFlow<DashboardSummary?>(null)
    private val _loadState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _warning = MutableStateFlow<String?>(null)
    val warning: StateFlow<String?> = _warning.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _favoriteMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val favoriteMessage: SharedFlow<String> = _favoriteMessage.asSharedFlow()

    private val dashboardCore = combine(
        _rawSummary,
        _loadState,
        incidentRepository.incidentsFlow.distinctUntilChanged { old, new ->
            dashboardIncidentsSignature(old) == dashboardIncidentsSignature(new)
        },
        favoriteRepository.favoritesFlow.distinctUntilChanged { old, new ->
            old.map { it.targetKey } == new.map { it.targetKey }
        },
        _searchQuery
    ) { summary, loadState, incidents, favorites, query ->
        CoreDashboardData(summary, loadState, incidents, favorites, query)
    }

    val dashboardUi: StateFlow<UiState<DashboardUiState>> = combine(
        dashboardCore,
        favoriteRepository.dashboardFavoritesOnlyFlow
    ) { core, favoritesOnly ->
        if (core.summary == null && core.loadState is UiState.Loading) {
            return@combine UiState.Loading
        }
        val base = core.summary ?: DashboardSummary(0, 0, 0, emptyList())
        var enriched = DashboardEnricher.enrichObjects(
            base.objects,
            core.incidents,
            core.favorites,
            core.query
        )
        if (favoritesOnly) {
            enriched = enriched.filter { it.isFavorite }
        }
        if (core.loadState is UiState.Error && enriched.isEmpty()) {
            return@combine UiState.Error(core.loadState.message ?: "Ошибка загрузки")
        }
        val criticalCount = core.incidents.count {
            it.severity == IncidentSeverity.CRITICAL && it.status != IncidentStatus.CLOSED
        }
        UiState.Success(
            DashboardUiState(
                criticalCount = criticalCount,
                serversOnline = base.serversOnline,
                serversTotal = base.serversTotal,
                objects = enriched,
                favoritesCount = core.favorites.size,
                favoritesOnly = favoritesOnly
            )
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        refresh()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFavoritesOnly(enabled: Boolean) {
        favoriteRepository.setDashboardFavoritesOnly(enabled)
    }

    fun toggleFavorite(item: MonitoringObject) {
        viewModelScope.launch {
            val wasFavorite = item.isFavorite
            favoriteRepository.toggleFavorite(
                FavoriteTarget(
                    targetKey = item.targetKey,
                    displayName = item.name,
                    host = item.host
                )
            )
            _favoriteMessage.emit(if (wasFavorite) "removed" else "added")
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val hasData = _rawSummary.value != null
            if (hasData) {
                _isRefreshing.value = true
            } else {
                _loadState.value = UiState.Loading
            }
            _warning.value = null
            try {
                incidentRepository.refreshIncidents().onFailure { error ->
                    _warning.value = ApiErrorMapper.toMessage(error)
                }
                val overviewResult = metricsRepository.getOverview()
                val overview = overviewResult.getOrNull()

                if (overviewResult.isFailure || overview == null) {
                    _warning.value = "Метрики Prometheus недоступны — объекты мониторинга не загружены"
                }

                runCatching {
                    metricsRepository.buildDashboardSummary(overview)
                }.fold(
                    onSuccess = { summary ->
                        val previous = _rawSummary.value
                        _rawSummary.value = when {
                            summary.objects.isNotEmpty() || previous == null -> summary
                            else -> previous.copy(criticalCount = summary.criticalCount)
                        }
                        _loadState.value = UiState.Success(Unit)
                    },
                    onFailure = {
                        _loadState.value = UiState.Error(it.message ?: "Ошибка загрузки")
                    }
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private data class CoreDashboardData(
        val summary: DashboardSummary?,
        val loadState: UiState<Unit>,
        val incidents: List<com.example.monitoringapp.domain.model.Incident>,
        val favorites: List<FavoriteTarget>,
        val query: String
    )

    companion object {
        private fun dashboardIncidentsSignature(incidents: List<com.example.monitoringapp.domain.model.Incident>): String =
            incidents
                .asSequence()
                .filter { it.status != IncidentStatus.CLOSED }
                .sortedBy { it.id }
                .joinToString("|") { incident ->
                    "${incident.id}:${incident.status}:${incident.severity}:" +
                        "${incident.host}:${incident.title}:${incident.metricName.orEmpty()}"
                }
    }
}

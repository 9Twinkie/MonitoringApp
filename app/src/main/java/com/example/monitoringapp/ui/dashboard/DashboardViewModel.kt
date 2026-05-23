package com.example.monitoringapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monitoringapp.domain.model.DashboardSummary
import com.example.monitoringapp.domain.repository.IncidentRepository
import com.example.monitoringapp.domain.repository.MetricsRepository
import com.example.monitoringapp.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val metricsRepository: MetricsRepository,
    private val incidentRepository: IncidentRepository
) : ViewModel() {

    val isOffline = incidentRepository.isOfflineFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _summary = MutableStateFlow<UiState<DashboardSummary>>(UiState.Loading)
    val summary: StateFlow<UiState<DashboardSummary>> = _summary.asStateFlow()

    private val _warning = MutableStateFlow<String?>(null)
    val warning: StateFlow<String?> = _warning.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _summary.value = UiState.Loading
            _warning.value = null
            incidentRepository.refreshIncidents()
            val overviewResult = metricsRepository.getOverview()
            val overview = overviewResult.getOrNull()

            if (overviewResult.isFailure) {
                _warning.value = "Метрики Prometheus недоступны — показаны данные по инцидентам"
            }

            _summary.value = runCatching {
                metricsRepository.buildDashboardSummary(overview)
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Ошибка загрузки") }
            )
        }
    }
}

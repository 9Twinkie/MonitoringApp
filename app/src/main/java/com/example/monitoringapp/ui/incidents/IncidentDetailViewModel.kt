package com.example.monitoringapp.ui.incidents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monitoringapp.domain.model.ChartTimeRange
import com.example.monitoringapp.domain.model.Incident
import com.example.monitoringapp.domain.model.IncidentChartData
import com.example.monitoringapp.domain.repository.AuthRepository
import com.example.monitoringapp.domain.repository.IncidentRepository
import com.example.monitoringapp.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IncidentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val incidentRepository: IncidentRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val incidentId: Long = savedStateHandle["incidentId"] ?: 0L

    val currentUsername: String? get() = authRepository.getUsername()

    private val _incident = MutableStateFlow<UiState<Incident>>(UiState.Loading)
    val incident: StateFlow<UiState<Incident>> = _incident.asStateFlow()

    private val _chart = MutableStateFlow(IncidentChartData())
    val chart: StateFlow<IncidentChartData> = _chart.asStateFlow()

    private val _action = MutableStateFlow<UiState<String>>(UiState.Idle)
    val action: StateFlow<UiState<String>> = _action.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _incident.value = UiState.Loading
            incidentRepository.refreshIncidents()
            val data = incidentRepository.getIncident(incidentId)
            if (data == null) {
                _incident.value = UiState.Error("Инцидент не найден")
                return@launch
            }
            _incident.value = UiState.Success(data)
            if (data.chartPoints.isNotEmpty()) {
                _chart.value = IncidentChartData(
                    points = data.chartPoints,
                    threshold = data.threshold,
                    promql = data.metricName
                )
            }
            incidentRepository.loadIncidentChart(data, ChartTimeRange.HOUR.minutes).onSuccess { chart ->
                _chart.value = chart
            }
        }
    }

    fun complete() = perform("COMPLETED") { incidentRepository.confirmIncident(incidentId) }

    fun close(comment: String? = null) = perform("CLOSED") {
        incidentRepository.closeIncident(incidentId, comment)
    }

    fun accept() = perform("ACCEPTED") { incidentRepository.acceptIncident(incidentId) }

    private fun perform(successCode: String, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _action.value = UiState.Loading
            _action.value = block().fold(
                onSuccess = {
                    load()
                    UiState.Success(successCode)
                },
                onFailure = { UiState.Error(it.message ?: "Ошибка") }
            )
        }
    }
}

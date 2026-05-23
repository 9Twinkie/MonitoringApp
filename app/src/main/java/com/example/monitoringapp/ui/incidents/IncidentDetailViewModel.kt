package com.example.monitoringapp.ui.incidents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monitoringapp.domain.model.Incident
import com.example.monitoringapp.domain.model.IncidentChartData
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
    private val incidentRepository: IncidentRepository
) : ViewModel() {

    private val incidentId: Long = savedStateHandle["incidentId"] ?: 0L

    private val _incident = MutableStateFlow<UiState<Incident>>(UiState.Loading)
    val incident: StateFlow<UiState<Incident>> = _incident.asStateFlow()

    private val _chart = MutableStateFlow(IncidentChartData())
    val chart: StateFlow<IncidentChartData> = _chart.asStateFlow()

    private val _action = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val action: StateFlow<UiState<Unit>> = _action.asStateFlow()

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
            incidentRepository.loadIncidentChart(data).onSuccess { chart ->
                _chart.value = chart
            }
        }
    }

    fun confirm() = act { incidentRepository.confirmIncident(incidentId) }
    fun close() = act { incidentRepository.closeIncident(incidentId) }
    fun accept() = act { incidentRepository.acceptIncident(incidentId) }

    private fun act(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _action.value = UiState.Loading
            _action.value = block().fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = { UiState.Error(it.message ?: "Ошибка") }
            )
            load()
        }
    }
}

package com.example.monitoringapp.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monitoringapp.domain.repository.AuthRepository
import com.example.monitoringapp.domain.repository.SessionRepository
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
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val state: StateFlow<UiState<Unit>> = _state.asStateFlow()

    val serverUrl: StateFlow<String> = sessionRepository.baseUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sessionRepository.getBaseUrl())

    fun login(username: String, password: String, serverUrl: String) {
        if (username.isBlank() || password.isBlank()) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            if (serverUrl.isNotBlank()) {
                sessionRepository.setBaseUrl(serverUrl)
            }
            _state.value = authRepository.login(username, password).fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = { UiState.Error(it.message ?: "Ошибка входа") }
            )
        }
    }
}

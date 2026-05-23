package com.example.monitoringapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monitoringapp.domain.model.UserRole
import com.example.monitoringapp.domain.repository.AuthRepository
import com.example.monitoringapp.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    val baseUrl = sessionRepository.baseUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sessionRepository.getBaseUrl())

    fun username(): String? = authRepository.getUsername()

    fun userRole(): UserRole = authRepository.getUserRole()

    fun saveBaseUrl(url: String) {
        sessionRepository.setBaseUrl(url)
    }

    fun logout() {
        authRepository.logout()
        sessionRepository.clearSession()
    }
}

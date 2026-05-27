package com.example.monitoringapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monitoringapp.domain.model.UserRole
import com.example.monitoringapp.domain.repository.AuthRepository
import com.example.monitoringapp.domain.repository.FavoriteRepository
import com.example.monitoringapp.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    val baseUrl = sessionRepository.baseUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sessionRepository.getBaseUrl())

    val notifyFavoritesOnly = favoriteRepository.notifyFavoritesOnlyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _userRole = MutableStateFlow(authRepository.getUserRole())
    val userRole: StateFlow<UserRole> = _userRole.asStateFlow()

    private val _username = MutableStateFlow(authRepository.getUsername())
    val username: StateFlow<String?> = _username.asStateFlow()

    init {
        refreshProfile()
    }

    fun refreshProfile() {
        viewModelScope.launch {
            authRepository.refreshProfile()
            _userRole.value = authRepository.getUserRole()
            _username.value = authRepository.getUsername()
        }
    }

    fun isAdmin(): Boolean = _userRole.value.isAdmin

    fun saveBaseUrl(url: String) {
        sessionRepository.setBaseUrl(url)
    }

    fun setNotifyFavoritesOnly(enabled: Boolean) {
        favoriteRepository.setNotifyFavoritesOnly(enabled)
    }

    fun logout() {
        authRepository.logout()
        sessionRepository.clearSession()
    }
}

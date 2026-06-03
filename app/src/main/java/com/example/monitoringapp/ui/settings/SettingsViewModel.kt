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

    private val _canManageUsers = MutableStateFlow(false)
    val canManageUsers: StateFlow<Boolean> = _canManageUsers.asStateFlow()

    init {
        refreshProfile()
    }

    fun refreshProfile() {
        viewModelScope.launch {
            authRepository.refreshProfile()
                .onSuccess {
                    _userRole.value = authRepository.getUserRole()
                    _username.value = authRepository.getUsername()
                    _canManageUsers.value = authRepository.getUserRole().isAdmin
                }
                .onFailure {
                    _canManageUsers.value = false
                }
        }
    }

    fun isAdmin(): Boolean = _userRole.value.isAdmin

    fun currentBaseUrl(): String = sessionRepository.getBaseUrl()

    fun saveBaseUrl(url: String): Boolean {
        val wasLoggedIn = authRepository.isLoggedIn()
        sessionRepository.setBaseUrl(url)
        val sessionReset = wasLoggedIn && !authRepository.isLoggedIn()
        if (sessionReset) {
            _canManageUsers.value = false
        }
        return sessionReset
    }

    fun setNotifyFavoritesOnly(enabled: Boolean) {
        favoriteRepository.setNotifyFavoritesOnly(enabled)
    }

    fun logout() {
        authRepository.logout()
        sessionRepository.clearSession()
    }
}

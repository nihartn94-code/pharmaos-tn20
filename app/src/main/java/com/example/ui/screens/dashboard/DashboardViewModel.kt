package com.example.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AuthUser
import com.example.domain.model.UserRole
import com.example.domain.repository.DashboardKpis
import com.example.domain.repository.PharmaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val currentUser: AuthUser? = null,
    val kpis: DashboardKpis? = null,
    val unreadNotificationsCount: Int = 0,
    val cartItemCount: Int = 0,
    val errorMessage: String? = null
)

class DashboardViewModel(
    private val repository: PharmaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
        observeNotifications()
        observeBasket()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val user = repository.getApiUsersMe()
                val kpis = repository.getDashboardStats()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentUser = user,
                        kpis = kpis,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load dashboard") }
            }
        }
    }

    private fun observeNotifications() {
        viewModelScope.launch {
            repository.getApiNotificationsUnreadCountFlow().collect { count ->
                _uiState.update { it.copy(unreadNotificationsCount = count) }
            }
        }
    }

    private fun observeBasket() {
        viewModelScope.launch {
            repository.getApiBasketFlow().collect { basket ->
                val count = basket.items.sumOf { it.quantity }
                _uiState.update { it.copy(cartItemCount = count) }
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.logout()
            } catch (_: Exception) { }
            onLoggedOut()
        }
    }
}

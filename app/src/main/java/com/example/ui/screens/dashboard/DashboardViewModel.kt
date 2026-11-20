package com.example.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AuthUser
import com.example.domain.model.UserRole
import com.example.domain.repository.DashboardKpis
import com.example.domain.repository.PharmaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.data.local.entity.ProductInventoryEntity
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
    val inventoryItems: List<ProductInventoryEntity> = emptyList(),
    val shortageAlertsCount: Int = 0,
    val reorderSuccessMessage: String? = null,
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
        observeInventory()
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

    private fun observeInventory() {
        viewModelScope.launch {
            repository.getProductInventoryFlow().collect { items ->
                val critical = items.count { it.currentStock <= it.reorderThreshold }
                _uiState.update {
                    it.copy(
                        inventoryItems = items,
                        shortageAlertsCount = critical
                    )
                }
            }
        }
    }

    fun adjustInventoryStock(sku: String, delta: Int) {
        viewModelScope.launch {
            val item = repository.getProductInventoryBySku(sku) ?: return@launch
            val newStock = (item.currentStock + delta).coerceAtLeast(0)
            repository.updateProductInventoryStock(sku, newStock)
        }
    }

    fun reorderProduct(item: ProductInventoryEntity, quantity: Int) {
        viewModelScope.launch {
            val productId = item.productId ?: 1L
            repository.quickRestockToBasket(productId, quantity)
            _uiState.update {
                it.copy(reorderSuccessMessage = "Reorder placed: $quantity units of ${item.productName} added to basket.")
            }
        }
    }

    fun clearReorderMessage() {
        _uiState.update { it.copy(reorderSuccessMessage = null) }
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

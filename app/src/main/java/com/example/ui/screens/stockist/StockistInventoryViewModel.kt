package com.example.ui.screens.stockist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AccessControlPolicy
import com.example.domain.model.AuthUser
import com.example.domain.model.StockistPurchasedInventoryItem
import com.example.domain.repository.PharmaRepository
import com.example.domain.repository.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StockistInventoryUiState(
    val user: AuthUser? = null,
    val isAuthorized: Boolean = false,
    val items: List<StockistPurchasedInventoryItem> = emptyList(),
    val totalUnitsInStock: Int = 0,
    val lowStockCount: Int = 0,
    val nearExpiryCount: Int = 0,
    val isSaving: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class StockistInventoryViewModel(
    private val pharmaRepository: PharmaRepository,
    private val workspaceRepository: WorkspaceRepository
) : ViewModel() {

    private val isSaving = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<StockistInventoryUiState> = combine(
        pharmaRepository.getApiUsersMeFlow(),
        workspaceRepository.getStockistPurchasedInventoryFlow(),
        isSaving,
        statusMessage,
        errorMessage
    ) { user, items, saving, msg, err ->
        val now = System.currentTimeMillis()
        val ninetyDaysMs = 90L * 24L * 60L * 60L * 1000L
        StockistInventoryUiState(
            user = user,
            isAuthorized = AccessControlPolicy.canAccessStockistPurchasedInventory(user),
            items = items,
            totalUnitsInStock = items.sumOf { it.remainingQuantity },
            lowStockCount = items.count { it.isLowStock },
            nearExpiryCount = items.count { (it.expiryDateEpochMillis - now) <= ninetyDaysMs },
            isSaving = saving,
            statusMessage = msg,
            errorMessage = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = StockistInventoryUiState()
    )

    fun saveItem(item: StockistPurchasedInventoryItem, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            workspaceRepository.upsertStockistPurchasedInventory(item)
                .onSuccess {
                    statusMessage.value = "Updated '${it.productName}' in your purchased stock."
                    onSuccess()
                }
                .onFailure { errorMessage.value = it.message }
            isSaving.value = false
        }
    }

    fun adjustRemainingQuantity(itemId: Long, newQuantity: Int) {
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            workspaceRepository.adjustStockistPurchasedQuantity(itemId, newQuantity)
                .onSuccess { statusMessage.value = "Remaining stock updated to ${it.remainingQuantity} units." }
                .onFailure { errorMessage.value = it.message }
            isSaving.value = false
        }
    }

    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            isSaving.value = true
            workspaceRepository.deleteStockistPurchasedInventory(itemId)
                .onSuccess { statusMessage.value = "Removed entry from purchased stock." }
                .onFailure { errorMessage.value = it.message }
            isSaving.value = false
        }
    }

    fun clearMessages() {
        statusMessage.value = null
        errorMessage.value = null
    }
}

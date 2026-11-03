package com.example.ui.screens.basket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AuthUser
import com.example.domain.repository.BasketItemDetail
import com.example.domain.repository.BasketLoadReport
import com.example.domain.repository.BasketWithDetails
import com.example.domain.repository.NamedBasketSummary
import com.example.domain.repository.PharmaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BasketUiState(
    val isLoading: Boolean = true,
    val currentUser: AuthUser? = null,
    val basket: BasketWithDetails? = null,
    val savedBaskets: List<NamedBasketSummary> = emptyList(),
    val loadReport: BasketLoadReport? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class BasketViewModel(
    private val repository: PharmaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BasketUiState())
    val uiState: StateFlow<BasketUiState> = _uiState.asStateFlow()

    init {
        loadCurrentUser()
        observeBasket()
        observeSavedBaskets()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            repository.getApiUsersMeFlow().collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
    }

    private fun observeBasket() {
        viewModelScope.launch {
            repository.getApiBasketFlow().collect { b ->
                _uiState.update { it.copy(isLoading = false, basket = b) }
            }
        }
    }

    private fun observeSavedBaskets() {
        viewModelScope.launch {
            repository.getApiSavedBasketsFlow().collect { list ->
                _uiState.update { it.copy(savedBaskets = list) }
            }
        }
    }

    fun updateQuantity(itemId: Long, newQuantity: Int) {
        viewModelScope.launch {
            val result = repository.putApiBasketItemQuantity(itemId, newQuantity)
            result.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Failed to update quantity") }
            }
        }
    }

    fun removeItem(itemId: Long) {
        viewModelScope.launch {
            repository.deleteApiBasketItem(itemId)
        }
    }

    fun clearBasket() {
        viewModelScope.launch {
            repository.deleteApiBasketClear()
        }
    }

    fun saveNamedBasket(name: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val result = repository.postApiBasketSaveNamed(name)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(successMessage = "Basket saved successfully!") }
                    onComplete()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to save basket") }
                }
            )
        }
    }

    fun loadSavedBasket(savedBasketId: Long) {
        viewModelScope.launch {
            val result = repository.postApiBasketLoadSaved(savedBasketId)
            result.fold(
                onSuccess = { report ->
                    _uiState.update { it.copy(loadReport = report, successMessage = "Loaded ${report.itemsLoadedCount} items.") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to load saved basket") }
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null, loadReport = null) }
    }
}

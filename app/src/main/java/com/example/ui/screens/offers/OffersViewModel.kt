package com.example.ui.screens.offers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.SupplierOfferEntity
import com.example.domain.model.AuthUser
import com.example.domain.model.OfferWithSupplier
import com.example.domain.model.ProductDetail
import com.example.domain.repository.PharmaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OffersUiState(
    val isLoading: Boolean = true,
    val currentUser: AuthUser? = null,
    val myOffers: List<OfferWithSupplier> = emptyList(),
    val catalogueProducts: List<ProductDetail> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class OffersViewModel(
    private val repository: PharmaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OffersUiState())
    val uiState: StateFlow<OffersUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getApiUsersMeFlow().collect { user ->
                _uiState.update { it.copy(currentUser = user) }
                if (user != null) {
                    observeSupplierOffers(user.id)
                }
            }
        }
        viewModelScope.launch {
            repository.getApiProductsFlow().collect { prods ->
                _uiState.update { it.copy(catalogueProducts = prods) }
            }
        }
    }

    private fun observeSupplierOffers(supplierId: Long) {
        viewModelScope.launch {
            repository.getApiSupplierOffersFlow(supplierId).collect { offers ->
                _uiState.update { it.copy(isLoading = false, myOffers = offers) }
            }
        }
    }

    fun saveOffer(offer: SupplierOfferEntity, onComplete: () -> Unit) {
        viewModelScope.launch {
            val result = if (offer.id == 0L) {
                repository.postApiSupplierOffers(offer)
            } else {
                repository.putApiSupplierOffers(offer).map { offer.id }
            }
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(successMessage = "Offer saved successfully!") }
                    onComplete()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to save offer") }
                }
            )
        }
    }

    fun toggleOfferActive(offerId: Long, isActive: Boolean) {
        viewModelScope.launch {
            repository.patchApiSupplierOfferDeactivate(offerId, isActive)
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}

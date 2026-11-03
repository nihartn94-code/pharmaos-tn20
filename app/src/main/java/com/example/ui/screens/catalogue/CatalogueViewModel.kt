package com.example.ui.screens.catalogue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ProductEntity
import com.example.domain.model.AuthUser
import com.example.domain.model.DosageForm
import com.example.domain.model.OfferWithSupplier
import com.example.domain.model.ProductDetail
import com.example.domain.model.StorageCondition
import com.example.domain.repository.PharmaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CatalogueUiState(
    val isLoading: Boolean = true,
    val currentUser: AuthUser? = null,
    val allProducts: List<ProductDetail> = emptyList(),
    val filteredProducts: List<ProductDetail> = emptyList(),
    val searchQuery: String = "",
    val selectedCategoryChip: String = "Capsules", // Default to Capsules as in reference image
    val selectedDosageForm: DosageForm? = null,
    val selectedCategory: String? = null,
    val selectedStorageCondition: StorageCondition? = null,
    val isRxOnly: Boolean? = null,
    val inStockOnly: Boolean = false,
    val sortBy: SortOption = SortOption.NAME_ASC,
    val selectedProduct: ProductDetail? = null,
    val selectedProductOffers: List<OfferWithSupplier> = emptyList(),
    val alternativeBrands: List<ProductEntity> = emptyList(),
    val cartItemsCount: Int = 0,
    val cartTotalPrice: Double = 0.0,
    val cartProductQuantities: Map<Long, Int> = emptyMap(),
    val cartProductItemIds: Map<Long, Long> = emptyMap(),
    val pendingQuantities: Map<Long, Int> = emptyMap(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)

enum class SortOption(val label: String) {
    NAME_ASC("Name: A to Z"),
    PRICE_LOW_HIGH("Price: Low to High"),
    PRICE_HIGH_LOW("Price: High to Low")
}

class CatalogueViewModel(
    private val repository: PharmaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogueUiState())
    val uiState: StateFlow<CatalogueUiState> = _uiState.asStateFlow()

    init {
        loadCurrentUser()
        viewModelScope.launch {
            repository.ensureDefaultCatalogueSeeded()
        }
        observeProducts()
        observeBasket()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            repository.getApiUsersMeFlow().collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
    }

    private fun observeProducts() {
        viewModelScope.launch {
            repository.getApiProductsFlow().collect { list ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        allProducts = list,
                        filteredProducts = applyFiltersAndSort(list, it)
                    )
                }
            }
        }
    }

    private fun observeBasket() {
        viewModelScope.launch {
            repository.getApiBasketFlow().collect { basket ->
                val count = basket.items.sumOf { it.quantity }
                val total = basket.grandTotal
                val quantities = basket.items.associate { it.productId to it.quantity }
                val itemIds = basket.items.associate { it.productId to it.itemId }
                _uiState.update {
                    it.copy(
                        cartItemsCount = count,
                        cartTotalPrice = total,
                        cartProductQuantities = quantities,
                        cartProductItemIds = itemIds
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            val updated = it.copy(searchQuery = query)
            updated.copy(filteredProducts = applyFiltersAndSort(it.allProducts, updated))
        }
    }

    fun selectCategoryChip(chip: String) {
        _uiState.update {
            val updated = it.copy(selectedCategoryChip = chip)
            updated.copy(filteredProducts = applyFiltersAndSort(it.allProducts, updated))
        }
    }

    fun setFilters(
        dosageForm: DosageForm?,
        category: String?,
        storage: StorageCondition?,
        rxOnly: Boolean?,
        inStockOnly: Boolean,
        sortOption: SortOption
    ) {
        _uiState.update {
            val updated = it.copy(
                selectedDosageForm = dosageForm,
                selectedCategory = category,
                selectedStorageCondition = storage,
                isRxOnly = rxOnly,
                inStockOnly = inStockOnly,
                sortBy = sortOption
            )
            updated.copy(filteredProducts = applyFiltersAndSort(it.allProducts, updated))
        }
    }

    fun clearFilters() {
        _uiState.update {
            val updated = it.copy(
                searchQuery = "",
                selectedCategoryChip = "All",
                selectedDosageForm = null,
                selectedCategory = null,
                selectedStorageCondition = null,
                isRxOnly = null,
                inStockOnly = false,
                sortBy = SortOption.NAME_ASC
            )
            updated.copy(filteredProducts = applyFiltersAndSort(it.allProducts, updated))
        }
    }

    fun selectProduct(productId: Long) {
        viewModelScope.launch {
            val prod = repository.getApiProductById(productId)
            _uiState.update { it.copy(selectedProduct = prod) }

            // Observe offers for this product
            repository.getApiProductOffersFlow(productId).collect { offers ->
                _uiState.update { it.copy(selectedProductOffers = offers) }
            }
        }
        viewModelScope.launch {
            repository.getApiProductAlternativeBrandsFlow(productId).collect { alts ->
                _uiState.update { it.copy(alternativeBrands = alts) }
            }
        }
    }

    fun setPendingQuantity(productId: Long, qty: Int) {
        _uiState.update {
            val map = it.pendingQuantities.toMutableMap()
            map[productId] = qty.coerceAtLeast(1)
            it.copy(pendingQuantities = map)
        }
    }

    fun addToCart(product: ProductDetail, quantity: Int = 10) {
        val safeQty = if (quantity <= 0) 10 else quantity
        // Instant optimistic UI update
        _uiState.update { current ->
            val updatedMap = current.cartProductQuantities.toMutableMap()
            updatedMap[product.id] = (updatedMap[product.id] ?: 0) + safeQty
            val newCount = current.cartItemsCount + safeQty
            val newTotal = current.cartTotalPrice + ((product.minOfferPrice ?: 68.50) * safeQty)
            current.copy(
                cartProductQuantities = updatedMap,
                cartItemsCount = newCount,
                cartTotalPrice = newTotal,
                successMessage = "Added ${product.name} to basket!"
            )
        }

        viewModelScope.launch {
            val offerId = product.bestOfferId ?: repository.getApiProductOffers(product.id).firstOrNull()?.offerId ?: 0L
            val result = repository.postApiBasketItems(product.id, offerId, safeQty)
            result.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Failed to add to basket") }
            }
        }
    }

    fun incrementQuantity(product: ProductDetail) {
        val currentQty = _uiState.value.cartProductQuantities[product.id] ?: 0
        val newQty = currentQty + 1
        _uiState.update { current ->
            val updatedMap = current.cartProductQuantities.toMutableMap()
            updatedMap[product.id] = newQty
            current.copy(
                cartProductQuantities = updatedMap,
                cartItemsCount = current.cartItemsCount + 1,
                cartTotalPrice = current.cartTotalPrice + (product.minOfferPrice ?: 68.50)
            )
        }

        viewModelScope.launch {
            val existingItemId = _uiState.value.cartProductItemIds[product.id]
            if (existingItemId != null && currentQty > 0) {
                repository.putApiBasketItemQuantity(existingItemId, newQty)
            } else {
                val offerId = product.bestOfferId ?: repository.getApiProductOffers(product.id).firstOrNull()?.offerId ?: 0L
                repository.postApiBasketItems(product.id, offerId, 1)
            }
        }
    }

    fun decrementQuantity(product: ProductDetail) {
        val currentQty = _uiState.value.cartProductQuantities[product.id] ?: 0
        val newQty = currentQty - 1
        _uiState.update { current ->
            val updatedMap = current.cartProductQuantities.toMutableMap()
            if (newQty <= 0) {
                updatedMap.remove(product.id)
            } else {
                updatedMap[product.id] = newQty
            }
            current.copy(
                cartProductQuantities = updatedMap,
                cartItemsCount = (current.cartItemsCount - 1).coerceAtLeast(0),
                cartTotalPrice = (current.cartTotalPrice - (product.minOfferPrice ?: 68.50)).coerceAtLeast(0.0)
            )
        }

        viewModelScope.launch {
            val existingItemId = _uiState.value.cartProductItemIds[product.id]
            if (existingItemId != null) {
                if (newQty <= 0) {
                    repository.deleteApiBasketItem(existingItemId)
                } else {
                    repository.putApiBasketItemQuantity(existingItemId, newQty)
                }
            }
        }
    }

    fun saveProduct(product: ProductEntity, onComplete: () -> Unit) {
        viewModelScope.launch {
            val result = if (product.id == 0L) {
                repository.postApiProducts(product)
            } else {
                repository.putApiProducts(product).map { product.id }
            }
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(successMessage = "Product saved successfully!") }
                    onComplete()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to save product") }
                }
            )
        }
    }

    fun setProductActive(productId: Long, isActive: Boolean) {
        viewModelScope.launch {
            repository.patchApiProductDeactivate(productId, isActive)
        }
    }

    fun addOfferToBasket(offer: OfferWithSupplier, quantity: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = repository.postApiBasketItems(offer.productId, offer.offerId, quantity)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(successMessage = "Added to basket!") }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Could not add to basket") }
                }
            )
        }
    }

    private fun applyFiltersAndSort(
        list: List<ProductDetail>,
        state: CatalogueUiState
    ): List<ProductDetail> {
        val q = state.searchQuery.trim().lowercase()
        var filtered = list.filter { p ->
            val matchesQuery = q.isEmpty() ||
                p.name.lowercase().contains(q) ||
                p.brand.lowercase().contains(q) ||
                p.saltComposition.lowercase().contains(q) ||
                p.therapeuticCategory.lowercase().contains(q) ||
                p.manufacturerName.lowercase().contains(q)

            val matchesChip = when (state.selectedCategoryChip) {
                "All" -> true
                "Tablets" -> p.dosageForm == DosageForm.TABLET || p.name.contains("Tab", ignoreCase = true)
                "Capsules" -> p.dosageForm == DosageForm.CAPSULE || p.name.contains("Cap", ignoreCase = true) || p.name.contains("Softgel", ignoreCase = true)
                "Injectables" -> p.dosageForm == DosageForm.INJECTION || p.therapeuticCategory.contains("Inject", ignoreCase = true)
                "Syrups" -> p.dosageForm == DosageForm.SYRUP || p.therapeuticCategory.contains("Syrup", ignoreCase = true)
                "OTC" -> !p.isRx
                "Diabetics" -> p.therapeuticCategory.contains("Diabet", ignoreCase = true) || p.name.contains("Metformin", ignoreCase = true)
                "Dermatology" -> p.therapeuticCategory.contains("Dermat", ignoreCase = true) || p.dosageForm == DosageForm.OINTMENT
                else -> true
            }

            val matchesDosage = state.selectedDosageForm == null || p.dosageForm == state.selectedDosageForm
            val matchesCategory = state.selectedCategory.isNullOrBlank() || p.therapeuticCategory.equals(state.selectedCategory, ignoreCase = true)
            val matchesStorage = state.selectedStorageCondition == null || p.storageCondition == state.selectedStorageCondition
            val matchesRx = state.isRxOnly == null || p.isRx == state.isRxOnly
            val matchesStock = !state.inStockOnly || p.activeOfferCount > 0

            matchesQuery && matchesChip && matchesDosage && matchesCategory && matchesStorage && matchesRx && matchesStock
        }

        filtered = when (state.sortBy) {
            SortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortOption.PRICE_LOW_HIGH -> filtered.sortedBy { it.minOfferPrice ?: Double.MAX_VALUE }
            SortOption.PRICE_HIGH_LOW -> filtered.sortedByDescending { it.minOfferPrice ?: 0.0 }
        }

        return filtered
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}

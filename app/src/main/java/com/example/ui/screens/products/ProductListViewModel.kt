package com.example.ui.screens.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ProductInventoryEntity
import com.example.data.repository.ProductRepository
import com.example.domain.repository.PharmaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Filter categories for pharmaceutical product inventory.
 */
enum class ProductInventoryFilter(val displayName: String) {
    ALL("All Products"),
    LOW_STOCK("Low Stock Alerts"),
    HEALTHY("Adequate Stock")
}

/**
 * UI State for the Product Inventory List screen.
 */
data class ProductListUiState(
    val isAuthResolved: Boolean = true,
    val isInventoryAuthorized: Boolean = true,
    val products: List<ProductInventoryEntity> = emptyList(),
    val filteredProducts: List<ProductInventoryEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: ProductInventoryFilter = ProductInventoryFilter.ALL,
    val isAddDialogOpen: Boolean = false,
    val isLoading: Boolean = false,
    val totalProductsCount: Int = 0,
    val totalStockUnits: Int = 0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0,
    val userMessage: String? = null,
    val isError: Boolean = false
)

/**
 * ViewModel managing Room database interactions via [ProductRepository]
 * for the Product List and Add Product screen.
 */
class ProductListViewModel(
    private val productRepository: ProductRepository,
    private val externalScope: kotlinx.coroutines.CoroutineScope? = null,
    private val pharmaRepository: PharmaRepository? = null
) : ViewModel() {

    private val scope: kotlinx.coroutines.CoroutineScope
        get() = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(
        ProductListUiState(
            isAuthResolved = pharmaRepository == null,
            isInventoryAuthorized = pharmaRepository == null,
            isLoading = true
        )
    )
    val uiState: StateFlow<ProductListUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _selectedFilter = MutableStateFlow(ProductInventoryFilter.ALL)

    private var allItemsCache: List<ProductInventoryEntity> = emptyList()

    init {
        if (pharmaRepository != null) {
            observeUserAuthorization()
        }
        observeProducts()
    }

    private fun observeUserAuthorization() {
        val repo = pharmaRepository ?: return
        scope.launch {
            repo.getApiUsersMeFlow().collect { user ->
                val authorized = user?.role?.canAccessRealTimeInventory == true
                if (!authorized) {
                    _uiState.update {
                        it.copy(
                            isAuthResolved = true,
                            isInventoryAuthorized = false,
                            isLoading = false,
                            isAddDialogOpen = false,
                            products = emptyList(),
                            filteredProducts = emptyList(),
                            totalProductsCount = 0,
                            totalStockUnits = 0,
                            lowStockCount = 0,
                            outOfStockCount = 0
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isAuthResolved = true,
                            isInventoryAuthorized = true
                        )
                    }
                    applyFiltersAndEmit(allItemsCache, _searchQuery.value, _selectedFilter.value)
                }
            }
        }
    }

    private fun observeProducts() {
        scope.launch {
            productRepository.getProductInventoryFlow().collect { allItems ->
                allItemsCache = allItems
                if (_uiState.value.isInventoryAuthorized) {
                    applyFiltersAndEmit(allItems, _searchQuery.value, _selectedFilter.value)
                }
            }
        }
    }

    private fun applyFiltersAndEmit(
        allItems: List<ProductInventoryEntity>,
        query: String,
        filter: ProductInventoryFilter
    ) {
        if (!_uiState.value.isInventoryAuthorized) {
            return
        }
        val trimmedQuery = query.trim()
        val searched = if (trimmedQuery.isBlank()) {
            allItems
        } else {
            allItems.filter { item ->
                item.productName.contains(trimmedQuery, ignoreCase = true) ||
                item.sku.contains(trimmedQuery, ignoreCase = true) ||
                item.saltComposition.contains(trimmedQuery, ignoreCase = true)
            }
        }

        val filtered = when (filter) {
            ProductInventoryFilter.ALL -> searched
            ProductInventoryFilter.LOW_STOCK -> searched.filter { it.currentStock <= it.reorderThreshold }
            ProductInventoryFilter.HEALTHY -> searched.filter { it.currentStock > it.reorderThreshold }
        }

        val totalUnits = allItems.sumOf { it.currentStock }
        val lowStock = allItems.count { it.currentStock <= it.reorderThreshold && it.currentStock > 0 }
        val outOfStock = allItems.count { it.currentStock <= 0 }

        _uiState.update { current ->
            current.copy(
                products = allItems,
                filteredProducts = filtered,
                searchQuery = query,
                selectedFilter = filter,
                isLoading = false,
                totalProductsCount = allItems.size,
                totalStockUnits = totalUnits,
                lowStockCount = lowStock,
                outOfStockCount = outOfStock
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        applyFiltersAndEmit(allItemsCache, query, _selectedFilter.value)
    }

    fun onFilterSelect(filter: ProductInventoryFilter) {
        _selectedFilter.value = filter
        applyFiltersAndEmit(allItemsCache, _searchQuery.value, filter)
    }

    fun openAddProductDialog() {
        if (!_uiState.value.isInventoryAuthorized) {
            _uiState.update {
                it.copy(
                    userMessage = "Access denied (403): Real-Time Inventory is restricted to Brand and Manufacturer accounts.",
                    isError = true
                )
            }
            return
        }
        _uiState.update { it.copy(isAddDialogOpen = true, userMessage = null) }
    }

    fun closeAddProductDialog() {
        _uiState.update { it.copy(isAddDialogOpen = false) }
    }

    fun addProduct(
        productName: String,
        sku: String,
        currentStock: Int,
        reorderThreshold: Int,
        dosageForm: String = "Tablet",
        packSize: String = "Strip of 10",
        saltComposition: String = "",
        unitPricePtr: Double = 0.0,
        mrp: Double = 0.0
    ) {
        if (!_uiState.value.isInventoryAuthorized) {
            _uiState.update {
                it.copy(
                    userMessage = "Access denied (403): Real-Time Inventory is restricted to Brand and Manufacturer accounts.",
                    isError = true
                )
            }
            return
        }
        val trimmedName = productName.trim()
        val trimmedSku = sku.trim().uppercase()

        if (trimmedName.isBlank()) {
            _uiState.update {
                it.copy(userMessage = "Product name cannot be empty.", isError = true)
            }
            return
        }

        if (trimmedSku.isBlank()) {
            _uiState.update {
                it.copy(userMessage = "SKU code cannot be empty.", isError = true)
            }
            return
        }

        if (currentStock < 0) {
            _uiState.update {
                it.copy(userMessage = "Current stock cannot be negative.", isError = true)
            }
            return
        }

        if (reorderThreshold < 0) {
            _uiState.update {
                it.copy(userMessage = "Reorder threshold cannot be negative.", isError = true)
            }
            return
        }

        scope.launch {
            try {
                productRepository.addProduct(
                    productName = trimmedName,
                    sku = trimmedSku,
                    currentStock = currentStock,
                    reorderThreshold = reorderThreshold,
                    dosageForm = dosageForm,
                    packSize = packSize,
                    saltComposition = saltComposition,
                    unitPricePtr = unitPricePtr,
                    mrp = mrp
                )
                _uiState.update {
                    it.copy(
                        isAddDialogOpen = false,
                        userMessage = "Product '$trimmedName' ($trimmedSku) added successfully to Room database.",
                        isError = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        userMessage = "Failed to add product: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    fun adjustStock(sku: String, delta: Int) {
        if (!_uiState.value.isInventoryAuthorized) {
            _uiState.update {
                it.copy(
                    userMessage = "Access denied (403): Real-Time Inventory is restricted to Brand and Manufacturer accounts.",
                    isError = true
                )
            }
            return
        }
        scope.launch {
            try {
                productRepository.adjustStockBySku(sku, delta)
                val action = if (delta > 0) "Restocked +$delta units" else "Dispensed $delta units"
                _uiState.update {
                    it.copy(userMessage = "$action for $sku", isError = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(userMessage = "Error updating stock: ${e.message}", isError = true)
                }
            }
        }
    }

    fun deleteProduct(sku: String) {
        if (!_uiState.value.isInventoryAuthorized) {
            _uiState.update {
                it.copy(
                    userMessage = "Access denied (403): Real-Time Inventory is restricted to Brand and Manufacturer accounts.",
                    isError = true
                )
            }
            return
        }
        scope.launch {
            try {
                productRepository.deleteInventoryBySku(sku)
                _uiState.update {
                    it.copy(userMessage = "Product $sku removed from inventory.", isError = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(userMessage = "Error deleting product: ${e.message}", isError = true)
                }
            }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}

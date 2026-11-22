package com.example.ui.screens.catalogue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ProductEntity
import com.example.domain.model.AlternativeBrandQuotation
import com.example.domain.model.AuthUser
import com.example.domain.model.DosageForm
import com.example.domain.model.OfferWithSupplier
import com.example.domain.model.ProductDetail
import com.example.domain.model.StorageCondition
import com.example.domain.model.SupplierQuotationItem
import com.example.domain.model.SupplierQuotationSummary
import com.example.domain.repository.PharmaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExploreMode {
    PRODUCTS,
    MANUFACTURERS
}

data class ManufacturerSummary(
    val name: String,
    val productCount: Int,
    val activeOfferProductCount: Int,
    val dosageForms: List<DosageForm>,
    val therapeuticCategories: List<String>,
    val strengths: List<String>,
    val saltCompositions: List<String>,
    val minOfferPrice: Double?,
    val maxMarginPercent: Int?
)

data class CatalogueUiState(
    val isLoading: Boolean = true,
    val isDetailsLoading: Boolean = false,
    val detailsErrorMessage: String? = null,
    val currentUser: AuthUser? = null,
    val canManageCatalogue: Boolean = false,
    val exploreMode: ExploreMode = ExploreMode.PRODUCTS,
    val allProducts: List<ProductDetail> = emptyList(),
    val filteredProducts: List<ProductDetail> = emptyList(),
    val manufacturers: List<ManufacturerSummary> = emptyList(),
    val filteredManufacturers: List<ManufacturerSummary> = emptyList(),
    val searchQuery: String = "",
    val selectedCategoryChip: String = "All",
    val selectedManufacturer: String? = null,
    val selectedStrength: String? = null,
    val selectedSaltFilter: String = "",
    val selectedDosageForm: DosageForm? = null,
    val selectedCategory: String? = null,
    val selectedStorageCondition: StorageCondition? = null,
    val availableManufacturers: List<String> = emptyList(),
    val availableStrengths: List<String> = emptyList(),
    val availableSalts: List<String> = emptyList(),
    val isRxOnly: Boolean? = null,
    val inStockOnly: Boolean = false,
    val sortBy: SortOption = SortOption.NAME_ASC,
    val selectedProduct: ProductDetail? = null,
    val selectedProductOffers: List<OfferWithSupplier> = emptyList(),
    val supplierQuotations: List<SupplierQuotationItem> = emptyList(),
    val quotationSummary: SupplierQuotationSummary? = null,
    val alternativeBrands: List<ProductEntity> = emptyList(),
    val alternativeBrandQuotations: List<AlternativeBrandQuotation> = emptyList(),
    val cartItemsCount: Int = 0,
    val cartTotalPrice: Double = 0.0,
    val cartProductQuantities: Map<Long, Int> = emptyMap(),
    val cartProductItemIds: Map<Long, Long> = emptyMap(),
    val cartOfferQuantities: Map<Long, Int> = emptyMap(),
    val pendingQuantities: Map<Long, Int> = emptyMap(),
    val errorMessage: String? = null,
    val successMessage: String? = null
) {
    val activeFilterCount: Int
        get() {
            var count = 0
            if (!selectedManufacturer.isNullOrBlank()) count++
            if (!selectedStrength.isNullOrBlank()) count++
            if (selectedSaltFilter.isNotBlank()) count++
            if (selectedDosageForm != null) count++
            if (!selectedCategory.isNullOrBlank()) count++
            if (selectedStorageCondition != null) count++
            if (isRxOnly != null) count++
            if (inStockOnly) count++
            if (sortBy != SortOption.NAME_ASC) count++
            return count
        }

    val selectedManufacturerSummary: ManufacturerSummary?
        get() {
            val target = selectedManufacturer?.trim() ?: return null
            return manufacturers.firstOrNull { it.name.equals(target, ignoreCase = true) }
        }
}

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

    private var productDetailsJob: Job? = null
    private var appliedInitialRouteKey: String? = null

    init {
        loadCurrentUser()
        viewModelScope.launch {
            repository.ensureDefaultCatalogueSeeded()
        }
        observeProducts()
        observeBasket()
    }

    /**
     * Applies route-level navigation arguments once per unique navigation target,
     * preserving user exploration state when returning from Product Details.
     */
    fun applyInitialNavigation(
        focus: String? = null,
        manufacturer: String? = null,
        query: String? = null
    ) {
        val normalizedFocus = focus?.trim()?.lowercase().orEmpty()
        val normalizedMfg = manufacturer?.trim().orEmpty()
        val normalizedQuery = query?.trim().orEmpty()
        val routeKey = "$normalizedFocus|$normalizedMfg|$normalizedQuery"
        if (appliedInitialRouteKey == routeKey) return
        appliedInitialRouteKey = routeKey

        val targetMode = if (normalizedFocus == "manufacturers") {
            ExploreMode.MANUFACTURERS
        } else {
            ExploreMode.PRODUCTS
        }

        _uiState.update { state ->
            val updated = state.copy(
                exploreMode = targetMode,
                selectedManufacturer = normalizedMfg.takeIf { it.isNotEmpty() },
                searchQuery = normalizedQuery,
                selectedCategoryChip = "All"
            )
            recomputeDerivedState(updated)
        }
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            repository.getApiUsersMeFlow().collect { user ->
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        canManageCatalogue = user?.role?.canAccessRealTimeInventory == true
                    )
                }
            }
        }
    }

    private fun observeProducts() {
        viewModelScope.launch {
            repository.getApiProductsFlow().collect { list ->
                _uiState.update { state ->
                    recomputeDerivedState(
                        state.copy(
                            isLoading = false,
                            allProducts = list
                        )
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
                val quantities = basket.items.groupBy { it.productId }.mapValues { (_, lines) ->
                    lines.sumOf { it.quantity }
                }
                val itemIds = basket.items.associate { it.productId to it.itemId }
                val offerQuantities = basket.items.groupBy { it.offerId }.mapValues { (_, lines) ->
                    lines.sumOf { it.quantity }
                }
                _uiState.update {
                    it.copy(
                        cartItemsCount = count,
                        cartTotalPrice = total,
                        cartProductQuantities = quantities,
                        cartProductItemIds = itemIds,
                        cartOfferQuantities = offerQuantities
                    )
                }
            }
        }
    }

    fun setExploreMode(mode: ExploreMode) {
        _uiState.update { state ->
            val updated = if (mode == ExploreMode.MANUFACTURERS) {
                state.copy(
                    exploreMode = ExploreMode.MANUFACTURERS,
                    selectedManufacturer = null,
                    selectedCategoryChip = "All"
                )
            } else {
                state.copy(exploreMode = ExploreMode.PRODUCTS)
            }
            recomputeDerivedState(updated)
        }
    }

    fun selectManufacturer(manufacturerName: String?) {
        val cleaned = manufacturerName?.trim()?.takeIf { it.isNotEmpty() }
        _uiState.update { state ->
            val updated = state.copy(
                selectedManufacturer = cleaned,
                selectedCategoryChip = if (cleaned != null) "All" else state.selectedCategoryChip,
                selectedStrength = if (cleaned != state.selectedManufacturer) null else state.selectedStrength
            )
            recomputeDerivedState(updated)
        }
    }

    fun selectStrength(strength: String?) {
        val cleaned = strength?.trim()?.takeIf { it.isNotEmpty() }
        _uiState.update { state ->
            val next = if (state.selectedStrength.equals(cleaned, ignoreCase = true)) null else cleaned
            recomputeDerivedState(state.copy(selectedStrength = next))
        }
    }

    fun onSaltFilterChanged(salt: String) {
        _uiState.update { state ->
            recomputeDerivedState(state.copy(selectedSaltFilter = salt))
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            recomputeDerivedState(state.copy(searchQuery = query))
        }
    }

    fun selectCategoryChip(chip: String) {
        _uiState.update { state ->
            recomputeDerivedState(state.copy(selectedCategoryChip = chip))
        }
    }

    fun setFilters(
        dosageForm: DosageForm?,
        category: String?,
        storage: StorageCondition?,
        rxOnly: Boolean?,
        inStockOnly: Boolean,
        sortOption: SortOption,
        manufacturer: String? = _uiState.value.selectedManufacturer,
        strength: String? = _uiState.value.selectedStrength,
        saltFilter: String = _uiState.value.selectedSaltFilter
    ) {
        _uiState.update { state ->
            val updated = state.copy(
                selectedManufacturer = manufacturer?.trim()?.takeIf { it.isNotEmpty() },
                selectedStrength = strength?.trim()?.takeIf { it.isNotEmpty() },
                selectedSaltFilter = saltFilter.trim(),
                selectedDosageForm = dosageForm,
                selectedCategory = category,
                selectedStorageCondition = storage,
                isRxOnly = rxOnly,
                inStockOnly = inStockOnly,
                sortBy = sortOption
            )
            recomputeDerivedState(updated)
        }
    }

    fun clearFilters() {
        _uiState.update { state ->
            val updated = state.copy(
                searchQuery = "",
                selectedCategoryChip = "All",
                selectedManufacturer = null,
                selectedStrength = null,
                selectedSaltFilter = "",
                selectedDosageForm = null,
                selectedCategory = null,
                selectedStorageCondition = null,
                isRxOnly = null,
                inStockOnly = false,
                sortBy = SortOption.NAME_ASC
            )
            recomputeDerivedState(updated)
        }
    }

    fun selectProduct(productId: Long) {
        productDetailsJob?.cancel()
        val isSwitchingProduct = _uiState.value.selectedProduct?.id != productId
        _uiState.update {
            it.copy(
                isDetailsLoading = true,
                detailsErrorMessage = null,
                selectedProduct = if (isSwitchingProduct) null else it.selectedProduct,
                selectedProductOffers = if (isSwitchingProduct) emptyList() else it.selectedProductOffers,
                supplierQuotations = if (isSwitchingProduct) emptyList() else it.supplierQuotations,
                quotationSummary = if (isSwitchingProduct) null else it.quotationSummary,
                alternativeBrands = if (isSwitchingProduct) emptyList() else it.alternativeBrands,
                alternativeBrandQuotations = if (isSwitchingProduct) emptyList() else it.alternativeBrandQuotations
            )
        }

        productDetailsJob = viewModelScope.launch {
            // Observe local Room product, quotations, and alternative brands reactively
            launch {
                repository.getApiProductByIdFlow(productId).collect { prod ->
                    _uiState.update { state ->
                        state.copy(
                            selectedProduct = prod,
                            isDetailsLoading = if (prod != null) false else state.isDetailsLoading
                        )
                    }
                }
            }
            launch {
                repository.getApiProductQuotationsFlow(productId).collect { (summary, quotations) ->
                    _uiState.update {
                        it.copy(
                            quotationSummary = summary,
                            supplierQuotations = quotations,
                            selectedProductOffers = quotations.map { q -> q.offer }
                        )
                    }
                }
            }
            launch {
                repository.getApiAlternativeBrandQuotationsFlow(productId).collect { alts ->
                    _uiState.update {
                        it.copy(
                            alternativeBrandQuotations = alts,
                            alternativeBrands = alts.map { alt -> alt.product }
                        )
                    }
                }
            }

            // Refresh from backend APIs when PRODUCTION_BACKEND_HTTP is active
            val syncRes = repository.refreshProductDetailsFromBackend(productId)
            if (syncRes.isFailure) {
                val errMsg = syncRes.exceptionOrNull()?.message
                    ?: "Failed to fetch live supplier quotations from server."
                _uiState.update {
                    it.copy(
                        isDetailsLoading = false,
                        detailsErrorMessage = errMsg
                    )
                }
            } else {
                // Ensure loading clears even if product ID does not exist
                val resolved = repository.getApiProductById(productId)
                _uiState.update {
                    it.copy(
                        selectedProduct = it.selectedProduct ?: resolved,
                        isDetailsLoading = false
                    )
                }
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
        if (quantity <= 0) {
            _uiState.update { it.copy(errorMessage = "Quantity must be greater than 0.") }
            return
        }
        viewModelScope.launch {
            val offers = repository.getApiProductOffers(product.id)
            val eligibleOffer = offers.firstOrNull { offer ->
                val notExpired = offer.validTillEpochMillis <= 0L ||
                    offer.validTillEpochMillis > System.currentTimeMillis()
                offer.isActive && notExpired && offer.availableQuantity >= offer.moq && offer.unitPricePtr > 0.0
            }
            val offerId = product.bestOfferId ?: eligibleOffer?.offerId ?: 0L
            if (offerId <= 0L) {
                _uiState.update { it.copy(errorMessage = "No eligible supplier quotation available for ${product.name}.") }
                return@launch
            }
            val result = repository.postApiBasketItems(product.id, offerId, quantity)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(successMessage = "Added ${product.name} ($quantity) to basket!") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to add to basket") }
                }
            )
        }
    }

    fun incrementQuantity(product: ProductDetail) {
        val currentQty = _uiState.value.cartProductQuantities[product.id] ?: 0
        val newQty = currentQty + 1
        viewModelScope.launch {
            val existingItemId = _uiState.value.cartProductItemIds[product.id]
            val result = if (existingItemId != null && currentQty > 0) {
                repository.putApiBasketItemQuantity(existingItemId, newQty)
            } else {
                val offerId = product.bestOfferId
                    ?: repository.getApiProductOffers(product.id).firstOrNull()?.offerId
                    ?: 0L
                repository.postApiBasketItems(product.id, offerId, 1)
            }
            result.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Could not update basket quantity") }
            }
        }
    }

    fun decrementQuantity(product: ProductDetail) {
        val currentQty = _uiState.value.cartProductQuantities[product.id] ?: 0
        val newQty = currentQty - 1
        viewModelScope.launch {
            val existingItemId = _uiState.value.cartProductItemIds[product.id]
            if (existingItemId != null) {
                val result = if (newQty <= 0) {
                    repository.deleteApiBasketItem(existingItemId)
                } else {
                    repository.putApiBasketItemQuantity(existingItemId, newQty)
                }
                result.onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Could not update basket quantity") }
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

    fun addOfferToBasket(offer: OfferWithSupplier, quantity: Int, onSuccess: () -> Unit = {}) {
        val now = System.currentTimeMillis()
        when {
            !offer.isActive -> {
                _uiState.update { it.copy(errorMessage = "Selected supplier quotation is inactive.") }
                return
            }
            offer.validTillEpochMillis in 1..now -> {
                _uiState.update { it.copy(errorMessage = "Selected supplier quotation has expired.") }
                return
            }
            offer.unitPricePtr <= 0.0 -> {
                _uiState.update { it.copy(errorMessage = "Selected supplier quotation is missing a valid PTR.") }
                return
            }
            offer.availableQuantity <= 0 -> {
                _uiState.update { it.copy(errorMessage = "${offer.supplierBusinessName} is currently out of stock.") }
                return
            }
            offer.availableQuantity < offer.moq -> {
                _uiState.update {
                    it.copy(
                        errorMessage = "Offer unavailable: quoted stock (${offer.availableQuantity}) is less than MOQ (${offer.moq})."
                    )
                }
                return
            }
            quantity <= 0 -> {
                _uiState.update { it.copy(errorMessage = "Quantity must be greater than 0.") }
                return
            }
            quantity < offer.moq -> {
                _uiState.update {
                    it.copy(errorMessage = "Minimum order quantity (MOQ) is ${offer.moq} units.")
                }
                return
            }
            quantity > offer.availableQuantity -> {
                _uiState.update {
                    it.copy(
                        errorMessage = "Requested quantity ($quantity) exceeds available stock (${offer.availableQuantity})."
                    )
                }
                return
            }
        }

        val inBasketForOffer = _uiState.value.cartOfferQuantities[offer.offerId] ?: 0
        if (inBasketForOffer + quantity > offer.availableQuantity) {
            _uiState.update {
                it.copy(
                    errorMessage = "Cannot add $quantity more units: basket already has $inBasketForOffer units and only ${offer.availableQuantity} are available."
                )
            }
            return
        }

        viewModelScope.launch {
            val result = repository.postApiBasketItems(offer.productId, offer.offerId, quantity)
            result.fold(
                onSuccess = {
                    val actionVerb = if (inBasketForOffer > 0) "Updated" else "Added"
                    _uiState.update {
                        it.copy(
                            successMessage = "$actionVerb $quantity units from ${offer.supplierBusinessName} in basket!"
                        )
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Could not add to basket") }
                }
            )
        }
    }

    private fun recomputeDerivedState(state: CatalogueUiState): CatalogueUiState {
        val allProducts = state.allProducts
        val allManufacturers = buildManufacturerSummaries(allProducts)
        val filteredProducts = applyFiltersAndSort(allProducts, state)
        val filteredManufacturers = filterManufacturers(allProducts, state)

        val scopeProductsForFacets = if (!state.selectedManufacturer.isNullOrBlank()) {
            allProducts.filter {
                it.manufacturerName.trim().equals(state.selectedManufacturer.trim(), ignoreCase = true)
            }
        } else {
            allProducts
        }

        val availableManufacturers = allManufacturers.map { it.name }
        val availableStrengths = scopeProductsForFacets
            .map { it.strength.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sorted()
        val availableSalts = scopeProductsForFacets
            .map { it.saltComposition.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sorted()

        return state.copy(
            manufacturers = allManufacturers,
            filteredManufacturers = filteredManufacturers,
            filteredProducts = filteredProducts,
            availableManufacturers = availableManufacturers,
            availableStrengths = availableStrengths,
            availableSalts = availableSalts
        )
    }

    private fun buildManufacturerSummaries(list: List<ProductDetail>): List<ManufacturerSummary> {
        val grouped = linkedMapOf<String, MutableList<ProductDetail>>()
        for (product in list) {
            val displayName = product.manufacturerName.trim().ifBlank { "Independent Manufacturer" }
            val key = displayName.lowercase()
            grouped.getOrPut(key) { mutableListOf() }.add(product)
        }

        return grouped.values.map { products ->
            val canonicalName = products.first().manufacturerName.trim().ifBlank { "Independent Manufacturer" }
            val dosageForms = products.map { it.dosageForm }.distinct().sortedBy { it.displayName }
            val categories = products
                .map { it.therapeuticCategory.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
            val strengths = products
                .map { it.strength.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
            val salts = products
                .map { it.saltComposition.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
            val activeOfferProductCount = products.count { it.activeOfferCount > 0 || it.bestOfferId != null }
            val minPrice = products.mapNotNull { it.minOfferPrice }.minOrNull()
            val maxMargin = products.mapNotNull { it.marginPercent }.maxOrNull()

            ManufacturerSummary(
                name = canonicalName,
                productCount = products.size,
                activeOfferProductCount = activeOfferProductCount,
                dosageForms = dosageForms,
                therapeuticCategories = categories,
                strengths = strengths,
                saltCompositions = salts,
                minOfferPrice = minPrice,
                maxMarginPercent = maxMargin
            )
        }.sortedBy { it.name.lowercase() }
    }

    private fun filterManufacturers(
        list: List<ProductDetail>,
        state: CatalogueUiState
    ): List<ManufacturerSummary> {
        val stateWithoutSelectedMfg = state.copy(selectedManufacturer = null)
        val matchingProducts = applyFiltersAndSort(list, stateWithoutSelectedMfg)
        return buildManufacturerSummaries(matchingProducts)
    }

    private fun applyFiltersAndSort(
        list: List<ProductDetail>,
        state: CatalogueUiState
    ): List<ProductDetail> {
        val q = state.searchQuery.trim().lowercase()
        val mfgFilter = state.selectedManufacturer?.trim()
        val strengthFilter = state.selectedStrength?.trim()
        val saltFilter = state.selectedSaltFilter.trim().lowercase()

        var filtered = list.filter { p ->
            val matchesQuery = q.isEmpty() ||
                p.name.lowercase().contains(q) ||
                p.brand.lowercase().contains(q) ||
                p.saltComposition.lowercase().contains(q) ||
                p.strength.lowercase().contains(q) ||
                p.dosageForm.displayName.lowercase().contains(q) ||
                p.therapeuticCategory.lowercase().contains(q) ||
                p.manufacturerName.lowercase().contains(q)

            val matchesManufacturer = mfgFilter.isNullOrBlank() ||
                p.manufacturerName.trim().equals(mfgFilter, ignoreCase = true)

            val matchesStrength = strengthFilter.isNullOrBlank() ||
                p.strength.trim().equals(strengthFilter, ignoreCase = true) ||
                p.strength.contains(strengthFilter, ignoreCase = true)

            val matchesSalt = saltFilter.isEmpty() ||
                p.saltComposition.lowercase().contains(saltFilter)

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

            matchesQuery &&
                matchesManufacturer &&
                matchesStrength &&
                matchesSalt &&
                matchesChip &&
                matchesDosage &&
                matchesCategory &&
                matchesStorage &&
                matchesRx &&
                matchesStock
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

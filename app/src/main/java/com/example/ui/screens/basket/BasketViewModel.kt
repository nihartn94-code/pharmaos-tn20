package com.example.ui.screens.basket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AuthUser
import com.example.domain.repository.BasketLoadReport
import com.example.domain.repository.BasketWithDetails
import com.example.domain.repository.DuplicateBasketTemplateException
import com.example.domain.repository.NamedBasketSummary
import com.example.domain.repository.PharmaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class BasketUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSavingTemplate: Boolean = false,
    val currentUser: AuthUser? = null,
    val basket: BasketWithDetails? = null,
    val basketRevision: Long = 0L,
    val savedBaskets: List<NamedBasketSummary> = emptyList(),
    val loadReport: BasketLoadReport? = null,
    val syncErrorMessage: String? = null,
    val templateSaveError: String? = null,
    val duplicateTemplateCandidate: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val messageNonce: Long = 0L
)

class BasketViewModel(
    private val repository: PharmaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BasketUiState())
    val uiState: StateFlow<BasketUiState> = _uiState.asStateFlow()

    // Serializes all user basket mutations (rapid +/- taps, quick adds, removals, template loads)
    // so every operation reads and persists against the latest authoritative quantity.
    // Network refresh pulls run OUTSIDE this mutex so user taps are never blocked by slow/offline syncs.
    private val basketMutationMutex = Mutex()
    private val mutationEpoch = AtomicLong(0L)
    private val activeMutationCount = AtomicInteger(0)

    init {
        loadCurrentUser()
        observeBasket()
        observeSavedBaskets()
        refreshBasket(isInitialSync = true)
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
                // While serialized local mutations are mid-flight, the mutation coroutine itself
                // commits to Room and publishes the authoritative snapshot immediately.
                // Repository's monotonic CommittedBasketSnapshot guarantees no stale pre-mutation
                // Room snapshot can ever be emitted afterward.
                if (activeMutationCount.get() == 0) {
                    _uiState.update { current ->
                        val changed = current.basket != b || current.isLoading
                        current.copy(
                            isLoading = false,
                            basket = b,
                            basketRevision = if (changed) current.basketRevision + 1L else current.basketRevision
                        )
                    }
                }
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

    fun refreshBasket(isInitialSync: Boolean = false) {
        val startedEpoch = mutationEpoch.get()
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, syncErrorMessage = null) }
            // Run remote refresh OUTSIDE basketMutationMutex so user quantity adjustments
            // are never blocked behind slow or failing network requests.
            val basketRes = repository.refreshBasketFromBackend()
            val savedRes = repository.refreshSavedBasketsFromBackend()
            val syncErr = basketRes.exceptionOrNull()?.message ?: savedRes.exceptionOrNull()?.message

            basketMutationMutex.withLock {
                // Only apply the refreshed snapshot if no newer user mutation occurred while refreshing.
                if (!isInitialSync || mutationEpoch.get() == startedEpoch) {
                    val latestBasket = repository.getApiBasket()
                    if (mutationEpoch.get() == startedEpoch) {
                        _uiState.update { current ->
                            current.copy(
                                isLoading = false,
                                basket = latestBasket,
                                basketRevision = current.basketRevision + 1L
                            )
                        }
                    }
                }
            }
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    syncErrorMessage = syncErr
                )
            }
        }
    }

    fun quickAddQuantity(itemId: Long, addedUnits: Int) {
        if (addedUnits <= 0) {
            _uiState.update {
                it.copy(
                    successMessage = null,
                    errorMessage = "Quick add quantity must be greater than 0.",
                    messageNonce = it.messageNonce + 1L
                )
            }
            return
        }
        adjustQuantity(itemId = itemId, delta = addedUnits)
    }

    fun adjustQuantity(itemId: Long, delta: Int) {
        if (delta == 0) return
        mutationEpoch.incrementAndGet()
        activeMutationCount.incrementAndGet()
        viewModelScope.launch {
            try {
                basketMutationMutex.withLock {
                    val authoritativeBasket = repository.getApiBasket()
                    val currentItem = authoritativeBasket.items.firstOrNull { it.itemId == itemId }
                    if (currentItem == null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                basket = authoritativeBasket,
                                basketRevision = it.basketRevision + 1L,
                                successMessage = null,
                                errorMessage = "Basket item not found.",
                                messageNonce = it.messageNonce + 1L
                            )
                        }
                        return@withLock
                    }

                    val targetQuantity = currentItem.quantity + delta
                    if (targetQuantity < 0) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                basket = authoritativeBasket,
                                basketRevision = it.basketRevision + 1L,
                                successMessage = null,
                                errorMessage = "Quantity cannot be negative.",
                                messageNonce = it.messageNonce + 1L
                            )
                        }
                        return@withLock
                    }
                    if (targetQuantity == 0) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                basket = authoritativeBasket,
                                basketRevision = it.basketRevision + 1L,
                                successMessage = null,
                                errorMessage = "Quantity must be at least 1. Tap Remove to delete this item.",
                                messageNonce = it.messageNonce + 1L
                            )
                        }
                        return@withLock
                    }

                    val result = repository.putApiBasketItemQuantity(itemId, targetQuantity)
                    val updatedBasket = repository.getApiBasket()
                    result.fold(
                        onSuccess = {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    basket = updatedBasket,
                                    basketRevision = it.basketRevision + 1L,
                                    errorMessage = null
                                )
                            }
                        },
                        onFailure = { err ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    basket = updatedBasket,
                                    basketRevision = it.basketRevision + 1L,
                                    successMessage = null,
                                    errorMessage = err.message ?: "Failed to update quantity",
                                    messageNonce = it.messageNonce + 1L
                                )
                            }
                        }
                    )
                }
            } finally {
                activeMutationCount.decrementAndGet()
            }
        }
    }

    fun updateQuantity(itemId: Long, newQuantity: Int) {
        if (newQuantity < 0) {
            _uiState.update {
                it.copy(
                    successMessage = null,
                    errorMessage = "Quantity cannot be negative.",
                    messageNonce = it.messageNonce + 1L
                )
            }
            return
        }
        mutationEpoch.incrementAndGet()
        activeMutationCount.incrementAndGet()
        viewModelScope.launch {
            try {
                basketMutationMutex.withLock {
                    val result = repository.putApiBasketItemQuantity(itemId, newQuantity)
                    val updatedBasket = repository.getApiBasket()
                    result.fold(
                        onSuccess = {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    basket = updatedBasket,
                                    basketRevision = it.basketRevision + 1L,
                                    errorMessage = null
                                )
                            }
                        },
                        onFailure = { err ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    basket = updatedBasket,
                                    basketRevision = it.basketRevision + 1L,
                                    successMessage = null,
                                    errorMessage = err.message ?: "Failed to update quantity",
                                    messageNonce = it.messageNonce + 1L
                                )
                            }
                        }
                    )
                }
            } finally {
                activeMutationCount.decrementAndGet()
            }
        }
    }

    fun removeItem(itemId: Long) {
        mutationEpoch.incrementAndGet()
        activeMutationCount.incrementAndGet()
        viewModelScope.launch {
            try {
                basketMutationMutex.withLock {
                    val result = repository.deleteApiBasketItem(itemId)
                    val updatedBasket = repository.getApiBasket()
                    result.fold(
                        onSuccess = {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    basket = updatedBasket,
                                    basketRevision = it.basketRevision + 1L,
                                    errorMessage = null,
                                    successMessage = "Item removed from basket.",
                                    messageNonce = it.messageNonce + 1L
                                )
                            }
                        },
                        onFailure = { err ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    basket = updatedBasket,
                                    basketRevision = it.basketRevision + 1L,
                                    successMessage = null,
                                    errorMessage = err.message ?: "Failed to remove item from basket.",
                                    messageNonce = it.messageNonce + 1L
                                )
                            }
                        }
                    )
                }
            } finally {
                activeMutationCount.decrementAndGet()
            }
        }
    }

    fun clearBasket() {
        mutationEpoch.incrementAndGet()
        activeMutationCount.incrementAndGet()
        viewModelScope.launch {
            try {
                basketMutationMutex.withLock {
                    val result = repository.deleteApiBasketClear()
                    val updatedBasket = repository.getApiBasket()
                    result.fold(
                        onSuccess = {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    basket = updatedBasket,
                                    basketRevision = it.basketRevision + 1L,
                                    errorMessage = null,
                                    successMessage = "Basket cleared.",
                                    loadReport = null,
                                    messageNonce = it.messageNonce + 1L
                                )
                            }
                        },
                        onFailure = { err ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    basket = updatedBasket,
                                    basketRevision = it.basketRevision + 1L,
                                    successMessage = null,
                                    errorMessage = err.message ?: "Failed to clear basket.",
                                    messageNonce = it.messageNonce + 1L
                                )
                            }
                        }
                    )
                }
            } finally {
                activeMutationCount.decrementAndGet()
            }
        }
    }

    fun saveNamedBasket(
        name: String,
        overwriteExisting: Boolean = false,
        onComplete: () -> Unit
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(templateSaveError = "Please enter a template name.") }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSavingTemplate = true,
                    templateSaveError = null,
                    duplicateTemplateCandidate = null
                )
            }
            val result = repository.postApiBasketSaveNamed(trimmed, overwriteExisting)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSavingTemplate = false,
                            templateSaveError = null,
                            duplicateTemplateCandidate = null,
                            successMessage = if (overwriteExisting) {
                                "Template '$trimmed' updated!"
                            } else {
                                "Basket template '$trimmed' saved!"
                            },
                            messageNonce = it.messageNonce + 1L
                        )
                    }
                    onComplete()
                },
                onFailure = { err ->
                    val dupName = (err as? DuplicateBasketTemplateException)?.templateName
                    _uiState.update {
                        it.copy(
                            isSavingTemplate = false,
                            templateSaveError = err.message ?: "Failed to save basket template.",
                            duplicateTemplateCandidate = dupName
                        )
                    }
                }
            )
        }
    }

    fun clearTemplateDialogState() {
        _uiState.update {
            it.copy(
                templateSaveError = null,
                duplicateTemplateCandidate = null,
                isSavingTemplate = false
            )
        }
    }

    fun loadSavedBasket(savedBasketId: Long, onLoaded: () -> Unit = {}) {
        mutationEpoch.incrementAndGet()
        activeMutationCount.incrementAndGet()
        viewModelScope.launch {
            try {
                basketMutationMutex.withLock {
                    val result = repository.postApiBasketLoadSaved(savedBasketId)
                    val updatedBasket = repository.getApiBasket()
                    result.fold(
                        onSuccess = { report ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    basket = updatedBasket,
                                    basketRevision = it.basketRevision + 1L,
                                    loadReport = report,
                                    errorMessage = null,
                                    successMessage = "Loaded ${report.itemsLoadedCount} item(s) from template.",
                                    messageNonce = it.messageNonce + 1L
                                )
                            }
                            onLoaded()
                        },
                        onFailure = { err ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    basket = updatedBasket,
                                    basketRevision = it.basketRevision + 1L,
                                    successMessage = null,
                                    errorMessage = err.message ?: "Failed to load saved basket.",
                                    messageNonce = it.messageNonce + 1L
                                )
                            }
                        }
                    )
                }
            } finally {
                activeMutationCount.decrementAndGet()
            }
        }
    }

    fun deleteSavedBasket(savedBasketId: Long) {
        viewModelScope.launch {
            val result = repository.deleteApiSavedBasket(savedBasketId)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            successMessage = "Template deleted.",
                            messageNonce = it.messageNonce + 1L
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            errorMessage = err.message ?: "Failed to delete template.",
                            messageNonce = it.messageNonce + 1L
                        )
                    }
                }
            )
        }
    }

    fun dismissLoadReport() {
        _uiState.update { it.copy(loadReport = null) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}

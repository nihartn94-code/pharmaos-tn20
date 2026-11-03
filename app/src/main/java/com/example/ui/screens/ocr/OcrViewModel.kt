package com.example.ui.screens.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ProductEntity
import com.example.domain.model.ParsedOcrItem
import com.example.domain.model.ProductDetail
import com.example.domain.repository.PharmaRepository
import com.example.domain.usecase.ParsePurchaseListUseCase
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

data class OcrUiState(
    val isScanning: Boolean = false,
    val rawText: String = "",
    val parsedItems: List<ParsedOcrItem> = emptyList(),
    val catalogueProducts: List<ProductDetail> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val itemsAddedCount: Int = 0
)

class OcrViewModel(
    private val repository: PharmaRepository,
    private val parsePurchaseListUseCase: ParsePurchaseListUseCase = ParsePurchaseListUseCase()
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    init {
        loadCatalogue()
    }

    private fun loadCatalogue() {
        viewModelScope.launch {
            repository.getApiProductsFlow().collect { prods ->
                _uiState.update { it.copy(catalogueProducts = prods) }
            }
        }
    }

    fun scanImageUri(context: Context, imageUri: Uri, onParsed: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, errorMessage = null) }
            try {
                val inputImage = withContext(Dispatchers.IO) {
                    InputImage.fromFilePath(context, imageUri)
                }
                val visionText = textRecognizer.process(inputImage).awaitTask()
                processRawText(visionText.text, onParsed)
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanning = false, errorMessage = "OCR recognition failed: ${e.message}") }
            }
        }
    }

    fun scanBitmap(bitmap: Bitmap, onParsed: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, errorMessage = null) }
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val visionText = textRecognizer.process(inputImage).awaitTask()
                processRawText(visionText.text, onParsed)
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanning = false, errorMessage = "Camera capture OCR failed: ${e.message}") }
            }
        }
    }

    fun processRawText(text: String, onParsed: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, rawText = text) }
            val rawCatalogue = repository.getApiProducts()
            // Convert to ProductEntity for usecase matcher
            val entityList = rawCatalogue.map { p ->
                ProductEntity(
                    id = p.id,
                    name = p.name,
                    brand = p.brand,
                    saltComposition = p.saltComposition,
                    strength = p.strength,
                    dosageForm = p.dosageForm.name,
                    therapeuticCategory = p.therapeuticCategory,
                    manufacturerName = p.manufacturerName,
                    packSize = p.packSize,
                    hsnCode = p.hsnCode,
                    gstRatePercent = p.gstRatePercent,
                    storageCondition = p.storageCondition.name,
                    isRx = p.isRx,
                    createdBy = p.createdBy
                )
            }

            val parsed = parsePurchaseListUseCase.parse(text, entityList)
            _uiState.update {
                it.copy(
                    isScanning = false,
                    parsedItems = parsed,
                    errorMessage = if (parsed.isEmpty()) "No recognizable medicine lines detected. You can paste or type lines manually." else null
                )
            }
            onParsed()
        }
    }

    fun updateParsedItem(index: Int, medicineName: String, quantity: Int, matchedProductId: Long?) {
        _uiState.update { state ->
            val list = state.parsedItems.toMutableList()
            if (index in list.indices) {
                val old = list[index]
                val matchedProd = state.catalogueProducts.find { it.id == matchedProductId }
                list[index] = old.copy(
                    medicineName = medicineName,
                    quantity = quantity,
                    matchedProductId = matchedProductId,
                    matchedProductName = matchedProd?.let { "${it.name} (${it.brand})" },
                    matchConfidence = if (matchedProductId != null) 1.0 else 0.0,
                    isConfirmed = matchedProductId != null
                )
            }
            state.copy(parsedItems = list)
        }
    }

    fun addConfirmedItemsToBasket(onComplete: () -> Unit) {
        viewModelScope.launch {
            val confirmed = _uiState.value.parsedItems.filter { it.matchedProductId != null }
            if (confirmed.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "Please match at least one line to a catalogue medicine.") }
                return@launch
            }

            var added = 0
            for (item in confirmed) {
                val prodId = item.matchedProductId ?: continue
                val offers = repository.getApiProductOffers(prodId)
                val bestOffer = offers.minByOrNull { it.unitPricePtr }
                if (bestOffer != null) {
                    val targetQty = Math.max(item.quantity, bestOffer.moq)
                    repository.postApiBasketItems(prodId, bestOffer.offerId, targetQty)
                    added++
                }
            }

            _uiState.update { it.copy(itemsAddedCount = added, successMessage = "Added $added items to basket successfully!") }
            onComplete()
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}

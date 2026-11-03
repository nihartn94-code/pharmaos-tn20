package com.example.ui.screens.returns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.CreditNoteEntity
import com.example.domain.model.AuthUser
import com.example.domain.model.ReturnReason
import com.example.domain.repository.PharmaRepository
import com.example.domain.repository.ReturnWithDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReturnsUiState(
    val isLoading: Boolean = true,
    val currentUser: AuthUser? = null,
    val returns: List<ReturnWithDetails> = emptyList(),
    val creditNotes: List<CreditNoteEntity> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class ReturnsViewModel(
    private val repository: PharmaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReturnsUiState())
    val uiState: StateFlow<ReturnsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getApiUsersMeFlow().collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
        viewModelScope.launch {
            repository.getApiReturnsFlow().collect { list ->
                _uiState.update { it.copy(isLoading = false, returns = list) }
            }
        }
        viewModelScope.launch {
            repository.getApiCreditNotesFlow().collect { list ->
                _uiState.update { it.copy(creditNotes = list) }
            }
        }
    }

    fun raiseReturn(
        orderId: Long,
        orderItemId: Long,
        quantity: Int,
        reason: ReturnReason,
        batchNumber: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.postApiReturns(orderId, orderItemId, quantity, reason, batchNumber)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(successMessage = "Return request registered!") }
                    onComplete()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to raise return") }
                }
            )
        }
    }

    fun reviewReturn(returnId: Long, isApproved: Boolean, notes: String) {
        viewModelScope.launch {
            val result = repository.patchApiReturnStatus(returnId, isApproved, notes)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(successMessage = if (isApproved) "Return approved and Credit Note generated!" else "Return rejected.") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to update return") }
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}

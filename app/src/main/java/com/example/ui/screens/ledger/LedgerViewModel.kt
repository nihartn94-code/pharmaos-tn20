package com.example.ui.screens.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.CreditLedgerEntryEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.AuthUser
import com.example.domain.model.UserRole
import com.example.domain.repository.PharmaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LedgerUiState(
    val isLoading: Boolean = true,
    val currentUser: AuthUser? = null,
    val counterparties: List<UserEntity> = emptyList(),
    val selectedCounterpartyId: Long? = null,
    val entries: List<CreditLedgerEntryEntity> = emptyList(),
    val outstandingBalance: Double = 0.0,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class LedgerViewModel(
    private val repository: PharmaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LedgerUiState())
    val uiState: StateFlow<LedgerUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getApiUsersMeFlow().collect { user ->
                _uiState.update { it.copy(currentUser = user) }
                if (user != null) {
                    loadCounterpartiesAndLedger(user)
                }
            }
        }
    }

    private fun loadCounterpartiesAndLedger(currentUser: AuthUser) {
        viewModelScope.launch {
            repository.getApiUsersFlow().collect { allUsers ->
                val counterparties = if (currentUser.role.isSupplier) {
                    allUsers.filter { it.role == UserRole.STOCKIST.name }
                } else {
                    allUsers.filter { it.role != UserRole.STOCKIST.name && it.role != UserRole.ADMIN.name }
                }
                _uiState.update { it.copy(counterparties = counterparties) }

                observeLedgerEntries(_uiState.value.selectedCounterpartyId)
            }
        }
    }

    fun selectCounterparty(userId: Long?) {
        _uiState.update { it.copy(selectedCounterpartyId = userId) }
        observeLedgerEntries(userId)
    }

    private fun observeLedgerEntries(counterpartyId: Long?) {
        viewModelScope.launch {
            repository.getApiLedgerEntriesFlow(counterpartyId).collect { list ->
                val bal = if (counterpartyId != null) {
                    repository.getApiLedgerBalance(counterpartyId)
                } else {
                    list.firstOrNull()?.balanceAfter ?: 0.0
                }
                _uiState.update { it.copy(isLoading = false, entries = list, outstandingBalance = bal) }
            }
        }
    }

    fun recordPayment(buyerId: Long, amount: Double, notes: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val result = repository.postApiLedgerPayment(buyerId, amount, notes)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(successMessage = "Payment recorded and credited to ledger!") }
                    onComplete()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to record payment") }
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}

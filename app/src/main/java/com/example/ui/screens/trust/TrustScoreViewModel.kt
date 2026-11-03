package com.example.ui.screens.trust

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AuthUser
import com.example.domain.model.TrustScoreBreakdown
import com.example.domain.repository.PharmaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrustScoreUiState(
    val isLoading: Boolean = true,
    val currentUser: AuthUser? = null,
    val myScoreBreakdown: TrustScoreBreakdown? = null,
    val allSuppliersComparison: List<TrustScoreBreakdown> = emptyList(),
    val errorMessage: String? = null
)

class TrustScoreViewModel(
    private val repository: PharmaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrustScoreUiState())
    val uiState: StateFlow<TrustScoreUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getApiUsersMeFlow().collect { user ->
                _uiState.update { it.copy(currentUser = user) }
                if (user != null && user.role.isSupplier) {
                    val breakdown = repository.getApiSuppliersTrustScore(user.id)
                    _uiState.update { it.copy(myScoreBreakdown = breakdown) }
                }
            }
        }
        viewModelScope.launch {
            repository.getApiSupplierTrustScoreComparisonFlow().collect { list ->
                _uiState.update { it.copy(isLoading = false, allSuppliersComparison = list) }
            }
        }
    }
}

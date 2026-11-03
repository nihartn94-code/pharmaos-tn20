package com.example.ui.screens.optimizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.OptimizationResult
import com.example.domain.repository.PharmaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OptimizerUiState(
    val isOptimizing: Boolean = true,
    val preferReliableSuppliers: Boolean = false,
    val result: OptimizationResult? = null,
    val isApplying: Boolean = false,
    val errorMessage: String? = null,
    val appliedSuccessfully: Boolean = false
)

class OptimizerViewModel(
    private val repository: PharmaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OptimizerUiState())
    val uiState: StateFlow<OptimizerUiState> = _uiState.asStateFlow()

    init {
        runOptimization(false)
    }

    fun setPreferReliableSuppliers(prefer: Boolean) {
        _uiState.update { it.copy(preferReliableSuppliers = prefer) }
        runOptimization(prefer)
    }

    fun runOptimization(preferReliable: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOptimizing = true, errorMessage = null) }
            try {
                val res = repository.postApiBasketOptimize(preferReliable)
                _uiState.update { it.copy(isOptimizing = false, result = res) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isOptimizing = false, errorMessage = e.message ?: "Optimization failed") }
            }
        }
    }

    fun applySuggestion(onApplied: () -> Unit) {
        val res = _uiState.value.result ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true) }
            val outcome = repository.postApiBasketApplyOptimization(res)
            outcome.fold(
                onSuccess = {
                    _uiState.update { it.copy(isApplying = false, appliedSuccessfully = true) }
                    onApplied()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isApplying = false, errorMessage = err.message ?: "Failed to apply suggestion") }
                }
            )
        }
    }
}

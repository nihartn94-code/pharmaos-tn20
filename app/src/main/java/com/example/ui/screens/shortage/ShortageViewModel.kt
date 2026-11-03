package com.example.ui.screens.shortage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AuthUser
import com.example.domain.model.ShortageEvaluationResult
import com.example.domain.model.ShortageRiskLevel
import com.example.domain.repository.PharmaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShortageUiState(
    val isLoading: Boolean = true,
    val currentUser: AuthUser? = null,
    val alerts: List<ShortageEvaluationResult> = emptyList(),
    val filteredAlerts: List<ShortageEvaluationResult> = emptyList(),
    val selectedRiskFilter: ShortageRiskLevel? = null,
    val errorMessage: String? = null
)

class ShortageViewModel(
    private val repository: PharmaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShortageUiState())
    val uiState: StateFlow<ShortageUiState> = _uiState.asStateFlow()

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
            repository.getApiShortageAlertsFlow().collect { list ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        alerts = list,
                        filteredAlerts = filterList(list, it.selectedRiskFilter)
                    )
                }
            }
        }
    }

    fun setRiskFilter(level: ShortageRiskLevel?) {
        _uiState.update {
            it.copy(
                selectedRiskFilter = level,
                filteredAlerts = filterList(it.alerts, level)
            )
        }
    }

    fun reevaluateAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val list = repository.evaluateAllShortageRisks()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    alerts = list,
                    filteredAlerts = filterList(list, it.selectedRiskFilter)
                )
            }
        }
    }

    private fun filterList(list: List<ShortageEvaluationResult>, level: ShortageRiskLevel?): List<ShortageEvaluationResult> {
        return if (level == null) list else list.filter { it.riskLevel == level }
    }
}

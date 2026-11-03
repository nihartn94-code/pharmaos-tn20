package com.example.ui.screens.compliance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AuthUser
import com.example.domain.model.UserRole
import com.example.domain.repository.PharmaRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComplianceUiState(
    val currentStep: Int = 1, // 1 = Overview, 2 = GSTIN, 3 = Drug License, 4 = WHO-GMP
    val currentUser: AuthUser? = null,
    val gstin: String = "",
    val isGstinVerified: Boolean = false,
    val isVerifyingGstin: Boolean = false,
    val verifiedEntityName: String? = null,
    val verifiedState: String? = null,
    val drugLicenseNumber: String = "",
    val drugLicenseIssueDate: String = "",
    val drugLicenseExpiryDate: String = "",
    val drugLicenseDocName: String? = null,
    val drugLicenseDocUri: String? = null,
    val isWhoGmpApplicable: Boolean = true,
    val whoGmpCertificateNumber: String = "",
    val whoGmpIssueDate: String = "",
    val whoGmpExpiryDate: String = "",
    val whoGmpDocName: String? = null,
    val whoGmpDocUri: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCompleted: Boolean = false
)

class ComplianceViewModel(
    private val repository: PharmaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComplianceUiState())
    val uiState: StateFlow<ComplianceUiState> = _uiState.asStateFlow()

    init {
        loadUser()
    }

    private fun loadUser() {
        viewModelScope.launch {
            val user = repository.getApiUsersMe()
            _uiState.update { current ->
                val gstinVal = user?.gstin ?: ""
                val isGmpDefault = user?.role == UserRole.MANUFACTURER
                current.copy(
                    currentUser = user,
                    gstin = gstinVal,
                    isWhoGmpApplicable = isGmpDefault,
                    // Pre-populate sample verified data if user already provided GSTIN during registration
                    isGstinVerified = gstinVal.isNotBlank() && gstinVal.length == 15,
                    verifiedEntityName = user?.businessName ?: "Apex Pharma Enterprises"
                )
            }
        }
    }

    fun goToStep(step: Int) {
        _uiState.update { it.copy(currentStep = step.coerceIn(1, 4), errorMessage = null) }
    }

    fun nextStep() {
        val current = _uiState.value.currentStep
        if (current < 4) {
            _uiState.update { it.copy(currentStep = current + 1, errorMessage = null) }
        }
    }

    fun previousStep() {
        val current = _uiState.value.currentStep
        if (current > 1) {
            _uiState.update { it.copy(currentStep = current - 1, errorMessage = null) }
        }
    }

    fun setGstin(value: String) {
        _uiState.update { it.copy(gstin = value.uppercase().trim(), isGstinVerified = false, errorMessage = null) }
    }

    fun verifyGstin(onSuccess: (() -> Unit)? = null) {
        val gstin = _uiState.value.gstin
        if (gstin.isBlank() || gstin.length != 15) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid 15-digit GSTIN (e.g. 27AAAAA0000A1Z5).") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingGstin = true, errorMessage = null) }
            delay(600) // Simulated API verification check with GST portal
            val stateCode = gstin.take(2)
            val stateName = when (stateCode) {
                "27" -> "Maharashtra"
                "24" -> "Gujarat"
                "07" -> "Delhi"
                "29" -> "Karnataka"
                "33" -> "Tamil Nadu"
                "09" -> "Uttar Pradesh"
                else -> "State Code $stateCode"
            }
            _uiState.update {
                it.copy(
                    isVerifyingGstin = false,
                    isGstinVerified = true,
                    verifiedEntityName = it.currentUser?.businessName ?: "Registered Pharma Enterprise",
                    verifiedState = stateName
                )
            }
            onSuccess?.invoke()
        }
    }

    fun scanGstinDemo() {
        val sampleGstin = "27AAACA1234F1Z5"
        _uiState.update {
            it.copy(
                gstin = sampleGstin,
                isGstinVerified = true,
                verifiedEntityName = it.currentUser?.businessName ?: "Apex Pharma Distributors",
                verifiedState = "Maharashtra (Code 27)"
            )
        }
    }

    fun updateDrugLicenseDetails(
        number: String,
        issueDate: String,
        expiryDate: String,
        docName: String? = null,
        docUri: String? = null
    ) {
        _uiState.update {
            it.copy(
                drugLicenseNumber = number.uppercase(),
                drugLicenseIssueDate = issueDate,
                drugLicenseExpiryDate = expiryDate,
                drugLicenseDocName = docName ?: it.drugLicenseDocName,
                drugLicenseDocUri = docUri ?: it.drugLicenseDocUri,
                errorMessage = null
            )
        }
    }

    fun setWhoGmpApplicable(applicable: Boolean) {
        _uiState.update { it.copy(isWhoGmpApplicable = applicable, errorMessage = null) }
    }

    fun updateWhoGmpDetails(
        number: String,
        issueDate: String,
        expiryDate: String,
        docName: String? = null,
        docUri: String? = null
    ) {
        _uiState.update {
            it.copy(
                whoGmpCertificateNumber = number.uppercase(),
                whoGmpIssueDate = issueDate,
                whoGmpExpiryDate = expiryDate,
                whoGmpDocName = docName ?: it.whoGmpDocName,
                whoGmpDocUri = docUri ?: it.whoGmpDocUri,
                errorMessage = null
            )
        }
    }

    fun submitCompliance(onSuccess: () -> Unit) {
        val state = _uiState.value
        val userId = state.currentUser?.id ?: 1L

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.saveComplianceVerification(
                userId = userId,
                gstin = state.gstin.ifBlank { "27AAACA1234F1Z5" },
                drugLicenseNumber = state.drugLicenseNumber.ifBlank { "20B/MH/104928" },
                drugLicenseIssueDate = state.drugLicenseIssueDate.ifBlank { "01/01/2023" },
                drugLicenseExpiryDate = state.drugLicenseExpiryDate.ifBlank { "31/12/2027" },
                drugLicenseDocUri = state.drugLicenseDocUri ?: "content://pharma/docs/drug_license.pdf",
                whoGmpCertificateNumber = if (state.isWhoGmpApplicable) state.whoGmpCertificateNumber.ifBlank { "WHO-GMP/MH/2022/884" } else null,
                whoGmpIssueDate = if (state.isWhoGmpApplicable) state.whoGmpIssueDate.ifBlank { "15/03/2022" } else null,
                whoGmpExpiryDate = if (state.isWhoGmpApplicable) state.whoGmpExpiryDate.ifBlank { "14/03/2027" } else null,
                whoGmpDocUri = if (state.isWhoGmpApplicable) state.whoGmpDocUri ?: "content://pharma/docs/who_gmp.pdf" else null,
                isWhoGmpApplicable = state.isWhoGmpApplicable
            )

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, isCompleted = true) }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Failed to save compliance details.") }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

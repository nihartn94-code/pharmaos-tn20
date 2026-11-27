package com.example.ui.screens.compliance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.config.AuthExecutionMode
import com.example.domain.model.AuthUser
import com.example.domain.model.UserRole
import com.example.domain.repository.PharmaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComplianceUiState(
    val currentStep: Int = 1, // 1 = Overview, 2 = GSTIN, 3 = Drug License, 4 = WHO-GMP
    val currentUser: AuthUser? = null,
    val isSessionLoaded: Boolean = false,
    val authExecutionMode: AuthExecutionMode = AuthExecutionMode.PRODUCTION_BACKEND_HTTP,
    val gstin: String = "",
    val isGstinFormatValid: Boolean = false,
    val isGstinVerified: Boolean = false,
    val isVerifyingGstin: Boolean = false,
    val verifiedEntityName: String? = null,
    val verifiedState: String? = null,
    val drugLicenseNumber: String = "",
    val drugLicenseIssueDate: String = "",
    val drugLicenseExpiryDate: String = "",
    val drugLicenseDocName: String? = null,
    val drugLicenseDocUri: String? = null,
    val isWhoGmpApplicable: Boolean = false,
    val whoGmpCertificateNumber: String = "",
    val whoGmpIssueDate: String = "",
    val whoGmpExpiryDate: String = "",
    val whoGmpDocName: String? = null,
    val whoGmpDocUri: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val isCompleted: Boolean = false
) {
    val isDemoModeEnabled: Boolean
        get() = authExecutionMode == AuthExecutionMode.DEVELOPMENT_LOCAL_MODE
}

class ComplianceViewModel(
    private val repository: PharmaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComplianceUiState())
    val uiState: StateFlow<ComplianceUiState> = _uiState.asStateFlow()

    init {
        observeAuthExecutionMode()
        loadUser()
    }

    private fun observeAuthExecutionMode() {
        viewModelScope.launch {
            repository.getAuthExecutionModeFlow()
                .catch { err -> if (err is CancellationException) throw err }
                .collect { mode ->
                    _uiState.update { it.copy(authExecutionMode = mode) }
                }
        }
    }

    private fun loadUser() {
        viewModelScope.launch {
            try {
                val user = repository.getApiUsersMe()
                if (user == null) {
                    _uiState.update {
                        it.copy(
                            currentUser = null,
                            isSessionLoaded = true,
                            errorMessage = "Your session is not active. Please sign in again to complete compliance verification."
                        )
                    }
                    return@launch
                }

                val existingCompliance = runCatching {
                    repository.getComplianceVerificationFlow(user.id).firstOrNull()
                }.getOrNull()

                val initialGstin = existingCompliance?.gstin?.takeIf { it.isNotBlank() }
                    ?: user.gstin?.trim()?.uppercase().orEmpty()
                val formatValid = isValidGstinFormat(initialGstin)
                val isGmpDefault = existingCompliance?.isWhoGmpApplicable
                    ?: (user.role == UserRole.MANUFACTURER)

                _uiState.update { current ->
                    current.copy(
                        currentUser = user,
                        isSessionLoaded = true,
                        gstin = initialGstin,
                        isGstinFormatValid = formatValid,
                        isGstinVerified = existingCompliance?.isGstinVerified == true,
                        verifiedEntityName = user.businessName.takeIf { it.isNotBlank() },
                        verifiedState = if (formatValid) resolveGstStateName(initialGstin.take(2)) else null,
                        drugLicenseNumber = existingCompliance?.drugLicenseNumber.orEmpty(),
                        drugLicenseIssueDate = existingCompliance?.drugLicenseIssueDate.orEmpty(),
                        drugLicenseExpiryDate = existingCompliance?.drugLicenseExpiryDate.orEmpty(),
                        drugLicenseDocUri = existingCompliance?.drugLicenseDocUri,
                        isWhoGmpApplicable = isGmpDefault,
                        whoGmpCertificateNumber = existingCompliance?.whoGmpCertificateNumber.orEmpty(),
                        whoGmpIssueDate = existingCompliance?.whoGmpIssueDate.orEmpty(),
                        whoGmpExpiryDate = existingCompliance?.whoGmpExpiryDate.orEmpty(),
                        whoGmpDocUri = existingCompliance?.whoGmpDocUri,
                        errorMessage = null
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Throwable) {
                _uiState.update {
                    it.copy(
                        isSessionLoaded = true,
                        errorMessage = err.message ?: "Unable to load account compliance status."
                    )
                }
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
        val cleaned = value.uppercase().trim()
        _uiState.update {
            it.copy(
                gstin = cleaned,
                isGstinFormatValid = isValidGstinFormat(cleaned),
                isGstinVerified = false,
                verifiedState = if (isValidGstinFormat(cleaned)) resolveGstStateName(cleaned.take(2)) else null,
                errorMessage = null,
                statusMessage = null
            )
        }
    }

    fun verifyGstin(onSuccess: (() -> Unit)? = null) {
        val state = _uiState.value
        val gstin = state.gstin.trim().uppercase()
        if (!isValidGstinFormat(gstin)) {
            _uiState.update {
                it.copy(
                    isGstinFormatValid = false,
                    isGstinVerified = false,
                    errorMessage = "Please enter a valid 15-character GSTIN (e.g. 27AAAAA0000A1Z5)."
                )
            }
            return
        }

        val stateName = resolveGstStateName(gstin.take(2))
        val isDevLocal = state.authExecutionMode == AuthExecutionMode.DEVELOPMENT_LOCAL_MODE

        _uiState.update {
            it.copy(
                gstin = gstin,
                isVerifyingGstin = false,
                isGstinFormatValid = true,
                isGstinVerified = isDevLocal,
                verifiedEntityName = it.currentUser?.businessName?.takeIf { name -> name.isNotBlank() },
                verifiedState = stateName,
                errorMessage = null,
                statusMessage = if (isDevLocal) {
                    "GSTIN validated in Development Local Mode."
                } else {
                    "GSTIN format validated. Final verification is performed by the backend upon submission."
                }
            )
        }
        onSuccess?.invoke()
    }

    fun scanGstinDemo() {
        if (_uiState.value.authExecutionMode != AuthExecutionMode.DEVELOPMENT_LOCAL_MODE) {
            _uiState.update {
                it.copy(
                    errorMessage = "Sample GSTIN autofill is only available in Development Local Mode. Please enter your registered 15-character GSTIN."
                )
            }
            return
        }
        val sampleGstin = "27AAACA1234F1Z5"
        _uiState.update {
            it.copy(
                gstin = sampleGstin,
                isGstinFormatValid = true,
                isGstinVerified = true,
                verifiedEntityName = it.currentUser?.businessName?.takeIf { name -> name.isNotBlank() },
                verifiedState = resolveGstStateName("27"),
                errorMessage = null,
                statusMessage = "Loaded Development Local Mode sample GSTIN."
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

    fun validateDrugLicenseStep(onValid: () -> Unit) {
        val state = _uiState.value
        val number = state.drugLicenseNumber.trim()
        if (number.length < 4) {
            _uiState.update { it.copy(errorMessage = "Please enter your valid Drug License number (e.g. 20B/21B license).") }
            return
        }
        if (state.drugLicenseIssueDate.isBlank() || state.drugLicenseExpiryDate.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please select both Drug License issue date and expiry date.") }
            return
        }
        _uiState.update { it.copy(errorMessage = null) }
        onValid()
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
        val gstin = state.gstin.trim().uppercase()
        val drugLicenseNumber = state.drugLicenseNumber.trim().uppercase()
        val drugIssueDate = state.drugLicenseIssueDate.trim()
        val drugExpiryDate = state.drugLicenseExpiryDate.trim()

        if (!isValidGstinFormat(gstin)) {
            _uiState.update {
                it.copy(
                    currentStep = 2,
                    errorMessage = "Please enter a valid 15-character GSTIN before submitting."
                )
            }
            return
        }
        if (drugLicenseNumber.length < 4) {
            _uiState.update {
                it.copy(
                    currentStep = 3,
                    errorMessage = "Please enter a valid Drug License number before submitting."
                )
            }
            return
        }
        if (drugIssueDate.isBlank() || drugExpiryDate.isBlank()) {
            _uiState.update {
                it.copy(
                    currentStep = 3,
                    errorMessage = "Please provide both Drug License issue and expiry dates."
                )
            }
            return
        }

        val whoGmpNumber = state.whoGmpCertificateNumber.trim().uppercase().takeIf { state.isWhoGmpApplicable }
        val whoGmpIssue = state.whoGmpIssueDate.trim().takeIf { state.isWhoGmpApplicable }
        val whoGmpExpiry = state.whoGmpExpiryDate.trim().takeIf { state.isWhoGmpApplicable }
        if (state.isWhoGmpApplicable) {
            if (whoGmpNumber.isNullOrBlank() || whoGmpIssue.isNullOrBlank() || whoGmpExpiry.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        currentStep = 4,
                        errorMessage = "Please enter WHO-GMP certificate number, issue date, and expiry date, or mark WHO-GMP as Not Applicable if not required for your license."
                    )
                }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val activeUser = state.currentUser ?: repository.getApiUsersMe()
                if (activeUser == null || activeUser.id <= 0L) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = null,
                            isSessionLoaded = true,
                            errorMessage = "Your session is not active. Please sign in again to submit compliance verification."
                        )
                    }
                    return@launch
                }

                val result = repository.saveComplianceVerification(
                    userId = activeUser.id,
                    gstin = gstin,
                    drugLicenseNumber = drugLicenseNumber,
                    drugLicenseIssueDate = drugIssueDate,
                    drugLicenseExpiryDate = drugExpiryDate,
                    drugLicenseDocUri = state.drugLicenseDocUri,
                    whoGmpCertificateNumber = whoGmpNumber,
                    whoGmpIssueDate = whoGmpIssue,
                    whoGmpExpiryDate = whoGmpExpiry,
                    whoGmpDocUri = if (state.isWhoGmpApplicable) state.whoGmpDocUri else null,
                    isWhoGmpApplicable = state.isWhoGmpApplicable
                )

                result.fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentUser = activeUser,
                                isCompleted = true,
                                errorMessage = null
                            )
                        }
                        onSuccess()
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = err.message ?: "Failed to save compliance details."
                            )
                        }
                    }
                )
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isLoading = false) }
                throw ce
            } catch (err: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Unable to submit compliance details. Please try again."
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        private val GSTIN_REGEX = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$")

        fun isValidGstinFormat(value: String): Boolean {
            val cleaned = value.trim().uppercase()
            return cleaned.length == 15 && GSTIN_REGEX.matches(cleaned)
        }

        fun resolveGstStateName(stateCode: String): String = when (stateCode) {
            "27" -> "Maharashtra (Code 27)"
            "24" -> "Gujarat (Code 24)"
            "07" -> "Delhi (Code 07)"
            "29" -> "Karnataka (Code 29)"
            "33" -> "Tamil Nadu (Code 33)"
            "09" -> "Uttar Pradesh (Code 09)"
            "36" -> "Telangana (Code 36)"
            "19" -> "West Bengal (Code 19)"
            else -> "State Code $stateCode"
        }
    }
}

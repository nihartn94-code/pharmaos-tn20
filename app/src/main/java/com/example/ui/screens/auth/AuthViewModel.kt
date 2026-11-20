package com.example.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.config.ApiConfig
import com.example.data.remote.config.AuthExecutionMode
import com.example.domain.model.AuthUser
import com.example.domain.model.UserRole
import com.example.domain.repository.PharmaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val selectedRole: UserRole = UserRole.STOCKIST,
    val currentUser: AuthUser? = null,
    val allRegisteredUsers: List<com.example.data.local.entity.UserEntity> = emptyList(),
    val isFirstUser: Boolean = false,
    val authExecutionMode: AuthExecutionMode = AuthExecutionMode.PRODUCTION_BACKEND_HTTP,
    val backendBaseUrl: String = ApiConfig.PLACEHOLDER_UNCONFIGURED_BASE_URL,
    val isBackendConfigured: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class AuthViewModel(
    private val repository: PharmaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkSession()
        observeUsers()
        observeBackendConfig()
    }

    private fun observeBackendConfig() {
        viewModelScope.launch {
            repository.getAuthExecutionModeFlow().collect { mode ->
                _uiState.update { it.copy(authExecutionMode = mode) }
                if (mode == AuthExecutionMode.DEVELOPMENT_LOCAL_MODE) {
                    repository.ensureDefaultManufacturerUser()
                }
            }
        }
        viewModelScope.launch {
            repository.getBackendBaseUrlFlow().collect { url ->
                _uiState.update {
                    it.copy(
                        backendBaseUrl = url,
                        isBackendConfigured = ApiConfig.isBackendUrlConfigured(url)
                    )
                }
            }
        }
    }

    fun setAuthExecutionMode(mode: AuthExecutionMode) {
        viewModelScope.launch {
            repository.setAuthExecutionMode(mode)
            _uiState.update {
                it.copy(
                    authExecutionMode = mode,
                    successMessage = "Switched to ${mode.label}"
                )
            }
        }
    }

    fun configureBackendUrl(rawUrl: String) {
        viewModelScope.launch {
            val res = repository.setBackendBaseUrl(rawUrl)
            res.fold(
                onSuccess = { normalized ->
                    _uiState.update {
                        it.copy(
                            backendBaseUrl = normalized,
                            isBackendConfigured = ApiConfig.isBackendUrlConfigured(normalized),
                            successMessage = if (ApiConfig.isBackendUrlConfigured(normalized)) {
                                "Backend URL saved: $normalized"
                            } else {
                                "Backend URL reset to unconfigured placeholder."
                            }
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Invalid backend URL.") }
                }
            )
        }
    }

    fun seedManufacturerTestingAccount(onReady: (com.example.data.local.entity.UserEntity) -> Unit = {}) {
        if (_uiState.value.authExecutionMode != AuthExecutionMode.DEVELOPMENT_LOCAL_MODE) {
            _uiState.update {
                it.copy(errorMessage = "Local test accounts require Development Local Mode.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val mfg = repository.ensureDefaultManufacturerUser()
            _uiState.update { it.copy(isLoading = false, successMessage = "Manufacturer test account ready: ${mfg.email}") }
            onReady(mfg)
        }
    }

    private fun checkSession() {
        viewModelScope.launch {
            repository.getApiUsersMeFlow().collect { user ->
                val isFirst = repository.getFirstUserRegistrationCheck()
                _uiState.update { it.copy(currentUser = user, isFirstUser = isFirst) }
            }
        }
    }

    private fun observeUsers() {
        viewModelScope.launch {
            repository.getApiUsersFlow().collect { users ->
                val isFirst = users.isEmpty()
                _uiState.update { it.copy(allRegisteredUsers = users, isFirstUser = isFirst) }
            }
        }
    }

    fun selectRole(role: UserRole) {
        _uiState.update { it.copy(selectedRole = role, errorMessage = null) }
    }

    fun login(
        identifier: String,
        password: String,
        expectedRole: UserRole? = null,
        onSuccess: () -> Unit
    ) {
        if (identifier.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter both mobile number / email ID and password.") }
            return
        }

        val targetRole = expectedRole ?: _uiState.value.selectedRole

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.postApiAuthLogin(identifier, password, targetRole)
            result.fold(
                onSuccess = { user ->
                    _uiState.update { it.copy(isLoading = false, currentUser = user, errorMessage = null) }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Login failed.") }
                }
            )
        }
    }

    fun signInWithGoogle(
        email: String,
        displayName: String = "",
        role: UserRole? = null,
        onSuccess: () -> Unit,
        markOnboardingCompleted: Boolean = true
    ) {
        val targetRole = role ?: _uiState.value.selectedRole
        val cleanEmail = email.trim().lowercase()
        val cleanName = displayName.trim().ifBlank {
            cleanEmail.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.postApiAuthGoogle(cleanEmail, cleanName, targetRole)
            result.fold(
                onSuccess = { user ->
                    if (markOnboardingCompleted || user.role == UserRole.ADMIN) {
                        repository.setOnboardingCompleted(true)
                    }
                    _uiState.update { it.copy(isLoading = false, currentUser = user, errorMessage = null) }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Google sign-in failed.") }
                }
            )
        }
    }

    fun quickSwitchUser(userId: Long, onSuccess: () -> Unit) {
        if (_uiState.value.authExecutionMode != AuthExecutionMode.DEVELOPMENT_LOCAL_MODE) {
            _uiState.update {
                it.copy(errorMessage = "Quick local test switching is only available in Development Local Mode.")
            }
            return
        }
        viewModelScope.launch {
            val userEntity = _uiState.value.allRegisteredUsers.find { it.id == userId } ?: return@launch
            login(userEntity.email, "Password@123", onSuccess = onSuccess)
        }
    }

    fun register(
        fullName: String,
        businessName: String,
        email: String,
        phone: String,
        gstin: String?,
        address: String = "Licensed B2B Commercial Premises",
        password: String,
        confirmPassword: String = password,
        role: UserRole,
        onSuccess: () -> Unit
    ) {
        val effectiveBusinessName = businessName.trim()
        val effectiveFullName = if (fullName.isNotBlank()) fullName.trim() else effectiveBusinessName
        val effectiveAddress = if (address.isNotBlank()) address.trim() else "Licensed B2B Commercial Premises"
        val effectivePhone = phone.trim().removePrefix("+91").replace(" ", "").replace("-", "")

        if (effectiveBusinessName.isBlank() || email.isBlank() || effectivePhone.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please fill in all required fields.") }
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid business email address.") }
            return
        }

        if (!effectivePhone.matches("^\\d{10}$".toRegex())) {
            _uiState.update { it.copy(errorMessage = "Phone number must be a valid 10-digit mobile number.") }
            return
        }

        val cleanedGstin = gstin?.trim()?.uppercase()
        if (!cleanedGstin.isNullOrBlank()) {
            val isGstin = cleanedGstin.matches("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$".toRegex())
            val isDrugLicense = cleanedGstin.length in 4..30 && cleanedGstin.all { it.isLetterOrDigit() || it == '/' || it == '-' || it == ' ' || it == '.' }
            if (!isGstin && !isDrugLicense) {
                _uiState.update { it.copy(errorMessage = "Please enter a valid 15-digit GSTIN or 20B/21B Drug License number.") }
                return
            }
        }

        if (password.length < 8) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 8 characters long.") }
            return
        }

        if (password != confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.postApiAuthRegister(
                fullName = effectiveFullName,
                businessName = effectiveBusinessName,
                email = email.trim(),
                phone = effectivePhone,
                gstin = cleanedGstin,
                address = effectiveAddress,
                password = password,
                role = role
            )
            result.fold(
                onSuccess = { user ->
                    _uiState.update { it.copy(isLoading = false, currentUser = user, errorMessage = null) }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Registration failed.") }
                }
            )
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            _uiState.update { it.copy(currentUser = null) }
            onLoggedOut()
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}

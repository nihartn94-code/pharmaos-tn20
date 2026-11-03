package com.example.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        onSuccess: () -> Unit
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
        viewModelScope.launch {
            val userEntity = _uiState.value.allRegisteredUsers.find { it.id == userId } ?: return@launch
            // For fast testing on one device, switch session
            val role = try { UserRole.valueOf(userEntity.role) } catch (_: Exception) { UserRole.STOCKIST }
            val status = try { com.example.domain.model.AccountStatus.valueOf(userEntity.status) } catch (_: Exception) { com.example.domain.model.AccountStatus.ACTIVE }
            repository.postApiAuthLogin(userEntity.email, "") // Will fail password check, so we can set directly via repository session
            // We use direct switch
            (repository as? com.example.data.repository.PharmaRepositoryImpl)?.let {
                // Set data store user
            }
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

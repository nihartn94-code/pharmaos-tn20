package com.example.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DataStoreManager
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SupplierSettingsEntity
import com.example.data.local.entity.UserEntity
import com.example.data.remote.config.ApiConfig
import com.example.data.remote.config.AuthExecutionMode
import com.example.domain.model.AccountStatus
import com.example.domain.model.AuthUser
import com.example.domain.model.ContactVerificationChannel
import com.example.domain.model.LegalDocument
import com.example.domain.model.LegalDocumentsCatalog
import com.example.domain.model.UserAddress
import com.example.domain.repository.PharmaRepository
import com.example.util.CsvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val currentUser: AuthUser? = null,
    val addresses: List<UserAddress> = emptyList(),
    val legalDocuments: List<LegalDocument> = LegalDocumentsCatalog.resolveDocuments(null),
    val isDarkTheme: Boolean = false,
    val authExecutionMode: AuthExecutionMode = AuthExecutionMode.PRODUCTION_BACKEND_HTTP,
    val backendBaseUrl: String = ApiConfig.PLACEHOLDER_UNCONFIGURED_BASE_URL,
    val isBackendConfigured: Boolean = false,
    val isSyncing: Boolean = false,
    val isRefreshingProfile: Boolean = false,
    val isSavingProfile: Boolean = false,
    val isSavingAddress: Boolean = false,
    val isVerifyingContact: Boolean = false,
    val pendingVerificationChannel: ContactVerificationChannel? = null,
    val pendingVerificationValue: String = "",
    val verificationStatusHint: String? = null,
    val profileValidationError: String? = null,
    val addressValidationError: String? = null,
    val supplierSettings: SupplierSettingsEntity? = null,
    val allUsers: List<UserEntity> = emptyList(),
    val csvImportReport: List<String> = emptyList(),
    val csvImportSuccessCount: Int = 0,
    val exportedCsvContent: String? = null,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class SettingsViewModel(
    private val repository: PharmaRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getApiUsersMeFlow().collect { user ->
                _uiState.update { it.copy(currentUser = user) }
                if (user != null && user.role.isSupplier) {
                    val settings = repository.getApiSupplierSettings(user.id)
                    _uiState.update { it.copy(supplierSettings = settings) }
                }
            }
        }
        viewModelScope.launch {
            repository.getApiUserAddressesFlow().collect { addresses ->
                _uiState.update { it.copy(addresses = addresses) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.isDarkThemeFlow.collect { isDark ->
                _uiState.update { it.copy(isDarkTheme = isDark ?: false) }
            }
        }
        viewModelScope.launch {
            repository.getAuthExecutionModeFlow().collect { mode ->
                _uiState.update { it.copy(authExecutionMode = mode) }
            }
        }
        viewModelScope.launch {
            repository.getBackendBaseUrlFlow().collect { url ->
                val configured = ApiConfig.isBackendUrlConfigured(url)
                _uiState.update {
                    it.copy(
                        backendBaseUrl = url,
                        isBackendConfigured = configured,
                        legalDocuments = LegalDocumentsCatalog.resolveDocuments(url)
                    )
                }
                refreshLegalDocuments()
            }
        }
        viewModelScope.launch {
            repository.getApiUsersFlow().collect { users ->
                _uiState.update { it.copy(allUsers = users) }
            }
        }
    }

    fun refreshLegalDocuments() {
        viewModelScope.launch {
            repository.getApiLegalDocuments().onSuccess { docs ->
                _uiState.update { it.copy(legalDocuments = docs) }
            }
        }
    }

    fun refreshProfileAndAddresses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingProfile = true, errorMessage = null) }
            val profileRes = repository.refreshCurrentUserProfile()
            val addressRes = repository.refreshUserAddresses()
            refreshLegalDocuments()

            val firstErr = profileRes.exceptionOrNull() ?: addressRes.exceptionOrNull()
            if (firstErr != null) {
                _uiState.update {
                    it.copy(
                        isRefreshingProfile = false,
                        errorMessage = firstErr.message ?: "Failed to refresh profile from server."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isRefreshingProfile = false,
                        currentUser = profileRes.getOrNull() ?: it.currentUser,
                        addresses = addressRes.getOrNull() ?: it.addresses,
                        successMessage = "Profile and addresses refreshed."
                    )
                }
            }
        }
    }

    fun updateProfile(
        fullName: String,
        businessName: String,
        email: String,
        phone: String,
        gstin: String?,
        profileBio: String?,
        onSuccess: () -> Unit = {}
    ) {
        val current = _uiState.value.currentUser
        if (current == null) {
            _uiState.update { it.copy(errorMessage = "Please sign in to update your profile.") }
            return
        }

        // Immediate UI-level validation before firing repository/API call
        val validationError = validateProfileFields(fullName, businessName, email, phone, gstin)
        if (validationError != null) {
            _uiState.update { it.copy(profileValidationError = validationError) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSavingProfile = true,
                    profileValidationError = null,
                    errorMessage = null
                )
            }
            val result = repository.putApiUserProfile(
                fullName = fullName,
                businessName = businessName,
                email = email,
                phone = phone,
                gstin = gstin,
                address = current.address,
                profileBio = profileBio
            )
            result.fold(
                onSuccess = { updatedUser ->
                    _uiState.update {
                        it.copy(
                            isSavingProfile = false,
                            currentUser = updatedUser,
                            profileValidationError = null,
                            successMessage = "Profile updated successfully."
                        )
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    val message = err.message ?: "Failed to update profile."
                    _uiState.update {
                        it.copy(
                            isSavingProfile = false,
                            profileValidationError = message,
                            errorMessage = message
                        )
                    }
                }
            )
        }
    }

    fun requestContactVerification(channel: ContactVerificationChannel) {
        val user = _uiState.value.currentUser ?: return
        val targetValue = when (channel) {
            ContactVerificationChannel.EMAIL -> user.email
            ContactVerificationChannel.PHONE -> user.phone
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isVerifyingContact = true,
                    errorMessage = null,
                    verificationStatusHint = null
                )
            }
            repository.requestContactVerification(channel, targetValue).fold(
                onSuccess = { hint ->
                    _uiState.update {
                        it.copy(
                            isVerifyingContact = false,
                            pendingVerificationChannel = channel,
                            pendingVerificationValue = targetValue,
                            verificationStatusHint = hint
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isVerifyingContact = false,
                            errorMessage = err.message ?: "Unable to start ${channel.label.lowercase()} verification."
                        )
                    }
                }
            )
        }
    }

    fun confirmContactVerification(code: String, onVerified: () -> Unit = {}) {
        val channel = _uiState.value.pendingVerificationChannel ?: return
        val value = _uiState.value.pendingVerificationValue
        if (code.trim().length < 4) {
            _uiState.update { it.copy(errorMessage = "Enter a valid verification code (at least 4 characters).") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingContact = true, errorMessage = null) }
            repository.confirmContactVerification(channel, value, code).fold(
                onSuccess = { updatedUser ->
                    _uiState.update {
                        it.copy(
                            isVerifyingContact = false,
                            currentUser = updatedUser,
                            pendingVerificationChannel = null,
                            pendingVerificationValue = "",
                            verificationStatusHint = null,
                            successMessage = "${channel.label} verified successfully."
                        )
                    }
                    onVerified()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isVerifyingContact = false,
                            errorMessage = err.message ?: "${channel.label} verification failed."
                        )
                    }
                }
            )
        }
    }

    fun dismissContactVerification() {
        _uiState.update {
            it.copy(
                pendingVerificationChannel = null,
                pendingVerificationValue = "",
                verificationStatusHint = null
            )
        }
    }

    fun saveAddress(address: UserAddress, onSuccess: () -> Unit = {}) {
        val validationError = validateAddressFields(address)
        if (validationError != null) {
            _uiState.update { it.copy(addressValidationError = validationError) }
            return
        }

        val isEditing = address.id > 0L
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSavingAddress = true,
                    addressValidationError = null,
                    errorMessage = null
                )
            }
            val result = if (isEditing) {
                repository.putApiUserAddress(address)
            } else {
                repository.postApiUserAddress(address)
            }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSavingAddress = false,
                            addressValidationError = null,
                            successMessage = if (isEditing) "Address updated." else "Address added."
                        )
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    val message = err.message ?: "Failed to save address."
                    _uiState.update {
                        it.copy(
                            isSavingAddress = false,
                            addressValidationError = message,
                            errorMessage = message
                        )
                    }
                }
            )
        }
    }

    fun deleteAddress(addressId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingAddress = true, errorMessage = null) }
            repository.deleteApiUserAddress(addressId).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSavingAddress = false,
                            successMessage = "Address deleted."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSavingAddress = false,
                            errorMessage = err.message ?: "Failed to delete address."
                        )
                    }
                }
            )
        }
    }

    fun setDefaultAddress(addressId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingAddress = true, errorMessage = null) }
            repository.patchApiDefaultUserAddress(addressId).fold(
                onSuccess = { updated ->
                    _uiState.update {
                        it.copy(
                            isSavingAddress = false,
                            successMessage = "${updated.addressName} set as default address."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSavingAddress = false,
                            errorMessage = err.message ?: "Failed to set default address."
                        )
                    }
                }
            )
        }
    }

    fun clearValidationErrors() {
        _uiState.update {
            it.copy(
                profileValidationError = null,
                addressValidationError = null
            )
        }
    }

    private fun validateProfileFields(
        fullName: String,
        businessName: String,
        email: String,
        phone: String,
        gstin: String?
    ): String? {
        if (fullName.trim().length < 2) return "Full name must be at least 2 characters."
        if (businessName.trim().isEmpty()) return "Business or pharmacy name is required."
        val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        if (!emailRegex.matches(email.trim())) return "Enter a valid email address."
        val digits = phone.filter { it.isDigit() }
        if (digits.length !in 10..15) return "Enter a valid 10 to 15 digit phone number."
        val cleanGstin = gstin?.trim().orEmpty()
        if (cleanGstin.isNotEmpty() && !Regex("^[0-9A-Za-z]{15}$").matches(cleanGstin)) {
            return "GSTIN must be 15 alphanumeric characters."
        }
        return null
    }

    private fun validateAddressFields(address: UserAddress): String? {
        if (address.addressName.trim().isEmpty()) return "Address name/type is required."
        if (address.contactPerson.trim().isEmpty()) return "Contact person is required."
        val phoneDigits = address.phone.filter { it.isDigit() }
        if (phoneDigits.length !in 10..15) return "Enter a valid 10 to 15 digit contact phone."
        if (address.addressLine1.trim().isEmpty()) return "Address line 1 is required."
        if (address.city.trim().isEmpty()) return "City is required."
        if (address.state.trim().isEmpty()) return "State is required."
        val postal = address.postalCode.trim()
        if (postal.length < 4 || postal.length > 12) return "Enter a valid postal code."
        if (address.country.trim().isEmpty()) return "Country is required."
        return null
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
            repository.setBackendBaseUrl(rawUrl).fold(
                onSuccess = { normalized ->
                    _uiState.update {
                        it.copy(
                            backendBaseUrl = normalized,
                            isBackendConfigured = ApiConfig.isBackendUrlConfigured(normalized),
                            legalDocuments = LegalDocumentsCatalog.resolveDocuments(normalized),
                            successMessage = "Backend URL updated: $normalized"
                        )
                    }
                    refreshLegalDocuments()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Invalid backend URL.") }
                }
            )
        }
    }

    fun syncFromBackend() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, errorMessage = null) }
            val profileRes = repository.refreshCurrentUserProfile()
            val addrRes = repository.refreshUserAddresses()
            val catRes = repository.syncCatalogueFromBackend()
            val ordRes = repository.syncOrdersFromBackend()
            val notRes = repository.syncNotificationsFromBackend()
            refreshLegalDocuments()

            val firstError = profileRes.exceptionOrNull()
                ?: addrRes.exceptionOrNull()
                ?: catRes.exceptionOrNull()
                ?: ordRes.exceptionOrNull()
                ?: notRes.exceptionOrNull()

            if (firstError != null) {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        errorMessage = firstError.message ?: "Backend sync failed."
                    )
                }
            } else {
                val count = (catRes.getOrNull() ?: 0) + (ordRes.getOrNull() ?: 0) + (notRes.getOrNull() ?: 0)
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        successMessage = "Synced profile, addresses, and $count backend records."
                    )
                }
            }
        }
    }

    fun setDarkTheme(isDark: Boolean) {
        viewModelScope.launch {
            dataStoreManager.setDarkTheme(isDark)
        }
    }

    fun updateSupplierSettings(deliveryCharge: Double, freeThreshold: Double, leadTimeDays: Double) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            val settings = SupplierSettingsEntity(
                supplierId = user.id,
                deliveryCharge = deliveryCharge,
                freeDeliveryThreshold = freeThreshold,
                averageLeadTimeDays = leadTimeDays
            )
            val result = repository.putApiSupplierSettings(settings)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(supplierSettings = settings, successMessage = "Logistics settings saved!") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to save settings") }
                }
            )
        }
    }

    fun updateUserStatus(userId: Long, newStatus: AccountStatus) {
        viewModelScope.launch {
            val result = repository.patchApiUserStatus(userId, newStatus.name)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(successMessage = "User status updated to ${newStatus.name}") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to update user status") }
                }
            )
        }
    }

    fun importProductsCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, csvImportReport = emptyList()) }
            try {
                val csvContent = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: ""
                }
                val parseResult = CsvManager.parseProductsCsv(csvContent)
                val current = _uiState.value.currentUser

                var savedCount = 0
                for (row in parseResult.successfulRows) {
                    val entity = ProductEntity(
                        name = row.name,
                        brand = row.brand,
                        saltComposition = row.saltComposition,
                        strength = row.strength,
                        dosageForm = row.dosageForm.name,
                        therapeuticCategory = row.therapeuticCategory,
                        manufacturerName = row.manufacturerName,
                        packSize = row.packSize,
                        hsnCode = row.hsnCode,
                        gstRatePercent = row.gstRatePercent,
                        storageCondition = row.storageCondition.name,
                        isRx = row.isRx,
                        createdBy = current?.id ?: 0L
                    )
                    repository.postApiProducts(entity)
                    savedCount++
                }

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        csvImportSuccessCount = savedCount,
                        csvImportReport = parseResult.errorLines,
                        successMessage = "Successfully imported $savedCount products from CSV."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, errorMessage = "CSV import error: ${e.message}") }
            }
        }
    }

    fun exportProductsCsv(): String {
        return "Name,Brand,SaltComposition,Strength,DosageForm,Category,Manufacturer,PackSize,HSN,GSTRate,Storage,Rx\n"
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            onLoggedOut()
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}

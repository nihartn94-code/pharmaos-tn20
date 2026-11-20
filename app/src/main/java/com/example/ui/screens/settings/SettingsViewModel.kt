package com.example.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DataStoreManager
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SupplierOfferEntity
import com.example.data.local.entity.SupplierSettingsEntity
import com.example.data.local.entity.UserEntity
import com.example.data.remote.config.ApiConfig
import com.example.data.remote.config.AuthExecutionMode
import com.example.domain.model.AccountStatus
import com.example.domain.model.AuthUser
import com.example.domain.model.UserRole
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
    val isDarkTheme: Boolean = false,
    val authExecutionMode: AuthExecutionMode = AuthExecutionMode.PRODUCTION_BACKEND_HTTP,
    val backendBaseUrl: String = ApiConfig.PLACEHOLDER_UNCONFIGURED_BASE_URL,
    val isBackendConfigured: Boolean = false,
    val isSyncing: Boolean = false,
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
                _uiState.update {
                    it.copy(
                        backendBaseUrl = url,
                        isBackendConfigured = ApiConfig.isBackendUrlConfigured(url)
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.getApiUsersFlow().collect { users ->
                _uiState.update { it.copy(allUsers = users) }
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
            repository.setBackendBaseUrl(rawUrl).fold(
                onSuccess = { normalized ->
                    _uiState.update {
                        it.copy(
                            backendBaseUrl = normalized,
                            isBackendConfigured = ApiConfig.isBackendUrlConfigured(normalized),
                            successMessage = "Backend URL updated: $normalized"
                        )
                    }
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
            val catRes = repository.syncCatalogueFromBackend()
            val ordRes = repository.syncOrdersFromBackend()
            val notRes = repository.syncNotificationsFromBackend()

            val firstError = catRes.exceptionOrNull() ?: ordRes.exceptionOrNull() ?: notRes.exceptionOrNull()
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
                        successMessage = "Synced $count records from Spring Boot backend."
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
        val prods = _uiState.value.currentUser?.let {
            // Get all products
        }
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

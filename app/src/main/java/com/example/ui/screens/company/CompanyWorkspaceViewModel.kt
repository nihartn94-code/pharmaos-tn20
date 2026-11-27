package com.example.ui.screens.company

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ProductEntity
import com.example.data.remote.upload.MediaUploadKind
import com.example.data.remote.upload.MediaUploadManager
import com.example.data.remote.upload.MediaUploadState
import com.example.domain.model.AccessControlPolicy
import com.example.domain.model.ApprovalAuditEntry
import com.example.domain.model.AuthUser
import com.example.domain.model.CataloguePublicationStatus
import com.example.domain.model.CompanyDashboardSummary
import com.example.domain.model.MasterDataItem
import com.example.domain.model.MasterDataType
import com.example.domain.model.MedicineDuplicateCandidate
import com.example.domain.model.MedicineSubmission
import com.example.domain.model.OrganizationMember
import com.example.domain.model.PharmaPermission
import com.example.domain.model.UserRole
import com.example.domain.model.WorkspaceSyncState
import com.example.domain.model.WorkspaceType
import com.example.domain.repository.PharmaRepository
import com.example.domain.repository.WorkspaceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CompanyWorkspaceUiState(
    val user: AuthUser? = null,
    val isAuthorized: Boolean = false,
    val canSwitchToManufacturer: Boolean = false,
    val canApproveMedicines: Boolean = false,
    val canRejectMedicines: Boolean = false,
    val canManageCatalogue: Boolean = false,
    val canManageMasterData: Boolean = false,
    val canAssignRoles: Boolean = false,
    val summary: CompanyDashboardSummary = CompanyDashboardSummary(""),
    val catalogueProducts: List<ProductEntity> = emptyList(),
    val submissions: List<MedicineSubmission> = emptyList(),
    val auditLogs: List<ApprovalAuditEntry> = emptyList(),
    val masterDataItems: List<MasterDataItem> = emptyList(),
    val organizationMembers: List<OrganizationMember> = emptyList(),
    val duplicateCandidates: List<MedicineDuplicateCandidate> = emptyList(),
    val unreadNotificationsCount: Int = 0,
    val syncState: WorkspaceSyncState = WorkspaceSyncState(),
    val isSaving: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class CompanyWorkspaceViewModel(
    private val pharmaRepository: PharmaRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val mediaUploadManager: MediaUploadManager? = null
) : ViewModel() {

    private val isSaving = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val duplicateCandidates = MutableStateFlow<List<MedicineDuplicateCandidate>>(emptyList())

    private val _mediaUploadState = MutableStateFlow<MediaUploadState>(MediaUploadState.Idle)
    val mediaUploadState: StateFlow<MediaUploadState> = _mediaUploadState.asStateFlow()
    private var activeUploadJob: Job? = null
    private var lastUploadUri: Uri? = null
    private var lastUploadKind: MediaUploadKind = MediaUploadKind.IMAGE

    private val primaryFlow = combine(
        pharmaRepository.getApiUsersMeFlow(),
        workspaceRepository.getCompanyDashboardSummaryFlow(),
        workspaceRepository.getCompanyCatalogueProductsFlow(),
        workspaceRepository.getOrganizationSubmissionsFlow(),
        workspaceRepository.getAuditLogsForOrganizationFlow()
    ) { user, summary, catalogue, submissions, audits ->
        PrimaryCompanyBundle(user, summary, catalogue, submissions, audits)
    }

    private val secondaryFlow = combine(
        workspaceRepository.getMasterDataFlow(),
        workspaceRepository.getOrganizationMembersFlow(),
        pharmaRepository.getApiNotificationsUnreadCountFlow(),
        duplicateCandidates
    ) { masterData, members, unreadCount, duplicates ->
        SecondaryCompanyBundle(masterData, members, unreadCount, duplicates)
    }

    private val feedbackFlow = combine(isSaving, statusMessage, errorMessage) { saving, msg, err ->
        Triple(saving, msg, err)
    }

    val uiState: StateFlow<CompanyWorkspaceUiState> = combine(
        primaryFlow,
        secondaryFlow,
        feedbackFlow,
        workspaceRepository.workspaceSyncStateFlow
    ) { primary, secondary, (saving, msg, err), syncState ->
        val user = primary.user
        CompanyWorkspaceUiState(
            user = user,
            isAuthorized = AccessControlPolicy.canAccessWorkspace(user, WorkspaceType.COMPANY),
            canSwitchToManufacturer = user?.canSwitchBetweenManufacturerAndCompany() == true,
            canApproveMedicines = user?.hasPermission(PharmaPermission.MEDICINE_APPROVE) == true,
            canRejectMedicines = user?.hasPermission(PharmaPermission.MEDICINE_REJECT) == true,
            canManageCatalogue = user?.hasPermission(PharmaPermission.MEDICINE_CATALOGUE_MANAGE) == true,
            canManageMasterData = user?.hasPermission(PharmaPermission.COMPOSITION_MANAGE) == true ||
                user?.hasPermission(PharmaPermission.DOSAGE_FORM_MANAGE) == true ||
                user?.hasPermission(PharmaPermission.BRAND_MANAGE) == true,
            canAssignRoles = user != null && AccessControlPolicy.canManageOrganizationRoles(user, user.organizationId).isSuccess,
            summary = primary.summary,
            catalogueProducts = primary.catalogue,
            submissions = primary.submissions,
            auditLogs = primary.audits,
            masterDataItems = secondary.masterData,
            organizationMembers = secondary.members,
            duplicateCandidates = secondary.duplicates,
            unreadNotificationsCount = secondary.unreadCount,
            syncState = syncState,
            isSaving = saving,
            statusMessage = msg,
            errorMessage = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = CompanyWorkspaceUiState()
    )

    init {
        viewModelScope.launch {
            workspaceRepository.ensureWorkspaceSeededForLocalDev()
            runCatching { workspaceRepository.syncActiveWorkspace() }
        }
    }

    fun refreshWorkspace() {
        viewModelScope.launch {
            workspaceRepository.syncActiveWorkspace()
                .onFailure { errorMessage.value = it.message }
        }
    }

    fun uploadMedicineMedia(
        uri: Uri,
        kind: MediaUploadKind = MediaUploadKind.IMAGE,
        onUploaded: (String) -> Unit
    ) {
        val uploader = mediaUploadManager
        if (uploader == null) {
            _mediaUploadState.value = MediaUploadState.Failed(
                message = "Media upload service is not initialized.",
                localPreviewUri = uri.toString(),
                retryable = false
            )
            return
        }
        lastUploadUri = uri
        lastUploadKind = kind
        activeUploadJob?.cancel()
        activeUploadJob = viewModelScope.launch {
            uploader.uploadUri(uri, kind) { state ->
                _mediaUploadState.value = state
            }.onSuccess { remoteUrl ->
                onUploaded(remoteUrl)
            }
        }
    }

    fun retryMedicineMediaUpload(onUploaded: (String) -> Unit) {
        val uri = lastUploadUri ?: return
        uploadMedicineMedia(uri, lastUploadKind, onUploaded)
    }

    fun cancelMedicineMediaUpload() {
        activeUploadJob?.cancel()
        activeUploadJob = null
        _mediaUploadState.value = MediaUploadState.Cancelled
    }

    fun resetMediaUploadState() {
        activeUploadJob?.cancel()
        activeUploadJob = null
        lastUploadUri = null
        _mediaUploadState.value = MediaUploadState.Idle
    }

    fun switchToManufacturerWorkspace(onSwitched: () -> Unit) {
        viewModelScope.launch {
            workspaceRepository.switchActiveWorkspace(WorkspaceType.MANUFACTURER)
                .onSuccess { onSwitched() }
                .onFailure { errorMessage.value = it.message }
        }
    }

    fun checkDuplicates(
        medicineName: String,
        brandName: String,
        saltComposition: String,
        strength: String,
        dosageForm: String
    ) {
        viewModelScope.launch {
            duplicateCandidates.value = workspaceRepository.detectDuplicateMedicines(
                medicineName, brandName, saltComposition, strength, dosageForm
            )
        }
    }

    fun saveMedicineDraft(submission: MedicineSubmission, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            workspaceRepository.saveMedicineDraft(submission)
                .onSuccess {
                    statusMessage.value = "Draft '${it.medicineName}' saved."
                    resetMediaUploadState()
                    onSuccess()
                }
                .onFailure { errorMessage.value = it.message }
            isSaving.value = false
        }
    }

    fun submitNewMedicine(submission: MedicineSubmission, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            workspaceRepository.submitMedicineForApproval(submission)
                .onSuccess {
                    statusMessage.value = "'${it.medicineName}' queued for mandatory approval."
                    resetMediaUploadState()
                    onSuccess()
                }
                .onFailure { errorMessage.value = it.message }
            isSaving.value = false
        }
    }

    fun approveSubmission(submissionId: Long, reviewNotes: String) {
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            workspaceRepository.approveMedicineSubmission(submissionId, reviewNotes)
                .onSuccess {
                    statusMessage.value = "'${it.medicineName}' approved and published to active catalogue."
                }
                .onFailure { errorMessage.value = it.message }
            isSaving.value = false
        }
    }

    fun rejectSubmission(submissionId: Long, reason: String) {
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            workspaceRepository.rejectMedicineSubmission(submissionId, reason)
                .onSuccess {
                    statusMessage.value = "'${it.medicineName}' rejected with reason logged."
                }
                .onFailure { errorMessage.value = it.message }
            isSaving.value = false
        }
    }

    fun updateCatalogueProduct(product: ProductEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            workspaceRepository.updateCompanyCatalogueProduct(product)
                .onSuccess {
                    statusMessage.value = "'${product.name}' updated."
                    onSuccess()
                }
                .onFailure { errorMessage.value = it.message }
            isSaving.value = false
        }
    }

    fun updateCatalogueStatus(productId: Long, newStatus: CataloguePublicationStatus) {
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            workspaceRepository.updateCataloguePublicationStatus(productId, newStatus)
                .onSuccess {
                    statusMessage.value = "Catalogue medicine status set to ${newStatus.displayName}."
                }
                .onFailure { errorMessage.value = it.message }
            isSaving.value = false
        }
    }

    fun addMasterData(
        type: MasterDataType,
        name: String,
        code: String,
        description: String,
        isOrganizationScoped: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            workspaceRepository.addMasterDataItem(type, name, code, description, isOrganizationScoped)
                .onSuccess {
                    statusMessage.value = "Added '${it.name}' to ${type.displayName}."
                    onSuccess()
                }
                .onFailure { errorMessage.value = it.message }
            isSaving.value = false
        }
    }

    fun assignMemberRoles(memberId: Long, roles: Set<UserRole>) {
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            workspaceRepository.assignOrganizationMemberRoles(memberId, roles)
                .onSuccess {
                    statusMessage.value = "Updated roles for ${it.fullName}."
                }
                .onFailure { errorMessage.value = it.message }
            isSaving.value = false
        }
    }

    fun clearMessages() {
        statusMessage.value = null
        errorMessage.value = null
    }

    private data class PrimaryCompanyBundle(
        val user: AuthUser?,
        val summary: CompanyDashboardSummary,
        val catalogue: List<ProductEntity>,
        val submissions: List<MedicineSubmission>,
        val audits: List<ApprovalAuditEntry>
    )

    private data class SecondaryCompanyBundle(
        val masterData: List<MasterDataItem>,
        val members: List<OrganizationMember>,
        val unreadCount: Int,
        val duplicates: List<MedicineDuplicateCandidate>
    )
}

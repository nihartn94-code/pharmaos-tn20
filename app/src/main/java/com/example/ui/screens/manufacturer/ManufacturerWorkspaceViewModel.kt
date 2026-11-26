package com.example.ui.screens.manufacturer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ProductEntity
import com.example.domain.model.AccessControlPolicy
import com.example.domain.model.AnnouncementStatus
import com.example.domain.model.AuthUser
import com.example.domain.model.BannerStatus
import com.example.domain.model.CampaignStatus
import com.example.domain.model.ManufacturerDashboardSummary
import com.example.domain.model.MedicineAnnouncement
import com.example.domain.model.MedicineDuplicateCandidate
import com.example.domain.model.MedicineSubmission
import com.example.domain.model.OfferBanner
import com.example.domain.model.PharmaPermission
import com.example.domain.model.PromotionalCampaign
import com.example.domain.model.WorkspaceType
import com.example.domain.repository.PharmaRepository
import com.example.domain.repository.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ManufacturerWorkspaceUiState(
    val user: AuthUser? = null,
    val isAuthorized: Boolean = false,
    val canSwitchToCompany: Boolean = false,
    val canCreateBanner: Boolean = false,
    val canUpdateBanner: Boolean = false,
    val canDeleteBanner: Boolean = false,
    val canManageCampaigns: Boolean = false,
    val canCreateAnnouncement: Boolean = false,
    val canSubmitMedicine: Boolean = false,
    val summary: ManufacturerDashboardSummary = ManufacturerDashboardSummary(""),
    val banners: List<OfferBanner> = emptyList(),
    val campaigns: List<PromotionalCampaign> = emptyList(),
    val announcements: List<MedicineAnnouncement> = emptyList(),
    val submissions: List<MedicineSubmission> = emptyList(),
    val approvedProducts: List<ProductEntity> = emptyList(),
    val duplicateCandidates: List<MedicineDuplicateCandidate> = emptyList(),
    val unreadNotificationsCount: Int = 0,
    val isSaving: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class ManufacturerWorkspaceViewModel(
    private val pharmaRepository: PharmaRepository,
    private val workspaceRepository: WorkspaceRepository
) : ViewModel() {

    private val isSaving = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val duplicateCandidates = MutableStateFlow<List<MedicineDuplicateCandidate>>(emptyList())

    private val primaryDataFlow = combine(
        pharmaRepository.getApiUsersMeFlow(),
        workspaceRepository.getManufacturerDashboardSummaryFlow(),
        workspaceRepository.getManufacturerBannersFlow(),
        workspaceRepository.getManufacturerCampaignsFlow(),
        workspaceRepository.getManufacturerAnnouncementsFlow()
    ) { user, summary, banners, campaigns, announcements ->
        PrimaryManufacturerBundle(user, summary, banners, campaigns, announcements)
    }

    private val secondaryDataFlow = combine(
        workspaceRepository.getOrganizationSubmissionsFlow(),
        workspaceRepository.getApprovedProductsForOrganizationFlow(),
        pharmaRepository.getApiNotificationsUnreadCountFlow(),
        duplicateCandidates
    ) { submissions, approvedProducts, unreadCount, duplicates ->
        SecondaryManufacturerBundle(submissions, approvedProducts, unreadCount, duplicates)
    }

    private val feedbackFlow = combine(isSaving, statusMessage, errorMessage) { saving, msg, err ->
        Triple(saving, msg, err)
    }

    val uiState: StateFlow<ManufacturerWorkspaceUiState> = combine(
        primaryDataFlow,
        secondaryDataFlow,
        feedbackFlow
    ) { primary, secondary, (saving, msg, err) ->
        val user = primary.user
        ManufacturerWorkspaceUiState(
            user = user,
            isAuthorized = AccessControlPolicy.canAccessWorkspace(user, WorkspaceType.MANUFACTURER),
            canSwitchToCompany = user?.canSwitchBetweenManufacturerAndCompany() == true,
            canCreateBanner = user?.hasPermission(PharmaPermission.BANNER_CREATE) == true,
            canUpdateBanner = user?.hasPermission(PharmaPermission.BANNER_UPDATE) == true,
            canDeleteBanner = user?.hasPermission(PharmaPermission.BANNER_DELETE) == true,
            canManageCampaigns = user?.hasPermission(PharmaPermission.CAMPAIGN_MANAGE) == true,
            canCreateAnnouncement = user?.hasPermission(PharmaPermission.MEDICINE_ANNOUNCEMENT_CREATE) == true,
            canSubmitMedicine = user?.hasPermission(PharmaPermission.MEDICINE_SUBMIT) == true,
            summary = primary.summary,
            banners = primary.banners,
            campaigns = primary.campaigns,
            announcements = primary.announcements,
            submissions = secondary.submissions,
            approvedProducts = secondary.approvedProducts,
            duplicateCandidates = secondary.duplicates,
            unreadNotificationsCount = secondary.unreadCount,
            isSaving = saving,
            statusMessage = msg,
            errorMessage = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ManufacturerWorkspaceUiState()
    )

    init {
        viewModelScope.launch {
            workspaceRepository.ensureWorkspaceSeededForLocalDev()
        }
    }

    fun switchToCompanyWorkspace(onSwitched: () -> Unit) {
        viewModelScope.launch {
            workspaceRepository.switchActiveWorkspace(WorkspaceType.COMPANY)
                .onSuccess { onSwitched() }
                .onFailure { errorMessage.value = it.message }
        }
    }

    fun saveBanner(banner: OfferBanner, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            workspaceRepository.saveOfferBanner(banner)
                .onSuccess { saved ->
                    statusMessage.value = "Banner '${saved.title}' saved (${saved.effectiveStatus().displayName})."
                    onSuccess()
                }
                .onFailure { errorMessage.value = it.message ?: "Failed to save offer banner." }
            isSaving.value = false
        }
    }

    fun updateBannerStatus(bannerId: Long, newStatus: BannerStatus) {
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            workspaceRepository.updateOfferBannerStatus(bannerId, newStatus)
                .onSuccess { statusMessage.value = "Banner status updated to ${newStatus.displayName}." }
                .onFailure { errorMessage.value = it.message ?: "Unable to update banner status." }
            isSaving.value = false
        }
    }

    fun deleteBanner(bannerId: Long) {
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            workspaceRepository.deleteOfferBanner(bannerId)
                .onSuccess { statusMessage.value = "Offer banner deleted." }
                .onFailure { errorMessage.value = it.message ?: "Unable to delete banner." }
            isSaving.value = false
        }
    }

    fun saveCampaign(campaign: PromotionalCampaign, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            workspaceRepository.savePromotionalCampaign(campaign)
                .onSuccess { saved ->
                    statusMessage.value = "Campaign '${saved.name}' saved."
                    onSuccess()
                }
                .onFailure { errorMessage.value = it.message ?: "Unable to save promotional campaign." }
            isSaving.value = false
        }
    }

    fun updateCampaignStatus(campaignId: Long, newStatus: CampaignStatus) {
        viewModelScope.launch {
            isSaving.value = true
            workspaceRepository.updateCampaignStatus(campaignId, newStatus)
                .onSuccess { statusMessage.value = "Campaign status set to ${newStatus.displayName}." }
                .onFailure { errorMessage.value = it.message }
            isSaving.value = false
        }
    }

    fun saveAnnouncement(announcement: MedicineAnnouncement, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            workspaceRepository.saveMedicineAnnouncement(announcement)
                .onSuccess { saved ->
                    statusMessage.value = if (saved.status == AnnouncementStatus.PUBLISHED) {
                        "Announcement '${saved.title}' published to Stockists."
                    } else {
                        "Announcement draft saved."
                    }
                    onSuccess()
                }
                .onFailure { errorMessage.value = it.message ?: "Unable to save announcement." }
            isSaving.value = false
        }
    }

    fun updateAnnouncementStatus(announcementId: Long, newStatus: AnnouncementStatus) {
        viewModelScope.launch {
            isSaving.value = true
            workspaceRepository.updateAnnouncementStatus(announcementId, newStatus)
                .onSuccess { statusMessage.value = "Announcement ${newStatus.displayName}." }
                .onFailure { errorMessage.value = it.message }
            isSaving.value = false
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

    fun saveSubmissionDraft(submission: MedicineSubmission, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            workspaceRepository.saveMedicineDraft(submission)
                .onSuccess {
                    statusMessage.value = "Draft saved for '${it.medicineName}'."
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
                    statusMessage.value = "'${it.medicineName}' submitted to Company Approvals Queue."
                    onSuccess()
                }
                .onFailure { errorMessage.value = it.message }
            isSaving.value = false
        }
    }

    fun withdrawSubmission(submissionId: Long) {
        viewModelScope.launch {
            isSaving.value = true
            workspaceRepository.withdrawMedicineSubmission(submissionId)
                .onSuccess { statusMessage.value = "Submission withdrawn." }
                .onFailure { errorMessage.value = it.message }
            isSaving.value = false
        }
    }

    fun clearMessages() {
        statusMessage.value = null
        errorMessage.value = null
    }

    private data class PrimaryManufacturerBundle(
        val user: AuthUser?,
        val summary: ManufacturerDashboardSummary,
        val banners: List<OfferBanner>,
        val campaigns: List<PromotionalCampaign>,
        val announcements: List<MedicineAnnouncement>
    )

    private data class SecondaryManufacturerBundle(
        val submissions: List<MedicineSubmission>,
        val approvedProducts: List<ProductEntity>,
        val unreadCount: Int,
        val duplicates: List<MedicineDuplicateCandidate>
    )
}

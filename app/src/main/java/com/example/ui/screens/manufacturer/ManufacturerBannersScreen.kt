package com.example.ui.screens.manufacturer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Alignment
import com.example.data.local.entity.ProductEntity
import com.example.data.remote.upload.MediaUploadState
import com.example.domain.model.BannerOfferType
import com.example.domain.model.BannerStatus
import com.example.domain.model.BannerTargetAudience
import com.example.domain.model.OfferBanner
import com.example.ui.components.ManufacturerBottomBar
import com.example.ui.components.OfferBannerShowcaseCard
import com.example.ui.components.WorkspaceAccessDeniedScreen
import com.example.util.Formatters

/**
 * Manufacturer Offer Banner Management screen (Products & Banners tab).
 * Supports all 16 enterprise banner fields, zero-permission Android Photo Picker,
 * live preview, status lifecycle (DRAFT, SCHEDULED, ACTIVE, EXPIRED, INACTIVE, ARCHIVED),
 * and approved product linkage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManufacturerBannersScreen(
    viewModel: ManufacturerWorkspaceViewModel,
    onNavigateHome: () -> Unit,
    onNavigatePromotions: () -> Unit,
    onNavigateUpdates: () -> Unit,
    onNavigateProfile: () -> Unit,
    onReturnToAuthorizedWorkspace: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uploadState by viewModel.mediaUploadState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedStatusFilter by remember { mutableStateOf<BannerStatus?>(null) }
    var editingBanner by remember { mutableStateOf<OfferBanner?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    if (uiState.user != null && !uiState.isAuthorized) {
        WorkspaceAccessDeniedScreen(
            reason = "Offer Banner Management is restricted to Manufacturer accounts with BANNER_CREATE/BANNER_UPDATE permissions.",
            onReturnToAuthorizedWorkspace = onReturnToAuthorizedWorkspace
        )
        return
    }

    val filteredBanners = uiState.banners.filter { banner ->
        selectedStatusFilter == null || banner.effectiveStatus(uiState.currentTimeMillis) == selectedStatusFilter
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Offer Banners & Products", fontWeight = FontWeight.Bold)
                        Text(
                            text = "${uiState.approvedProducts.size} approved medicines eligible for banners",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.canCreateBanner) {
                FloatingActionButton(
                    onClick = {
                        editingBanner = null
                        showCreateDialog = true
                    },
                    modifier = Modifier.testTag("mfg_create_banner_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Offer Banner")
                }
            }
        },
        bottomBar = {
            ManufacturerBottomBar(
                selectedTab = "products",
                onNavigateHome = onNavigateHome,
                onNavigateProducts = {},
                onNavigatePromotions = onNavigatePromotions,
                onNavigateUpdates = onNavigateUpdates,
                onNavigateProfile = onNavigateProfile
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("manufacturer_banners_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedStatusFilter == null,
                        onClick = { selectedStatusFilter = null },
                        label = { Text("All (${uiState.banners.size})") }
                    )
                    BannerStatus.entries.forEach { st ->
                        FilterChip(
                            selected = selectedStatusFilter == st,
                            onClick = { selectedStatusFilter = st },
                            label = { Text(st.displayName) }
                        )
                    }
                }
            }

            if (filteredBanners.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("No Offer Banners in this view", fontWeight = FontWeight.Bold)
                            Text(
                                text = "Create a Stockist promotional banner linked to your approved products and set start/end validity dates.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredBanners, key = { it.id }) { banner ->
                    val effective = banner.effectiveStatus(uiState.currentTimeMillis)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OfferBannerShowcaseCard(
                            banner = banner,
                            showStatusPill = true,
                            onActionClick = {
                                if (uiState.canUpdateBanner) {
                                    editingBanner = banner
                                    showCreateDialog = true
                                }
                            },
                            modifier = Modifier.testTag("mfg_banner_card_${banner.id}")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${Formatters.formatDate(banner.startDateEpochMillis)} → ${Formatters.formatDate(banner.endDateEpochMillis)} • Priority #${banner.priorityOrder} • ${banner.targetAudience.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (uiState.canUpdateBanner && effective != BannerStatus.ACTIVE) {
                                OutlinedButton(
                                    onClick = { viewModel.updateBannerStatus(banner.id, BannerStatus.ACTIVE) },
                                    modifier = Modifier.testTag("mfg_banner_activate_${banner.id}")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Text("Activate")
                                }
                            }
                            if (uiState.canUpdateBanner && effective == BannerStatus.ACTIVE) {
                                OutlinedButton(
                                    onClick = { viewModel.updateBannerStatus(banner.id, BannerStatus.INACTIVE) },
                                    modifier = Modifier.testTag("mfg_banner_deactivate_${banner.id}")
                                ) {
                                    Icon(Icons.Default.PauseCircle, contentDescription = null)
                                    Text("Deactivate")
                                }
                            }
                            if (uiState.canUpdateBanner && effective != BannerStatus.ARCHIVED) {
                                OutlinedButton(
                                    onClick = { viewModel.updateBannerStatus(banner.id, BannerStatus.ARCHIVED) }
                                ) {
                                    Icon(Icons.Default.Archive, contentDescription = null)
                                    Text("Archive")
                                }
                            }
                            if (uiState.canDeleteBanner) {
                                OutlinedButton(
                                    onClick = { viewModel.deleteBanner(banner.id) },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showCreateDialog) {
        OfferBannerEditorDialog(
            initial = editingBanner,
            organizationName = uiState.summary.organizationName,
            approvedProducts = uiState.approvedProducts,
            uploadState = uploadState,
            onUploadImage = { uri, onUploaded -> viewModel.uploadBannerImage(uri, onUploaded) },
            onRetryUpload = { onUploaded -> viewModel.retryBannerImageUpload(onUploaded) },
            onCancelUpload = { viewModel.cancelBannerImageUpload() },
            onDismiss = {
                viewModel.resetMediaUploadState()
                showCreateDialog = false
            },
            onSave = { banner ->
                viewModel.saveBanner(banner) { showCreateDialog = false }
            }
        )
    }
}

@Composable
private fun OfferBannerEditorDialog(
    initial: OfferBanner?,
    organizationName: String,
    approvedProducts: List<ProductEntity>,
    uploadState: MediaUploadState,
    onUploadImage: (android.net.Uri, (String) -> Unit) -> Unit,
    onRetryUpload: ((String) -> Unit) -> Unit,
    onCancelUpload: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (OfferBanner) -> Unit
) {
    val now = System.currentTimeMillis()
    val dayMs = 24L * 60L * 60L * 1000L

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var subtitle by remember { mutableStateOf(initial?.subtitle ?: "") }
    var discountSummary by remember { mutableStateOf(initial?.discountOrOfferSummary ?: "") }
    var offerType by remember { mutableStateOf(initial?.offerType ?: BannerOfferType.PERCENT_DISCOUNT) }
    var targetAudience by remember { mutableStateOf(initial?.targetAudience ?: BannerTargetAudience.ALL_STOCKISTS) }
    var ctaText by remember { mutableStateOf(initial?.ctaText ?: "View Offer") }
    var priorityText by remember { mutableStateOf((initial?.priorityOrder ?: 1).toString()) }
    var durationDaysText by remember { mutableStateOf("14") }
    var startDelayDaysText by remember { mutableStateOf("0") }
    var selectedProduct by remember {
        mutableStateOf(approvedProducts.firstOrNull { it.id == initial?.linkedProductId } ?: approvedProducts.firstOrNull())
    }
    var persistedImageUrl by remember { mutableStateOf(initial?.imageUrl) }
    var localPreviewUri by remember { mutableStateOf(initial?.imageUrl) }

    // Zero-permission Android Photo Picker (Play policy compliant) -> immediate validated upload
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            localPreviewUri = uri.toString()
            onUploadImage(uri) { remoteUrl ->
                persistedImageUrl = remoteUrl
            }
        }
    }

    val isUploadInFlight = uploadState is MediaUploadState.Validating || uploadState is MediaUploadState.Uploading

    val startEpoch = now + ((startDelayDaysText.toLongOrNull() ?: 0L) * dayMs)
    val endEpoch = startEpoch + ((durationDaysText.toLongOrNull()?.coerceAtLeast(1L) ?: 14L) * dayMs)

    val previewBanner = OfferBanner(
        id = initial?.id ?: 0L,
        organizationId = initial?.organizationId ?: 0L,
        organizationName = organizationName.ifBlank { "Your Pharmaceutical Organization" },
        title = title.ifBlank { "Offer Banner Title Preview" },
        subtitle = subtitle.ifBlank { "Subtitle displayed to eligible Stockists" },
        imageUrl = localPreviewUri ?: persistedImageUrl,
        offerType = offerType,
        discountOrOfferSummary = discountSummary.ifBlank { "10% Trade Discount + Priority Dispatch" },
        linkedProductId = selectedProduct?.id,
        linkedProductName = selectedProduct?.name,
        linkedCategory = selectedProduct?.therapeuticCategory,
        startDateEpochMillis = startEpoch,
        endDateEpochMillis = endEpoch,
        priorityOrder = priorityText.toIntOrNull() ?: 1,
        status = if ((startDelayDaysText.toLongOrNull() ?: 0L) > 0L) BannerStatus.SCHEDULED else BannerStatus.ACTIVE,
        targetAudience = targetAudience,
        ctaText = ctaText,
        createdByUserId = initial?.createdByUserId ?: 0L
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "Create Offer Banner" else "Edit Offer Banner",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Live Stockist Preview", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                OfferBannerShowcaseCard(banner = previewBanner, showStatusPill = true)

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Banner Title*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("banner_title_input")
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Subtitle / Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = discountSummary,
                    onValueChange = { discountSummary = it },
                    label = { Text("Discount / Offer Summary*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("banner_discount_input")
                )

                OutlinedButton(
                    enabled = !isUploadInFlight,
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("banner_pick_image_btn")
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Text(
                        text = if (persistedImageUrl == null && localPreviewUri == null) {
                            "Select Banner Artwork (Photo Picker)"
                        } else {
                            "Change Selected Artwork"
                        },
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                when (uploadState) {
                    is MediaUploadState.Validating -> {
                        Text(
                            text = "Validating image type & size...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is MediaUploadState.Uploading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Uploading ${uploadState.fileName} (${(uploadState.progress * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onCancelUpload) { Text("Cancel") }
                        }
                        LinearProgressIndicator(
                            progress = { uploadState.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is MediaUploadState.Success -> {
                        Text(
                            text = "Artwork uploaded (${uploadState.contentType})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is MediaUploadState.Failed -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uploadState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            if (uploadState.retryable) {
                                TextButton(onClick = { onRetryUpload { persistedImageUrl = it } }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                    MediaUploadState.Cancelled -> {
                        Text(
                            text = "Upload cancelled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MediaUploadState.Idle -> Unit
                }

                Text("Offer Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BannerOfferType.entries.forEach { type ->
                        FilterChip(
                            selected = offerType == type,
                            onClick = { offerType = type },
                            label = { Text(type.displayName) }
                        )
                    }
                }

                if (approvedProducts.isNotEmpty()) {
                    Text("Link Approved Medicine", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        approvedProducts.take(6).forEach { prod ->
                            FilterChip(
                                selected = selectedProduct?.id == prod.id,
                                onClick = { selectedProduct = prod },
                                label = { Text(prod.name) }
                            )
                        }
                    }
                }

                Text("Target Audience", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BannerTargetAudience.entries.forEach { audience ->
                        FilterChip(
                            selected = targetAudience == audience,
                            onClick = { targetAudience = audience },
                            label = { Text(audience.displayName) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDelayDaysText,
                        onValueChange = { startDelayDaysText = it.filter(Char::isDigit) },
                        label = { Text("Start In (Days)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = durationDaysText,
                        onValueChange = { durationDaysText = it.filter(Char::isDigit) },
                        label = { Text("Duration (Days)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priorityText,
                        onValueChange = { priorityText = it.filter(Char::isDigit) },
                        label = { Text("Priority #") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = ctaText,
                        onValueChange = { ctaText = it },
                        label = { Text("CTA Label") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    enabled = !isUploadInFlight,
                    onClick = { onSave(previewBanner.copy(imageUrl = persistedImageUrl, status = BannerStatus.DRAFT)) }
                ) {
                    Text("Save Draft")
                }
                Button(
                    enabled = !isUploadInFlight,
                    onClick = {
                        val targetStatus = if ((startDelayDaysText.toLongOrNull() ?: 0L) > 0L) {
                            BannerStatus.SCHEDULED
                        } else {
                            BannerStatus.ACTIVE
                        }
                        onSave(previewBanner.copy(imageUrl = persistedImageUrl, status = targetStatus))
                    },
                    modifier = Modifier.testTag("banner_publish_btn")
                ) {
                    Text(if ((startDelayDaysText.toLongOrNull() ?: 0L) > 0L) "Schedule" else "Publish Live")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

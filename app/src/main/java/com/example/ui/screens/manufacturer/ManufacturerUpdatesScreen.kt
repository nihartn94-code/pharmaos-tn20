package com.example.ui.screens.manufacturer

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.ProductEntity
import com.example.domain.model.AnnouncementCategory
import com.example.domain.model.AnnouncementStatus
import com.example.domain.model.MedicineAnnouncement
import com.example.domain.model.MedicineSubmission
import com.example.domain.model.MedicineSubmissionStatus
import com.example.domain.model.UserRole
import com.example.ui.components.ManufacturerBottomBar
import com.example.ui.components.MedicineAnnouncementCard
import com.example.ui.components.WorkspaceAccessDeniedScreen

/**
 * Manufacturer Updates Screen featuring two focused sections:
 * 1) Medicine Announcements (strictly linked to organization's APPROVED medicines)
 * 2) New Medicine Submissions (Draft -> Submit for Company Approval -> Track Pending / Approved / Rejected -> Resubmit)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManufacturerUpdatesScreen(
    viewModel: ManufacturerWorkspaceViewModel,
    onNavigateHome: () -> Unit,
    onNavigateProducts: () -> Unit,
    onNavigatePromotions: () -> Unit,
    onNavigateProfile: () -> Unit,
    onReturnToAuthorizedWorkspace: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeTab by remember { mutableIntStateOf(0) }
    var showAnnouncementDialog by remember { mutableStateOf(false) }
    var showSubmissionDialog by remember { mutableStateOf(false) }
    var resubmittingItem by remember { mutableStateOf<MedicineSubmission?>(null) }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    if (uiState.user != null && !uiState.isAuthorized) {
        WorkspaceAccessDeniedScreen(
            reason = "Manufacturer Updates & Medicine Submissions require authorized Pharmaceutical Organization permissions.",
            onReturnToAuthorizedWorkspace = onReturnToAuthorizedWorkspace
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Updates & Submissions", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Announce approved medicines or submit new products for Company approval",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (activeTab == 0) {
                        showAnnouncementDialog = true
                    } else {
                        resubmittingItem = null
                        showSubmissionDialog = true
                    }
                },
                modifier = Modifier.testTag(if (activeTab == 0) "mfg_new_announcement_fab" else "mfg_new_submission_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = if (activeTab == 0) "New Announcement" else "Submit Medicine")
            }
        },
        bottomBar = {
            ManufacturerBottomBar(
                selectedTab = "updates",
                onNavigateHome = onNavigateHome,
                onNavigateProducts = onNavigateProducts,
                onNavigatePromotions = onNavigatePromotions,
                onNavigateUpdates = {},
                onNavigateProfile = onNavigateProfile
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = activeTab) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Announcements (${uiState.announcements.size})") },
                    modifier = Modifier.testTag("mfg_tab_announcements")
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Medicine Submissions (${uiState.submissions.size})") },
                    modifier = Modifier.testTag("mfg_tab_submissions")
                )
            }

            if (activeTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.announcements.isEmpty()) {
                        item {
                            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("No Medicine Announcements Yet", fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "Publish restock updates, formulation alerts, or launch announcements linked strictly to your organization's APPROVED medicines.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(uiState.announcements, key = { it.id }) { ann ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                MedicineAnnouncementCard(announcement = ann)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (ann.status != AnnouncementStatus.PUBLISHED) {
                                        OutlinedButton(onClick = { viewModel.updateAnnouncementStatus(ann.id, AnnouncementStatus.PUBLISHED) }) {
                                            Text("Publish Live")
                                        }
                                    }
                                    if (ann.status != AnnouncementStatus.ARCHIVED) {
                                        OutlinedButton(onClick = { viewModel.updateAnnouncementStatus(ann.id, AnnouncementStatus.ARCHIVED) }) {
                                            Text("Archive")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.submissions.isEmpty()) {
                        item {
                            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("No Medicine Submissions Yet", fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "All new medicines must pass Company Approval before becoming discoverable by Stockists.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(uiState.submissions, key = { it.id }) { sub ->
                            ManufacturerSubmissionStatusCard(
                                submission = sub,
                                onResubmit = {
                                    resubmittingItem = sub
                                    showSubmissionDialog = true
                                },
                                onWithdraw = { viewModel.withdrawSubmission(sub.id) }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showAnnouncementDialog) {
        CreateAnnouncementDialog(
            approvedProducts = uiState.approvedProducts,
            onDismiss = { showAnnouncementDialog = false },
            onSave = { ann -> viewModel.saveAnnouncement(ann) { showAnnouncementDialog = false } }
        )
    }

    if (showSubmissionDialog) {
        ManufacturerMedicineSubmissionDialog(
            initial = resubmittingItem,
            onDismiss = { showSubmissionDialog = false },
            onSaveDraft = { sub -> viewModel.saveSubmissionDraft(sub) { showSubmissionDialog = false } },
            onSubmitForApproval = { sub -> viewModel.submitNewMedicine(sub) { showSubmissionDialog = false } }
        )
    }
}

@Composable
private fun ManufacturerSubmissionStatusCard(
    submission: MedicineSubmission,
    onResubmit: () -> Unit,
    onWithdraw: () -> Unit
) {
    val badgeBg = when (submission.approvalStatus) {
        MedicineSubmissionStatus.APPROVED -> MaterialTheme.colorScheme.primaryContainer
        MedicineSubmissionStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer
        MedicineSubmissionStatus.PENDING_APPROVAL -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mfg_submission_card_${submission.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(submission.medicineName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(8.dp), color = badgeBg) {
                    Text(
                        text = submission.approvalStatus.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = "${submission.brandName} • ${submission.saltComposition} • ${submission.strength} ${submission.dosageForm}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!submission.rejectionReason.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Rejection Reason: ${submission.rejectionReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (submission.approvalStatus == MedicineSubmissionStatus.REJECTED ||
                    submission.approvalStatus == MedicineSubmissionStatus.DRAFT ||
                    submission.approvalStatus == MedicineSubmissionStatus.WITHDRAWN
                ) {
                    OutlinedButton(onClick = onResubmit) {
                        Text(if (submission.approvalStatus == MedicineSubmissionStatus.REJECTED) "Edit & Resubmit" else "Submit for Approval")
                    }
                }
                if (submission.approvalStatus == MedicineSubmissionStatus.PENDING_APPROVAL) {
                    OutlinedButton(onClick = onWithdraw) {
                        Text("Withdraw")
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateAnnouncementDialog(
    approvedProducts: List<ProductEntity>,
    onDismiss: () -> Unit,
    onSave: (MedicineAnnouncement) -> Unit
) {
    var selectedProduct by remember { mutableStateOf(approvedProducts.firstOrNull()) }
    var category by remember { mutableStateOf(AnnouncementCategory.NEW_LAUNCH) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Publish Medicine Announcement", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Only APPROVED medicines from your Pharmaceutical Organization can be announced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AnnouncementCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.displayName) }
                        )
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Headline*") }, singleLine = true)
                OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Announcement Details*") }, minLines = 3)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    enabled = selectedProduct != null,
                    onClick = {
                        val prod = selectedProduct ?: return@TextButton
                        onSave(
                            MedicineAnnouncement(
                                organizationId = 0L,
                                organizationName = "",
                                linkedProductId = prod.id,
                                linkedProductName = prod.name,
                                title = title,
                                body = body,
                                category = category,
                                status = AnnouncementStatus.DRAFT,
                                createdByUserId = 0L
                            )
                        )
                    }
                ) { Text("Draft") }
                Button(
                    enabled = selectedProduct != null,
                    onClick = {
                        val prod = selectedProduct ?: return@Button
                        onSave(
                            MedicineAnnouncement(
                                organizationId = 0L,
                                organizationName = "",
                                linkedProductId = prod.id,
                                linkedProductName = prod.name,
                                title = title,
                                body = body,
                                category = category,
                                status = AnnouncementStatus.PUBLISHED,
                                createdByUserId = 0L
                            )
                        )
                    }
                ) { Text("Publish") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ManufacturerMedicineSubmissionDialog(
    initial: MedicineSubmission?,
    onDismiss: () -> Unit,
    onSaveDraft: (MedicineSubmission) -> Unit,
    onSubmitForApproval: (MedicineSubmission) -> Unit
) {
    var name by remember { mutableStateOf(initial?.medicineName ?: "") }
    var brand by remember { mutableStateOf(initial?.brandName ?: "") }
    var genericName by remember { mutableStateOf(initial?.genericName ?: "") }
    var salt by remember { mutableStateOf(initial?.saltComposition ?: "") }
    var dosageForm by remember { mutableStateOf(initial?.dosageForm ?: "Tablet") }
    var strength by remember { mutableStateOf(initial?.strength ?: "") }
    var packSize by remember { mutableStateOf(initial?.packSize ?: "10 x 10 Tablets") }
    var category by remember { mutableStateOf(initial?.therapeuticCategory ?: "General Medicine") }
    var mrp by remember { mutableStateOf(initial?.mrp?.takeIf { it > 0 }?.toString() ?: "") }
    var ptr by remember { mutableStateOf(initial?.wholesaleRatePtr?.takeIf { it > 0 }?.toString() ?: "") }
    var hsn by remember { mutableStateOf(initial?.hsnCode ?: "30049099") }
    var description by remember { mutableStateOf(initial?.description ?: "") }

    fun buildSubmission() = MedicineSubmission(
        id = initial?.id ?: 0L,
        organizationId = initial?.organizationId ?: 0L,
        organizationName = initial?.organizationName ?: "",
        submittedByUserId = initial?.submittedByUserId ?: 0L,
        submittedByName = initial?.submittedByName ?: "",
        submittedByRole = UserRole.MANUFACTURER,
        medicineName = name,
        brandName = brand,
        genericName = genericName.ifBlank { salt },
        saltComposition = salt,
        dosageForm = dosageForm,
        strength = strength,
        packSize = packSize,
        therapeuticCategory = category,
        mrp = mrp.toDoubleOrNull() ?: 0.0,
        wholesaleRatePtr = ptr.toDoubleOrNull() ?: 0.0,
        hsnCode = hsn,
        description = description
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "Submit New Medicine for Approval" else "Resubmit Medicine",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Submitted medicines enter PENDING_APPROVAL and require Company approval before Stockists can discover them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Medicine Name*") }, singleLine = true, modifier = Modifier.testTag("mfg_sub_name"))
                OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Brand Name*") }, singleLine = true)
                OutlinedTextField(value = genericName, onValueChange = { genericName = it }, label = { Text("Generic Name") }, singleLine = true)
                OutlinedTextField(value = salt, onValueChange = { salt = it }, label = { Text("Salt Composition*") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = dosageForm, onValueChange = { dosageForm = it }, label = { Text("Dosage Form*") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = strength, onValueChange = { strength = it }, label = { Text("Strength*") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(value = packSize, onValueChange = { packSize = it }, label = { Text("Pack Size*") }, singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Therapeutic Category") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = mrp, onValueChange = { mrp = it }, label = { Text("MRP (₹)") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = ptr, onValueChange = { ptr = it }, label = { Text("PTR (₹)") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(value = hsn, onValueChange = { hsn = it }, label = { Text("HSN Code") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Formulation Notes") })
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onSaveDraft(buildSubmission()) }) { Text("Save Draft") }
                Button(
                    onClick = { onSubmitForApproval(buildSubmission()) },
                    modifier = Modifier.testTag("mfg_sub_submit_btn")
                ) { Text("Submit") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

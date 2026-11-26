package com.example.ui.screens.company

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.MedicineSubmission
import com.example.domain.model.UserRole
import com.example.ui.components.WorkspaceAccessDeniedScreen

/**
 * Multi-section Company Add New Medicine Screen:
 * 1) Basic Information
 * 2) Product Information
 * 3) Commercial Information
 * 4) Supporting Information (zero-permission Android Photo Picker)
 * Includes live duplicate detection, Save Draft, Preview Dialog, and Compulsory Approval Submission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyAddMedicineScreen(
    viewModel: CompanyWorkspaceViewModel,
    onNavigateBack: () -> Unit,
    onSubmittedSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Section 1: Basic Information
    var medicineName by remember { mutableStateOf("") }
    var brandName by remember { mutableStateOf("") }
    var genericName by remember { mutableStateOf("") }
    var saltComposition by remember { mutableStateOf("") }

    // Section 2: Product Information
    var dosageForm by remember { mutableStateOf("Tablet") }
    var strength by remember { mutableStateOf("") }
    var routeOfAdmin by remember { mutableStateOf("Oral") }
    var packSize by remember { mutableStateOf("10 x 10 Tablets") }
    var packagingType by remember { mutableStateOf("Alu-Alu Blister Strip") }
    var therapeuticCategory by remember { mutableStateOf("General Medicine") }
    var isRx by remember { mutableStateOf(true) }

    // Section 3: Commercial Information
    var mrp by remember { mutableStateOf("") }
    var ptr by remember { mutableStateOf("") }
    var hsnCode by remember { mutableStateOf("30049099") }
    var gstPercent by remember { mutableStateOf("12.0") }
    var skuCode by remember { mutableStateOf("") }

    // Section 4: Supporting Information
    var description by remember { mutableStateOf("") }
    var regulatoryId by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }

    var showPreviewDialog by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) imageUri = uri.toString()
    }

    LaunchedEffect(medicineName, brandName, saltComposition, strength, dosageForm) {
        if (medicineName.length >= 3 || saltComposition.length >= 3) {
            viewModel.checkDuplicates(medicineName, brandName, saltComposition, strength, dosageForm)
        }
    }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    if (uiState.user != null && !uiState.isAuthorized) {
        WorkspaceAccessDeniedScreen(
            reason = "Only authorized Company / Pharmaceutical Organization accounts can add new medicines.",
            onReturnToAuthorizedWorkspace = onNavigateBack
        )
        return
    }

    fun buildSubmission() = MedicineSubmission(
        organizationId = uiState.user?.organizationId ?: 0L,
        organizationName = uiState.user?.organizationName ?: "",
        submittedByUserId = uiState.user?.id ?: 0L,
        submittedByName = uiState.user?.fullName ?: "",
        submittedByRole = uiState.user?.role ?: UserRole.PHARMA_COMPANY,
        medicineName = medicineName,
        brandName = brandName,
        genericName = genericName.ifBlank { saltComposition },
        saltComposition = saltComposition,
        dosageForm = dosageForm,
        strength = strength,
        routeOfAdministration = routeOfAdmin,
        packSize = packSize,
        packagingType = packagingType,
        therapeuticCategory = therapeuticCategory,
        isRx = isRx,
        mrp = mrp.toDoubleOrNull() ?: 0.0,
        wholesaleRatePtr = ptr.toDoubleOrNull() ?: 0.0,
        hsnCode = hsnCode,
        gstRatePercent = gstPercent.toDoubleOrNull() ?: 12.0,
        skuCode = skuCode,
        description = description,
        imageUrl = imageUri,
        regulatoryIdentifier = regulatoryId.ifBlank { null }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Medicine", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showPreviewDialog = true }) {
                        Icon(Icons.Default.Preview, contentDescription = "Preview Submission")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("company_add_medicine_form"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (uiState.duplicateCandidates.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().testTag("duplicate_medicine_warning")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Column {
                            Text(
                                text = "Possible Duplicate Detected (${uiState.duplicateCandidates.size})",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            val first = uiState.duplicateCandidates.first()
                            Text(
                                text = "${first.name} (${first.brand}, ${first.strength}) — ${first.matchReason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            FormSectionCard("1. Basic Information") {
                OutlinedTextField(value = medicineName, onValueChange = { medicineName = it }, label = { Text("Medicine Name*") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("add_med_name"))
                OutlinedTextField(value = brandName, onValueChange = { brandName = it }, label = { Text("Brand Name*") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("add_med_brand"))
                OutlinedTextField(value = genericName, onValueChange = { genericName = it }, label = { Text("Generic Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = saltComposition, onValueChange = { saltComposition = it }, label = { Text("Salt Composition*") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("add_med_salt"))
            }

            FormSectionCard("2. Product Information") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = dosageForm, onValueChange = { dosageForm = it }, label = { Text("Dosage Form*") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = strength, onValueChange = { strength = it }, label = { Text("Strength*") }, singleLine = true, modifier = Modifier.weight(1f).testTag("add_med_strength"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = routeOfAdmin, onValueChange = { routeOfAdmin = it }, label = { Text("Route") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = packSize, onValueChange = { packSize = it }, label = { Text("Pack Size*") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = packagingType, onValueChange = { packagingType = it }, label = { Text("Packaging Type") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = therapeuticCategory, onValueChange = { therapeuticCategory = it }, label = { Text("Therapeutic Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Prescription Required (Schedule H / Rx)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isRx, onCheckedChange = { isRx = it })
                }
            }

            FormSectionCard("3. Commercial Information") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = mrp, onValueChange = { mrp = it }, label = { Text("MRP (₹)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = ptr, onValueChange = { ptr = it }, label = { Text("PTR / Wholesale (₹)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = hsnCode, onValueChange = { hsnCode = it }, label = { Text("HSN Code") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = gstPercent, onValueChange = { gstPercent = it }, label = { Text("GST %") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = skuCode, onValueChange = { skuCode = it }, label = { Text("Internal SKU / Product Code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            FormSectionCard("4. Supporting Information") {
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Clinical / Commercial Description") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = regulatoryId, onValueChange = { regulatoryId = it }, label = { Text("CDSCO / Regulatory Identifier") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Text(if (imageUri == null) "Attach Medicine Pack Image (Photo Picker)" else "Change Attached Pack Image")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    enabled = !uiState.isSaving,
                    onClick = { viewModel.saveMedicineDraft(buildSubmission(), onSubmittedSuccess) },
                    modifier = Modifier.weight(1f).testTag("add_med_save_draft_btn")
                ) {
                    Text("Save Draft")
                }
                Button(
                    enabled = !uiState.isSaving,
                    onClick = { viewModel.submitNewMedicine(buildSubmission(), onSubmittedSuccess) },
                    modifier = Modifier.weight(1f).testTag("add_med_submit_btn")
                ) {
                    Text("Submit for Approval")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showPreviewDialog) {
        val sub = buildSubmission()
        AlertDialog(
            onDismissRequest = { showPreviewDialog = false },
            title = { Text("Submission Preview", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${sub.medicineName.ifBlank { "(Unnamed)" }} — ${sub.brandName}")
                    Text("Salt: ${sub.saltComposition} • ${sub.strength} ${sub.dosageForm}")
                    Text("Pack: ${sub.packSize} (${sub.packagingType})")
                    Text("Commercial: MRP ₹${sub.mrp} • PTR ₹${sub.wholesaleRatePtr} • HSN ${sub.hsnCode} • GST ${sub.gstRatePercent}%")
                    Text("Workflow: Will enter PENDING_APPROVAL until an authorized Company approver approves it.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showPreviewDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun FormSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

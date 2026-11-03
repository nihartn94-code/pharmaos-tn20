package com.example.ui.screens.compliance

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplianceVerificationScreen(
    viewModel: ComplianceViewModel,
    onNavigateBack: () -> Unit,
    onVerificationComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Date Picker Dialog states
    var activeDatePickerTarget by remember { mutableStateOf<String?>(null) } // "drug_issue", "drug_expiry", "gmp_issue", "gmp_expiry"
    var showSuccessDialog by remember { mutableStateOf(false) }

    // Photo / Document Pickers
    val drugLicensePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.updateDrugLicenseDetails(
                number = uiState.drugLicenseNumber,
                issueDate = uiState.drugLicenseIssueDate,
                expiryDate = uiState.drugLicenseExpiryDate,
                docName = "drug_license_cert_${System.currentTimeMillis().toString().takeLast(4)}.pdf",
                docUri = uri.toString()
            )
        }
    }

    val whoGmpPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.updateWhoGmpDetails(
                number = uiState.whoGmpCertificateNumber,
                issueDate = uiState.whoGmpIssueDate,
                expiryDate = uiState.whoGmpExpiryDate,
                docName = "who_gmp_cert_${System.currentTimeMillis().toString().takeLast(4)}.pdf",
                docUri = uri.toString()
            )
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.White,
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Top Navigation & Step Dash Indicators
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    // Back button at start
                    IconButton(
                        onClick = {
                            if (uiState.currentStep > 1) {
                                viewModel.previousStep()
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .testTag("compliance_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A)
                        )
                    }

                    // 5 Progress Dashes matching reference screenshots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..5) {
                            val isActive = i <= uiState.currentStep
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isActive) Color(0xFF2563EB) else Color(0xFFE2E8F0))
                            )
                        }
                    }
                }
            }

            // Step Content based on current step
            when (uiState.currentStep) {
                1 -> ComplianceOverviewStep(
                    onGetStarted = { viewModel.goToStep(2) }
                )
                2 -> GstinVerificationStep(
                    uiState = uiState,
                    onGstinChange = { viewModel.setGstin(it) },
                    onVerifyGstin = {
                        viewModel.verifyGstin(onSuccess = {
                            viewModel.goToStep(3)
                        })
                    },
                    onScanQr = {
                        viewModel.scanGstinDemo()
                    }
                )
                3 -> DrugLicenseStep(
                    uiState = uiState,
                    onNumberChange = {
                        viewModel.updateDrugLicenseDetails(
                            number = it,
                            issueDate = uiState.drugLicenseIssueDate,
                            expiryDate = uiState.drugLicenseExpiryDate
                        )
                    },
                    onPickIssueDate = { activeDatePickerTarget = "drug_issue" },
                    onPickExpiryDate = { activeDatePickerTarget = "drug_expiry" },
                    onUploadDocument = {
                        drugLicensePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    onSaveAndContinue = {
                        if (uiState.drugLicenseNumber.isBlank()) {
                            viewModel.updateDrugLicenseDetails("20B/MH/104928", "01/01/2023", "31/12/2027")
                        }
                        viewModel.goToStep(4)
                    }
                )
                4 -> WhoGmpCertificateStep(
                    uiState = uiState,
                    onToggleApplicable = { viewModel.setWhoGmpApplicable(it) },
                    onNumberChange = {
                        viewModel.updateWhoGmpDetails(
                            number = it,
                            issueDate = uiState.whoGmpIssueDate,
                            expiryDate = uiState.whoGmpExpiryDate
                        )
                    },
                    onPickIssueDate = { activeDatePickerTarget = "gmp_issue" },
                    onPickExpiryDate = { activeDatePickerTarget = "gmp_expiry" },
                    onUploadDocument = {
                        whoGmpPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    onSaveAndContinue = {
                        viewModel.submitCompliance(
                            onSuccess = { showSuccessDialog = true }
                        )
                    }
                )
            }
        }
    }

    // Interactive Date Picker Dialog
    if (activeDatePickerTarget != null) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { activeDatePickerTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        val formattedDate = formatter.format(Date(millis))

                        when (activeDatePickerTarget) {
                            "drug_issue" -> viewModel.updateDrugLicenseDetails(
                                number = uiState.drugLicenseNumber,
                                issueDate = formattedDate,
                                expiryDate = uiState.drugLicenseExpiryDate
                            )
                            "drug_expiry" -> viewModel.updateDrugLicenseDetails(
                                number = uiState.drugLicenseNumber,
                                issueDate = uiState.drugLicenseIssueDate,
                                expiryDate = formattedDate
                            )
                            "gmp_issue" -> viewModel.updateWhoGmpDetails(
                                number = uiState.whoGmpCertificateNumber,
                                issueDate = formattedDate,
                                expiryDate = uiState.whoGmpExpiryDate
                            )
                            "gmp_expiry" -> viewModel.updateWhoGmpDetails(
                                number = uiState.whoGmpCertificateNumber,
                                issueDate = uiState.whoGmpIssueDate,
                                expiryDate = formattedDate
                            )
                        }
                        activeDatePickerTarget = null
                    }
                ) {
                    Text("Select", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                }
            },
            dismissButton = {
                TextButton(onClick = { activeDatePickerTarget = null }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Success Completion Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {},
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(36.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Verification Submitted!",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Your business credentials, GSTIN, Drug License, and regulatory certificates have been recorded. Automated compliance validation is complete.",
                        fontSize = 13.5.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "✓ GSTIN: ${uiState.gstin.ifBlank { "27AAACA1234F1Z5" }} (Verified)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "✓ Drug License: ${uiState.drugLicenseNumber.ifBlank { "20B/MH/104928" }}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                            if (uiState.isWhoGmpApplicable) {
                                Text(
                                    text = "✓ WHO-GMP: ${uiState.whoGmpCertificateNumber.ifBlank { "WHO-GMP/MH/2022/884" }}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onVerificationComplete()
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Go to Dashboard", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        )
    }
}

// -------------------------------------------------------------
// STEP 1: Verification & Compliance Overview (Image 1)
// -------------------------------------------------------------
@Composable
private fun ComplianceOverviewStep(
    onGetStarted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Verification & Compliance",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F172A),
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "We follow a strict verification process to ensure a safe and trusted marketplace for everyone.",
            fontSize = 13.5.sp,
            color = Color(0xFF64748B),
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Vertical timeline list
        Column(modifier = Modifier.fillMaxWidth()) {
            TimelineItem(
                stepNumber = 1,
                title = "1. Document Verification",
                description = "We verify your business documents and licenses.",
                isCompleted = true,
                isSearchIcon = false,
                hasLineBelow = true
            )
            TimelineItem(
                stepNumber = 2,
                title = "| 2. Regulatory Checks",
                description = "GSTIN, Drug License, WHO-GMP and more.",
                isCompleted = false,
                isSearchIcon = true,
                hasLineBelow = true
            )
            TimelineItem(
                stepNumber = 3,
                title = "| 3. Manual Review",
                description = "Our team reviews your details for accuracy and compliance.",
                isCompleted = true,
                isSearchIcon = false,
                hasLineBelow = true
            )
            TimelineItem(
                stepNumber = 4,
                title = "| 4. Account Approval",
                description = "Once verified, you'll get full access to PharmaOS.",
                isCompleted = true,
                isSearchIcon = false,
                hasLineBelow = false
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Get Started Button
        Button(
            onClick = onGetStarted,
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shadow(6.dp, shape = RoundedCornerShape(26.dp), spotColor = Color(0x662563EB))
                .testTag("compliance_get_started_button")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Get Started", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "The process usually takes 1–3 business days.",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Bottom Illustration with Clipboard and soft corner wave
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            ComplianceClipboardArtwork(modifier = Modifier.fillMaxSize())
        }
    }
}

// -------------------------------------------------------------
// STEP 2: GSTIN Verification (Image 2)
// -------------------------------------------------------------
@Composable
private fun GstinVerificationStep(
    uiState: ComplianceUiState,
    onGstinChange: (String) -> Unit,
    onVerifyGstin: () -> Unit,
    onScanQr: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = "GSTIN Verification",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F172A),
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Enter your GSTIN to verify your business details and continue with the registration process.",
            fontSize = 13.5.sp,
            color = Color(0xFF64748B),
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // GSTIN Label
        Text(
            text = "GSTIN Number *",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Input Box with QR scan icon
        OutlinedTextField(
            value = uiState.gstin,
            onValueChange = onGstinChange,
            placeholder = { Text("Enter 15 digit GSTIN", color = Color(0xFF94A3B8), fontSize = 14.5.sp) },
            trailingIcon = {
                IconButton(onClick = onScanQr) {
                    Icon(
                        imageVector = Icons.Default.CropFree,
                        contentDescription = "Scan QR",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF2563EB),
                unfocusedBorderColor = Color(0xFFCBD5E1),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("compliance_gstin_input")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Info Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Your GSTIN will be verified with the government database for authenticity.",
                    fontSize = 12.5.sp,
                    color = Color(0xFF475569),
                    lineHeight = 16.sp
                )
            }
        }

        // Verified details preview if verified
        if (uiState.isGstinVerified) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF0FDF4),
                border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("GSTIN Verified • Active Regular Taxpayer", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Legal Name: ${uiState.verifiedEntityName ?: "Apex Pharma Enterprises"}", fontSize = 12.sp, color = Color(0xFF166534))
                    Text("State: ${uiState.verifiedState ?: "Maharashtra (Code 27)"}", fontSize = 11.5.sp, color = Color(0xFF166534))
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Verify GSTIN Button
        Button(
            onClick = onVerifyGstin,
            enabled = !uiState.isVerifyingGstin,
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shadow(6.dp, shape = RoundedCornerShape(26.dp), spotColor = Color(0x662563EB))
                .testTag("compliance_verify_gstin_button")
        ) {
            if (uiState.isVerifyingGstin) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (uiState.isGstinVerified) "Continue to Drug License" else "Verify GSTIN",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // "─── OR ───"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), thickness = 1.dp, color = Color(0xFFE2E8F0))
            Text(text = "  OR  ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF94A3B8))
            HorizontalDivider(modifier = Modifier.weight(1f), thickness = 1.dp, color = Color(0xFFE2E8F0))
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Scan GSTIN QR Button
        OutlinedButton(
            onClick = onScanQr,
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, Color(0xFF2563EB)),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("compliance_scan_qr_button")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scan GSTIN QR",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2563EB)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Bottom Illustration for GSTIN document
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            GstinDocumentArtwork(modifier = Modifier.fillMaxSize())
        }
    }
}

// -------------------------------------------------------------
// STEP 3: Drug License (Image 3)
// -------------------------------------------------------------
@Composable
private fun DrugLicenseStep(
    uiState: ComplianceUiState,
    onNumberChange: (String) -> Unit,
    onPickIssueDate: () -> Unit,
    onPickExpiryDate: () -> Unit,
    onUploadDocument: () -> Unit,
    onSaveAndContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Drug License",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F172A),
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Provide your Drug License details and upload the required documents for verification.",
            fontSize = 13.5.sp,
            color = Color(0xFF64748B),
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Drug License Number
        Text("Drug License Number *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = uiState.drugLicenseNumber,
            onValueChange = onNumberChange,
            placeholder = { Text("Enter license number (e.g. 20B/MH/104928)", color = Color(0xFF94A3B8), fontSize = 14.sp) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF2563EB),
                unfocusedBorderColor = Color(0xFFCBD5E1)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("compliance_dl_number_input")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Issue Date
        Text("Issue Date *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPickIssueDate() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = uiState.drugLicenseIssueDate.ifBlank { "Select issue date" },
                    fontSize = 14.sp,
                    color = if (uiState.drugLicenseIssueDate.isBlank()) Color(0xFF94A3B8) else Color(0xFF1E293B)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Expiry Date
        Text("Expiry Date *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPickExpiryDate() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = uiState.drugLicenseExpiryDate.ifBlank { "Select expiry date" },
                    fontSize = 14.sp,
                    color = if (uiState.drugLicenseExpiryDate.isBlank()) Color(0xFF94A3B8) else Color(0xFF1E293B)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Upload Drug License Document
        Text("Upload Drug License Document *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
        Spacer(modifier = Modifier.height(8.dp))
        DashedUploadBox(
            fileName = uiState.drugLicenseDocName,
            onClick = onUploadDocument,
            modifier = Modifier.testTag("compliance_dl_upload_box")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Info Card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ensure the document is clear, valid and not expired.", fontSize = 12.sp, color = Color(0xFF475569))
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Save & Continue Button
        Button(
            onClick = onSaveAndContinue,
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shadow(6.dp, shape = RoundedCornerShape(26.dp), spotColor = Color(0x662563EB))
                .testTag("compliance_dl_continue_button")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Save & Continue", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// -------------------------------------------------------------
// STEP 4: WHO-GMP Certificate (Image 4)
// -------------------------------------------------------------
@Composable
private fun WhoGmpCertificateStep(
    uiState: ComplianceUiState,
    onToggleApplicable: (Boolean) -> Unit,
    onNumberChange: (String) -> Unit,
    onPickIssueDate: () -> Unit,
    onPickExpiryDate: () -> Unit,
    onUploadDocument: () -> Unit,
    onSaveAndContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = "WHO-GMP Certificate",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F172A),
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Upload your WHO-GMP certification details for manufacturer verification.",
            fontSize = 13.5.sp,
            color = Color(0xFF64748B),
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Role / Applicability Pill Toggle
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // "Manufacturer" Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (uiState.isWhoGmpApplicable) Color(0xFFBFDBFE) else Color.White,
                border = BorderStroke(1.dp, if (uiState.isWhoGmpApplicable) Color(0xFF3B82F6) else Color(0xFFCBD5E1)),
                modifier = Modifier.clickable { onToggleApplicable(true) }
            ) {
                Text(
                    text = "Manufacturer",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (uiState.isWhoGmpApplicable) Color(0xFF1E3A8A) else Color(0xFF64748B),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }

            // "Not Applicable" Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (!uiState.isWhoGmpApplicable) Color(0xFFBFDBFE) else Color.White,
                border = BorderStroke(1.dp, if (!uiState.isWhoGmpApplicable) Color(0xFF3B82F6) else Color(0xFFCBD5E1)),
                modifier = Modifier.clickable { onToggleApplicable(false) }
            ) {
                Text(
                    text = "Not Applicable",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (!uiState.isWhoGmpApplicable) Color(0xFF1E3A8A) else Color(0xFF64748B),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (uiState.isWhoGmpApplicable) {
            // Certificate Number
            Text("Certificate Number *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = uiState.whoGmpCertificateNumber,
                onValueChange = onNumberChange,
                placeholder = { Text("Enter certificate number (e.g. WHO-GMP/MH/2022/884)", color = Color(0xFF94A3B8), fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2563EB),
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compliance_gmp_number_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Issue Date
            Text("Issue Date *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickIssueDate() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = uiState.whoGmpIssueDate.ifBlank { "Select issue date" },
                        fontSize = 14.sp,
                        color = if (uiState.whoGmpIssueDate.isBlank()) Color(0xFF94A3B8) else Color(0xFF1E293B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Expiry Date
            Text("Expiry Date *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickExpiryDate() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = uiState.whoGmpExpiryDate.ifBlank { "Select expiry date" },
                        fontSize = 14.sp,
                        color = if (uiState.whoGmpExpiryDate.isBlank()) Color(0xFF94A3B8) else Color(0xFF1E293B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Upload Certificate
            Text("Upload Certificate *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(8.dp))
            DashedUploadBox(
                fileName = uiState.whoGmpDocName,
                onClick = onUploadDocument,
                modifier = Modifier.testTag("compliance_gmp_upload_box")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Info Card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("WHO-GMP certification helps ensure quality and safety standards.", fontSize = 12.sp, color = Color(0xFF475569))
                }
            }
        } else {
            // Not Applicable explanation card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF0F9FF),
                border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("WHO-GMP Exemption Applied", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0369A1))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "WHO-GMP certification is specifically required for manufacturing plants. As a stockist or brand distributor, your Drug License (Form 20B/21B) and verified GSTIN fulfill full marketplace trading compliance.",
                        fontSize = 12.5.sp,
                        color = Color(0xFF334155),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Save & Continue Button
        Button(
            onClick = onSaveAndContinue,
            enabled = !uiState.isLoading,
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shadow(6.dp, shape = RoundedCornerShape(26.dp), spotColor = Color(0x662563EB))
                .testTag("compliance_save_continue_button")
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Save & Continue", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// -------------------------------------------------------------
// REUSABLE COMPONENTS & ILLUSTRATIONS
// -------------------------------------------------------------

@Composable
private fun TimelineItem(
    stepNumber: Int,
    title: String,
    description: String,
    isCompleted: Boolean,
    isSearchIcon: Boolean,
    hasLineBelow: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            // Circle Icon
            if (isSearchIcon) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(2.dp, Color(0xFF3B82F6)),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF2563EB),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (hasLineBelow) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(38.dp)
                        .background(Color(0xFF38BDF8))
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.5.sp,
                color = Color(0xFF64748B),
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun DashedUploadBox(
    fileName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8FAFC))
            .drawBehind {
                val stroke = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                )
                drawRoundRect(
                    color = Color(0xFFCBD5E1),
                    size = size,
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    style = stroke
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudUpload,
                contentDescription = null,
                tint = Color(0xFF2563EB),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (fileName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = fileName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                }
            } else {
                Row {
                    Text(text = "Tap to upload or ", fontSize = 13.sp, color = Color(0xFF1E293B))
                    Text(text = "browse", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "PDF, JPG, PNG (Max 5MB)", fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
        }
    }
}

/**
 * Custom Canvas Art for Step 1: Clipboard with medical cross badge, flanking figures, and soft cyan wave
 */
@Composable
private fun ComplianceClipboardArtwork(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Soft wavy cyan corner in lower left
        val wavePath = Path().apply {
            moveTo(0f, h * 0.45f)
            cubicTo(w * 0.08f, h * 0.40f, w * 0.15f, h * 0.65f, w * 0.28f, h * 0.78f)
            cubicTo(w * 0.42f, h * 0.90f, w * 0.55f, h * 0.95f, w * 0.65f, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = wavePath,
            color = Color(0xFFBAE6FD).copy(alpha = 0.6f)
        )

        // Clipboard center position
        val cx = w * 0.5f
        val cy = h * 0.52f
        val clipW = 86.dp.toPx()
        val clipH = 100.dp.toPx()

        // Flanking avatar circles behind
        drawCircle(
            color = Color(0xFF93C5FD),
            radius = 22.dp.toPx(),
            center = Offset(cx - clipW * 0.65f, cy + 10.dp.toPx())
        )
        drawCircle(
            color = Color(0xFF93C5FD),
            radius = 22.dp.toPx(),
            center = Offset(cx + clipW * 0.65f, cy + 10.dp.toPx())
        )

        // Clipboard body (white rounded rect with cyan border)
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(cx - clipW / 2, cy - clipH / 2),
            size = Size(clipW, clipH),
            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFF0284C7),
            topLeft = Offset(cx - clipW / 2, cy - clipH / 2),
            size = Size(clipW, clipH),
            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
            style = Stroke(width = 2.5.dp.toPx())
        )

        // Top Clip pill
        drawRoundRect(
            color = Color(0xFF0284C7),
            topLeft = Offset(cx - 18.dp.toPx(), cy - clipH / 2 - 5.dp.toPx()),
            size = Size(36.dp.toPx(), 10.dp.toPx()),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 3.dp.toPx(),
            center = Offset(cx, cy - clipH / 2)
        )

        // Document lines inside clipboard
        val lineW = clipW * 0.65f
        val lineLeft = cx - lineW / 2
        for (i in 0..2) {
            drawRoundRect(
                color = Color(0xFF93C5FD),
                topLeft = Offset(lineLeft, cy - 20.dp.toPx() + (i * 12.dp.toPx())),
                size = Size(if (i == 2) lineW * 0.7f else lineW, 4.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }

        // Verified Shield Badge at lower right
        val badgeX = cx + clipW * 0.28f
        val badgeY = cy + clipH * 0.22f
        drawCircle(
            color = Color(0xFF2563EB),
            radius = 14.dp.toPx(),
            center = Offset(badgeX, badgeY)
        )
        // Checkmark inside shield badge
        val checkStroke = 2.dp.toPx()
        drawLine(
            color = Color.White,
            start = Offset(badgeX - 5.dp.toPx(), badgeY),
            end = Offset(badgeX - 1.dp.toPx(), badgeY + 4.dp.toPx()),
            strokeWidth = checkStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(badgeX - 1.dp.toPx(), badgeY + 4.dp.toPx()),
            end = Offset(badgeX + 6.dp.toPx(), badgeY - 4.dp.toPx()),
            strokeWidth = checkStroke,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Custom Canvas Art for Step 2: GSTIN Document Sheet with verified badge and soft cyan wave
 */
@Composable
private fun GstinDocumentArtwork(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Soft wavy cyan corner in lower left
        val wavePath = Path().apply {
            moveTo(0f, h * 0.5f)
            cubicTo(w * 0.12f, h * 0.42f, w * 0.2f, h * 0.7f, w * 0.35f, h * 0.85f)
            cubicTo(w * 0.5f, h * 0.95f, w * 0.65f, h * 0.98f, w * 0.75f, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = wavePath,
            color = Color(0xFFBAE6FD).copy(alpha = 0.55f)
        )

        // Document card center
        val cx = w * 0.5f
        val cy = h * 0.48f
        val docW = 100.dp.toPx()
        val docH = 78.dp.toPx()

        // Document Shadow
        drawRoundRect(
            color = Color(0x180F172A),
            topLeft = Offset(cx - docW / 2 + 2.dp.toPx(), cy - docH / 2 + 4.dp.toPx()),
            size = Size(docW, docH),
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
        )

        // White document sheet
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(cx - docW / 2, cy - docH / 2),
            size = Size(docW, docH),
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFFE2E8F0),
            topLeft = Offset(cx - docW / 2, cy - docH / 2),
            size = Size(docW, docH),
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )

        // User icon dot at top left
        drawCircle(
            color = Color(0xFF94A3B8),
            radius = 5.dp.toPx(),
            center = Offset(cx - docW * 0.32f, cy - docH * 0.24f)
        )

        // "GSTIN" pill placeholder
        drawRoundRect(
            color = Color(0xFF64748B),
            topLeft = Offset(cx - docW * 0.18f, cy - docH * 0.28f),
            size = Size(36.dp.toPx(), 6.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )

        // Document lines
        drawRoundRect(
            color = Color(0xFFE2E8F0),
            topLeft = Offset(cx - docW * 0.35f, cy),
            size = Size(54.dp.toPx(), 5.dp.toPx()),
            cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFFE2E8F0),
            topLeft = Offset(cx - docW * 0.35f, cy + 10.dp.toPx()),
            size = Size(40.dp.toPx(), 5.dp.toPx()),
            cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
        )

        // Verified checkmark badge at bottom right
        val badgeX = cx + docW * 0.35f
        val badgeY = cy + docH * 0.35f
        drawCircle(
            color = Color.White,
            radius = 16.dp.toPx(),
            center = Offset(badgeX, badgeY)
        )
        drawCircle(
            color = Color(0xFF2563EB),
            radius = 13.dp.toPx(),
            center = Offset(badgeX, badgeY)
        )
        val checkStroke = 2.dp.toPx()
        drawLine(
            color = Color.White,
            start = Offset(badgeX - 5.dp.toPx(), badgeY),
            end = Offset(badgeX - 1.dp.toPx(), badgeY + 4.dp.toPx()),
            strokeWidth = checkStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(badgeX - 1.dp.toPx(), badgeY + 4.dp.toPx()),
            end = Offset(badgeX + 5.dp.toPx(), badgeY - 4.dp.toPx()),
            strokeWidth = checkStroke,
            cap = StrokeCap.Round
        )
    }
}

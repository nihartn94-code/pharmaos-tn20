package com.example.ui.screens.company

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.example.domain.model.MedicineSubmission
import com.example.domain.model.MedicineSubmissionStatus
import com.example.ui.components.CompanyBottomBar
import com.example.ui.components.WorkspaceAccessDeniedScreen
import com.example.util.Formatters

/**
 * Company Approvals Screen implementing the Compulsory Medicine Approval Workflow:
 * - Pending Submissions Queue with submitter, org, date, full formulation specs
 * - Approve action (publishes into Active B2B Catalogue, prevents self-approval by default)
 * - Reject action with mandatory rejection reason
 * - Complete Approval Audit History tab
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyApprovalsScreen(
    viewModel: CompanyWorkspaceViewModel,
    onNavigateHome: () -> Unit,
    onNavigateCatalogue: () -> Unit,
    onNavigateManagement: () -> Unit,
    onNavigateProfile: () -> Unit,
    onReturnToAuthorizedWorkspace: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeTab by remember { mutableIntStateOf(0) }
    var rejectingSubmission by remember { mutableStateOf<MedicineSubmission?>(null) }
    var approvingSubmission by remember { mutableStateOf<MedicineSubmission?>(null) }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    if (uiState.user != null && !uiState.isAuthorized) {
        WorkspaceAccessDeniedScreen(
            reason = "Company Approvals Queue requires MEDICINE_APPROVE / MEDICINE_REJECT permissions.",
            onReturnToAuthorizedWorkspace = onReturnToAuthorizedWorkspace
        )
        return
    }

    val pendingList = uiState.submissions.filter { it.approvalStatus == MedicineSubmissionStatus.PENDING_APPROVAL }
    val reviewedList = uiState.submissions.filter { it.approvalStatus != MedicineSubmissionStatus.PENDING_APPROVAL }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Medicine Approvals", fontWeight = FontWeight.Bold)
                        Text(
                            text = "${pendingList.size} awaiting decision • Audit trail enforced",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            CompanyBottomBar(
                selectedTab = "approvals",
                pendingApprovalsCount = pendingList.size,
                onNavigateHome = onNavigateHome,
                onNavigateCatalogue = onNavigateCatalogue,
                onNavigateApprovals = {},
                onNavigateManagement = onNavigateManagement,
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
                    text = { Text("Pending (${pendingList.size})") },
                    modifier = Modifier.testTag("approvals_tab_pending")
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Decisions (${reviewedList.size})") },
                    modifier = Modifier.testTag("approvals_tab_reviewed")
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Audit Log (${uiState.auditLogs.size})") },
                    modifier = Modifier.testTag("approvals_tab_audit")
                )
            }

            when (activeTab) {
                0 -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("company_pending_approvals_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (pendingList.isEmpty()) {
                        item {
                            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("No Pending Approvals", fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "All Manufacturer & Company medicine submissions have been reviewed.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(pendingList, key = { it.id }) { sub ->
                            val isSelfSubmission = uiState.user?.id == sub.submittedByUserId
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth().testTag("approval_card_${sub.id}")
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(sub.medicineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Text(
                                                text = "PENDING",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Brand: ${sub.brandName} • Generic: ${sub.genericName}\nSalt: ${sub.saltComposition} • ${sub.strength} ${sub.dosageForm} (${sub.packSize})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "MRP ₹${sub.mrp} • PTR ₹${sub.wholesaleRatePtr} • HSN ${sub.hsnCode} • GST ${sub.gstRatePercent}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Submitted by ${sub.submittedByName} (${sub.submittedByRole.displayName}) on ${Formatters.formatDate(sub.submittedAtEpochMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isSelfSubmission) {
                                        Text(
                                            text = "Self-approval policy: Another Company approver must review your own submission.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            enabled = uiState.canApproveMedicines && !isSelfSubmission && !uiState.isSaving,
                                            onClick = { approvingSubmission = sub },
                                            modifier = Modifier.weight(1f).testTag("approve_submission_btn_${sub.id}")
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                                            Text("Approve")
                                        }
                                        OutlinedButton(
                                            enabled = uiState.canRejectMedicines && !uiState.isSaving,
                                            onClick = { rejectingSubmission = sub },
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                            modifier = Modifier.weight(1f).testTag("reject_submission_btn_${sub.id}")
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                            Text("Reject", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }

                1 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(reviewedList, key = { it.id }) { sub ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(sub.medicineName, fontWeight = FontWeight.Bold)
                                    Text(sub.approvalStatus.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                                Text("${sub.brandName} • ${sub.saltComposition}", style = MaterialTheme.typography.bodySmall)
                                sub.reviewedByName?.let { reviewer ->
                                    Text("Reviewed by $reviewer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                sub.rejectionReason?.let { reason ->
                                    Text("Reason: $reason", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.auditLogs, key = { it.id }) { log ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${log.action} (${log.fromStatus} → ${log.toStatus})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                    Text(Formatters.formatDate(log.timestampEpochMillis), style = MaterialTheme.typography.labelSmall)
                                }
                                Text("Actor: ${log.actorName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(log.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    approvingSubmission?.let { sub ->
        var notes by remember { mutableStateOf("Verified salt composition, HSN & regulatory identifiers.") }
        AlertDialog(
            onDismissRequest = { approvingSubmission = null },
            title = { Text("Approve Medicine for Catalogue?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Approving '${sub.medicineName}' makes it ACTIVE and immediately discoverable to Stockists.")
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Approval Audit Notes") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.approveSubmission(sub.id, notes)
                        approvingSubmission = null
                    },
                    modifier = Modifier.testTag("confirm_approve_btn")
                ) { Text("Approve & Publish") }
            },
            dismissButton = { TextButton(onClick = { approvingSubmission = null }) { Text("Cancel") } }
        )
    }

    rejectingSubmission?.let { sub ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectingSubmission = null },
            title = { Text("Reject Submission", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A clear rejection reason is mandatory so the submitter can fix issues and resubmit.")
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Mandatory Rejection Reason*") },
                        minLines = 2,
                        modifier = Modifier.testTag("reject_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = reason.trim().length >= 5,
                    onClick = {
                        viewModel.rejectSubmission(sub.id, reason)
                        rejectingSubmission = null
                    },
                    modifier = Modifier.testTag("confirm_reject_btn")
                ) { Text("Confirm Reject") }
            },
            dismissButton = { TextButton(onClick = { rejectingSubmission = null }) { Text("Cancel") } }
        )
    }
}

package com.example.ui.screens.manufacturer

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.example.domain.model.CampaignStatus
import com.example.domain.model.PromotionalCampaign
import com.example.ui.components.ManufacturerBottomBar
import com.example.ui.components.WorkspaceAccessDeniedScreen
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManufacturerPromotionsScreen(
    viewModel: ManufacturerWorkspaceViewModel,
    onNavigateHome: () -> Unit,
    onNavigateProducts: () -> Unit,
    onNavigateUpdates: () -> Unit,
    onNavigateProfile: () -> Unit,
    onReturnToAuthorizedWorkspace: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
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
            reason = "Promotional Campaigns require CAMPAIGN_MANAGE permission inside a Pharmaceutical Organization.",
            onReturnToAuthorizedWorkspace = onReturnToAuthorizedWorkspace
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Promotional Campaigns", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Wholesale discount schemes, bulk slabs & trade drives",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.canManageCampaigns) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("mfg_create_campaign_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Campaign")
                }
            }
        },
        bottomBar = {
            ManufacturerBottomBar(
                selectedTab = "promotions",
                onNavigateHome = onNavigateHome,
                onNavigateProducts = onNavigateProducts,
                onNavigatePromotions = {},
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
                .testTag("manufacturer_campaigns_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.campaigns.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("No Promotional Campaigns Yet", fontWeight = FontWeight.Bold)
                            Text(
                                text = "Launch bulk purchase slabs and trade schemes to drive Stockist procurement.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.campaigns, key = { it.id }) { campaign ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(campaign.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (campaign.status == CampaignStatus.ACTIVE) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = campaign.status.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(campaign.schemeSummary, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Discount: ${campaign.discountPercent}% • Min Order: ${campaign.minOrderQuantity} units • Redemptions: ${campaign.redemptionsCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Validity: ${Formatters.formatDate(campaign.startDateEpochMillis)} – ${Formatters.formatDate(campaign.endDateEpochMillis)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (campaign.termsAndConditions.isNotBlank()) {
                                Text(
                                    text = "Terms: ${campaign.termsAndConditions}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (campaign.status != CampaignStatus.ACTIVE) {
                                    OutlinedButton(onClick = { viewModel.updateCampaignStatus(campaign.id, CampaignStatus.ACTIVE) }) {
                                        Text("Activate")
                                    }
                                } else {
                                    OutlinedButton(onClick = { viewModel.updateCampaignStatus(campaign.id, CampaignStatus.PAUSED) }) {
                                        Text("Pause")
                                    }
                                }
                                if (campaign.status != CampaignStatus.ARCHIVED) {
                                    OutlinedButton(onClick = { viewModel.updateCampaignStatus(campaign.id, CampaignStatus.ARCHIVED) }) {
                                        Text("Archive")
                                    }
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
        CreateCampaignDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { campaign ->
                viewModel.saveCampaign(campaign) { showCreateDialog = false }
            }
        )
    }
}

@Composable
private fun CreateCampaignDialog(
    onDismiss: () -> Unit,
    onSave: (PromotionalCampaign) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var scheme by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("10") }
    var moq by remember { mutableStateOf("25") }
    var terms by remember { mutableStateOf("Valid on GST-invoiced wholesale purchase orders.") }
    val now = System.currentTimeMillis()
    val dayMs = 24L * 60L * 60L * 1000L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Promotional Campaign", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Campaign Name*") }, singleLine = true)
                OutlinedTextField(value = scheme, onValueChange = { scheme = it }, label = { Text("Wholesale Scheme Summary*") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = discount, onValueChange = { discount = it }, label = { Text("Discount %") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = moq, onValueChange = { moq = it.filter(Char::isDigit) }, label = { Text("MOQ") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(value = terms, onValueChange = { terms = it }, label = { Text("Terms & Conditions") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    PromotionalCampaign(
                        organizationId = 0L,
                        organizationName = "",
                        name = name,
                        schemeSummary = scheme,
                        discountPercent = discount.toDoubleOrNull() ?: 0.0,
                        minOrderQuantity = moq.toIntOrNull() ?: 10,
                        termsAndConditions = terms,
                        startDateEpochMillis = now,
                        endDateEpochMillis = now + (30L * dayMs),
                        status = CampaignStatus.ACTIVE,
                        createdByUserId = 0L
                    )
                )
            }) {
                Text("Launch Campaign")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

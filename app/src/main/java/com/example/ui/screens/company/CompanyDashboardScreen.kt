package com.example.ui.screens.company

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.CompanyDashboardSummary
import com.example.domain.model.WorkspaceType
import com.example.ui.components.CompanyBottomBar
import com.example.ui.components.WorkspaceAccessDeniedScreen
import com.example.ui.components.WorkspaceSwitcherChip
import com.example.ui.components.WorkspaceSwitcherDialog
import com.example.ui.theme.PharmaOSTheme

@Composable
fun CompanyDashboardScreen(
    viewModel: CompanyWorkspaceViewModel,
    onNavigateCatalogue: () -> Unit,
    onNavigateAddMedicine: () -> Unit,
    onNavigateApprovals: () -> Unit,
    onNavigateManagement: () -> Unit,
    onNavigateProfile: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onSwitchToManufacturerWorkspace: () -> Unit,
    onReturnToAuthorizedWorkspace: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (uiState.user != null && !uiState.isAuthorized) {
        WorkspaceAccessDeniedScreen(
            title = "Company Workspace Restricted",
            reason = "Only Pharmaceutical Organization employees with Company Catalogue / Approval permissions can access the Company Workspace.",
            onReturnToAuthorizedWorkspace = onReturnToAuthorizedWorkspace
        )
        return
    }
    CompanyDashboardContent(
        uiState = uiState,
        onNavigateCatalogue = onNavigateCatalogue,
        onNavigateAddMedicine = onNavigateAddMedicine,
        onNavigateApprovals = onNavigateApprovals,
        onNavigateManagement = onNavigateManagement,
        onNavigateProfile = onNavigateProfile,
        onNavigateNotifications = onNavigateNotifications,
        onSwitchToManufacturer = { viewModel.switchToManufacturerWorkspace(onSwitchToManufacturerWorkspace) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyDashboardContent(
    uiState: CompanyWorkspaceUiState,
    onNavigateCatalogue: () -> Unit,
    onNavigateAddMedicine: () -> Unit,
    onNavigateApprovals: () -> Unit,
    onNavigateManagement: () -> Unit,
    onNavigateProfile: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onSwitchToManufacturer: () -> Unit
) {
    var showWorkspaceDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Company Governance",
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.testTag("company_dashboard_title")
                        )
                        Text(
                            text = uiState.summary.organizationName.ifBlank { "Pharmaceutical Organization" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    WorkspaceSwitcherChip(
                        currentWorkspace = WorkspaceType.COMPANY,
                        canSwitch = uiState.canSwitchToManufacturer,
                        onClick = { showWorkspaceDialog = true }
                    )
                    IconButton(
                        onClick = onNavigateNotifications,
                        modifier = Modifier.testTag("company_notifications_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (uiState.unreadNotificationsCount > 0) {
                                    Badge { Text(uiState.unreadNotificationsCount.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                        }
                    }
                }
            )
        },
        bottomBar = {
            CompanyBottomBar(
                selectedTab = "home",
                pendingApprovalsCount = uiState.summary.pendingApprovalsCount,
                onNavigateHome = {},
                onNavigateCatalogue = onNavigateCatalogue,
                onNavigateApprovals = onNavigateApprovals,
                onNavigateManagement = onNavigateManagement,
                onNavigateProfile = onNavigateProfile
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("company_dashboard_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apartment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Catalogue Governance & Approval Authority",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = "Approve or reject Manufacturer medicine submissions with full audit trail, govern active B2B catalogue visibility, maintain salt/dosage master data, and assign team permissions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CompanyKpiCard("Pending Approvals", uiState.summary.pendingApprovalsCount.toString(), "Awaiting review", Modifier.weight(1f), onNavigateApprovals)
                        CompanyKpiCard("Active Catalogue", uiState.summary.activeCatalogueCount.toString(), "${uiState.summary.totalCatalogueCount} total SKUs", Modifier.weight(1f), onNavigateCatalogue)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CompanyKpiCard("Master Data", uiState.summary.masterDataEntriesCount.toString(), "Salts, forms, brands", Modifier.weight(1f), onNavigateManagement)
                        CompanyKpiCard("Org Members", uiState.summary.organizationMembersCount.toString(), "${uiState.summary.rejectedSubmissionsCount} rejected items", Modifier.weight(1f), onNavigateManagement)
                    }
                }
            }

            item {
                Text("Company Core Modules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompanyModuleCard(
                        icon = Icons.Default.Approval,
                        title = "Medicine Approval Queue (${uiState.summary.pendingApprovalsCount} pending)",
                        subtitle = "Review submissions, detect duplicates, approve into live Stockist catalogue or reject with reason",
                        testTag = "company_module_approvals",
                        onClick = onNavigateApprovals
                    )
                    CompanyModuleCard(
                        icon = Icons.Default.AddBox,
                        title = "Add New Medicine",
                        subtitle = "Multi-section pharmaceutical dossier form with duplicate checks, draft & submission",
                        testTag = "company_module_add_medicine",
                        onClick = onNavigateAddMedicine
                    )
                    CompanyModuleCard(
                        icon = Icons.Default.Medication,
                        title = "Medicine Catalogue Management",
                        subtitle = "Search, filter, edit authorized fields, activate, deactivate & archive medicines",
                        testTag = "company_module_catalogue",
                        onClick = onNavigateCatalogue
                    )
                    CompanyModuleCard(
                        icon = Icons.Default.Tune,
                        title = "Master Data & Organization Roles",
                        subtitle = "Manage salt compositions, generics, dosage forms, strengths, brands & team permissions",
                        testTag = "company_module_master_data",
                        onClick = onNavigateManagement
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showWorkspaceDialog) {
        WorkspaceSwitcherDialog(
            organizationName = uiState.summary.organizationName,
            currentWorkspace = WorkspaceType.COMPANY,
            onSelectWorkspace = { target ->
                showWorkspaceDialog = false
                if (target == WorkspaceType.MANUFACTURER) onSwitchToManufacturer()
            },
            onDismiss = { showWorkspaceDialog = false }
        )
    }
}

@Composable
private fun CompanyKpiCard(
    title: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompanyModuleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CompanyDashboardPreview() {
    PharmaOSTheme {
        CompanyDashboardContent(
            uiState = CompanyWorkspaceUiState(
                isAuthorized = true,
                canSwitchToManufacturer = true,
                summary = CompanyDashboardSummary(
                    organizationName = "Bharat Bio-Pharma Formulations",
                    totalCatalogueCount = 16,
                    activeCatalogueCount = 14,
                    pendingApprovalsCount = 3,
                    masterDataEntriesCount = 24,
                    organizationMembersCount = 6
                )
            ),
            onNavigateCatalogue = {},
            onNavigateAddMedicine = {},
            onNavigateApprovals = {},
            onNavigateManagement = {},
            onNavigateProfile = {},
            onNavigateNotifications = {},
            onSwitchToManufacturer = {}
        )
    }
}

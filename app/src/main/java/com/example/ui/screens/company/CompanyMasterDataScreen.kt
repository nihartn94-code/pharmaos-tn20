package com.example.ui.screens.company

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Switch
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
import com.example.domain.model.MasterDataType
import com.example.domain.model.OrganizationMember
import com.example.domain.model.UserRole
import com.example.ui.components.CompanyBottomBar
import com.example.ui.components.WorkspaceAccessDeniedScreen

/**
 * Company Master Data & Organization Team Management Screen.
 * Tab 1: Master Data (Salt Compositions, Generic Medicines, Dosage Forms, Strengths, Brands, Packaging, Classifications)
 * Tab 2: Pharmaceutical Organization Members & Role Assignment (Manufacturer / Company / Dual permissions under one org)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyMasterDataScreen(
    viewModel: CompanyWorkspaceViewModel,
    onNavigateHome: () -> Unit,
    onNavigateCatalogue: () -> Unit,
    onNavigateApprovals: () -> Unit,
    onNavigateProfile: () -> Unit,
    onReturnToAuthorizedWorkspace: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeTab by remember { mutableIntStateOf(0) }
    var selectedType by remember { mutableStateOf(MasterDataType.SALT_COMPOSITION) }
    var showAddMasterDataDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<OrganizationMember?>(null) }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    if (uiState.user != null && !uiState.isAuthorized) {
        WorkspaceAccessDeniedScreen(
            reason = "Master Data & Organization Management requires Company workspace permissions.",
            onReturnToAuthorizedWorkspace = onReturnToAuthorizedWorkspace
        )
        return
    }

    val itemsForType = uiState.masterDataItems.filter { it.type == selectedType }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Master Data & Team", fontWeight = FontWeight.Bold)
                        Text(
                            text = uiState.summary.organizationName.ifBlank { "Pharmaceutical Organization" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (activeTab == 0 && uiState.canManageMasterData) {
                FloatingActionButton(
                    onClick = { showAddMasterDataDialog = true },
                    modifier = Modifier.testTag("company_add_master_data_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Master Data Entry")
                }
            }
        },
        bottomBar = {
            CompanyBottomBar(
                selectedTab = "management",
                pendingApprovalsCount = uiState.summary.pendingApprovalsCount,
                onNavigateHome = onNavigateHome,
                onNavigateCatalogue = onNavigateCatalogue,
                onNavigateApprovals = onNavigateApprovals,
                onNavigateManagement = {},
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
                    text = { Text("Master Data (${uiState.masterDataItems.size})") },
                    modifier = Modifier.testTag("company_tab_master_data")
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Org Members (${uiState.organizationMembers.size})") },
                    modifier = Modifier.testTag("company_tab_members")
                )
            }

            if (activeTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("company_master_data_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MasterDataType.entries.forEach { type ->
                                FilterChip(
                                    selected = selectedType == type,
                                    onClick = { selectedType = type },
                                    label = { Text(type.displayName) }
                                )
                            }
                        }
                    }

                    items(itemsForType, key = { it.id }) { item ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    if (item.description.isNotBlank()) {
                                        Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (item.code.isNotBlank()) {
                                        Text("Code: ${item.code}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (item.isOrganizationScoped) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = if (item.isOrganizationScoped) "Org Scoped" else "Platform Std",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("company_org_members_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "Manufacturer and Company users belong to the same Pharmaceutical Organization. Employees granted both roles can switch workspaces seamlessly without re-login.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(uiState.organizationMembers, key = { it.id }) { member ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(member.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(member.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "Roles: ${member.roles.joinToString(" + ") { it.displayName }} • ${member.permissions.size} permissions",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (uiState.canAssignRoles) {
                                    OutlinedButton(onClick = { editingMember = member }) {
                                        Text("Assign Roles")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddMasterDataDialog) {
        var name by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var orgScoped by remember { mutableStateOf(selectedType == MasterDataType.BRAND) }

        AlertDialog(
            onDismissRequest = { showAddMasterDataDialog = false },
            title = { Text("Add ${selectedType.displayName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name*") }, singleLine = true, modifier = Modifier.testTag("master_data_name_input"))
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Standard Code") }, singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Organization-Scoped Entry", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = orgScoped, onCheckedChange = { orgScoped = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addMasterData(selectedType, name, code, desc, orgScoped) {
                            showAddMasterDataDialog = false
                        }
                    },
                    modifier = Modifier.testTag("master_data_save_btn")
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddMasterDataDialog = false }) { Text("Cancel") } }
        )
    }

    editingMember?.let { member ->
        var hasManufacturer by remember { mutableStateOf(member.roles.contains(UserRole.MANUFACTURER)) }
        var hasCompany by remember { mutableStateOf(member.roles.contains(UserRole.PHARMA_COMPANY)) }

        AlertDialog(
            onDismissRequest = { editingMember = null },
            title = { Text("Assign Roles: ${member.fullName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hasManufacturer, onCheckedChange = { hasManufacturer = it })
                        Text("Manufacturer Workspace Permissions")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hasCompany, onCheckedChange = { hasCompany = it })
                        Text("Company Workspace Permissions")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = hasManufacturer || hasCompany,
                    onClick = {
                        val updatedRoles = buildSet {
                            if (hasManufacturer) add(UserRole.MANUFACTURER)
                            if (hasCompany) add(UserRole.PHARMA_COMPANY)
                        }
                        viewModel.assignMemberRoles(member.id, updatedRoles)
                        editingMember = null
                    }
                ) { Text("Update Roles") }
            },
            dismissButton = { TextButton(onClick = { editingMember = null }) { Text("Cancel") } }
        )
    }
}

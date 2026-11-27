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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
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
import com.example.data.local.entity.ProductEntity
import com.example.domain.model.CataloguePublicationStatus
import com.example.ui.components.CompanyBottomBar
import com.example.ui.components.WorkspaceAccessDeniedScreen

/**
 * Company Medicine Catalogue Management screen.
 * Supports searching, status filtering (ACTIVE / INACTIVE / ARCHIVED), editing authorized fields,
 * and activating/deactivating/archiving medicines WITHOUT hard-deleting referenced products.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyCatalogueScreen(
    viewModel: CompanyWorkspaceViewModel,
    onNavigateHome: () -> Unit,
    onNavigateAddMedicine: () -> Unit,
    onNavigateApprovals: () -> Unit,
    onNavigateManagement: () -> Unit,
    onNavigateProfile: () -> Unit,
    onReturnToAuthorizedWorkspace: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<CataloguePublicationStatus?>(null) }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    if (uiState.user != null && !uiState.isAuthorized) {
        WorkspaceAccessDeniedScreen(
            reason = "Medicine Catalogue Management requires MEDICINE_CATALOGUE_MANAGE permission.",
            onReturnToAuthorizedWorkspace = onReturnToAuthorizedWorkspace
        )
        return
    }

    val filtered = uiState.catalogueProducts.filter { prod ->
        val matchesQuery = searchQuery.isBlank() ||
            prod.name.contains(searchQuery, ignoreCase = true) ||
            prod.brand.contains(searchQuery, ignoreCase = true) ||
            prod.saltComposition.contains(searchQuery, ignoreCase = true)
        val prodStatus = CataloguePublicationStatus.fromString(prod.catalogueStatus)
        val matchesStatus = statusFilter == null || prodStatus == statusFilter
        matchesQuery && matchesStatus
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Company Catalogue", fontWeight = FontWeight.Bold)
                        Text(
                            text = "${uiState.catalogueProducts.size} medicines • Soft-archive preserves order references",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateAddMedicine,
                modifier = Modifier.testTag("company_add_medicine_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add New Medicine")
            }
        },
        bottomBar = {
            CompanyBottomBar(
                selectedTab = "catalogue",
                pendingApprovalsCount = uiState.summary.pendingApprovalsCount,
                onNavigateHome = onNavigateHome,
                onNavigateCatalogue = {},
                onNavigateApprovals = onNavigateApprovals,
                onNavigateManagement = onNavigateManagement,
                onNavigateProfile = onNavigateProfile
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("company_catalogue_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search name, brand or salt composition...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("company_catalogue_search")
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { statusFilter = null },
                        label = { Text("All (${uiState.catalogueProducts.size})") }
                    )
                    CataloguePublicationStatus.entries
                        .filter { it != CataloguePublicationStatus.UNKNOWN }
                        .forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { statusFilter = st },
                            label = { Text(st.displayName) }
                        )
                    }
                }
            }

            items(filtered, key = { it.id }) { prod ->
                val pubStatus = CataloguePublicationStatus.fromString(prod.catalogueStatus)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().testTag("company_catalogue_card_${prod.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prod.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${prod.brand} • ${prod.saltComposition}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (pubStatus) {
                                    CataloguePublicationStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                                    CataloguePublicationStatus.INACTIVE -> MaterialTheme.colorScheme.tertiaryContainer
                                    CataloguePublicationStatus.ARCHIVED -> MaterialTheme.colorScheme.surfaceVariant
                                    CataloguePublicationStatus.UNKNOWN -> MaterialTheme.colorScheme.errorContainer
                                }
                            ) {
                                Text(
                                    text = pubStatus.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = "${prod.strength} • ${prod.dosageForm} • Pack: ${prod.packSize} • HSN ${prod.hsnCode} • GST ${prod.gstRatePercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(onClick = { editingProduct = prod }) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Text("Edit")
                            }
                            if (uiState.canManageCatalogue && pubStatus != CataloguePublicationStatus.ACTIVE) {
                                OutlinedButton(onClick = { viewModel.updateCatalogueStatus(prod.id, CataloguePublicationStatus.ACTIVE) }) {
                                    Text("Activate")
                                }
                            }
                            if (uiState.canManageCatalogue && pubStatus == CataloguePublicationStatus.ACTIVE) {
                                OutlinedButton(onClick = { viewModel.updateCatalogueStatus(prod.id, CataloguePublicationStatus.INACTIVE) }) {
                                    Text("Deactivate")
                                }
                            }
                            if (uiState.canManageCatalogue && pubStatus != CataloguePublicationStatus.ARCHIVED) {
                                OutlinedButton(onClick = { viewModel.updateCatalogueStatus(prod.id, CataloguePublicationStatus.ARCHIVED) }) {
                                    Icon(Icons.Default.Archive, contentDescription = null)
                                    Text("Archive")
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    editingProduct?.let { product ->
        EditCatalogueProductDialog(
            product = product,
            onDismiss = { editingProduct = null },
            onSave = { updated ->
                viewModel.updateCatalogueProduct(updated) { editingProduct = null }
            }
        )
    }
}

@Composable
private fun EditCatalogueProductDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onSave: (ProductEntity) -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var packSize by remember { mutableStateOf(product.packSize) }
    var hsn by remember { mutableStateOf(product.hsnCode) }
    var gst by remember { mutableStateOf(product.gstRatePercent.toString()) }
    var description by remember { mutableStateOf(product.description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Catalogue Medicine", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Medicine Name") }, singleLine = true)
                OutlinedTextField(value = packSize, onValueChange = { packSize = it }, label = { Text("Pack Size") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = hsn, onValueChange = { hsn = it }, label = { Text("HSN Code") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = gst, onValueChange = { gst = it }, label = { Text("GST %") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    product.copy(
                        name = name.trim(),
                        packSize = packSize.trim(),
                        hsnCode = hsn.trim(),
                        gstRatePercent = gst.toDoubleOrNull() ?: product.gstRatePercent,
                        description = description.trim()
                    )
                )
            }) { Text("Save Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

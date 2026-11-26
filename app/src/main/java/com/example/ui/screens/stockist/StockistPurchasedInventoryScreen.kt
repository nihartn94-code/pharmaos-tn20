package com.example.ui.screens.stockist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.domain.model.StockistPurchasedInventoryItem
import com.example.ui.components.WorkspaceAccessDeniedScreen
import com.example.util.Formatters

/**
 * Strictly isolated Stockist Purchased Inventory screen.
 * Operates exclusively on `stockist_purchased_inventory` / `/api/v1/stockist/purchased-inventory`.
 * NEVER exposes Manufacturer real-time warehouse stock or factory batches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockistPurchasedInventoryScreen(
    viewModel: StockistInventoryViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    if (uiState.user != null && !uiState.isAuthorized) {
        WorkspaceAccessDeniedScreen(
            title = "Stockist Inventory Restricted",
            reason = "Purchased stock management is available only to authorized Stockist accounts with STOCKIST_INVENTORY_MANAGE permission.",
            onReturnToAuthorizedWorkspace = onNavigateBack
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My Purchased Inventory", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Stockist-Owned Stock • Isolated from Manufacturer Plants",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("stockist_inv_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("stockist_inv_add_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Record Purchased Stock")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("stockist_purchased_inventory_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Private Stockist Ledger: Tracks only stock purchased by your distributorship. Manufacturer plant inventory remains completely isolated.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StockistMetricTile("SKUs", uiState.items.size.toString(), Modifier.weight(1f))
                    StockistMetricTile("Units On Hand", uiState.totalUnitsInStock.toString(), Modifier.weight(1f))
                    StockistMetricTile("Low Stock", uiState.lowStockCount.toString(), Modifier.weight(1f))
                }
            }

            if (uiState.items.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text("No Purchased Stock Recorded Yet", fontWeight = FontWeight.Bold)
                            Text(
                                text = "Tap + to record delivered batches into your private Stockist inventory.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.items, key = { it.id }) { item ->
                    StockistPurchasedInventoryCard(
                        item = item,
                        onAdjust = { delta ->
                            viewModel.adjustRemainingQuantity(item.id, (item.remainingQuantity + delta).coerceAtLeast(0))
                        },
                        onDelete = { viewModel.deleteItem(item.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showAddDialog) {
        AddStockistInventoryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { item ->
                viewModel.saveItem(item) { showAddDialog = false }
            }
        )
    }
}

@Composable
private fun StockistMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StockistPurchasedInventoryCard(
    item: StockistPurchasedInventoryItem,
    onAdjust: (Int) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (item.isLowStock) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stockist_inv_card_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.productName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${item.brand} • ${item.saltComposition}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.isLowStock) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = "Low Stock",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Batch: ${item.batchNumber}", style = MaterialTheme.typography.labelMedium)
                Text("Exp: ${Formatters.formatDate(item.expiryDateEpochMillis)}", style = MaterialTheme.typography.labelMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Remaining: ${item.remainingQuantity} / ${item.purchasedQuantity} (Reorder ≤ ${item.reorderLevel})",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onAdjust(-5) }) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease stock by 5")
                    }
                    IconButton(onClick = { onAdjust(5) }) {
                        Icon(Icons.Default.Add, contentDescription = "Increase stock by 5")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete entry",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddStockistInventoryDialog(
    onDismiss: () -> Unit,
    onSave: (StockistPurchasedInventoryItem) -> Unit
) {
    var productName by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var salt by remember { mutableStateOf("") }
    var batch by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("50") }
    var reorder by remember { mutableStateOf("15") }
    var unitPrice by remember { mutableStateOf("45.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Purchased Stock", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = productName, onValueChange = { productName = it }, label = { Text("Medicine Name*") }, singleLine = true)
                OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Brand") }, singleLine = true)
                OutlinedTextField(value = salt, onValueChange = { salt = it }, label = { Text("Salt Composition") }, singleLine = true)
                OutlinedTextField(value = batch, onValueChange = { batch = it }, label = { Text("Batch Number*") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it.filter(Char::isDigit) }, label = { Text("Qty") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = reorder, onValueChange = { reorder = it.filter(Char::isDigit) }, label = { Text("Reorder") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(value = unitPrice, onValueChange = { unitPrice = it }, label = { Text("Purchase Rate (₹)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val quantity = qty.toIntOrNull() ?: 0
                    onSave(
                        StockistPurchasedInventoryItem(
                            stockistOrganizationId = 0L,
                            stockistUserId = 0L,
                            productId = 0L,
                            productName = productName,
                            brand = brand.ifBlank { "General" },
                            saltComposition = salt.ifBlank { productName },
                            batchNumber = batch,
                            expiryDateEpochMillis = System.currentTimeMillis() + (180L * 24L * 60L * 60L * 1000L),
                            purchasedQuantity = quantity,
                            remainingQuantity = quantity,
                            reorderLevel = reorder.toIntOrNull() ?: 15,
                            purchaseUnitPrice = unitPrice.toDoubleOrNull() ?: 0.0
                        )
                    )
                },
                modifier = Modifier.testTag("stockist_inv_save_btn")
            ) {
                Text("Save Stock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

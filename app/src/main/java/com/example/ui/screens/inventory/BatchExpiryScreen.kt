package com.example.ui.screens.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.domain.model.StorageCondition
import com.example.domain.model.UserRole
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyState
import com.example.ui.components.ExpiryChip
import com.example.ui.components.LoadingState
import com.example.ui.components.PriceText
import com.example.ui.components.StorageConditionBadge
import com.example.util.Formatters
import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.theme.PharmaOSTheme

@Composable
fun BatchExpiryScreen(
    viewModel: InventoryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRecalls: () -> Unit,
    onAddBatchClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BatchExpiryContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToRecalls = onNavigateToRecalls,
        onAddBatchClick = onAddBatchClick,
        onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
        onFilterSelected = { viewModel.setFilter(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchExpiryContent(
    uiState: InventoryUiState,
    onNavigateBack: () -> Unit,
    onNavigateToRecalls: () -> Unit,
    onAddBatchClick: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onFilterSelected: (BatchFilter) -> Unit
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Batch & Expiry Management",
                subtitle = "${uiState.filteredBatches.size} batches in warehouse",
                onBackClick = onNavigateBack,
                actions = {
                    TextButton(
                        onClick = onNavigateToRecalls,
                        modifier = Modifier.testTag("recalls_button")
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Recalls (${uiState.recalls.size})", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddBatchClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_batch_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Batch")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onAddBatchClick() }
                    .testTag("open_live_inventory_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Real-Time Inventory Dashboard", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("View live Room stock levels, out-of-stock & critical alerts", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { onSearchQueryChanged(it) },
                placeholder = { Text("Search by Batch Number, Medicine, Brand...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("batch_search_input")
            )

            // Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BatchFilter.values()) { f ->
                    FilterChip(
                        selected = uiState.selectedFilter == f,
                        onClick = { onFilterSelected(f) },
                        label = { Text(f.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoading) {
                LoadingState(message = "Loading batches...")
            } else if (uiState.filteredBatches.isEmpty()) {
                EmptyState(
                    title = "No Batches Found",
                    message = if (uiState.allBatches.isEmpty()) "Batches are auto-created when orders are delivered, or you can record lots manually." else "No batches match filter criteria.",
                    actionLabel = "Add Batch",
                    onActionClick = onAddBatchClick
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.filteredBatches, key = { it.batch.id }) { item ->
                        val b = item.batch
                        val storage = try { StorageCondition.valueOf(b.storageCondition) } catch (_: Exception) { StorageCondition.ROOM_TEMPERATURE }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("batch_card_${b.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Batch: ${b.batchNumber}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${item.productName} (${item.brand})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    ExpiryChip(daysUntilExpiry = item.daysUntilExpiry)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Remaining: ${b.remainingQuantity} / ${b.initialQuantity} units", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("Expiry: ${Formatters.formatDate(b.expiryDateEpochMillis)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StorageConditionBadge(storageCondition = storage)
                                    PriceText(amount = b.ptr, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Batch Expiry - Light Mode", showBackground = true)
@Preview(name = "Batch Expiry - Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun BatchExpiryScreenPreview() {
    PharmaOSTheme {
        BatchExpiryContent(
            uiState = InventoryUiState(
                isLoading = false,
                filteredBatches = listOf(
                    com.example.domain.repository.BatchWithProduct(
                        batch = com.example.data.local.entity.BatchEntity(
                            id = 1L,
                            productId = 1L,
                            supplierId = 1L,
                            batchNumber = "AUG-24B01",
                            mfgDateEpochMillis = System.currentTimeMillis() - 86400000L * 60,
                            expiryDateEpochMillis = System.currentTimeMillis() + 86400000L * 24,
                            initialQuantity = 500,
                            remainingQuantity = 48,
                            storageCondition = "ROOM_TEMPERATURE",
                            mrp = 201.0,
                            ptr = 168.5
                        ),
                        productName = "Amoxicillin & Clavulanate",
                        brand = "Augmentin 625 Duo",
                        saltComposition = "Amoxicillin + Clavulanic Acid",
                        daysUntilExpiry = 24
                    )
                ),
                selectedFilter = BatchFilter.ALL
            ),
            onNavigateBack = {},
            onNavigateToRecalls = {},
            onAddBatchClick = {},
            onSearchQueryChanged = {},
            onFilterSelected = {}
        )
    }
}


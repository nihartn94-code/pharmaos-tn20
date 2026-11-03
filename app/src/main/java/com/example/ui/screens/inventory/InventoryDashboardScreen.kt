package com.example.ui.screens.inventory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.BatchEntity
import com.example.domain.model.ExpiryRiskStatus
import com.example.domain.model.InventoryDashboardSummary
import com.example.domain.model.InventoryStockItem
import com.example.domain.model.StockLevelStatus
import com.example.domain.model.StorageCondition
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.components.StorageConditionBadge
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryDashboardScreen(
    viewModel: InventoryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRecalls: () -> Unit,
    onNavigateToBasket: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var itemToAdjust by remember { mutableStateOf<InventoryStockItem?>(null) }
    var showAddBatchDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Real-Time Inventory",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Room State: ${uiState.inventorySummary.totalStockUnits} units across ${uiState.inventorySummary.totalSkusCount} SKUs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Recalls badge button
                    IconButton(
                        onClick = onNavigateToRecalls,
                        modifier = Modifier.testTag("recalls_quick_action")
                    ) {
                        if (uiState.recalls.isNotEmpty()) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = Color(0xFFDC2626)) {
                                        Text("${uiState.recalls.size}")
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Recalls",
                                    tint = Color(0xFFDC2626)
                                )
                            }
                        } else {
                            Icon(Icons.Default.Warning, contentDescription = "Recalls")
                        }
                    }

                    // Cart quick button
                    IconButton(onClick = onNavigateToBasket) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = "Cart / Orders")
                    }

                    // Sort menu anchor
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Sort Inventory")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            InventorySort.values().forEach { sortOption ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = sortOption.label,
                                                fontWeight = if (uiState.selectedSort == sortOption) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (uiState.selectedSort == sortOption) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.onInventorySortChanged(sortOption)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddBatchDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_stock_batch_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Inward Stock", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // KPI Summary Row
            InventorySummaryCardsRow(
                summary = uiState.inventorySummary,
                onCardClick = { tab -> viewModel.onInventoryTabChanged(tab) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search by Medicine, Brand, Salt, Batch Lot...") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("inventory_search_input")
            )

            // Filter Tabs Row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(InventoryTab.values()) { tab ->
                    val isSelected = uiState.selectedTab == tab
                    val count = when (tab) {
                        InventoryTab.ALL -> uiState.inventorySummary.totalSkusCount
                        InventoryTab.CRITICAL -> uiState.inventorySummary.criticalItemsCount
                        InventoryTab.OUT_OF_STOCK -> uiState.inventorySummary.outOfStockCount
                        InventoryTab.LOW_STOCK -> uiState.inventorySummary.lowStockCount
                        InventoryTab.EXPIRING_SOON -> uiState.inventorySummary.nearExpiryCount
                        InventoryTab.COLD_CHAIN -> uiState.inventoryItems.count {
                            it.storageCondition == StorageCondition.REFRIGERATED_2_8 ||
                                it.storageCondition == StorageCondition.FROZEN
                        }
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onInventoryTabChanged(tab) },
                        label = {
                            Text("${tab.label} ($count)")
                        },
                        modifier = Modifier.testTag("inventory_tab_${tab.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Content List
            if (uiState.isLoading && uiState.inventoryItems.isEmpty()) {
                LoadingState(message = "Reading real-time inventory from Room database...")
            } else if (uiState.filteredInventoryItems.isEmpty()) {
                EmptyState(
                    title = "No Inventory Matches",
                    message = if (uiState.searchQuery.isNotBlank()) "No medicines match '${uiState.searchQuery}' in this category."
                    else "No stock records found for this filter tab.",
                    actionLabel = "Clear Filter",
                    onActionClick = {
                        viewModel.onSearchQueryChanged("")
                        viewModel.onInventoryTabChanged(InventoryTab.ALL)
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.filteredInventoryItems, key = { "${it.productId}_${it.batchNumber}_${it.id}" }) { item ->
                        InventoryStockItemCard(
                            item = item,
                            onAdjustStock = { itemToAdjust = item },
                            onQuickRestock = { viewModel.quickRestock(item) }
                        )
                    }
                }
            }
        }
    }

    // Dialog for stock adjustment (Direct Room update)
    itemToAdjust?.let { item ->
        StockAdjustmentDialog(
            item = item,
            onDismiss = { itemToAdjust = null },
            onConfirmAdjustment = { newQty, reason ->
                viewModel.adjustBatchStock(
                    batchId = item.id,
                    newQuantity = newQty,
                    reason = reason,
                    onDone = { itemToAdjust = null }
                )
            }
        )
    }

    // Dialog for adding a new inward batch to Room
    if (showAddBatchDialog) {
        AddStockBatchDialog(
            catalogue = uiState.catalogueProducts,
            onDismiss = { showAddBatchDialog = false },
            onAddBatch = { batch ->
                viewModel.createBatch(batch) {
                    showAddBatchDialog = false
                }
            }
        )
    }
}

@Composable
private fun InventorySummaryCardsRow(
    summary: InventoryDashboardSummary,
    onCardClick: (InventoryTab) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Total Valuation Card
        SummaryCard(
            title = "Inventory Valuation",
            value = Formatters.formatInr(summary.totalInventoryValue),
            subtitle = "${summary.totalStockUnits} total units",
            accentColor = Color(0xFF2563EB),
            containerColor = Color(0xFFEFF6FF),
            onClick = { onCardClick(InventoryTab.ALL) }
        )

        // Critical Attention Flag Card
        SummaryCard(
            title = "🚨 Critical Flags",
            value = "${summary.criticalItemsCount} Items",
            subtitle = "Needs urgent action",
            accentColor = Color(0xFFDC2626),
            containerColor = Color(0xFFFEF2F2),
            onClick = { onCardClick(InventoryTab.CRITICAL) }
        )

        // Out of Stock Card
        SummaryCard(
            title = "Out of Stock",
            value = "${summary.outOfStockCount} SKUs",
            subtitle = "0 units remaining",
            accentColor = Color(0xFFEA580C),
            containerColor = Color(0xFFFFF7ED),
            onClick = { onCardClick(InventoryTab.OUT_OF_STOCK) }
        )

        // Low Stock Card
        SummaryCard(
            title = "Low Stock Alert",
            value = "${summary.lowStockCount} SKUs",
            subtitle = "≤ 25 units reorder limit",
            accentColor = Color(0xFFD97706),
            containerColor = Color(0xFFFFFBEB),
            onClick = { onCardClick(InventoryTab.LOW_STOCK) }
        )

        // Near Expiry Card
        SummaryCard(
            title = "Near Expiry / Expired",
            value = "${summary.nearExpiryCount} Lots",
            subtitle = "≤ 90 days or expired",
            accentColor = Color(0xFF7C3AED),
            containerColor = Color(0xFFF5F3FF),
            onClick = { onCardClick(InventoryTab.EXPIRING_SOON) }
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    containerColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        modifier = Modifier
            .clickable(onClick = onClick)
            .width(160.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InventoryStockItemCard(
    item: InventoryStockItem,
    onAdjustStock: () -> Unit,
    onQuickRestock: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inventory_item_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isCritical) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.2.dp,
            if (item.isCritical) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Medicine Name, Brand, and Stock Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.productName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${item.brand} • ${item.dosageForm} • ${item.therapeuticCategory}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Stock Level Badge
                StockStatusBadge(status = item.stockStatus)
            }

            // Salt composition
            if (item.saltComposition.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.saltComposition,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Critical Flags Notification Box (if any)
            if (item.criticalFlags.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (item.stockStatus == StockLevelStatus.OUT_OF_STOCK || item.isRecalled) {
                        Color(0xFFFEE2E2)
                    } else {
                        Color(0xFFFEF3C7)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        item.criticalFlags.forEach { flag ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 1.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (item.stockStatus == StockLevelStatus.OUT_OF_STOCK || item.isRecalled) Color(0xFFDC2626) else Color(0xFFD97706),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = flag,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.stockStatus == StockLevelStatus.OUT_OF_STOCK || item.isRecalled) Color(0xFF991B1B) else Color(0xFF92400E)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Stock Level Visual Progress Meter
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Warehouse Stock Level",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${item.remainingQuantity} / ${item.initialQuantity.coerceAtLeast(item.remainingQuantity)} units",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (item.stockStatus) {
                            StockLevelStatus.OUT_OF_STOCK -> Color(0xFFDC2626)
                            StockLevelStatus.CRITICAL_LOW -> Color(0xFFEA580C)
                            StockLevelStatus.LOW_STOCK -> Color(0xFFD97706)
                            StockLevelStatus.HEALTHY -> Color(0xFF16A34A)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { item.stockRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when (item.stockStatus) {
                        StockLevelStatus.OUT_OF_STOCK -> Color(0xFFDC2626)
                        StockLevelStatus.CRITICAL_LOW -> Color(0xFFEA580C)
                        StockLevelStatus.LOW_STOCK -> Color(0xFFD97706)
                        StockLevelStatus.HEALTHY -> Color(0xFF16A34A)
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Batch Details & Storage Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = "Lot: ${item.batchNumber}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    StorageConditionBadge(storageCondition = item.storageCondition)
                }

                // Expiry chip
                ExpiryRiskBadge(item = item)
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 0.8.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Pricing and Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Valuation: ${Formatters.formatInr(item.totalValuation)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "PTR: ${Formatters.formatInr(item.ptr)} / unit",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Adjust stock button (only for real batches)
                    if (item.id > 0) {
                        OutlinedButton(
                            onClick = onAdjustStock,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Adjust", fontSize = 12.sp)
                        }
                    }

                    // Quick restock button
                    Button(
                        onClick = onQuickRestock,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restock", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StockStatusBadge(status: StockLevelStatus) {
    val (bg, fg) = when (status) {
        StockLevelStatus.OUT_OF_STOCK -> Color(0xFFFEE2E2) to Color(0xFFDC2626)
        StockLevelStatus.CRITICAL_LOW -> Color(0xFFFFF7ED) to Color(0xFFEA580C)
        StockLevelStatus.LOW_STOCK -> Color(0xFFFFFBEB) to Color(0xFFD97706)
        StockLevelStatus.HEALTHY -> Color(0xFFDCFCE7) to Color(0xFF16A34A)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.35f))
    ) {
        Text(
            text = status.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ExpiryRiskBadge(item: InventoryStockItem) {
    if (item.batchNumber == "NO-LOT") return

    val (bg, fg, label) = when {
        item.daysUntilExpiry < 0 -> Triple(Color(0xFFFEE2E2), Color(0xFFB91C1C), "Expired")
        item.daysUntilExpiry <= 30 -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "${item.daysUntilExpiry}d left")
        item.daysUntilExpiry <= 90 -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), "${item.daysUntilExpiry}d left")
        else -> Triple(Color(0xFFF1F5F9), Color(0xFF475569), Formatters.formatDate(item.expiryDateEpochMillis))
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = "Exp: $label",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun StockAdjustmentDialog(
    item: InventoryStockItem,
    onDismiss: () -> Unit,
    onConfirmAdjustment: (newQuantity: Int, reason: String) -> Unit
) {
    var quantityText by remember { mutableStateOf(item.remainingQuantity.toString()) }
    var selectedReason by remember { mutableStateOf("Physical Warehouse Audit") }

    val reasons = listOf(
        "Physical Warehouse Audit",
        "Stock Intake / Inward Shipment",
        "Damage / Breakage Adjustment",
        "Expired / Scrap Quarantine",
        "Internal Inventory Transfer"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Adjust Stock Level",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "${item.productName} • Lot ${item.batchNumber}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Current registered in Room: ${item.remainingQuantity} units",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Quantity Input with Stepper Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            val curr = quantityText.toIntOrNull() ?: item.remainingQuantity
                            val next = (curr - 10).coerceAtLeast(0)
                            quantityText = next.toString()
                        }
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("-10", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            val curr = quantityText.toIntOrNull() ?: item.remainingQuantity
                            val next = (curr - 1).coerceAtLeast(0)
                            quantityText = next.toString()
                        }
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Remove, contentDescription = "-1")
                            }
                        }
                    }

                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it.filter { char -> char.isDigit() } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(100.dp)
                    )

                    IconButton(
                        onClick = {
                            val curr = quantityText.toIntOrNull() ?: item.remainingQuantity
                            quantityText = (curr + 1).toString()
                        }
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, contentDescription = "+1")
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            val curr = quantityText.toIntOrNull() ?: item.remainingQuantity
                            quantityText = (curr + 10).toString()
                        }
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("+10", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Select Audit / Modification Reason:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                reasons.forEach { r ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedReason == r) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { selectedReason = r }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedReason == r) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = r,
                                fontSize = 12.sp,
                                fontWeight = if (selectedReason == r) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedReason == r) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalQty = quantityText.toIntOrNull() ?: item.remainingQuantity
                    onConfirmAdjustment(finalQty, selectedReason)
                }
            ) {
                Text("Confirm Adjustment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStockBatchDialog(
    catalogue: List<com.example.domain.model.ProductDetail>,
    onDismiss: () -> Unit,
    onAddBatch: (BatchEntity) -> Unit
) {
    var selectedProductId by remember { mutableStateOf(catalogue.firstOrNull()?.id ?: 1L) }
    var batchNumber by remember { mutableStateOf("LOT-${System.currentTimeMillis().toString().takeLast(5)}") }
    var quantityText by remember { mutableStateOf("100") }
    var ptrText by remember { mutableStateOf("120.00") }
    var mrpText by remember { mutableStateOf("160.00") }
    var storageCondition by remember { mutableStateOf(StorageCondition.ROOM_TEMPERATURE) }
    var expiryDaysText by remember { mutableStateOf("365") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Inward New Stock Batch", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Select Medicine:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(catalogue) { prod ->
                        FilterChip(
                            selected = selectedProductId == prod.id,
                            onClick = { selectedProductId = prod.id },
                            label = { Text(prod.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = batchNumber,
                    onValueChange = { batchNumber = it },
                    label = { Text("Batch / Lot Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter { c -> c.isDigit() } },
                    label = { Text("Initial Stock Units") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ptrText,
                        onValueChange = { ptrText = it },
                        label = { Text("PTR (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = mrpText,
                        onValueChange = { mrpText = it },
                        label = { Text("MRP (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = expiryDaysText,
                    onValueChange = { expiryDaysText = it.filter { c -> c.isDigit() } },
                    label = { Text("Days until expiry") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantityText.toIntOrNull() ?: 100
                    val ptr = ptrText.toDoubleOrNull() ?: 100.0
                    val mrp = mrpText.toDoubleOrNull() ?: 150.0
                    val days = expiryDaysText.toLongOrNull() ?: 365L
                    val expiryEpoch = System.currentTimeMillis() + (days * 86400000L)

                    val newBatch = BatchEntity(
                        productId = selectedProductId,
                        supplierId = 1L,
                        batchNumber = batchNumber.ifBlank { "LOT-001" },
                        mfgDateEpochMillis = System.currentTimeMillis() - 86400000L * 30,
                        expiryDateEpochMillis = expiryEpoch,
                        initialQuantity = qty,
                        remainingQuantity = qty,
                        storageCondition = storageCondition.name,
                        mrp = mrp,
                        ptr = ptr
                    )
                    onAddBatch(newBatch)
                }
            ) {
                Text("Save to Room DB")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

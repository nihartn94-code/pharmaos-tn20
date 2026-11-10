package com.example.ui.screens.products

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.ProductInventoryEntity

/**
 * Jetpack Compose screen that lists products from the Room database using [ProductRepository],
 * with real-time stock and reorder threshold monitoring, and an 'Add Product' button with dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    viewModel: ProductListViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler {
        onNavigateBack()
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Products & Inventory",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Room Database • SKU & Stock Thresholds",
                            fontSize = 11.5.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("product_list_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A)
                        )
                    }
                },
                actions = {
                    // Quick count badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${uiState.totalProductsCount} SKUs",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddProductDialog() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Product"
                    )
                },
                text = {
                    Text(
                        text = "Add Product",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                },
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Color(0x662563EB))
                    .testTag("add_product_button")
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Metrics Summary Bar
            InventoryMetricsHeader(
                totalProducts = uiState.totalProductsCount,
                totalUnits = uiState.totalStockUnits,
                lowStockCount = uiState.lowStockCount,
                outOfStockCount = uiState.outOfStockCount
            )

            // Search Bar & Filter Chips
            SearchAndFilterSection(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                selectedFilter = uiState.selectedFilter,
                onFilterSelect = { viewModel.onFilterSelect(it) }
            )

            // Product List
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF2563EB))
                }
            } else if (uiState.filteredProducts.isEmpty()) {
                EmptyProductsView(
                    searchQuery = uiState.searchQuery,
                    onAddProductClick = { viewModel.openAddProductDialog() }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .testTag("products_list_view"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = uiState.filteredProducts,
                        key = { it.sku }
                    ) { item ->
                        ProductInventoryCard(
                            item = item,
                            onStockIncrement = { viewModel.adjustStock(item.sku, +1) },
                            onStockDecrement = { viewModel.adjustStock(item.sku, -1) },
                            onDelete = { viewModel.deleteProduct(item.sku) }
                        )
                    }

                    // Spacer at bottom to accommodate FAB
                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }

    // Add Product Dialog
    if (uiState.isAddDialogOpen) {
        AddProductDialog(
            onDismiss = { viewModel.closeAddProductDialog() },
            onConfirm = { name, sku, stock, threshold, form, pack, salt, ptr, mrp ->
                viewModel.addProduct(
                    productName = name,
                    sku = sku,
                    currentStock = stock,
                    reorderThreshold = threshold,
                    dosageForm = form,
                    packSize = pack,
                    saltComposition = salt,
                    unitPricePtr = ptr,
                    mrp = mrp
                )
            }
        )
    }
}

/**
 * Top metrics overview card showing total stock, SKU count, and low-stock alerts.
 */
@Composable
private fun InventoryMetricsHeader(
    totalProducts: Int,
    totalUnits: Int,
    lowStockCount: Int,
    outOfStockCount: Int
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricStatItem(
                label = "Total SKUs",
                value = "$totalProducts",
                color = Color(0xFF0F172A)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(Color(0xFFE2E8F0))
            )

            MetricStatItem(
                label = "Total Units",
                value = "$totalUnits",
                color = Color(0xFF2563EB)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(Color(0xFFE2E8F0))
            )

            MetricStatItem(
                label = "Low Stock",
                value = "$lowStockCount",
                color = if (lowStockCount > 0) Color(0xFFD97706) else Color(0xFF10B981)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(Color(0xFFE2E8F0))
            )

            MetricStatItem(
                label = "Depleted",
                value = "$outOfStockCount",
                color = if (outOfStockCount > 0) Color(0xFFDC2626) else Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun MetricStatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFF64748B)
        )
    }
}

/**
 * Search text field and filter chips section.
 */
@Composable
private fun SearchAndFilterSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: ProductInventoryFilter,
    onFilterSelect: (ProductInventoryFilter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = "Search product name, SKU, or salt...",
                    fontSize = 13.5.sp,
                    color = Color(0xFF94A3B8)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFF2563EB),
                unfocusedBorderColor = Color(0xFFCBD5E1)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_products_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProductInventoryFilter.entries.forEach { filter ->
                val isSelected = filter == selectedFilter
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterSelect(filter) },
                    label = {
                        Text(
                            text = filter.displayName,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    leadingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFDBEAFE),
                        selectedLabelColor = Color(0xFF1D4ED8),
                        containerColor = Color.White,
                        labelColor = Color(0xFF475569)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF3B82F6) else Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                )
            }
        }
    }
}

/**
 * Individual product inventory card displaying product name, SKU, current stock,
 * reorder threshold, status badge, and stock adjustment buttons.
 */
@Composable
private fun ProductInventoryCard(
    item: ProductInventoryEntity,
    onStockIncrement: () -> Unit,
    onStockDecrement: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isOutOfStock = item.currentStock <= 0
    val isLowStock = item.currentStock <= item.reorderThreshold && !isOutOfStock

    val statusBadgeColor = when {
        isOutOfStock -> Color(0xFFFEE2E2)
        isLowStock -> Color(0xFFFEF3C7)
        else -> Color(0xFFDCFCE7)
    }

    val statusTextColor = when {
        isOutOfStock -> Color(0xFFB91C1C)
        isLowStock -> Color(0xFFB45309)
        else -> Color(0xFF15803D)
    }

    val statusLabel = when {
        isOutOfStock -> "OUT OF STOCK"
        isLowStock -> "LOW STOCK ALERT"
        else -> "IN STOCK"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(
            1.dp,
            if (isLowStock || isOutOfStock) Color(0xFFFCA5A5) else Color(0xFFE2E8F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_card_${item.sku}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Product Name + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.productName,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // SKU Code Badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            Text(
                                text = item.sku,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF334155),
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("product_sku_${item.sku}")
                            )
                        }

                        if (item.dosageForm.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Text(
                                    text = item.dosageForm,
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Status Chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBadgeColor,
                    border = BorderStroke(1.dp, statusTextColor.copy(alpha = 0.3f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        if (isLowStock || isOutOfStock) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = statusTextColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = statusLabel,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusTextColor
                        )
                    }
                }
            }

            // Salt composition if available
            if (item.saltComposition.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.saltComposition,
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stock Level Comparison Bar
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Current Stock display
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Current Stock: ",
                                fontSize = 12.5.sp,
                                color = Color(0xFF475569)
                            )
                            Text(
                                text = "${item.currentStock} units",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLowStock || isOutOfStock) Color(0xFFDC2626) else Color(0xFF0F172A),
                                modifier = Modifier.testTag("stock_value_${item.sku}")
                            )
                        }

                        // Reorder Threshold display
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Alert Threshold: ",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "${item.reorderThreshold} units",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF334155),
                                modifier = Modifier.testTag("threshold_value_${item.sku}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Progress bar showing ratio of current stock to reorder threshold
                    val maxMeter = maxOf(item.reorderThreshold * 2, maxOf(item.currentStock, 1))
                    val progress = (item.currentStock.toFloat() / maxMeter.toFloat()).coerceIn(0f, 1f)

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = when {
                            isOutOfStock -> Color(0xFFDC2626)
                            isLowStock -> Color(0xFFF59E0B)
                            else -> Color(0xFF10B981)
                        },
                        trackColor = Color(0xFFE2E8F0),
                        strokeCap = StrokeCap.Round
                    )

                    if (isLowStock) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚠ Deficit of ${item.reorderThreshold - item.currentStock} units below reorder safety stock.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFB45309)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Quick Stock Adjustment Controls & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete button
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_delete_product_${item.sku}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete product",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Stock Adjustment Stepper
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Adjust Stock:",
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B)
                    )

                    // Minus button
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(enabled = item.currentStock > 0) { onStockDecrement() }
                            .testTag("btn_deduct_stock_${item.sku}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Deduct 1 unit",
                                tint = if (item.currentStock > 0) Color(0xFF334155) else Color(0xFFCBD5E1),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Plus button
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onStockIncrement() }
                            .testTag("btn_add_stock_${item.sku}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add 1 unit",
                                tint = Color(0xFF1D4ED8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "Delete Product?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove '${item.productName}' (${item.sku}) from the Room database?",
                    fontSize = 13.5.sp,
                    color = Color(0xFF475569)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Empty state view when no products match search query or database is empty.
 */
@Composable
private fun EmptyProductsView(
    searchQuery: String,
    onAddProductClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFEFF6FF),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Medication,
                        contentDescription = null,
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (searchQuery.isNotBlank()) "No Matching Products" else "No Products in Room Database",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (searchQuery.isNotBlank())
                    "No product matched '$searchQuery'. Try adjusting your search query."
                else
                    "Your inventory is currently empty. Tap 'Add Product' to insert medicines with stock & reorder thresholds.",
                fontSize = 13.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(horizontal = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onAddProductClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add First Product")
            }
        }
    }
}

/**
 * Interactive 'Add Product' Dialog with input fields for:
 * 1. Product Name
 * 2. SKU
 * 3. Current Stock
 * 4. Reorder Threshold
 * plus optional dosage form and pricing metadata.
 */
@Composable
fun AddProductDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        sku: String,
        stock: Int,
        threshold: Int,
        dosageForm: String,
        packSize: String,
        saltComposition: String,
        ptr: Double,
        mrp: Double
    ) -> Unit
) {
    var productName by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var currentStockText by remember { mutableStateOf("") }
    var reorderThresholdText by remember { mutableStateOf("") }
    var dosageForm by remember { mutableStateOf("Tablet") }
    var packSize by remember { mutableStateOf("Strip of 10") }
    var saltComposition by remember { mutableStateOf("") }
    var ptrText by remember { mutableStateOf("") }
    var mrpText by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }

    val dosageForms = listOf("Tablet", "Capsule", "Syrup", "Injection", "Ointment")

    // Helper to generate SKU from Product Name
    fun generateSku(name: String): String {
        val clean = name.trim().uppercase()
            .replace(Regex("[^A-Z0-9 ]"), "")
            .split(" ")
            .filter { it.isNotBlank() }
        val prefix = clean.take(2).joinToString("-") { it.take(4) }
        val rand = (100..999).random()
        return if (prefix.isNotBlank()) "SKU-$prefix-$rand" else "SKU-MED-$rand"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFEFF6FF),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Medication,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Add Pharmaceutical Product",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Room Database Inventory Entity",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Error banner if any
                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEE2E2),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = errorMessage ?: "",
                                fontSize = 12.sp,
                                color = Color(0xFFB91C1C)
                            )
                        }
                    }
                }

                // 1. Product Name Input
                OutlinedTextField(
                    value = productName,
                    onValueChange = {
                        productName = it
                        errorMessage = null
                        if (sku.isBlank() && it.isNotBlank()) {
                            sku = generateSku(it)
                        }
                    },
                    label = { Text("Product Name *", fontSize = 13.sp) },
                    placeholder = { Text("e.g. Augmentin 625 Duo", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2563EB),
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_product_name")
                )

                // 2. SKU Input + Auto Suggestion
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = sku,
                        onValueChange = {
                            sku = it.uppercase()
                            errorMessage = null
                        },
                        label = { Text("SKU (Code) *", fontSize = 13.sp) },
                        placeholder = { Text("e.g. SKU-AUG-625", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_product_sku")
                    )

                    OutlinedButton(
                        onClick = { sku = generateSku(productName.ifBlank { "MED" }) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .padding(top = 6.dp)
                            .testTag("btn_generate_sku")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Auto generate SKU",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF2563EB)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto", fontSize = 11.sp, color = Color(0xFF2563EB))
                    }
                }

                // 3 & 4. Current Stock & Reorder Threshold Inputs (Row)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Current Stock
                    OutlinedTextField(
                        value = currentStockText,
                        onValueChange = {
                            currentStockText = it.filter { ch -> ch.isDigit() }
                            errorMessage = null
                        },
                        label = { Text("Current Stock *", fontSize = 12.sp) },
                        placeholder = { Text("e.g. 50", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_current_stock")
                    )

                    // Reorder Threshold
                    OutlinedTextField(
                        value = reorderThresholdText,
                        onValueChange = {
                            reorderThresholdText = it.filter { ch -> ch.isDigit() }
                            errorMessage = null
                        },
                        label = { Text("Threshold *", fontSize = 12.sp) },
                        placeholder = { Text("e.g. 15", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_reorder_threshold")
                    )
                }

                Text(
                    text = "Reorder threshold triggers automated push notifications and warning badges when stock falls to or below this quantity.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )

                // Dosage Form Chips
                Text(
                    text = "Dosage Form",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dosageForms.forEach { form ->
                        val isSelected = form == dosageForm
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF3B82F6) else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.clickable { dosageForm = form }
                        ) {
                            Text(
                                text = form,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF1D4ED8) else Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Toggle Optional / Advanced Details
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showAdvanced) "▾ Hide Optional Details" else "▸ Add Salt & Pricing Details",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2563EB)
                    )
                }

                AnimatedVisibility(visible = showAdvanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = saltComposition,
                            onValueChange = { saltComposition = it },
                            label = { Text("Active Salt Composition", fontSize = 12.sp) },
                            placeholder = { Text("e.g. Amoxicillin (500mg) + Clavulanic Acid (125mg)", fontSize = 12.sp) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = ptrText,
                                onValueChange = { ptrText = it },
                                label = { Text("PTR Price (₹)", fontSize = 12.sp) },
                                placeholder = { Text("e.g. 142.50", fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = mrpText,
                                onValueChange = { mrpText = it },
                                label = { Text("MRP (₹)", fontSize = 12.sp) },
                                placeholder = { Text("e.g. 198.00", fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nameTrimmed = productName.trim()
                    val skuTrimmed = sku.trim().uppercase()
                    val stock = currentStockText.toIntOrNull()
                    val threshold = reorderThresholdText.toIntOrNull()

                    when {
                        nameTrimmed.isBlank() -> {
                            errorMessage = "Please enter a product name."
                        }
                        skuTrimmed.isBlank() -> {
                            errorMessage = "Please specify a SKU code."
                        }
                        stock == null -> {
                            errorMessage = "Please enter valid current stock (units)."
                        }
                        threshold == null -> {
                            errorMessage = "Please enter a valid reorder threshold (units)."
                        }
                        else -> {
                            val ptr = ptrText.toDoubleOrNull() ?: 0.0
                            val mrp = mrpText.toDoubleOrNull() ?: 0.0
                            onConfirm(
                                nameTrimmed,
                                skuTrimmed,
                                stock,
                                threshold,
                                dosageForm,
                                packSize,
                                saltComposition,
                                ptr,
                                mrp
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                modifier = Modifier.testTag("dialog_save_product_button")
            ) {
                Text(
                    text = "Save Product",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_button")
            ) {
                Text(
                    text = "Cancel",
                    color = Color(0xFF64748B)
                )
            }
        },
        shape = RoundedCornerShape(18.dp),
        containerColor = Color.White,
        modifier = Modifier.testTag("add_product_dialog")
    )
}

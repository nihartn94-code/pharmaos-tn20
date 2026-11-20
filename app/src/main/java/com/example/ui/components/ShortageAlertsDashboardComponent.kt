package com.example.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ProductInventoryEntity
import com.example.ui.theme.PharmaOSTheme
import com.example.util.Formatters

/**
 * Filter mode for the Shortage Alerts dashboard component.
 */
enum class ShortageAlertFilter(val label: String) {
    ALL("All Monitored"),
    CRITICAL_REORDER("Critical Reorder"),
    OUT_OF_STOCK("Out of Stock"),
    HEALTHY("Adequate Buffer")
}

/**
 * 'Shortage Alerts' Dashboard Component.
 *
 * Displays real-time inventory levels from Room database and flags critical items
 * for replenishment using a scrollable list view with instant reorder triggers.
 */
@Composable
fun ShortageAlertsDashboardComponent(
    items: List<ProductInventoryEntity>,
    modifier: Modifier = Modifier,
    onReorderClick: (ProductInventoryEntity, Int) -> Unit = { _, _ -> },
    onAdjustStock: (String, Int) -> Unit = { _, _ -> },
    onViewAllClick: () -> Unit = {},
    onProductClick: ((ProductInventoryEntity) -> Unit)? = null,
    isStandalone: Boolean = false
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ShortageAlertFilter.ALL) }
    var itemToReorder by remember { mutableStateOf<ProductInventoryEntity?>(null) }
    var restockNotice by remember { mutableStateOf<String?>(null) }
    var dashboardTab by remember { mutableIntStateOf(0) } // 0 = Alerts & Reorder, 1 = Trends & Turnover

    // Summary calculations
    val totalCount = items.size
    val criticalCount = remember(items) { items.count { it.currentStock <= it.reorderThreshold } }
    val outOfStockCount = remember(items) { items.count { it.currentStock <= 0 } }
    val totalReplenishmentNeeded = remember(items) {
        items.filter { it.currentStock <= it.reorderThreshold }
            .sumOf { (it.reorderThreshold * 2) - it.currentStock }
    }
    val estimatedReorderCost = remember(items) {
        items.filter { it.currentStock <= it.reorderThreshold }
            .sumOf {
                val needed = (it.reorderThreshold * 2) - it.currentStock
                needed * it.unitPricePtr
            }
    }

    // Filtered items
    val filteredItems by remember(items, searchQuery, selectedFilter) {
        derivedStateOf {
            val query = searchQuery.trim().lowercase()
            items.filter { item ->
                val matchesSearch = query.isBlank() ||
                    item.productName.lowercase().contains(query) ||
                    item.sku.lowercase().contains(query) ||
                    item.saltComposition.lowercase().contains(query) ||
                    item.locationRack.lowercase().contains(query) ||
                    item.supplierName.lowercase().contains(query)

                val matchesFilter = when (selectedFilter) {
                    ShortageAlertFilter.ALL -> true
                    ShortageAlertFilter.CRITICAL_REORDER -> item.currentStock <= item.reorderThreshold
                    ShortageAlertFilter.OUT_OF_STOCK -> item.currentStock <= 0
                    ShortageAlertFilter.HEALTHY -> item.currentStock > item.reorderThreshold
                }

                matchesSearch && matchesFilter
            }.sortedWith(
                compareBy<ProductInventoryEntity> { it.currentStock > 0 }
                    .thenBy { it.currentStock.toFloat() / it.reorderThreshold.coerceAtLeast(1) }
                    .thenBy { it.productName }
            )
        }
    }

    // Pulse animation for critical alert banner
    val infiniteTransition = rememberInfiniteTransition(label = "critical_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("shortage_alerts_dashboard_component"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (criticalCount > 0) Color(0xFFFEE2E2) else Color(0xFFDCFCE7)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (criticalCount > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = "Shortage Alerts Status",
                            tint = if (criticalCount > 0) Color(0xFFDC2626) else Color(0xFF16A34A),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Shortage Alerts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (criticalCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFDC2626),
                                    modifier = Modifier.alpha(pulseAlpha)
                                ) {
                                    Text(
                                        text = "$criticalCount REORDER",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Real-time Room DB sync • Flagging critical items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(
                    onClick = onViewAllClick,
                    modifier = Modifier.testTag("shortage_alerts_view_all_button")
                ) {
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dashboard View Switcher: Active Alerts vs Trends & Turnover
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (dashboardTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { dashboardTab = 0 }
                        .padding(vertical = 8.dp)
                        .testTag("tab_critical_alerts"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = if (dashboardTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Alerts & Reorders ($criticalCount)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (dashboardTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (dashboardTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (dashboardTab == 1) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { dashboardTab = 1 }
                        .padding(vertical = 8.dp)
                        .testTag("tab_trends_turnover"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = if (dashboardTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Trends & Turnover",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (dashboardTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (dashboardTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (dashboardTab == 0) {
                // Metrics Summary Bar
                Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShortageMetricPill(
                    label = "Critical SKUs",
                    value = "$criticalCount",
                    subValue = "of $totalCount total",
                    containerColor = if (criticalCount > 0) Color(0xFFFEF2F2) else Color(0xFFF8FAFC),
                    contentColor = if (criticalCount > 0) Color(0xFFB91C1C) else Color(0xFF475569),
                    icon = Icons.Default.Warning,
                    modifier = Modifier.testTag("kpi_critical_skus")
                )
                ShortageMetricPill(
                    label = "Out of Stock",
                    value = "$outOfStockCount",
                    subValue = "0 units left",
                    containerColor = if (outOfStockCount > 0) Color(0xFFFEE2E2) else Color(0xFFF8FAFC),
                    contentColor = if (outOfStockCount > 0) Color(0xFF991B1B) else Color(0xFF475569),
                    icon = Icons.Default.ErrorOutline,
                    modifier = Modifier.testTag("kpi_out_of_stock")
                )
                ShortageMetricPill(
                    label = "Units Needed",
                    value = "+$totalReplenishmentNeeded",
                    subValue = "safe buffer",
                    containerColor = Color(0xFFFFFBEB),
                    contentColor = Color(0xFFB45309),
                    icon = Icons.Default.Inventory,
                    modifier = Modifier.testTag("kpi_units_needed")
                )
                if (estimatedReorderCost > 0) {
                    ShortageMetricPill(
                        label = "Reorder Cost",
                        value = Formatters.formatInr(estimatedReorderCost),
                        subValue = "est. PTR",
                        containerColor = Color(0xFFEFF6FF),
                        contentColor = Color(0xFF1D4ED8),
                        icon = Icons.Default.ShoppingCart,
                        modifier = Modifier.testTag("kpi_reorder_cost")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar & Filter Chips
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search SKU, medicine, salt composition...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.testTag("clear_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shortage_alerts_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(ShortageAlertFilter.entries) { filter ->
                    val isSelected = selectedFilter == filter
                    val badgeCount = when (filter) {
                        ShortageAlertFilter.ALL -> totalCount
                        ShortageAlertFilter.CRITICAL_REORDER -> criticalCount
                        ShortageAlertFilter.OUT_OF_STOCK -> outOfStockCount
                        ShortageAlertFilter.HEALTHY -> totalCount - criticalCount
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = "${filter.label} ($badgeCount)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (filter == ShortageAlertFilter.CRITICAL_REORDER || filter == ShortageAlertFilter.OUT_OF_STOCK) {
                                Color(0xFFFEE2E2)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            selectedLabelColor = if (filter == ShortageAlertFilter.CRITICAL_REORDER || filter == ShortageAlertFilter.OUT_OF_STOCK) {
                                Color(0xFF991B1B)
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        ),
                        modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Restock Notice Banner
            restockNotice?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFECFDF5),
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF065F46),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { restockNotice = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Dismiss",
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Scrollable List View of Critical & Monitored Items
            if (filteredItems.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No items match '$searchQuery'" else "All Stock Levels Healthy!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "Try refining your search keyword." else "No critical inventory shortages detected. All SKUs are within safe reorder thresholds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                // Scrollable List View with bounded height for embedded dashboard or expandable in standalone
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (isStandalone) 800.dp else 420.dp)
                        .testTag("shortage_alerts_scrollable_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(
                        items = filteredItems,
                        key = { it.sku }
                    ) { item ->
                        ShortageAlertItemCard(
                            item = item,
                            onReorderClick = {
                                itemToReorder = item
                            },
                            onAdjustStock = { delta ->
                                onAdjustStock(item.sku, delta)
                                val action = if (delta > 0) "Restocked +$delta units" else "Dispensed $delta units"
                                restockNotice = "$action for ${item.productName} (${item.sku})"
                            },
                            onProductClick = { onProductClick?.invoke(item) }
                        )
                    }
                }
            }
        } else {
            InventoryTrendsTurnoverComponent(
                items = items,
                isEmbedded = true,
                onReorderClick = { entity ->
                    itemToReorder = entity
                }
            )
        }
        }
    }

    // Reorder Confirmation Dialog
    itemToReorder?.let { item ->
        ReorderConfirmationDialog(
            item = item,
            onDismiss = { itemToReorder = null },
            onConfirmReorder = { reorderQty ->
                onReorderClick(item, reorderQty)
                restockNotice = "Reorder PO placed for $reorderQty units of ${item.productName} (${item.sku})."
                itemToReorder = null
            }
        )
    }
}

/**
 * Individual pharmaceutical inventory item card with real-time level meters,
 * urgency badge, SKU tags, and 1-Click Reorder button.
 */
@Composable
fun ShortageAlertItemCard(
    item: ProductInventoryEntity,
    onReorderClick: () -> Unit,
    onAdjustStock: (Int) -> Unit,
    onProductClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCritical = item.currentStock <= item.reorderThreshold
    val isDepleted = item.currentStock <= 0
    val isSevere = isCritical && item.currentStock < (item.reorderThreshold * 0.5)

    // Urgency visual styling
    val badgeBg = when {
        isDepleted -> Color(0xFFFEE2E2)
        isSevere -> Color(0xFFFFEDD5)
        isCritical -> Color(0xFFFEF9C3)
        else -> Color(0xFFDCFCE7)
    }
    val badgeText = when {
        isDepleted -> Color(0xFF991B1B)
        isSevere -> Color(0xFFC2410C)
        isCritical -> Color(0xFF854D0E)
        else -> Color(0xFF166534)
    }
    val badgeLabel = when {
        isDepleted -> "OUT OF STOCK • REORDER"
        isSevere -> "CRITICAL SHORTAGE"
        isCritical -> "LOW STOCK WARNING"
        else -> "HEALTHY BUFFER"
    }

    // Progress percentage
    val maxBuffer = (item.reorderThreshold * 2).coerceAtLeast(1)
    val progress = (item.currentStock.toFloat() / maxBuffer).coerceIn(0f, 1f)
    val progressColor = when {
        isDepleted -> Color(0xFFDC2626)
        isSevere -> Color(0xFFEA580C)
        isCritical -> Color(0xFFD97706)
        else -> Color(0xFF16A34A)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onProductClick() }
            .testTag("shortage_alert_card_${item.sku}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCritical) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            }
        ),
        border = BorderStroke(
            width = if (isCritical) 1.5.dp else 1.dp,
            color = if (isDepleted) Color(0xFFFCA5A5)
            else if (isSevere) Color(0xFFFDBA74)
            else if (isCritical) Color(0xFFFDE047)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top Row: Status Chip & SKU Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBg,
                    modifier = Modifier.testTag("urgency_badge_${item.sku}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isCritical) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = badgeText,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = badgeLabel,
                            color = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = item.sku,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (item.locationRack.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = item.locationRack,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Medicine Name & Packaging
            Text(
                text = item.productName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Salt composition & Form
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.saltComposition.isNotBlank()) {
                    Text(
                        text = item.saltComposition,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "${item.dosageForm} • ${item.packSize}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stock Level Gauge & Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Current Stock: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${item.currentStock} Units",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Black,
                        color = if (isCritical) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Threshold: ${item.reorderThreshold} Units",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Deficit & Replenishment Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCritical) {
                    val deficit = item.reorderThreshold - item.currentStock
                    val needed = (item.reorderThreshold * 2) - item.currentStock
                    Text(
                        text = "Deficit: -$deficit below safe buffer • Need +$needed",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Safe Buffer: +${item.currentStock - item.reorderThreshold} above threshold",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF16A34A),
                        fontWeight = FontWeight.Medium
                    )
                }

                if (item.unitPricePtr > 0) {
                    Text(
                        text = "PTR: ${Formatters.formatInr(item.unitPricePtr)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Action Row: Quick Stock Adjustment & 1-Click Reorder Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stock Stepper (- / +) for real-time local adjustment
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { onAdjustStock(-1) },
                        enabled = item.currentStock > 0,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .testTag("adjust_stock_minus_${item.sku}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Dispense 1 unit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "${item.currentStock}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    IconButton(
                        onClick = { onAdjustStock(+5) },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .testTag("adjust_stock_plus_${item.sku}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Stock in +5 units",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 1-Click Reorder Trigger
                Button(
                    onClick = onReorderClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCritical) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("reorder_button_${item.sku}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCritical) "Reorder Now" else "Restock",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Metric summary pill card for the dashboard component header.
 */
@Composable
private fun ShortageMetricPill(
    label: String,
    value: String,
    subValue: String,
    containerColor: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.2f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Reorder Confirmation Dialog allowing the user to select replenishment pack count,
 * review total order value, supplier details, and confirm into basket.
 */
@Composable
fun ReorderConfirmationDialog(
    item: ProductInventoryEntity,
    onDismiss: () -> Unit,
    onConfirmReorder: (Int) -> Unit
) {
    val recommendedUnits = remember(item) {
        val calc = (item.reorderThreshold * 2) - item.currentStock
        calc.coerceAtLeast(10)
    }
    var reorderUnits by remember { mutableIntStateOf(recommendedUnits) }
    val totalCost = remember(reorderUnits, item.unitPricePtr) {
        reorderUnits * item.unitPricePtr
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color(0xFFDC2626)
                )
                Text(
                    text = "Confirm Critical Reorder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Initiate replenishment purchase order for ${item.productName}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("SKU Code:", style = MaterialTheme.typography.labelSmall)
                            Text(item.sku, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Current Stock:", style = MaterialTheme.typography.labelSmall)
                            Text(
                                "${item.currentStock} Units (Threshold: ${item.reorderThreshold})",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.currentStock <= item.reorderThreshold) Color(0xFFDC2626) else Color.Unspecified,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (item.supplierName.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Primary Distributor:", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    item.supplierName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Quantity Stepper
                Text(
                    text = "Replenishment Quantity (Units/Packs):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { if (reorderUnits > 5) reorderUnits -= 5 },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease reorder quantity")
                        }

                        Text(
                            text = "$reorderUnits",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        IconButton(
                            onClick = { reorderUnits += 5 },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase reorder quantity")
                        }
                    }

                    if (item.unitPricePtr > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Est. Total Cost",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = Formatters.formatInr(totalCost),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmReorder(reorderUnits) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_reorder_submit_button")
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Confirm Reorder ($reorderUnits Units)")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

// =========================================================================
// JETPACK COMPOSE PREVIEWS FOR ANDROID STUDIO
// =========================================================================

private val SamplePreviewInventory = listOf(
    ProductInventoryEntity(
        productName = "Augmentin 625 Duo",
        sku = "SKU-AUG-625-10T",
        currentStock = 14,
        reorderThreshold = 25,
        saltComposition = "Amoxicillin (500mg) + Clavulanic Acid (125mg)",
        dosageForm = "Tablet",
        packSize = "Box of 10 Strips x 10 Tabs",
        unitPricePtr = 142.50,
        mrp = 198.00,
        supplierName = "Cipla Wholesale Direct Hub",
        locationRack = "RACK-A-01"
    ),
    ProductInventoryEntity(
        productName = "Azithromycin 500mg (Azithral)",
        sku = "SKU-AZTH-500-5T",
        currentStock = 0,
        reorderThreshold = 30,
        saltComposition = "Azithromycin IP (500mg)",
        dosageForm = "Tablet",
        packSize = "Strip of 5 Tablets",
        unitPricePtr = 94.00,
        mrp = 118.00,
        supplierName = "Sun Pharma Direct Depo",
        locationRack = "RACK-B-05"
    ),
    ProductInventoryEntity(
        productName = "Mixtard 30/70 100IU",
        sku = "SKU-MIXT-100IU-10ML",
        currentStock = 5,
        reorderThreshold = 15,
        saltComposition = "Biphasic Isophane Insulin Injection IP",
        dosageForm = "Injection",
        packSize = "Vial of 10ml (Cold Chain)",
        unitPricePtr = 185.00,
        mrp = 225.00,
        storageCondition = "COLD_CHAIN_2_TO_8",
        supplierName = "Novo Nordisk Logistics",
        locationRack = "FRIDGE-01"
    ),
    ProductInventoryEntity(
        productName = "Paracetamol 650mg (Dolo)",
        sku = "SKU-DOLO-650-15T",
        currentStock = 120,
        reorderThreshold = 50,
        saltComposition = "Paracetamol (650mg)",
        dosageForm = "Tablet",
        packSize = "Strip of 15 Tablets",
        unitPricePtr = 28.50,
        mrp = 34.00,
        supplierName = "Micro Labs Wholesale",
        locationRack = "RACK-B-01"
    )
)

@Preview(name = "Shortage Alerts Component - Light Mode", showBackground = true)
@Composable
private fun ShortageAlertsDashboardComponentPreview() {
    PharmaOSTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ShortageAlertsDashboardComponent(
                items = SamplePreviewInventory
            )
        }
    }
}

@Preview(name = "Shortage Alerts Component - Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ShortageAlertsDashboardComponentDarkPreview() {
    PharmaOSTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            ShortageAlertsDashboardComponent(
                items = SamplePreviewInventory
            )
        }
    }
}

package com.example.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.OrderStatus
import com.example.domain.model.StockLevelStatus
import com.example.util.Formatters

private val CommandNavy = Color(0xFF0F2544)
private val CommandCobalt = Color(0xFF1D4ED8)
private val ClinicalTeal = Color(0xFF00A896)
private val AlertCrimson = Color(0xFFDC2626)
private val AlertAmber = Color(0xFFD97706)
private val StatusEmerald = Color(0xFF059669)

/**
 * Executive 3-pillar pulse card surfacing Current Inventory, Active Trade Alerts,
 * and Pending Orders at a single glance at the top of the main dashboard.
 */
@Composable
fun DashboardOperationsPulseCard(
    uiState: DashboardUiState,
    onRefreshClick: () -> Unit,
    onInventoryClick: () -> Unit,
    onAlertsClick: () -> Unit,
    onPendingOrdersClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inventoryMetricValue = if (uiState.isRealTimeInventoryAuthorized) {
        val totalUnits = uiState.inventorySummary.totalStockUnits.takeIf { it > 0 }
            ?: uiState.inventoryItems.sumOf { it.currentStock }
        "$totalUnits"
    } else {
        "${uiState.catalogueSupplySummary.activeProductsCount}"
    }
    val inventoryMetricLabel = if (uiState.isRealTimeInventoryAuthorized) "Units in Stock" else "Active SKUs"
    val alertsCount = uiState.activeTradeAlerts.size
    val pendingCount = uiState.pendingOrdersSummary.totalPendingCount

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_operations_pulse_card"),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(CommandNavy, Color(0xFF163A6B), CommandCobalt)
                    )
                )
                .drawBehind {
                    // Subtle architectural clinical grid accent lines
                    val accentColor = Color.White.copy(alpha = 0.07f)
                    drawCircle(
                        color = ClinicalTeal.copy(alpha = 0.16f),
                        radius = size.height * 0.85f,
                        center = Offset(size.width * 0.94f, size.height * 0.12f)
                    )
                    drawLine(
                        color = accentColor,
                        start = Offset(0f, size.height * 0.56f),
                        end = Offset(size.width, size.height * 0.56f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LIVE TRADE COMMAND",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            color = Color(0xFF93C5FD)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = uiState.currentUser?.businessName?.ifBlank { "PharmaOS Operations" }
                                ?: "PharmaOS Operations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onRefreshClick,
                        enabled = !uiState.isRefreshing,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("dashboard_refresh_button")
                    ) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh dashboard metrics",
                                tint = Color.White
                            )
                        }
                    }
                }

                val fontScale = LocalDensity.current.fontScale
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val stackTiles = maxWidth < 310.dp || fontScale >= 1.45f
                    if (stackTiles) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PulseMetricTile(
                                value = inventoryMetricValue,
                                label = inventoryMetricLabel,
                                icon = Icons.Default.Inventory2,
                                accentTint = Color(0xFF5EEAD4),
                                testTag = "pulse_tile_inventory",
                                onClick = onInventoryClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                            PulseMetricTile(
                                value = "$alertsCount",
                                label = "Trade Alerts",
                                icon = Icons.Default.NotificationsActive,
                                accentTint = if (uiState.criticalAlertsCount > 0) Color(0xFFFCA5A5) else Color(0xFFFDE68A),
                                showAlertDot = uiState.criticalAlertsCount > 0,
                                testTag = "pulse_tile_alerts",
                                onClick = onAlertsClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                            PulseMetricTile(
                                value = "$pendingCount",
                                label = "Pending Orders",
                                icon = Icons.Default.PendingActions,
                                accentTint = Color(0xFFBFDBFE),
                                testTag = "pulse_tile_pending_orders",
                                onClick = onPendingOrdersClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PulseMetricTile(
                                value = inventoryMetricValue,
                                label = inventoryMetricLabel,
                                icon = Icons.Default.Inventory2,
                                accentTint = Color(0xFF5EEAD4),
                                testTag = "pulse_tile_inventory",
                                onClick = onInventoryClick,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            PulseMetricTile(
                                value = "$alertsCount",
                                label = "Trade Alerts",
                                icon = Icons.Default.NotificationsActive,
                                accentTint = if (uiState.criticalAlertsCount > 0) Color(0xFFFCA5A5) else Color(0xFFFDE68A),
                                showAlertDot = uiState.criticalAlertsCount > 0,
                                testTag = "pulse_tile_alerts",
                                onClick = onAlertsClick,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            PulseMetricTile(
                                value = "$pendingCount",
                                label = "Pending Orders",
                                icon = Icons.Default.PendingActions,
                                accentTint = Color(0xFFBFDBFE),
                                testTag = "pulse_tile_pending_orders",
                                onClick = onPendingOrdersClick,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PulseMetricTile(
    value: String,
    label: String,
    icon: ImageVector,
    accentTint: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showAlertDot: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentTint,
                    modifier = Modifier.size(16.dp)
                )
                if (showAlertDot) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Current Inventory Overview Section.
 * - For authorized Brand/Manufacturer accounts: displays live stock units, valuation,
 *   health breakdown, and top SKU/batch rows with stock progress indicators.
 * - For Stockist accounts: strictly honors RBAC (zero manufacturer batch/SKU exposure)
 *   and shows buyer-scoped Catalogue & Supply availability with direct restock actions.
 */
@Composable
fun CurrentInventoryDashboardSection(
    uiState: DashboardUiState,
    onOpenFullInventory: () -> Unit,
    onOpenCatalogue: () -> Unit,
    onOpenBasket: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_current_inventory_section"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ClinicalTeal.copy(alpha = 0.14f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (uiState.isRealTimeInventoryAuthorized) {
                                    Icons.Default.Inventory2
                                } else {
                                    Icons.Default.Storefront
                                },
                                contentDescription = "Current Inventory",
                                tint = ClinicalTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Current Inventory",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (uiState.isRealTimeInventoryAuthorized) {
                                "Real-time plant & batch stock levels"
                            } else {
                                "Available catalogue supply & procurement readiness"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(
                    onClick = if (uiState.isRealTimeInventoryAuthorized) onOpenFullInventory else onOpenCatalogue,
                    modifier = Modifier.testTag("dashboard_open_inventory_button")
                ) {
                    Text(
                        text = if (uiState.isRealTimeInventoryAuthorized) "Manage" else "Explore",
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (uiState.isRealTimeInventoryAuthorized) {
                AuthorizedInventorySummaryBody(
                    uiState = uiState,
                    onOpenFullInventory = onOpenFullInventory
                )
            } else {
                BuyerSupplyAvailabilityBody(
                    uiState = uiState,
                    onOpenCatalogue = onOpenCatalogue,
                    onOpenBasket = onOpenBasket
                )
            }
        }
    }
}

@Composable
private fun AuthorizedInventorySummaryBody(
    uiState: DashboardUiState,
    onOpenFullInventory: () -> Unit
) {
    val summary = uiState.inventorySummary
    val skuItems = uiState.inventoryItems
    val batchItems = uiState.realTimeInventory

    val totalUnits = if (summary.totalStockUnits > 0 || batchItems.isNotEmpty()) {
        summary.totalStockUnits
    } else {
        skuItems.sumOf { it.currentStock }
    }
    val totalValuation = if (summary.totalInventoryValue > 0.0 || batchItems.isNotEmpty()) {
        summary.totalInventoryValue
    } else {
        skuItems.sumOf { it.currentStock * it.unitPricePtr }
    }
    val totalSkus = if (summary.totalSkusCount > 0 || batchItems.isNotEmpty()) {
        summary.totalSkusCount
    } else {
        skuItems.size
    }
    val lowOrCriticalCount = if (batchItems.isNotEmpty()) {
        summary.lowStockCount + summary.outOfStockCount
    } else {
        skuItems.count { it.currentStock <= it.reorderThreshold }
    }
    val healthyCount = if (batchItems.isNotEmpty()) {
        summary.healthyCount
    } else {
        (skuItems.size - lowOrCriticalCount).coerceAtLeast(0)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactStatBox(
            label = "Total Units",
            value = "$totalUnits",
            valueColor = MaterialTheme.colorScheme.primary,
            testTag = "dashboard_inventory_total_units",
            modifier = Modifier.weight(1f)
        )
        CompactStatBox(
            label = "Valuation",
            value = Formatters.formatInr(totalValuation),
            valueColor = StatusEmerald,
            testTag = "dashboard_inventory_valuation",
            modifier = Modifier.weight(1.35f)
        )
        CompactStatBox(
            label = "Active SKUs",
            value = "$totalSkus",
            valueColor = MaterialTheme.colorScheme.onSurface,
            testTag = "dashboard_inventory_sku_count",
            modifier = Modifier.weight(0.9f)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusPillChip(
            label = "Healthy: $healthyCount",
            containerColor = StatusEmerald.copy(alpha = 0.12f),
            contentColor = StatusEmerald
        )
        StatusPillChip(
            label = "Low / Critical: $lowOrCriticalCount",
            containerColor = if (lowOrCriticalCount > 0) AlertAmber.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (lowOrCriticalCount > 0) AlertAmber else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (summary.nearExpiryCount > 0) {
            StatusPillChip(
                label = "Near Expiry: ${summary.nearExpiryCount}",
                containerColor = AlertCrimson.copy(alpha = 0.12f),
                contentColor = AlertCrimson
            )
        }
    }

    if (batchItems.isEmpty() && skuItems.isEmpty()) {
        EmptySectionPlaceholder(
            icon = Icons.Default.Inventory2,
            title = "No Inventory Batches Recorded",
            subtitle = "Add manufacturing batches or sync from your backend to monitor live stock levels.",
            actionLabel = "Open Inventory",
            onActionClick = onOpenFullInventory,
            testTag = "dashboard_inventory_empty"
        )
    } else {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (batchItems.isNotEmpty()) {
                batchItems
                    .sortedWith(compareByDescending<com.example.domain.model.InventoryStockItem> { it.isCritical }.thenBy { it.remainingQuantity })
                    .take(3)
                    .forEach { batch ->
                        val statusColor = Color(batch.stockStatus.badgeColorHex)
                        InventorySnapshotRow(
                            title = batch.productName,
                            subtitle = "Batch ${batch.batchNumber} • Exp ${Formatters.formatDate(batch.expiryDateEpochMillis)}",
                            unitsText = "${batch.remainingQuantity} / ${batch.initialQuantity} units",
                            statusLabel = batch.stockStatus.label,
                            statusColor = statusColor,
                            progress = batch.stockRatio,
                            testTag = "dashboard_inventory_row_batch_${batch.id}",
                            onClick = onOpenFullInventory
                        )
                    }
            } else {
                skuItems
                    .sortedBy { it.currentStock }
                    .take(3)
                    .forEach { sku ->
                        val status = when {
                            sku.currentStock <= 0 -> StockLevelStatus.OUT_OF_STOCK
                            sku.currentStock <= sku.reorderThreshold / 2 -> StockLevelStatus.CRITICAL_LOW
                            sku.currentStock <= sku.reorderThreshold -> StockLevelStatus.LOW_STOCK
                            else -> StockLevelStatus.HEALTHY
                        }
                        val denominator = (sku.reorderThreshold * 3).coerceAtLeast(sku.currentStock).coerceAtLeast(1)
                        InventorySnapshotRow(
                            title = sku.productName,
                            subtitle = "SKU ${sku.sku} • Reorder at ${sku.reorderThreshold}",
                            unitsText = "${sku.currentStock} units",
                            statusLabel = status.label,
                            statusColor = Color(status.badgeColorHex),
                            progress = (sku.currentStock.toFloat() / denominator.toFloat()).coerceIn(0f, 1f),
                            testTag = "dashboard_inventory_row_sku_${sku.sku}",
                            onClick = onOpenFullInventory
                        )
                    }
            }
        }
    }
}

@Composable
private fun InventorySnapshotRow(
    title: String,
    subtitle: String,
    unitsText: String,
    statusLabel: String,
    statusColor: Color,
    progress: Float,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = unitsText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = statusColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun BuyerSupplyAvailabilityBody(
    uiState: DashboardUiState,
    onOpenCatalogue: () -> Unit,
    onOpenBasket: () -> Unit
) {
    val supply = uiState.catalogueSupplySummary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_supply_availability_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactStatBox(
                label = "Catalogue SKUs",
                value = "${supply.activeProductsCount}",
                valueColor = MaterialTheme.colorScheme.primary,
                testTag = "dashboard_supply_sku_count",
                modifier = Modifier.weight(1f)
            )
            CompactStatBox(
                label = "Live Offers",
                value = "${supply.totalSupplierOffersCount}",
                valueColor = ClinicalTeal,
                testTag = "dashboard_supply_offers_count",
                modifier = Modifier.weight(1f)
            )
            CompactStatBox(
                label = "In Basket",
                value = "${uiState.cartItemCount}",
                valueColor = StatusEmerald,
                testTag = "dashboard_supply_basket_count",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOpenCatalogue,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Browse Supply", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onOpenBasket,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Review Basket", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Active Trade Alerts Section with interactive severity filter chips,
 * category badges, and one-tap alert acknowledgement.
 */
@Composable
fun ActiveTradeAlertsSection(
    alerts: List<DashboardTradeAlert>,
    filteredAlerts: List<DashboardTradeAlert>,
    selectedFilter: TradeAlertSeverity?,
    onSelectFilter: (TradeAlertSeverity?) -> Unit,
    onMarkAlertRead: (DashboardTradeAlert) -> Unit,
    onAlertClick: (DashboardTradeAlert) -> Unit,
    onViewAllAlerts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val criticalCount = alerts.count { it.severity == TradeAlertSeverity.CRITICAL }
    val warningCount = alerts.count { it.severity == TradeAlertSeverity.WARNING }
    val infoCount = alerts.count { it.severity == TradeAlertSeverity.INFO }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_trade_alerts_section"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (criticalCount > 0) AlertCrimson.copy(alpha = 0.14f) else AlertAmber.copy(alpha = 0.14f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Active Trade Alerts",
                                tint = if (criticalCount > 0) AlertCrimson else AlertAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Active Trade Alerts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (alerts.isNotEmpty()) AlertCrimson else StatusEmerald
                            ) {
                                Text(
                                    text = "${alerts.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                        .testTag("dashboard_trade_alerts_count")
                                )
                            }
                        }
                        Text(
                            text = "Recalls, shortages, expiry risks & order notices",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(
                    onClick = onViewAllAlerts,
                    modifier = Modifier.testTag("dashboard_view_all_alerts_button")
                ) {
                    Text("All", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Severity filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { onSelectFilter(null) },
                    label = { Text("All (${alerts.size})", fontSize = 12.sp) },
                    modifier = Modifier.testTag("dashboard_alert_filter_all")
                )
                FilterChip(
                    selected = selectedFilter == TradeAlertSeverity.CRITICAL,
                    onClick = { onSelectFilter(TradeAlertSeverity.CRITICAL) },
                    label = { Text("Critical ($criticalCount)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AlertCrimson.copy(alpha = 0.14f),
                        selectedLabelColor = AlertCrimson
                    ),
                    modifier = Modifier.testTag("dashboard_alert_filter_critical")
                )
                FilterChip(
                    selected = selectedFilter == TradeAlertSeverity.WARNING,
                    onClick = { onSelectFilter(TradeAlertSeverity.WARNING) },
                    label = { Text("Warning ($warningCount)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AlertAmber.copy(alpha = 0.16f),
                        selectedLabelColor = AlertAmber
                    ),
                    modifier = Modifier.testTag("dashboard_alert_filter_warning")
                )
                FilterChip(
                    selected = selectedFilter == TradeAlertSeverity.INFO,
                    onClick = { onSelectFilter(TradeAlertSeverity.INFO) },
                    label = { Text("Updates ($infoCount)", fontSize = 12.sp) },
                    modifier = Modifier.testTag("dashboard_alert_filter_info")
                )
            }

            AnimatedVisibility(
                visible = filteredAlerts.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                EmptySectionPlaceholder(
                    icon = Icons.Default.CheckCircle,
                    iconTint = StatusEmerald,
                    title = if (selectedFilter == null) "All Trade Channels Clear" else "No ${selectedFilter.label} Alerts",
                    subtitle = "No active recalls, shortages, or unread trade notices require attention right now.",
                    testTag = "dashboard_trade_alerts_empty"
                )
            }

            if (filteredAlerts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredAlerts.take(4).forEach { alert ->
                        TradeAlertItemRow(
                            alert = alert,
                            onClick = { onAlertClick(alert) },
                            onMarkRead = if (alert.notificationId != null && alert.isUnread) {
                                { onMarkAlertRead(alert) }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TradeAlertItemRow(
    alert: DashboardTradeAlert,
    onClick: () -> Unit,
    onMarkRead: (() -> Unit)?
) {
    val accentColor = when (alert.severity) {
        TradeAlertSeverity.CRITICAL -> AlertCrimson
        TradeAlertSeverity.WARNING -> AlertAmber
        TradeAlertSeverity.INFO -> MaterialTheme.colorScheme.primary
    }
    val severityIcon = when (alert.severity) {
        TradeAlertSeverity.CRITICAL -> Icons.Default.ReportProblem
        TradeAlertSeverity.WARNING -> Icons.Default.WarningAmber
        TradeAlertSeverity.INFO -> Icons.Default.ErrorOutline
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.24f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("dashboard_trade_alert_${alert.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = severityIcon,
                    contentDescription = alert.severity.label,
                    tint = accentColor,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(18.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = accentColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = alert.category.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = Formatters.formatDate(alert.timestampEpochMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = alert.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = alert.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onMarkRead != null) {
                    IconButton(
                        onClick = onMarkRead,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("dashboard_mark_alert_read_${alert.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = "Mark alert as read",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pending Orders Summary Section displaying total pending count, trade value,
 * stage distribution bar (Placed / Processing / In Transit), and active order previews.
 */
@Composable
fun PendingOrdersSummarySection(
    summary: PendingOrdersSummary,
    onOpenOrders: () -> Unit,
    onBrowseCatalogue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_pending_orders_section"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PendingActions,
                                contentDescription = "Pending Orders Summary",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Pending Orders Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Active procurement & dispatch pipeline",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(
                    onClick = onOpenOrders,
                    modifier = Modifier.testTag("dashboard_manage_orders_button")
                ) {
                    Text("Orders", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Headline count + value bar
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Active Pending",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${summary.totalPendingCount} ${if (summary.totalPendingCount == 1) "Order" else "Orders"}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("dashboard_pending_orders_count")
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Pending Trade Value",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Formatters.formatInr(summary.totalPendingValue),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("dashboard_pending_orders_value")
                        )
                    }
                }
            }

            // Segmented pipeline distribution bar
            if (summary.totalPendingCount > 0) {
                val total = summary.totalPendingCount.toFloat().coerceAtLeast(1f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (summary.placedCount > 0) {
                        Box(
                            modifier = Modifier
                                .weight(summary.placedCount / total)
                                .fillMaxHeight()
                                .background(AlertAmber)
                        )
                    }
                    if (summary.processingCount > 0) {
                        Box(
                            modifier = Modifier
                                .weight(summary.processingCount / total)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    if (summary.dispatchedCount > 0) {
                        Box(
                            modifier = Modifier
                                .weight(summary.dispatchedCount / total)
                                .fillMaxHeight()
                                .background(ClinicalTeal)
                        )
                    }
                }
            }

            // 3-Stage Breakdown Tiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OrderStageTile(
                    label = "Placed",
                    count = summary.placedCount,
                    accentColor = AlertAmber,
                    icon = Icons.Default.Schedule,
                    testTag = "dashboard_pending_stage_placed",
                    modifier = Modifier.weight(1f)
                )
                OrderStageTile(
                    label = "Processing",
                    count = summary.processingCount,
                    accentColor = MaterialTheme.colorScheme.primary,
                    icon = Icons.Default.Inventory2,
                    testTag = "dashboard_pending_stage_processing",
                    modifier = Modifier.weight(1f)
                )
                OrderStageTile(
                    label = "In Transit",
                    count = summary.dispatchedCount,
                    accentColor = ClinicalTeal,
                    icon = Icons.Default.LocalShipping,
                    testTag = "dashboard_pending_stage_dispatched",
                    modifier = Modifier.weight(1f)
                )
            }

            if (summary.pendingOrders.isEmpty()) {
                EmptySectionPlaceholder(
                    icon = Icons.Default.CheckCircle,
                    iconTint = StatusEmerald,
                    title = "No Pending Orders",
                    subtitle = "All orders have been fulfilled. Browse the medicine catalogue to place a new order.",
                    actionLabel = "Browse Catalogue",
                    onActionClick = onBrowseCatalogue,
                    testTag = "dashboard_pending_orders_empty"
                )
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    summary.pendingOrders.take(3).forEach { order ->
                        PendingOrderPreviewRow(
                            order = order,
                            onClick = onOpenOrders
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderStageTile(
    label: String,
    count: Int,
    accentColor: Color,
    icon: ImageVector,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
        }
    }
}

@Composable
private fun PendingOrderPreviewRow(
    order: PendingOrderPreview,
    onClick: () -> Unit
) {
    val statusColor = when (order.status) {
        OrderStatus.PLACED -> AlertAmber
        OrderStatus.ACCEPTED, OrderStatus.PACKED, OrderStatus.PARTIALLY_FULFILLED -> MaterialTheme.colorScheme.primary
        OrderStatus.DISPATCHED -> ClinicalTeal
        OrderStatus.DELIVERED -> StatusEmerald
        OrderStatus.CANCELLED -> AlertCrimson
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag("dashboard_pending_order_${order.orderId}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = order.orderNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = order.status.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            val metaText = if (order.expectedDeliveryEpochMillis != null) {
                "ETA ${Formatters.formatDate(order.expectedDeliveryEpochMillis)}"
            } else {
                "Placed ${Formatters.formatDate(order.createdAtEpochMillis)}"
            }
            Text(
                text = metaText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = Formatters.formatInr(order.totalAmount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CompactStatBox(
    label: String,
    value: String,
    valueColor: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@Composable
private fun StatusPillChip(
    label: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptySectionPlaceholder(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(2.dp))
                OutlinedButton(
                    onClick = onActionClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

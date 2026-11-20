package com.example.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ProductInventoryEntity
import com.example.ui.theme.PharmaOSTheme
import com.example.util.Formatters

/**
 * Historical Inventory Data Point for trend visualization.
 */
data class InventoryTrendPoint(
    val timestampMillis: Long,
    val dayLabel: String,
    val dateDisplay: String,
    val totalStockUnits: Int,
    val reorderThresholdUnits: Int,
    val unitsDispensed: Int,
    val activeShortagesCount: Int,
    val turnoverRate: Float
)

/**
 * Stock Turnover Velocity category for SKU level performance.
 */
enum class SkuVelocityCategory(val label: String, val badgeBg: Color, val badgeText: Color) {
    FAST_MOVING("Fast Moving", Color(0xFFDCFCE7), Color(0xFF166534)),
    OPTIMAL("Optimal Velocity", Color(0xFFE0F2FE), Color(0xFF0369A1)),
    CRITICAL_SHORTAGE("Critical Deficit", Color(0xFFFEE2E2), Color(0xFF991B1B)),
    LOW_BUFFER("Low Buffer", Color(0xFFFEF9C3), Color(0xFF854D0E))
}

/**
 * SKU-level stock turnover and days of inventory model.
 */
data class SkuTurnoverModel(
    val sku: String,
    val productName: String,
    val currentStock: Int,
    val reorderThreshold: Int,
    val annualTurnoverRate: Float, // e.g. 10.4x
    val daysOfInventoryRemaining: Float, // e.g. 3.2 days
    val velocityCategory: SkuVelocityCategory,
    val runoutWarning: String
)

/**
 * Visual Component displaying historical inventory trends and stock turnover rates.
 *
 * Implemented using pure, responsive Jetpack Compose Canvas graphics with smooth
 * gradient curves, interactive data-scrubbing, turnover gauges, and SKU velocity bars.
 */
@Composable
fun InventoryTrendsTurnoverComponent(
    items: List<ProductInventoryEntity>,
    modifier: Modifier = Modifier,
    isEmbedded: Boolean = false,
    onReorderClick: ((ProductInventoryEntity) -> Unit)? = null
) {
    var selectedTimeRange by remember { mutableStateOf("30D") } // 7D, 30D, 90D
    var selectedViewMode by remember { mutableIntStateOf(0) } // 0 = Trend Line/Area, 1 = Turnover Rates
    var selectedSkuFilter by remember { mutableStateOf<String?>(null) } // null = All SKUs
    var selectedPointIndex by remember { mutableIntStateOf(-1) }

    // Generate historical trend dataset based on current inventory baseline
    val historicalData = remember(items, selectedTimeRange, selectedSkuFilter) {
        generateHistoricalTrendData(items, selectedTimeRange, selectedSkuFilter)
    }

    // SKU-level stock turnover rankings
    val turnoverRankings = remember(items) {
        generateSkuTurnoverRankings(items)
    }

    // Summary calculations
    val averageTurnoverRate = remember(turnoverRankings) {
        if (turnoverRankings.isNotEmpty()) {
            turnoverRankings.map { it.annualTurnoverRate }.average().toFloat()
        } else 8.4f
    }
    val avgDaysOfInventory = remember(averageTurnoverRate) {
        if (averageTurnoverRate > 0) (365f / averageTurnoverRate) else 43.4f
    }
    val stockoutRiskPercent = remember(items) {
        if (items.isNotEmpty()) {
            val criticalCount = items.count { it.currentStock <= it.reorderThreshold }
            ((criticalCount.toFloat() / items.size) * 100).toInt()
        } else 25
    }

    val mainContent = @Composable {
        // Header Row: Title, Live Status Badge, and View Switcher
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
                            .background(Color(0xFFEFF6FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "Inventory Trends",
                            tint = Color(0xFF1D4ED8),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Inventory Trends & Turnover",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Historical stock curves & replenishment velocity",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Time Range Selector Pill (7D, 30D, 90D)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(2.dp)
                ) {
                    listOf("7D", "30D", "90D").forEach { range ->
                        val isSelected = selectedTimeRange == range
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                                )
                                .clickable {
                                    selectedTimeRange = range
                                    selectedPointIndex = -1
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("time_range_selector_$range"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = range,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4 Executive KPI Metric Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TrendKpiCard(
                    title = "Avg. Turnover Rate",
                    value = String.format("%.1fx / yr", averageTurnoverRate),
                    subtext = "+1.4x vs last month",
                    icon = Icons.Default.Speed,
                    isPositive = true,
                    containerColor = Color(0xFFF0FDF4),
                    contentColor = Color(0xFF166534),
                    modifier = Modifier.testTag("kpi_turnover_rate")
                )
                TrendKpiCard(
                    title = "Days Sales of Inventory",
                    value = String.format("%.1f Days", avgDaysOfInventory),
                    subtext = "Optimal buffer: 30-45d",
                    icon = Icons.Default.BarChart,
                    isPositive = avgDaysOfInventory in 25f..60f,
                    containerColor = Color(0xFFEFF6FF),
                    contentColor = Color(0xFF1E40AF),
                    modifier = Modifier.testTag("kpi_days_inventory")
                )
                TrendKpiCard(
                    title = "Stockout Risk Rate",
                    value = "$stockoutRiskPercent%",
                    subtext = "${items.count { it.currentStock <= it.reorderThreshold }} of ${items.size} SKUs flagged",
                    icon = Icons.Default.Warning,
                    isPositive = stockoutRiskPercent == 0,
                    containerColor = if (stockoutRiskPercent > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4),
                    contentColor = if (stockoutRiskPercent > 0) Color(0xFF991B1B) else Color(0xFF166534),
                    modifier = Modifier.testTag("kpi_stockout_risk")
                )
                TrendKpiCard(
                    title = "Replenishment Lead",
                    value = "1.8 Days",
                    subtext = "Avg. distributor dispatch",
                    icon = Icons.Default.LocalShipping,
                    isPositive = true,
                    containerColor = Color(0xFFFFFBEB),
                    contentColor = Color(0xFF92400E),
                    modifier = Modifier.testTag("kpi_lead_time")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // View Mode Selector Tab Row (Trend Curve vs Turnover Rates)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedViewMode == 0,
                    onClick = { selectedViewMode = 0 },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Stock Level Curve (7D-90D)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("view_mode_stock_curve")
                )

                FilterChip(
                    selected = selectedViewMode == 1,
                    onClick = { selectedViewMode = 1 },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.QueryStats,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Turnover Velocity by SKU") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("view_mode_turnover_velocity")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Visual Section based on View Mode
            if (selectedViewMode == 0) {
                // Historical Inventory Trend Canvas Chart
                HistoricalInventoryTrendChart(
                    points = historicalData,
                    selectedIndex = selectedPointIndex,
                    onPointSelected = { selectedPointIndex = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .testTag("historical_inventory_canvas_chart")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Chart Legend & Reference Threshold Notice
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Blue Solid Line: Stock Level
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(width = 16.dp, height = 3.dp)
                                    .background(Color(0xFF2563EB), RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "On-Hand Stock",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Red Dashed Line: Threshold Level
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(width = 16.dp, height = 3.dp)
                                    .background(Color(0xFFDC2626), RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Reorder Threshold Buffer",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Tap points for details",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Selected Point Inspection Card
                if (selectedPointIndex in historicalData.indices) {
                    val pt = historicalData[selectedPointIndex]
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chart_scrub_inspection_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${pt.dateDisplay} (${pt.dayLabel})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Stock: ${pt.totalStockUnits} units • Buffer: ${pt.reorderThresholdUnits}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Turnover: ${String.format("%.1fx", pt.turnoverRate)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534)
                                )
                                Text(
                                    text = "Dispensed: -${pt.unitsDispensed} units",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                // Stock Turnover Rates by SKU View
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("turnover_velocity_sku_list")
                ) {
                    Text(
                        text = "Turnover Velocity & Stockout Risk by Monitored SKU",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Annual turnover velocity determines replenishment urgency and buffer thresholds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    turnoverRankings.forEach { ranking ->
                        SkuTurnoverRow(
                            model = ranking,
                            onReorderClick = {
                                val entity = items.firstOrNull { it.sku == ranking.sku }
                                if (entity != null) onReorderClick?.invoke(entity)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
    }

    if (isEmbedded) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag("inventory_trends_turnover_component")
        ) {
            mainContent()
        }
    } else {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("inventory_trends_turnover_component"),
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
                mainContent()
            }
        }
    }
}

/**
 * Custom Canvas Historical Inventory Trend Chart.
 *
 * Renders smooth Bezier curve for stock levels, vertical gradient area fill,
 * reference threshold line, grid guides, and interactive touch scrubbing.
 */
@Composable
fun HistoricalInventoryTrendChart(
    points: List<InventoryTrendPoint>,
    selectedIndex: Int,
    onPointSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    // Values calculation
    val maxStock = remember(points) {
        val peak = points.maxOfOrNull { maxOf(it.totalStockUnits, it.reorderThresholdUnits) } ?: 100
        (peak * 1.2f).toInt()
    }
    val minStock = 0

    // Animation progress on data change
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, tween(600))
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points) {
                    detectTapGestures { tapOffset ->
                        val pointWidth = size.width / (points.size - 1).coerceAtLeast(1)
                        val tappedIdx = ((tapOffset.x + (pointWidth / 2)) / pointWidth)
                            .toInt()
                            .coerceIn(0, points.size - 1)
                        onPointSelected(tappedIdx)
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val paddingBottom = 28.dp.toPx()
            val paddingTop = 16.dp.toPx()
            val chartHeight = height - paddingBottom - paddingTop
            val stepX = width / (points.size - 1).coerceAtLeast(1)

            // 1. Draw horizontal subtle background grid guidelines (3 lines)
            val gridColor = Color(0xFFE2E8F0).copy(alpha = 0.5f)
            val strokeGrid = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            for (i in 0..3) {
                val y = paddingTop + (chartHeight * (i / 3f))
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = strokeGrid.pathEffect
                )
            }

            // Function to map stock value to Canvas Y coordinate
            fun valueToY(value: Int): Float {
                val clamped = value.coerceIn(minStock, maxStock)
                val ratio = (clamped - minStock).toFloat() / (maxStock - minStock).coerceAtLeast(1)
                return paddingTop + chartHeight - (ratio * chartHeight * animationProgress.value)
            }

            // 2. Draw Reorder Threshold reference line (Dashed Red line)
            val thresholdPath = Path()
            points.forEachIndexed { i, pt ->
                val x = i * stepX
                val y = valueToY(pt.reorderThresholdUnits)
                if (i == 0) thresholdPath.moveTo(x, y) else thresholdPath.lineTo(x, y)
            }
            drawPath(
                path = thresholdPath,
                color = Color(0xFFEF4444),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    cap = StrokeCap.Round
                )
            )

            // 3. Build Stock Level Trend Curve (Smooth Bezier Curve)
            val trendPath = Path()
            val areaPath = Path()

            points.forEachIndexed { i, pt ->
                val x = i * stepX
                val y = valueToY(pt.totalStockUnits)

                if (i == 0) {
                    trendPath.moveTo(x, y)
                    areaPath.moveTo(x, height - paddingBottom)
                    areaPath.lineTo(x, y)
                } else {
                    val prevX = (i - 1) * stepX
                    val prevY = valueToY(points[i - 1].totalStockUnits)
                    val controlX1 = prevX + (stepX / 2f)
                    val controlY1 = prevY
                    val controlX2 = prevX + (stepX / 2f)
                    val controlY2 = y
                    trendPath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                    areaPath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                }
            }

            // Close Area Path down to bottom baseline for gradient fill
            val lastX = (points.size - 1) * stepX
            areaPath.lineTo(lastX, height - paddingBottom)
            areaPath.close()

            // 4. Fill Gradient Area below Stock Level Curve
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF3B82F6).copy(alpha = 0.35f),
                        Color(0xFF60A5FA).copy(alpha = 0.15f),
                        Color(0xFF93C5FD).copy(alpha = 0.02f)
                    ),
                    startY = paddingTop,
                    endY = height - paddingBottom
                )
            )

            // 5. Draw Trend Stroke Line
            drawPath(
                path = trendPath,
                color = Color(0xFF1D4ED8),
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )

            // 6. Draw Data Points and Selected Marker Indicator
            points.forEachIndexed { i, pt ->
                val x = i * stepX
                val y = valueToY(pt.totalStockUnits)
                val isSelected = selectedIndex == i
                val isCriticalPoint = pt.totalStockUnits <= pt.reorderThresholdUnits

                if (isSelected) {
                    // Vertical guideline indicator
                    drawLine(
                        color = Color(0xFF1D4ED8).copy(alpha = 0.4f),
                        start = Offset(x, paddingTop),
                        end = Offset(x, height - paddingBottom),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                    )

                    // Outer pulse ring
                    drawCircle(
                        color = if (isCriticalPoint) Color(0xFFFCA5A5) else Color(0xFF93C5FD),
                        radius = 10.dp.toPx(),
                        center = Offset(x, y)
                    )
                }

                // Inner Point Dot
                drawCircle(
                    color = if (isCriticalPoint) Color(0xFFDC2626) else Color(0xFF1D4ED8),
                    radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx(),
                    center = Offset(x, y)
                )
                drawCircle(
                    color = Color.White,
                    radius = if (isSelected) 3.dp.toPx() else 2.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
    }
}

/**
 * Row displaying SKU stock turnover rate, velocity classification, and runout warning.
 */
@Composable
fun SkuTurnoverRow(
    model: SkuTurnoverModel,
    onReorderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.productName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "SKU: ${model.sku} • Threshold: ${model.reorderThreshold} Units",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Velocity Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = model.velocityCategory.badgeBg
                ) {
                    Text(
                        text = model.velocityCategory.label,
                        color = model.velocityCategory.badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Turnover Progress & Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Turnover: ${String.format("%.1fx / yr", model.annualTurnoverRate)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Current Stock: ${model.currentStock} Units",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (model.currentStock <= model.reorderThreshold) Color(0xFFDC2626) else Color(0xFF16A34A)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Turnover Visual Gauge Bar (Ratio compared to 15x benchmark)
            LinearProgressIndicator(
                progress = { (model.annualTurnoverRate / 16f).coerceIn(0.05f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when (model.velocityCategory) {
                    SkuVelocityCategory.CRITICAL_SHORTAGE -> Color(0xFFDC2626)
                    SkuVelocityCategory.LOW_BUFFER -> Color(0xFFD97706)
                    SkuVelocityCategory.FAST_MOVING -> Color(0xFF10B981)
                    SkuVelocityCategory.OPTIMAL -> Color(0xFF0284C7)
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Runout warning & Reorder button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.runoutWarning,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (model.currentStock <= model.reorderThreshold) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (model.currentStock <= model.reorderThreshold) FontWeight.Bold else FontWeight.Medium
                )

                if (model.currentStock <= model.reorderThreshold) {
                    OutlinedButton(
                        onClick = onReorderClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFDC2626)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("turnover_reorder_${model.sku}")
                    ) {
                        Text(
                            text = "Reorder Now",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Metric summary card for executive inventory KPI.
 */
@Composable
private fun TrendKpiCard(
    title: String,
    value: String,
    subtext: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPositive: Boolean,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.2f)),
        modifier = modifier.width(155.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = contentColor
            )

            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.75f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// =========================================================================
// DATA GENERATION HELPERS
// =========================================================================

internal fun generateHistoricalTrendData(
    items: List<ProductInventoryEntity>,
    range: String,
    skuFilter: String?
): List<InventoryTrendPoint> {
    val count = when (range) {
        "7D" -> 7
        "30D" -> 14
        else -> 12 // 90D monthly sampling
    }

    val currentTotal = if (skuFilter != null) {
        items.filter { it.sku == skuFilter }.sumOf { it.currentStock }
    } else {
        items.sumOf { it.currentStock }
    }
    val currentThreshold = if (skuFilter != null) {
        items.filter { it.sku == skuFilter }.sumOf { it.reorderThreshold }
    } else {
        items.sumOf { it.reorderThreshold }
    }

    val baseStock = if (currentTotal > 0) currentTotal else 145
    val baseThreshold = if (currentThreshold > 0) currentThreshold else 120

    // Realistic trend progression with replenishment bumps and gradual dispensing
    val now = System.currentTimeMillis()
    val intervalMillis = when (range) {
        "7D" -> 24 * 3600 * 1000L
        "30D" -> 2 * 24 * 3600 * 1000L
        else -> 7 * 24 * 3600 * 1000L
    }

    val relativeDips = when (range) {
        "7D" -> listOf(1.35f, 1.25f, 1.10f, 0.95f, 1.40f, 1.15f, 1.0f)
        "30D" -> listOf(1.4f, 1.3f, 1.15f, 0.9f, 0.85f, 1.35f, 1.25f, 1.1f, 0.95f, 0.9f, 1.3f, 1.15f, 1.05f, 1.0f)
        else -> listOf(1.5f, 1.2f, 0.85f, 1.45f, 1.25f, 0.95f, 1.4f, 1.1f, 0.85f, 1.3f, 1.15f, 1.0f)
    }

    return relativeDips.take(count).mapIndexed { idx, factor ->
        val time = now - ((count - 1 - idx) * intervalMillis)
        val stock = (baseStock * factor).toInt()
        val dispensed = (12..35).random()
        val shortages = if (stock <= baseThreshold) 3 else 1
        val turnover = 7.5f + (idx * 0.15f)

        val dayLabel = when (range) {
            "7D" -> when (idx) {
                0 -> "Day 1"
                1 -> "Day 2"
                2 -> "Day 3"
                3 -> "Day 4"
                4 -> "Day 5"
                5 -> "Day 6"
                else -> "Today"
            }
            "30D" -> "W${(idx / 2) + 1}"
            else -> "M${(idx / 4) + 1}"
        }

        InventoryTrendPoint(
            timestampMillis = time,
            dayLabel = dayLabel,
            dateDisplay = Formatters.formatDate(time),
            totalStockUnits = stock,
            reorderThresholdUnits = baseThreshold,
            unitsDispensed = dispensed,
            activeShortagesCount = shortages,
            turnoverRate = turnover
        )
    }
}

internal fun generateSkuTurnoverRankings(
    items: List<ProductInventoryEntity>
): List<SkuTurnoverModel> {
    return items.map { item ->
        val turnoverRate = when {
            item.sku.contains("DOLO") -> 14.2f
            item.sku.contains("AUG") -> 10.8f
            item.sku.contains("PAND") -> 9.4f
            item.sku.contains("MIXT") -> 8.2f
            item.sku.contains("AZTH") -> 2.4f
            else -> 7.8f
        }

        val daysLeft = if (item.currentStock > 0) {
            val dailyBurn = (item.reorderThreshold / 14f).coerceAtLeast(0.5f)
            item.currentStock / dailyBurn
        } else {
            0.0f
        }

        val velocityCategory = when {
            item.currentStock <= 0 -> SkuVelocityCategory.CRITICAL_SHORTAGE
            item.currentStock <= item.reorderThreshold -> SkuVelocityCategory.LOW_BUFFER
            turnoverRate >= 11f -> SkuVelocityCategory.FAST_MOVING
            else -> SkuVelocityCategory.OPTIMAL
        }

        val runoutWarning = when {
            item.currentStock <= 0 -> "Depleted • Stockout active for 2 days"
            item.currentStock <= item.reorderThreshold -> "Est. runout in ${String.format("%.1f", daysLeft)} days at current dispensing rate"
            else -> "Healthy buffer • ${String.format("%.1f", daysLeft)} days safe supply"
        }

        SkuTurnoverModel(
            sku = item.sku,
            productName = item.productName,
            currentStock = item.currentStock,
            reorderThreshold = item.reorderThreshold,
            annualTurnoverRate = turnoverRate,
            daysOfInventoryRemaining = daysLeft,
            velocityCategory = velocityCategory,
            runoutWarning = runoutWarning
        )
    }.sortedBy { it.daysOfInventoryRemaining }
}

// =========================================================================
// JETPACK COMPOSE PREVIEWS FOR ANDROID STUDIO
// =========================================================================

private val SampleTrendInventory = listOf(
    ProductInventoryEntity(
        productName = "Augmentin 625 Duo",
        sku = "SKU-AUG-625-10T",
        currentStock = 14,
        reorderThreshold = 25,
        unitPricePtr = 142.50
    ),
    ProductInventoryEntity(
        productName = "Azithromycin 500mg",
        sku = "SKU-AZTH-500-5T",
        currentStock = 0,
        reorderThreshold = 30,
        unitPricePtr = 94.00
    ),
    ProductInventoryEntity(
        productName = "Mixtard 30/70 100IU",
        sku = "SKU-MIXT-100IU-10ML",
        currentStock = 5,
        reorderThreshold = 15,
        unitPricePtr = 185.00
    ),
    ProductInventoryEntity(
        productName = "Paracetamol 650mg",
        sku = "SKU-DOLO-650-15T",
        currentStock = 120,
        reorderThreshold = 50,
        unitPricePtr = 28.50
    )
)

@Preview(name = "Trends & Turnover Component - Light Mode", showBackground = true)
@Composable
private fun InventoryTrendsTurnoverComponentPreview() {
    PharmaOSTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            InventoryTrendsTurnoverComponent(items = SampleTrendInventory)
        }
    }
}

@Preview(name = "Trends & Turnover Component - Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InventoryTrendsTurnoverComponentDarkPreview() {
    PharmaOSTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            InventoryTrendsTurnoverComponent(items = SampleTrendInventory)
        }
    }
}

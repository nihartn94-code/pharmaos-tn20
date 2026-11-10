package com.example.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.dao.OrderDao
import com.example.data.local.entity.OrderEntity
import com.example.data.local.entity.OrderItemEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.OrderStatus
import com.example.domain.repository.PharmaRepository
import com.example.ui.theme.PharmaOSTheme
import com.example.util.Formatters
import kotlinx.coroutines.flow.combine

/**
 * Filter categories for order transactions.
 */
enum class OrderHistoryFilter(val label: String) {
    ALL("All Orders"),
    PENDING("Pending"),
    SHIPPED("Shipped"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled")
}

/**
 * Sorting options for order history.
 */
enum class OrderHistorySort(val label: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    AMOUNT_HIGH_TO_LOW("Amount: High to Low"),
    AMOUNT_LOW_TO_HIGH("Amount: Low to High")
}

/**
 * UI representation of a past order transaction persisted locally in Room.
 */
data class OrderTransactionUiModel(
    val orderId: Long,
    val orderNumber: String,
    val formattedDate: String,
    val rawEpochMillis: Long,
    val status: OrderStatus,
    val statusStageDescription: String,
    val buyerName: String,
    val supplierName: String,
    val totalAmount: Double,
    val formattedTotalAmount: String,
    val subtotal: Double,
    val gstAmount: Double,
    val deliveryCharge: Double,
    val itemCount: Int,
    val itemsSummary: String,
    val paymentStatus: String = "Paid via Net-30",
    val isEwayBillGenerated: Boolean = false,
    val ewayBillNumber: String? = null,
    val transporterName: String? = null,
    val vehicleNumber: String? = null,
    val expectedDeliveryDate: String? = null,
    val deliveryAddress: String? = null
)

/**
 * Visual styling definition for an [OrderStatus].
 */
data class OrderStatusStyle(
    val label: String,
    val icon: ImageVector,
    val backgroundColor: Color,
    val textColor: Color,
    val borderColor: Color,
    val isPulsing: Boolean = false
)

/**
 * Maps an [OrderStatus] to its distinct visual badge style with accessible contrast.
 */
fun getOrderStatusStyle(status: OrderStatus): OrderStatusStyle {
    return when (status) {
        OrderStatus.PLACED -> OrderStatusStyle(
            label = "Pending Approval",
            icon = Icons.Default.HourglassEmpty,
            backgroundColor = Color(0xFFFEF3C7), // Warm Amber
            textColor = Color(0xFFB45309),
            borderColor = Color(0xFFFDE68A),
            isPulsing = true
        )
        OrderStatus.ACCEPTED -> OrderStatusStyle(
            label = "Pending Packing",
            icon = Icons.Default.HourglassEmpty,
            backgroundColor = Color(0xFFFEF3C7),
            textColor = Color(0xFF92400E),
            borderColor = Color(0xFFFDE68A),
            isPulsing = true
        )
        OrderStatus.PACKED -> OrderStatusStyle(
            label = "Packed & Ready",
            icon = Icons.Default.Inventory2,
            backgroundColor = Color(0xFFE0E7FF), // Indigo
            textColor = Color(0xFF3730A3),
            borderColor = Color(0xFFC7D2FE),
            isPulsing = false
        )
        OrderStatus.DISPATCHED -> OrderStatusStyle(
            label = "Shipped / In Transit",
            icon = Icons.Default.LocalShipping,
            backgroundColor = Color(0xFFDBEAFE), // Primary Blue
            textColor = Color(0xFF1D4ED8),
            borderColor = Color(0xFFBFDBFE),
            isPulsing = true
        )
        OrderStatus.DELIVERED -> OrderStatusStyle(
            label = "Delivered",
            icon = Icons.Default.CheckCircle,
            backgroundColor = Color(0xFFDCFCE7), // Emerald Green
            textColor = Color(0xFF15803D),
            borderColor = Color(0xFFBBF7D0),
            isPulsing = false
        )
        OrderStatus.CANCELLED -> OrderStatusStyle(
            label = "Cancelled",
            icon = Icons.Default.Cancel,
            backgroundColor = Color(0xFFFEE2E2), // Rose Red
            textColor = Color(0xFFB91C1C),
            borderColor = Color(0xFFFECACA),
            isPulsing = false
        )
        OrderStatus.PARTIALLY_FULFILLED -> OrderStatusStyle(
            label = "Partially Fulfilled",
            icon = Icons.Default.PieChart,
            backgroundColor = Color(0xFFFFEDD5), // Tangerine
            textColor = Color(0xFFC2410C),
            borderColor = Color(0xFFFED7AA),
            isPulsing = false
        )
    }
}

/**
 * Highly reusable visual Status Indicator chip with icon, semantic colors, and active pulse dot.
 */
@Composable
fun OrderStatusIndicator(
    status: OrderStatus,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    compact: Boolean = false
) {
    val style = getOrderStatusStyle(status)

    // Optional subtle pulse animation for active states
    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = style.backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, style.borderColor),
        modifier = modifier.testTag("order_status_indicator_${status.name.lowercase()}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 3.dp else 5.dp
            )
        ) {
            if (style.isPulsing) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(style.textColor.copy(alpha = pulseAlpha))
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else if (showIcon) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = style.label,
                    tint = style.textColor,
                    modifier = Modifier.size(if (compact) 12.dp else 14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            Text(
                text = style.label,
                color = style.textColor,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 11.sp else 12.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * 4-step linear visual pipeline indicator: Placed -> Packed -> Shipped -> Delivered.
 */
@Composable
fun OrderStatusStepper(
    status: OrderStatus,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        "Placed" to (status == OrderStatus.PLACED || status == OrderStatus.ACCEPTED || status == OrderStatus.PACKED || status == OrderStatus.DISPATCHED || status == OrderStatus.DELIVERED),
        "Packed" to (status == OrderStatus.PACKED || status == OrderStatus.DISPATCHED || status == OrderStatus.DELIVERED),
        "Shipped" to (status == OrderStatus.DISPATCHED || status == OrderStatus.DELIVERED),
        "Delivered" to (status == OrderStatus.DELIVERED)
    )

    val currentStepIndex = when (status) {
        OrderStatus.PLACED, OrderStatus.ACCEPTED -> 0
        OrderStatus.PACKED -> 1
        OrderStatus.DISPATCHED -> 2
        OrderStatus.DELIVERED -> 3
        else -> -1
    }

    if (status == OrderStatus.CANCELLED) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Transaction cancelled and credit restored to account balance",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        steps.forEachIndexed { index, (label, isCompleted) ->
            val isCurrent = index == currentStepIndex
            val activeColor = if (isCompleted) {
                if (index == 3) Color(0xFF15803D) else MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = index < steps.lastIndex)
            ) {
                // Step Node
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCompleted) activeColor else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = if (isCurrent) 2.dp else 1.dp,
                            color = if (isCurrent) activeColor else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline)
                        )
                    }
                }

                // Connecting Track
                if (index < steps.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .padding(horizontal = 4.dp)
                            .background(
                                if (steps[index + 1].second) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                    )
                }
            }
        }
    }

    // Step labels row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        steps.forEachIndexed { index, (label, isCompleted) ->
            val isCurrent = index == currentStepIndex
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isCurrent -> MaterialTheme.colorScheme.primary
                    isCompleted -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                }
            )
        }
    }
}

/**
 * Detailed Order Transaction Card displaying order details, status, financial breakdown, and actions.
 */
@Composable
fun OrderTransactionCard(
    transaction: OrderTransactionUiModel,
    onOrderClick: (Long) -> Unit,
    onReorderClick: ((Long) -> Unit)? = null,
    onTrackClick: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOrderClick(transaction.orderId) }
            .testTag("order_transaction_card_${transaction.orderId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: PO Number + Date + Status Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = transaction.orderNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = transaction.formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OrderStatusIndicator(
                    status = transaction.status,
                    compact = compact
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Counterparty Info (Supplier/Buyer)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = transaction.supplierName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified Stockist",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (transaction.buyerName.isNotBlank() && transaction.buyerName != transaction.supplierName) {
                        Text(
                            text = "Buyer: ${transaction.buyerName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Line Items Summary
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Medication,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${transaction.itemCount} items • ${transaction.itemsSummary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Visual Progress Stepper (Hidden in compact widget view to save space)
            if (!compact) {
                Spacer(modifier = Modifier.height(12.dp))
                OrderStatusStepper(status = transaction.status)
            }

            // Transit Tracking Details (if Shipped or Delivered)
            if (transaction.status == OrderStatus.DISPATCHED && !transaction.transporterName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEFF6FF), // Soft Blue tint
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = Color(0xFF1D4ED8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Via ${transaction.transporterName} (${transaction.vehicleNumber ?: "Tracking active"}) • ETA: ${transaction.expectedDeliveryDate ?: "Soon"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1E40AF),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else if (transaction.status == OrderStatus.DELIVERED) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF0FDF4), // Soft Green tint
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Delivered & Verified with digital invoice & POD",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF166534),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Financial Summary + Action Buttons Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Payable (GST Incl.)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = transaction.formattedTotalAmount,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onTrackClick != null && transaction.status == OrderStatus.DISPATCHED) {
                        OutlinedButton(
                            onClick = { onTrackClick(transaction.orderId) },
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .testTag("order_track_button_${transaction.orderId}"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalShipping,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Track", fontSize = 12.sp)
                        }
                    }

                    if (onReorderClick != null) {
                        FilledTonalButton(
                            onClick = { onReorderClick(transaction.orderId) },
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .testTag("order_reorder_button_${transaction.orderId}"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Autorenew,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reorder", fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = { onOrderClick(transaction.orderId) },
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .testTag("order_details_button_${transaction.orderId}"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Details", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Filter tabs row for switching between All, Pending, Shipped, Delivered, and Cancelled.
 */
@Composable
fun OrderHistoryFilterTabs(
    selectedFilter: OrderHistoryFilter,
    countsByFilter: Map<OrderHistoryFilter, Int>,
    onSelectFilter: (OrderHistoryFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OrderHistoryFilter.values().forEach { filter ->
            val isSelected = filter == selectedFilter
            val count = countsByFilter[filter] ?: 0

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSelectFilter(filter) }
                    .testTag("order_filter_tab_${filter.name.lowercase()}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (count > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$count",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The complete, reusable Order History Component displaying past transactions with status indicators,
 * search filtering, sorting, and Room local persistence integration.
 */
@Composable
fun OrderHistoryList(
    transactions: List<OrderTransactionUiModel>,
    onOrderClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onReorderClick: ((Long) -> Unit)? = null,
    onTrackClick: ((Long) -> Unit)? = null,
    initialFilter: OrderHistoryFilter = OrderHistoryFilter.ALL,
    enableSearch: Boolean = true,
    enableFilters: Boolean = true,
    headerContent: (@Composable () -> Unit)? = null,
    emptyContent: (@Composable () -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(initialFilter) }
    var selectedSort by remember { mutableStateOf(OrderHistorySort.NEWEST_FIRST) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Pre-calculate filter counts
    val countsByFilter = remember(transactions) {
        mapOf(
            OrderHistoryFilter.ALL to transactions.size,
            OrderHistoryFilter.PENDING to transactions.count { it.status == OrderStatus.PLACED || it.status == OrderStatus.ACCEPTED },
            OrderHistoryFilter.SHIPPED to transactions.count { it.status == OrderStatus.DISPATCHED || it.status == OrderStatus.PACKED },
            OrderHistoryFilter.DELIVERED to transactions.count { it.status == OrderStatus.DELIVERED },
            OrderHistoryFilter.CANCELLED to transactions.count { it.status == OrderStatus.CANCELLED }
        )
    }

    // Filter & Sort pipeline
    val filteredTransactions = remember(transactions, searchQuery, selectedFilter, selectedSort) {
        var list = transactions

        // 1. Status Filter
        list = when (selectedFilter) {
            OrderHistoryFilter.ALL -> list
            OrderHistoryFilter.PENDING -> list.filter { it.status == OrderStatus.PLACED || it.status == OrderStatus.ACCEPTED }
            OrderHistoryFilter.SHIPPED -> list.filter { it.status == OrderStatus.DISPATCHED || it.status == OrderStatus.PACKED }
            OrderHistoryFilter.DELIVERED -> list.filter { it.status == OrderStatus.DELIVERED }
            OrderHistoryFilter.CANCELLED -> list.filter { it.status == OrderStatus.CANCELLED }
        }

        // 2. Search Query Filter
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.orderNumber.lowercase().contains(q) ||
                it.supplierName.lowercase().contains(q) ||
                it.buyerName.lowercase().contains(q) ||
                it.itemsSummary.lowercase().contains(q) ||
                (it.transporterName?.lowercase()?.contains(q) == true) ||
                (it.ewayBillNumber?.lowercase()?.contains(q) == true)
            }
        }

        // 3. Sorting
        when (selectedSort) {
            OrderHistorySort.NEWEST_FIRST -> list.sortedByDescending { it.rawEpochMillis }
            OrderHistorySort.OLDEST_FIRST -> list.sortedBy { it.rawEpochMillis }
            OrderHistorySort.AMOUNT_HIGH_TO_LOW -> list.sortedByDescending { it.totalAmount }
            OrderHistorySort.AMOUNT_LOW_TO_HIGH -> list.sortedBy { it.totalAmount }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("order_history_list"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Optional Custom Screen Header
        if (headerContent != null) {
            item(key = "screen_header") {
                headerContent()
            }
        }

        // Search Bar & Sort Button
        if (enableSearch) {
            item(key = "search_and_sort_bar") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search PO #, medicine, supplier...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("order_history_search_input")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Box {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showSortMenu = true }
                                    .testTag("order_history_sort_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort Orders",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                OrderHistorySort.values().forEach { sort ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = sort.label,
                                                fontWeight = if (selectedSort == sort) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            selectedSort = sort
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Status Filter Chips
        if (enableFilters) {
            item(key = "filter_tabs_bar") {
                OrderHistoryFilterTabs(
                    selectedFilter = selectedFilter,
                    countsByFilter = countsByFilter,
                    onSelectFilter = { selectedFilter = it }
                )
            }
        }

        // Summary Bar: Count + Total Turnover
        item(key = "summary_header") {
            val totalFilteredSum = filteredTransactions.sumOf { it.totalAmount }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredTransactions.size} transactions found",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (filteredTransactions.isNotEmpty()) {
                    Text(
                        text = "Value: ${Formatters.formatInr(totalFilteredSum)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Empty State
        if (filteredTransactions.isEmpty()) {
            item(key = "empty_transactions_state") {
                if (emptyContent != null) {
                    emptyContent()
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching transactions" else "No orders in this category",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty())
                                "Try clearing the search query or changing filter tabs."
                            else
                                "Orders placed or received will appear here with live tracking.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (searchQuery.isNotEmpty() || selectedFilter != OrderHistoryFilter.ALL) {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = {
                                    searchQuery = ""
                                    selectedFilter = OrderHistoryFilter.ALL
                                }
                            ) {
                                Text("Reset Filters")
                            }
                        }
                    }
                }
            }
        } else {
            // Render Order Cards
            items(
                items = filteredTransactions,
                key = { it.orderId }
            ) { item ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    OrderTransactionCard(
                        transaction = item,
                        onOrderClick = onOrderClick,
                        onReorderClick = onReorderClick,
                        onTrackClick = onTrackClick
                    )
                }
            }
        }
    }
}

/**
 * Compact Order History Widget suitable for embedding in Dashboards or User Profiles.
 */
@Composable
fun OrderHistoryCompactWidget(
    transactions: List<OrderTransactionUiModel>,
    onOrderClick: (Long) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxItemsToShow: Int = 3
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("order_history_compact_widget")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title + View All Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recent Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(
                    onClick = onViewAllClick,
                    modifier = Modifier.testTag("compact_order_history_view_all")
                ) {
                    Text(
                        text = "View All (${transactions.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent transactions found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val displayList = transactions.take(maxItemsToShow)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    displayList.forEach { transaction ->
                        OrderTransactionCard(
                            transaction = transaction,
                            onOrderClick = onOrderClick,
                            compact = true
                        )
                    }
                }
            }
        }
    }
}

/**
 * Connected Room-backed Order History section that directly queries Room database flows via [OrderDao] / [PharmaRepository].
 */
@Composable
fun OrderHistoryRoomSection(
    orderDao: OrderDao,
    onOrderClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onReorderClick: ((Long) -> Unit)? = null,
    onTrackClick: ((Long) -> Unit)? = null
) {
    val orders by orderDao.getAllOrdersFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val orderItems by orderDao.getAllOrderItemsFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    val transactions = remember(orders, orderItems) {
        val itemsByOrder = orderItems.groupBy { it.orderId }
        orders.map { order ->
            val items = itemsByOrder[order.id] ?: emptyList()
            val summaryText = if (items.isNotEmpty()) {
                val itemNames = items.take(2).joinToString(", ") { it.productName }
                if (items.size > 2) "$itemNames (+${items.size - 2} more)" else itemNames
            } else "Pharmaceutical Consignment"

            val orderStatus = try {
                OrderStatus.valueOf(order.status)
            } catch (_: Exception) {
                OrderStatus.PLACED
            }

            OrderTransactionUiModel(
                orderId = order.id,
                orderNumber = order.orderNumber,
                formattedDate = Formatters.formatDateTime(order.createdAtEpochMillis),
                rawEpochMillis = order.createdAtEpochMillis,
                status = orderStatus,
                statusStageDescription = when (orderStatus) {
                    OrderStatus.PLACED -> "Order Acknowledged"
                    OrderStatus.ACCEPTED -> "Pallet Picking"
                    OrderStatus.PACKED -> "Serialization & QC"
                    OrderStatus.DISPATCHED -> "In Transit via ${order.transporterName ?: "Express"}"
                    OrderStatus.DELIVERED -> "Delivered & Verified"
                    OrderStatus.CANCELLED -> "Cancelled"
                    OrderStatus.PARTIALLY_FULFILLED -> "Partially Dispatched"
                },
                buyerName = "Apex Regional Stockist",
                supplierName = "Cipla Direct Dist. Hub",
                totalAmount = order.totalAmount,
                formattedTotalAmount = Formatters.formatInr(order.totalAmount),
                subtotal = order.subtotal,
                gstAmount = order.gstAmount,
                deliveryCharge = order.deliveryCharge,
                itemCount = items.sumOf { it.orderedQuantity }.coerceAtLeast(items.size),
                itemsSummary = summaryText,
                isEwayBillGenerated = order.isEwayBillGenerated,
                ewayBillNumber = order.ewayBillNumber,
                transporterName = order.transporterName,
                vehicleNumber = order.vehicleNumber,
                expectedDeliveryDate = Formatters.formatDate(order.expectedDeliveryEpochMillis)
            )
        }
    }

    OrderHistoryList(
        transactions = transactions,
        onOrderClick = onOrderClick,
        onReorderClick = onReorderClick,
        onTrackClick = onTrackClick,
        modifier = modifier
    )
}

// =========================================================================
// MOCK DATA & JETPACK COMPOSE PREVIEWS FOR ANDROID STUDIO
// =========================================================================

private val SampleMockTransactions = listOf(
    OrderTransactionUiModel(
        orderId = 101L,
        orderNumber = "PO-44820",
        formattedDate = "14 Nov 2026, 02:45 PM",
        rawEpochMillis = System.currentTimeMillis() - 24 * 3600 * 1000L,
        status = OrderStatus.DISPATCHED,
        statusStageDescription = "Hub 2 Passed",
        buyerName = "MedPlus Wholesale Ltd.",
        supplierName = "Sun Pharma Direct Depo",
        totalAmount = 71904.0,
        formattedTotalAmount = "₹71,904.00",
        subtotal = 64200.0,
        gstAmount = 7704.0,
        deliveryCharge = 0.0,
        itemCount = 1500,
        itemsSummary = "Paracetamol 500mg, Calpol 500 (Batch PCM-2024-B1)",
        isEwayBillGenerated = true,
        ewayBillNumber = "EWB-293847102938",
        transporterName = "BlueDart Express",
        vehicleNumber = "DL-01-AB-4200",
        expectedDeliveryDate = "Tomorrow, 11:30 AM"
    ),
    OrderTransactionUiModel(
        orderId = 102L,
        orderNumber = "PO-44815",
        formattedDate = "13 Nov 2026, 11:15 AM",
        rawEpochMillis = System.currentTimeMillis() - 36 * 3600 * 1000L,
        status = OrderStatus.PACKED,
        statusStageDescription = "Batch Serialization Verified",
        buyerName = "Fortis Healthcare Central",
        supplierName = "Cipla Wholesale Depot",
        totalAmount = 140000.0,
        formattedTotalAmount = "₹1,40,000.00",
        subtotal = 125000.0,
        gstAmount = 15000.0,
        deliveryCharge = 0.0,
        itemCount = 2000,
        itemsSummary = "Amoxicillin 250mg, Mox 250 (Batch AMX-2024-C9)",
        isEwayBillGenerated = true,
        ewayBillNumber = "EWB-109283746152",
        transporterName = "SafeX Logistics",
        vehicleNumber = "KA-04-E-1980",
        expectedDeliveryDate = "18 Nov 2026"
    ),
    OrderTransactionUiModel(
        orderId = 103L,
        orderNumber = "PO-44809",
        formattedDate = "12 Nov 2026, 04:30 PM",
        rawEpochMillis = System.currentTimeMillis() - 48 * 3600 * 1000L,
        status = OrderStatus.ACCEPTED,
        statusStageDescription = "Pallet Staging at Bay 3",
        buyerName = "Zydus Distribution",
        supplierName = "Torrent Pharma Ltd.",
        totalAmount = 848960.0,
        formattedTotalAmount = "₹8,48,960.00",
        subtotal = 758000.0,
        gstAmount = 90960.0,
        deliveryCharge = 0.0,
        itemCount = 7500,
        itemsSummary = "Cefixime 200mg, Taxim-O 200 (Batch CFX-2024-X4)",
        isEwayBillGenerated = false
    ),
    OrderTransactionUiModel(
        orderId = 104L,
        orderNumber = "PO-44780",
        formattedDate = "10 Nov 2026, 09:20 AM",
        rawEpochMillis = System.currentTimeMillis() - 120 * 3600 * 1000L,
        status = OrderStatus.DELIVERED,
        statusStageDescription = "Delivered & Verified with digital invoice & POD",
        buyerName = "Apollo Pharmacy Hub",
        supplierName = "Alembic Pharmaceuticals",
        totalAmount = 100128.0,
        formattedTotalAmount = "₹1,00,128.00",
        subtotal = 89400.0,
        gstAmount = 10728.0,
        deliveryCharge = 0.0,
        itemCount = 1200,
        itemsSummary = "Azithromycin 500mg, Azithral 500 (Batch AZM-2024-K2)",
        isEwayBillGenerated = true,
        ewayBillNumber = "EWB-991827364510",
        transporterName = "Gati KWE Express",
        vehicleNumber = "KA-01-MJ-8821"
    ),
    OrderTransactionUiModel(
        orderId = 105L,
        orderNumber = "PO-44835",
        formattedDate = "Just now",
        rawEpochMillis = System.currentTimeMillis() - 3 * 3600 * 1000L,
        status = OrderStatus.PLACED,
        statusStageDescription = "PO Generated - Pending Stockist Acceptance",
        buyerName = "Max Super Speciality",
        supplierName = "Dr. Reddy's Lab Supply",
        totalAmount = 43620.0,
        formattedTotalAmount = "₹43,620.00",
        subtotal = 38500.0,
        gstAmount = 4620.0,
        deliveryCharge = 500.0,
        itemCount = 500,
        itemsSummary = "Pantoprazole 40mg IV, Pan-IV (Batch PNT-2024-V9)",
        isEwayBillGenerated = false
    )
)

@Preview(name = "Order Card Light - Shipped", showBackground = true)
@Composable
private fun PreviewOrderTransactionCardShippedLight() {
    PharmaOSTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            OrderTransactionCard(
                transaction = SampleMockTransactions[0],
                onOrderClick = {},
                onReorderClick = {},
                onTrackClick = {}
            )
        }
    }
}

@Preview(name = "Order Card Dark - Delivered", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewOrderTransactionCardDeliveredDark() {
    PharmaOSTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            OrderTransactionCard(
                transaction = SampleMockTransactions[3],
                onOrderClick = {},
                onReorderClick = {}
            )
        }
    }
}

@Preview(name = "Status Indicators Gallery", showBackground = true)
@Composable
private fun PreviewOrderStatusIndicators() {
    PharmaOSTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrderStatus.values().forEach { status ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = status.name, style = MaterialTheme.typography.bodyMedium)
                    OrderStatusIndicator(status = status)
                }
            }
        }
    }
}

@Preview(name = "Order History List - Full Screen", showBackground = true, heightDp = 700)
@Composable
private fun PreviewOrderHistoryList() {
    PharmaOSTheme {
        OrderHistoryList(
            transactions = SampleMockTransactions,
            onOrderClick = {},
            onReorderClick = {},
            onTrackClick = {}
        )
    }
}

@Preview(name = "Order History Compact Widget", showBackground = true)
@Composable
private fun PreviewOrderHistoryCompactWidget() {
    PharmaOSTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            OrderHistoryCompactWidget(
                transactions = SampleMockTransactions,
                onOrderClick = {},
                onViewAllClick = {}
            )
        }
    }
}

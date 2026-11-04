package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.OrderStatus
import com.example.domain.model.ShortageRiskLevel
import com.example.domain.model.StorageCondition
import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.theme.PharmaOSTheme
import com.example.ui.theme.PharmaError
import com.example.ui.theme.PharmaInfo
import com.example.ui.theme.PharmaSuccess
import com.example.ui.theme.PharmaWarning
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("top_bar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    icon: ImageVector = Icons.Default.Inbox,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onActionClick,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("empty_state_action_button")
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun LoadingState(
    message: String = "Loading...",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(44.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.5.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ErrorState(
    errorMessage: String,
    onRetryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = "Error",
            tint = PharmaError,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (onRetryClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onRetryClick,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("error_retry_button")
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun StatusChip(statusString: String, modifier: Modifier = Modifier) {
    val status = try { OrderStatus.valueOf(statusString) } catch (_: Exception) { OrderStatus.PLACED }
    val (bgColor, textColor) = when (status) {
        OrderStatus.PLACED -> Color(0xFFE2E8F0) to Color(0xFF334155)
        OrderStatus.ACCEPTED -> Color(0xFFE0F2FE) to Color(0xFF0369A1)
        OrderStatus.PACKED -> Color(0xFFFEF3C7) to Color(0xFFB45309)
        OrderStatus.DISPATCHED -> Color(0xFFEDE9FE) to Color(0xFF6D28D9)
        OrderStatus.DELIVERED -> Color(0xFFDCFCE7) to Color(0xFF15803D)
        OrderStatus.CANCELLED -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
        OrderStatus.PARTIALLY_FULFILLED -> Color(0xFFFFEDD5) to Color(0xFFC2410C)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Text(
            text = status.displayName,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ExpiryChip(daysUntilExpiry: Long, modifier: Modifier = Modifier) {
    val (label, bgColor, textColor) = when {
        daysUntilExpiry < 0 -> Triple("Expired", Color(0xFFFEE2E2), Color(0xFFB91C1C))
        daysUntilExpiry <= 30 -> Triple("≤30 Days (${daysUntilExpiry}d)", Color(0xFFFEE2E2), Color(0xFF991B1B))
        daysUntilExpiry <= 90 -> Triple("≤90 Days (${daysUntilExpiry}d)", Color(0xFFFEF3C7), Color(0xFF92400E))
        else -> Triple("OK (${daysUntilExpiry / 30}m)", Color(0xFFDCFCE7), Color(0xFF166534))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun TrustScoreBadge(
    score: Int?,
    hasSufficientData: Boolean,
    modifier: Modifier = Modifier
) {
    if (!hasSufficientData || score == null) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Insufficient data",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 10.sp
                )
            }
        }
    } else {
        val (bgColor, textColor) = when {
            score >= 85 -> Color(0xFFDCFCE7) to Color(0xFF166534)
            score >= 70 -> Color(0xFFE0F2FE) to Color(0xFF0369A1)
            score >= 50 -> Color(0xFFFEF3C7) to Color(0xFF92400E)
            else -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = bgColor,
            modifier = modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Trust Score",
                    tint = textColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Trust: $score",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun RiskChip(riskLevel: ShortageRiskLevel, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (riskLevel) {
        ShortageRiskLevel.NONE -> Color(0xFFDCFCE7) to Color(0xFF166534)
        ShortageRiskLevel.LOW -> Color(0xFFFEF9C3) to Color(0xFF854D0E)
        ShortageRiskLevel.MEDIUM -> Color(0xFFFFEDD5) to Color(0xFFC2410C)
        ShortageRiskLevel.HIGH -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Text(
            text = riskLevel.label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun StorageConditionBadge(storageCondition: StorageCondition, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(storageCondition.badgeColorHex).copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(storageCondition.badgeColorHex).copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Text(
            text = storageCondition.label,
            color = Color(storageCondition.badgeColorHex),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun PriceText(
    amount: Double,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.primary,
    fontWeight: FontWeight = FontWeight.Bold,
    modifier: Modifier = Modifier
) {
    Text(
        text = Formatters.formatInr(amount),
        style = style,
        color = color,
        fontWeight = fontWeight,
        modifier = modifier
    )
}

@Composable
fun QuantityStepper(
    quantity: Int,
    onQuantityChanged: (Int) -> Unit,
    minQuantity: Int = 1,
    maxQuantity: Int = 99999,
    step: Int = 1,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        IconButton(
            onClick = {
                val newQty = Math.max(minQuantity, quantity - step)
                onQuantityChanged(newQty)
            },
            enabled = quantity > minQuantity,
            modifier = Modifier
                .size(48.dp)
                .testTag("stepper_minus_button")
        ) {
            Surface(
                shape = CircleShape,
                color = if (quantity > minQuantity) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease Quantity",
                        tint = if (quantity > minQuantity) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .width(48.dp)
                .height(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$quantity",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        IconButton(
            onClick = {
                val newQty = Math.min(maxQuantity, quantity + step)
                onQuantityChanged(newQty)
            },
            enabled = quantity < maxQuantity,
            modifier = Modifier
                .size(48.dp)
                .testTag("stepper_plus_button")
        ) {
            Surface(
                shape = CircleShape,
                color = if (quantity < maxQuantity) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase Quantity",
                        tint = if (quantity < maxQuantity) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    dismissLabel: String = "Cancel",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (isDestructive) ButtonDefaults.buttonColors(containerColor = PharmaError) else ButtonDefaults.buttonColors(),
                modifier = Modifier.testTag("dialog_confirm_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_dismiss_button")
            ) {
                Text(dismissLabel)
            }
        }
    )
}

@Preview(name = "Components - Light Mode", showBackground = true)
@Preview(name = "Components - Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun CommonComponentsPreview() {
    PharmaOSTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AppTopBar(title = "PharmaOS", subtitle = "Inventory Control")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(statusString = "DISPATCHED")
                    ExpiryChip(daysUntilExpiry = 28)
                    StorageConditionBadge(storageCondition = StorageCondition.ROOM_TEMPERATURE)
                }
                QuantityStepper(
                    quantity = 24,
                    minQuantity = 1,
                    maxQuantity = 100,
                    step = 5,
                    onQuantityChanged = {}
                )
                EmptyState(
                    title = "No Inventory Found",
                    message = "Add new products or import catalog to view stock.",
                    actionLabel = "Add Product",
                    onActionClick = {}
                )
            }
        }
    }
}

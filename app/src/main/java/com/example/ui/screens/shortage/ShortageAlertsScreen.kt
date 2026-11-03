package com.example.ui.screens.shortage

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ShortageEvaluationResult
import com.example.domain.model.ShortageRiskLevel
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.components.RiskChip
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortageAlertsScreen(
    viewModel: ShortageViewModel,
    onNavigateBack: () -> Unit,
    onProductClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Supply Shortage Warnings",
                subtitle = "${uiState.filteredAlerts.size} monitored molecules",
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.reevaluateAll() },
                        modifier = Modifier.testTag("refresh_shortage_eval_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Re-evaluate Rules")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Mandatory Rule-based Disclaimer
            Surface(
                shape = RoundedCornerShape(0.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Rule-based early indicators computed from active stock records and supplier lead times — not probabilistic forecasts.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Risk Level Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedRiskFilter == null,
                        onClick = { viewModel.setRiskFilter(null) },
                        label = { Text("All Products") }
                    )
                }
                items(ShortageRiskLevel.values()) { level ->
                    FilterChip(
                        selected = uiState.selectedRiskFilter == level,
                        onClick = { viewModel.setRiskFilter(if (uiState.selectedRiskFilter == level) null else level) },
                        label = { Text(level.label) }
                    )
                }
            }

            if (uiState.isLoading) {
                LoadingState(message = "Evaluating 4 shortage risk rules across all active suppliers...")
            } else if (uiState.filteredAlerts.isEmpty()) {
                EmptyState(
                    title = "Supply Chains Stable",
                    message = "No active supply shortage warnings detected across your monitored medicines.",
                    icon = Icons.Default.Warning
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(uiState.filteredAlerts, key = { it.productId }) { item ->
                        ShortageAlertCard(
                            alert = item,
                            onClick = { onProductClick(item.productId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShortageAlertCard(
    alert: ShortageEvaluationResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("shortage_card_${alert.productId}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(alert.productName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Brand: ${alert.brand} • Salt: ${alert.saltComposition}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RiskChip(riskLevel = alert.riskLevel)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Triggered Rule Explanations
            if (alert.triggeredReasons.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Triggered Risk Indicators:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(4.dp))
                        alert.triggeredReasons.forEach { r ->
                            Text("• $r", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Stocked by: ${alert.availableSuppliersCount} supplier(s)", style = MaterialTheme.typography.bodySmall)
                Text("Available: ${alert.totalAvailableQuantity} units", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text("Lead Time: ${"%.1f".format(alert.avgLeadTimeDays)}d", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Alternative Quotes & Same-Salt Brands")
            }
        }
    }
}

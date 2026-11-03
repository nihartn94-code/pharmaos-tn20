package com.example.ui.screens.optimizer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.OptimizedLineItem
import com.example.ui.components.AppTopBar
import com.example.ui.components.ErrorState
import com.example.ui.components.LoadingState
import com.example.ui.components.PriceText
import com.example.ui.components.TrustScoreBadge
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BestBuyOptimizerScreen(
    viewModel: OptimizerViewModel,
    onNavigateBack: () -> Unit,
    onApplied: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val res = uiState.result

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Best-Buy Basket Optimizer",
                subtitle = if (res?.isExact == true) "Exact best among available offers" else "Best found (heuristic search)",
                onBackClick = onNavigateBack
            )
        },
        bottomBar = {
            if (res != null && res.lines.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("optimizer_keep_selection_button")
                        ) {
                            Text("Keep My Selection")
                        }

                        Button(
                            onClick = { viewModel.applySuggestion(onApplied) },
                            enabled = !uiState.isApplying,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("optimizer_apply_button")
                        ) {
                            if (uiState.isApplying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Apply Suggestion", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (uiState.isOptimizing) {
            LoadingState(
                message = "Running combinatorial optimization across suppliers and delivery thresholds...",
                modifier = Modifier.padding(padding)
            )
        } else if (uiState.errorMessage != null) {
            ErrorState(
                errorMessage = uiState.errorMessage ?: "Optimization failed",
                onRetryClick = { viewModel.runOptimization(uiState.preferReliableSuppliers) },
                modifier = Modifier.padding(padding)
            )
        } else if (res == null || res.lines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No items available to optimize in your basket.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Trust Score Weighting Preference Toggle
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Prefer Reliable Suppliers",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Filters for suppliers with Trust Score ≥ 60",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Switch(
                                checked = uiState.preferReliableSuppliers,
                                onCheckedChange = { viewModel.setPreferReliableSuppliers(it) },
                                modifier = Modifier.testTag("prefer_reliable_suppliers_toggle")
                            )
                        }
                    }
                }

                // Savings Highlight Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (res.savingsRupees > 0) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (res.savingsRupees > 0) "Total Net Procurement Savings" else "Current Selection Already Optimal",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (res.savingsRupees > 0) Color(0xFF166534) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${Formatters.formatInr(res.savingsRupees)} (${Formatters.formatPercent(res.savingsPercent)} saved)",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (res.savingsRupees > 0) Color(0xFF15803D) else MaterialTheme.colorScheme.primary
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = if (res.savingsRupees > 0) Color(0xFF15803D) else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.TrendingDown,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = Color.Black.copy(alpha = 0.08f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Current Basket Total", style = MaterialTheme.typography.labelSmall, color = Color(0xFF374151))
                                    Text(Formatters.formatInr(res.currentTotal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFF6B7280))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Optimized Total", style = MaterialTheme.typography.labelSmall, color = Color(0xFF374151))
                                    PriceText(amount = res.optimizedTotal, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                // Side-by-side line item comparison
                item {
                    Text(
                        text = "Item-by-Item Allocation Changes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(res.lines) { line ->
                    OptimizedLineCard(line = line)
                }

                // Plain language "Why this suggestion"
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Why this suggestion",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            res.whySuggestions.forEach { why ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text("• ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = why,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Assumptions Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Assumptions & Methodology",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            res.assumptions.forEach { ass ->
                                Text(
                                    text = "• $ass",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OptimizedLineCard(line: OptimizedLineItem) {
    val hasChanged = line.originalSupplierId != line.suggestedSupplierId || line.moqAdjusted

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (hasChanged) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "${line.productName} (${line.brand})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Current Selection
                Column(modifier = Modifier.weight(1f)) {
                    Text("Current Selection", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = line.originalSupplierName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${line.originalQuantity} units @ ${Formatters.formatInr(line.originalEffectivePrice)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Right: Suggested Selection
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Suggested Best-Buy", style = MaterialTheme.typography.labelSmall, color = Color(0xFF15803D), fontWeight = FontWeight.Bold)
                    Text(
                        text = line.suggestedSupplierName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${line.suggestedQuantity} units @ ${Formatters.formatInr(line.suggestedEffectivePrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = line.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

package com.example.ui.screens.trust

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.TrustScoreBreakdown
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.components.TrustScoreBadge
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PharmaTrustScoreScreen(
    viewModel: TrustScoreViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCompare: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Pharma Trust Score",
                subtitle = "Algorithmic supply reliability benchmark",
                onBackClick = onNavigateBack,
                actions = {
                    TextButton(
                        onClick = onNavigateToCompare,
                        modifier = Modifier.testTag("compare_suppliers_button")
                    ) {
                        Icon(Icons.Default.CompareArrows, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Compare")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingState(message = "Calculating verified supplier trust scores...", modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mandatory Disclaimer
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Indicator based on recorded transactions — not a guarantee of quality or future performance.",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                val myScore = uiState.myScoreBreakdown
                if (myScore != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Your Organization's Score", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                        Text(myScore.supplierName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    }
                                    TrustScoreBadge(score = myScore.overallScore, hasSufficientData = myScore.hasSufficientData)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                if (!myScore.hasSufficientData) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Insufficient transaction volume: Completed ${myScore.completedOrdersCount} / 5 minimum orders required to generate a public numeric rating.",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "${myScore.overallScore} / 100",
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Computed across ${myScore.completedOrdersCount} delivered trade transactions",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }

                    // 5 Dimension Breakdown
                    item {
                        Text("Factor Weightings & Contribution", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                FactorRow(
                                    title = "Order Fulfilment Rate (35%)",
                                    metricText = Formatters.formatPercent(myScore.fulfilmentRate * 100),
                                    contributionText = "${"%.1f".format(myScore.fulfilmentScoreWeight)} / 35.0 pts",
                                    progress = myScore.fulfilmentRate.toFloat()
                                )
                                FactorRow(
                                    title = "On-Time Delivery Rate (30%)",
                                    metricText = Formatters.formatPercent(myScore.onTimeDeliveryRate * 100),
                                    contributionText = "${"%.1f".format(myScore.onTimeScoreWeight)} / 30.0 pts",
                                    progress = myScore.onTimeDeliveryRate.toFloat()
                                )
                                FactorRow(
                                    title = "Low Cancellation Rate (15% Inverted)",
                                    metricText = Formatters.formatPercent((1.0 - myScore.cancellationRate) * 100),
                                    contributionText = "${"%.1f".format(myScore.cancellationScoreWeight)} / 15.0 pts",
                                    progress = (1.0 - myScore.cancellationRate).toFloat()
                                )
                                FactorRow(
                                    title = "POD Discrepancy-Free Rate (10% Inverted)",
                                    metricText = Formatters.formatPercent((1.0 - myScore.discrepancyRate) * 100),
                                    contributionText = "${"%.1f".format(myScore.discrepancyScoreWeight)} / 10.0 pts",
                                    progress = (1.0 - myScore.discrepancyRate).toFloat()
                                )
                                FactorRow(
                                    title = "Low Damaged Return Rate (10% Inverted)",
                                    metricText = Formatters.formatPercent((1.0 - myScore.returnRate) * 100),
                                    contributionText = "${"%.1f".format(myScore.returnScoreWeight)} / 10.0 pts",
                                    progress = (1.0 - myScore.returnRate).toFloat()
                                )
                            }
                        }
                    }
                }

                // Peer Comparison Preview List
                item {
                    Text("Supplier Ecosystem Rankings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }

                items(uiState.allSuppliersComparison) { sup ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sup.supplierName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("${sup.completedOrdersCount} orders completed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            TrustScoreBadge(score = sup.overallScore, hasSufficientData = sup.hasSufficientData)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FactorRow(
    title: String,
    metricText: String,
    contributionText: String,
    progress: Float
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(metricText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Weighted Score: $contributionText",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 10.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierComparisonScreen(
    viewModel: TrustScoreViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Supplier Benchmarks",
                subtitle = "Side-by-side reliability ratings",
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.allSuppliersComparison) { sup ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(sup.supplierName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            TrustScoreBadge(score = sup.overallScore, hasSufficientData = sup.hasSufficientData)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Fulfilment: ${Formatters.formatPercent(sup.fulfilmentRate * 100)}", style = MaterialTheme.typography.bodySmall)
                            Text("On-Time: ${Formatters.formatPercent(sup.onTimeDeliveryRate * 100)}", style = MaterialTheme.typography.bodySmall)
                            Text("Orders: ${sup.completedOrdersCount}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

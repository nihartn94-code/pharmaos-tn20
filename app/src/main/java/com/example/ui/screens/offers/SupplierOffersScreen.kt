package com.example.ui.screens.offers

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.OfferWithSupplier
import com.example.domain.model.SchemeType
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.components.PriceText
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierOffersScreen(
    viewModel: OffersViewModel,
    onNavigateBack: () -> Unit,
    onAddOfferClick: (Long?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "My Supplier Quotes & Schemes",
                subtitle = "${uiState.myOffers.size} quotes published",
                onBackClick = onNavigateBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddOfferClick(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_offer_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add New Quote")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingState(message = "Loading quotes...", modifier = Modifier.padding(padding))
        } else if (uiState.myOffers.isEmpty()) {
            EmptyState(
                title = "No Supplier Quotes Published",
                message = "Publish your trade prices, MOQ, available inventory, and promotional discount schemes.",
                actionLabel = "Create First Quote",
                onActionClick = { onAddOfferClick(null) },
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.myOffers, key = { it.offerId }) { offer ->
                    val prod = uiState.catalogueProducts.find { it.id == offer.productId }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddOfferClick(offer.productId) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prod?.name ?: "Product #${offer.productId}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Brand: ${prod?.brand ?: ""} • ${prod?.strength ?: ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Switch(
                                    checked = offer.isActive,
                                    onCheckedChange = { viewModel.toggleOfferActive(offer.offerId, it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Unit Price (PTR)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    PriceText(amount = offer.unitPricePtr)
                                }
                                Column {
                                    Text("MRP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text(Formatters.formatInr(offer.mrp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Column {
                                    Text("MOQ / Stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text("${offer.moq} / ${offer.availableQuantity} units", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (offer.schemeType != SchemeType.NONE) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = when (offer.schemeType) {
                                            SchemeType.BUY_X_GET_Y_FREE -> "Scheme: Buy ${offer.schemeBuyQty} Get ${offer.schemeFreeQty} Free"
                                            SchemeType.PERCENT_DISCOUNT_ABOVE_QTY -> "Scheme: ${offer.schemeDiscountPercent}% discount above ${offer.schemeMinQtyForDiscount} units"
                                            else -> ""
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Last updated: ${Formatters.formatDateTime(offer.lastUpdatedEpochMillis)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

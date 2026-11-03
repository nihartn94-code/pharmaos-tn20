package com.example.ui.screens.catalogue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.OfferWithSupplier
import com.example.domain.model.SchemeType
import com.example.domain.model.UserRole
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyState
import com.example.ui.components.PriceText
import com.example.ui.components.QuantityStepper
import com.example.ui.components.StorageConditionBadge
import com.example.ui.components.TrustScoreBadge
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineDetailsScreen(
    productId: Long,
    viewModel: CatalogueViewModel,
    onNavigateBack: () -> Unit,
    onProductClick: (Long) -> Unit,
    onAddOfferClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(productId) {
        viewModel.selectProduct(productId)
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    val product = uiState.selectedProduct

    Scaffold(
        topBar = {
            AppTopBar(
                title = product?.name ?: "Medicine Details",
                subtitle = product?.let { "${it.brand} • ${it.strength}" },
                onBackClick = onNavigateBack,
                actions = {
                    if (uiState.currentUser?.role?.isSupplier == true || uiState.currentUser?.role == UserRole.ADMIN) {
                        IconButton(
                            onClick = { onAddOfferClick(productId) },
                            modifier = Modifier.testTag("add_supplier_quote_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Supplier Quote")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (product == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Product details not found.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Medicine Info Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = product.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Brand: ${product.brand} • Strength: ${product.strength}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (product.isRx) Color(0xFFFEE2E2) else Color(0xFFE0F2FE)
                                ) {
                                    Text(
                                        text = if (product.isRx) "Rx Prescription" else "OTC",
                                        color = if (product.isRx) Color(0xFFB91C1C) else Color(0xFF0369A1),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Active Ingredients:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = product.saltComposition,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StorageConditionBadge(storageCondition = product.storageCondition)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "Pack: ${product.packSize}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "GST: ${product.gstRatePercent}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 1: Supplier Offer Comparison
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Supplier Quotes (${uiState.selectedProductOffers.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.selectedProductOffers.isNotEmpty()) {
                            Text(
                                text = "Sorted by lowest PTR",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                if (uiState.selectedProductOffers.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No Quotes Available",
                            message = "No suppliers currently have active offers for this medicine.",
                            actionLabel = if (uiState.currentUser?.role?.isSupplier == true || uiState.currentUser?.role == UserRole.ADMIN) "Publish Quote" else null,
                            onActionClick = { onAddOfferClick(productId) }
                        )
                    }
                } else {
                    val lowestPriceOfferId = uiState.selectedProductOffers.minByOrNull { it.unitPricePtr }?.offerId
                    items(uiState.selectedProductOffers, key = { it.offerId }) { offer ->
                        SupplierOfferCard(
                            offer = offer,
                            isLowestPrice = offer.offerId == lowestPriceOfferId,
                            onAddToBasket = { qty ->
                                viewModel.addOfferToBasket(offer, qty) {}
                            }
                        )
                    }
                }

                // Section 2: Alternative Brand Suggestions
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Alternative Brands (${uiState.alternativeBrands.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Mandatory Disclaimer
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Informational only — not a guarantee of clinical equivalence.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (uiState.alternativeBrands.isEmpty()) {
                    item {
                        Text(
                            text = "No alternative brands with the exact same active salt and strength currently in catalogue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(uiState.alternativeBrands, key = { it.id }) { alt ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onProductClick(alt.id) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = alt.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Brand: ${alt.brand} • Mfg: ${alt.manufacturerName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { onProductClick(alt.id) },
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("View", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SupplierOfferCard(
    offer: OfferWithSupplier,
    isLowestPrice: Boolean,
    onAddToBasket: (Int) -> Unit
) {
    var quantity by remember(offer.moq) { mutableIntStateOf(Math.max(1, offer.moq)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("supplier_offer_card_${offer.offerId}"),
        border = if (isLowestPrice) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = offer.supplierBusinessName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isLowestPrice) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFDCFCE7)
                            ) {
                                Text(
                                    text = "Lowest PTR",
                                    color = Color(0xFF15803D),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Contact: ${offer.supplierName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                TrustScoreBadge(
                    score = offer.trustScore,
                    hasSufficientData = offer.hasSufficientTrustData
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
                    Text(text = Formatters.formatInr(offer.mrp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("Trade Margin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = Formatters.formatPercent(offer.marginPercent),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2A9D8F)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MOQ: ${offer.moq} | Stock: ${offer.availableQuantity} units",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (offer.schemeType != SchemeType.NONE) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = when (offer.schemeType) {
                                SchemeType.BUY_X_GET_Y_FREE -> "Scheme: ${offer.schemeBuyQty}+${offer.schemeFreeQty}"
                                SchemeType.PERCENT_DISCOUNT_ABOVE_QTY -> "${offer.schemeDiscountPercent}% off >${offer.schemeMinQtyForDiscount}"
                                else -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Add to basket row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuantityStepper(
                    quantity = quantity,
                    onQuantityChanged = { quantity = it },
                    minQuantity = Math.max(1, offer.moq),
                    maxQuantity = offer.availableQuantity
                )

                Button(
                    onClick = { onAddToBasket(quantity) },
                    enabled = offer.availableQuantity >= offer.moq,
                    modifier = Modifier.testTag("offer_add_to_basket_button")
                ) {
                    Icon(imageVector = Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add to Basket")
                }
            }
        }
    }
}

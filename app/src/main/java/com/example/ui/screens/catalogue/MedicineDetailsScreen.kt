package com.example.ui.screens.catalogue

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.domain.model.AlternativeBrandQuotation
import com.example.domain.model.OfferWithSupplier
import com.example.domain.model.SchemeType
import com.example.domain.model.SupplierQuotationItem
import com.example.domain.model.SupplierQuotationSummary
import com.example.domain.model.UserRole
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import com.example.ui.components.LoadingState
import com.example.ui.components.PriceText
import com.example.ui.components.QuantityStepper
import com.example.ui.components.StorageConditionBadge
import com.example.ui.components.TrustScoreBadge
import com.example.util.Formatters

@Composable
fun MedicineDetailsScreen(
    productId: Long,
    viewModel: CatalogueViewModel,
    onNavigateBack: () -> Unit,
    onProductClick: (Long) -> Unit,
    onAddOfferClick: (Long) -> Unit,
    onNavigateToBasket: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(productId) {
        viewModel.selectProduct(productId)
    }

    MedicineDetailsContent(
        productId = productId,
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRetryLoad = { viewModel.selectProduct(productId) },
        onProductClick = onProductClick,
        onAddOfferClick = onAddOfferClick,
        onNavigateToBasket = onNavigateToBasket,
        onAddOfferToBasket = { offer, qty -> viewModel.addOfferToBasket(offer, qty) },
        onClearMessages = { viewModel.clearMessages() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineDetailsContent(
    productId: Long,
    uiState: CatalogueUiState,
    onNavigateBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onProductClick: (Long) -> Unit,
    onAddOfferClick: (Long) -> Unit,
    onNavigateToBasket: () -> Unit = {},
    onAddOfferToBasket: (OfferWithSupplier, Int) -> Unit,
    onClearMessages: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearMessages()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearMessages()
        }
    }

    val product = uiState.selectedProduct
    val quotationSummary = uiState.quotationSummary
    val quotations = uiState.supplierQuotations
    val alternativeQuotations = uiState.alternativeBrandQuotations

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
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(48.dp)
                            .clickable(
                                role = androidx.compose.ui.semantics.Role.Button,
                                onClick = onNavigateToBasket
                            )
                            .testTag("details_basket_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "Open Basket (${uiState.cartItemsCount} items)"
                        )
                        if (uiState.cartItemsCount > 0) {
                            Badge(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 4.dp, end = 4.dp)
                                    .testTag("details_basket_badge")
                            ) {
                                Text(
                                    text = if (uiState.cartItemsCount > 99) "99+" else "${uiState.cartItemsCount}",
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.isDetailsLoading && product == null -> {
                LoadingState(
                    message = "Loading medicine details & supplier quotations...",
                    modifier = Modifier.padding(padding)
                )
            }

            uiState.detailsErrorMessage != null && product == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    ErrorState(
                        errorMessage = uiState.detailsErrorMessage,
                        onRetryClick = onRetryLoad
                    )
                }
            }

            product == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = "Product Details Not Found",
                        message = "The requested medicine could not be found in the catalogue.",
                        actionLabel = "Go Back",
                        onActionClick = onNavigateBack
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .testTag("medicine_details_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (uiState.detailsErrorMessage != null) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFEF3C7),
                                border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("details_sync_error_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Sync warning",
                                            tint = Color(0xFFB45309),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = uiState.detailsErrorMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF92400E)
                                        )
                                    }
                                    TextButton(onClick = onRetryLoad) {
                                        Text("Retry", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

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
                                        Text(
                                            text = "Manufacturer: ${product.manufacturerName.ifBlank { "Not specified" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

                    // Supplier Quotation Summary Card
                    if (quotationSummary != null) {
                        item {
                            SupplierQuotationSummaryCard(summary = quotationSummary)
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
                                text = "Supplier Quotes (${quotations.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (quotations.isNotEmpty()) {
                                Text(
                                    text = "Sorted by effective unit cost",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    if (quotations.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No Quotes Available",
                                message = "No suppliers currently have quotations published for this medicine.",
                                actionLabel = if (uiState.currentUser?.role?.isSupplier == true || uiState.currentUser?.role == UserRole.ADMIN) "Publish Quote" else null,
                                onActionClick = { onAddOfferClick(productId) }
                            )
                        }
                    } else {
                        val bestOfferId = quotationSummary?.bestOfferId
                        items(quotations, key = { it.offer.offerId }) { quotation ->
                            val inBasketQty = uiState.cartOfferQuantities[quotation.offer.offerId] ?: 0
                            SupplierOfferCard(
                                quotation = quotation,
                                gstRatePercent = product.gstRatePercent,
                                isLowestEffectiveCost = quotation.offer.offerId == bestOfferId,
                                inBasketQuantity = inBasketQty,
                                onAddToBasket = { qty ->
                                    onAddOfferToBasket(quotation.offer, qty)
                                }
                            )
                        }
                    }

                    // Section 2: Alternative Brand Suggestions
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Alternative Brands (${alternativeQuotations.size})",
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

                    if (alternativeQuotations.isEmpty()) {
                        item {
                            Text(
                                text = "No alternative brands with the exact same active salt and strength currently in catalogue.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        items(alternativeQuotations, key = { it.product.id }) { alt ->
                            AlternativeBrandCard(
                                alternative = alt,
                                onClick = { onProductClick(alt.product.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SupplierQuotationSummaryCard(summary: SupplierQuotationSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("supplier_quotation_summary_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Supplier Quotation Summary",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (summary.hasEligibleOffers) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                ) {
                    Text(
                        text = if (summary.hasEligibleOffers) {
                            "${summary.availableSuppliersCount} Available"
                        } else {
                            "No Eligible Quotes"
                        },
                        color = if (summary.hasEligibleOffers) Color(0xFF15803D) else Color(0xFFB91C1C),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lowest Unit Price", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = summary.lowestUnitPricePtr?.let { Formatters.formatInr(it) } ?: "Unavailable",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.lowestUnitPricePtr != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.testTag("summary_lowest_unit_price")
                    )
                    if (summary.lowestEffectiveUnitCost != null) {
                        Text(
                            text = "Eff. Cost: ${Formatters.formatInr(summary.lowestEffectiveUnitCost)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Min Order Qty (MOQ)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    val moqText = when {
                        summary.minMoq == null -> "Unavailable"
                        summary.maxMoq != null && summary.maxMoq > summary.minMoq -> "${summary.minMoq} – ${summary.maxMoq} units"
                        else -> "${summary.minMoq} units"
                    }
                    Text(
                        text = moqText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("summary_moq")
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Suppliers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = "${summary.availableSuppliersCount} of ${summary.totalOffersCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("summary_suppliers_count")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Quantity Available (Quoted Stock)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = "${summary.totalQuotedAvailableStock} units",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("quoted_stock_value")
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Verified Available Stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = if (summary.hasVerifiedStockRecords && summary.verifiedAvailableStock != null) {
                            "${summary.verifiedAvailableStock} units"
                        } else {
                            "Not verified (no batch record)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.hasVerifiedStockRecords) Color(0xFF15803D) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("verified_stock_value")
                    )
                }
            }

            if (summary.missingFields.isNotEmpty()) {
                Text(
                    text = "Unverified/Missing: ${summary.missingFields.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SupplierOfferCard(
    quotation: SupplierQuotationItem,
    gstRatePercent: Double,
    isLowestEffectiveCost: Boolean,
    inBasketQuantity: Int,
    onAddToBasket: (Int) -> Unit
) {
    val offer = quotation.offer
    val minQty = maxOf(1, offer.moq)
    val maxQty = maxOf(minQty, offer.availableQuantity)
    var quantity by remember(offer.offerId, offer.moq, offer.availableQuantity) {
        mutableIntStateOf(minQty)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("supplier_offer_card_${offer.offerId}"),
        border = when {
            isLowestEffectiveCost && quotation.isEligibleForPurchase ->
                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            !quotation.isEligibleForPurchase ->
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            else -> null
        },
        colors = CardDefaults.cardColors(
            containerColor = if (quotation.isEligibleForPurchase) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = offer.supplierBusinessName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isLowestEffectiveCost && quotation.isEligibleForPurchase) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFDCFCE7)
                            ) {
                                Text(
                                    text = "Lowest Eff. Cost",
                                    color = Color(0xFF15803D),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Contact: ${offer.supplierName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (quotation.isEligibleForPurchase) Color(0xFFE0F2FE) else Color(0xFFFEE2E2)
                        ) {
                            Text(
                                text = quotation.unavailabilityReason ?: "Available",
                                color = if (quotation.isEligibleForPurchase) Color(0xFF0369A1) else Color(0xFFB91C1C),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
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
                Column(modifier = Modifier.weight(1f)) {
                    Text("Unit Price (PTR)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    if (offer.unitPricePtr > 0.0) {
                        PriceText(amount = offer.unitPricePtr)
                    } else {
                        Text("Missing PTR", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Effective Cost", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = quotation.effectiveUnitCost?.let { Formatters.formatInr(it) } ?: "Unavailable",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(modifier = Modifier.weight(0.8f)) {
                    Text("MRP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = if (offer.mrp > 0.0) Formatters.formatInr(offer.mrp) else "Missing",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(modifier = Modifier.weight(0.9f)) {
                    Text("Trade Margin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    val marginPct = quotation.tradeMargin.marginPercent
                    Text(
                        text = marginPct?.let { Formatters.formatPercent(it) } ?: "Unavailable",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            marginPct == null -> MaterialTheme.colorScheme.outline
                            marginPct >= 0.0 -> Color(0xFF2A9D8F)
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MOQ: ${offer.moq} | Quoted Stock: ${quotation.quotedStock} units",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Verified Stock: ${quotation.verifiedStock?.let { "$it units" } ?: "Not verified"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (quotation.hasVerifiedStockRecord) Color(0xFF15803D) else MaterialTheme.colorScheme.outline
                    )
                }

                if (offer.schemeType != SchemeType.NONE) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = when (offer.schemeType) {
                                SchemeType.BUY_X_GET_Y_FREE -> "Scheme: ${offer.schemeBuyQty}+${offer.schemeFreeQty}"
                                SchemeType.PERCENT_DISCOUNT_ABOVE_QTY -> "${offer.schemeDiscountPercent}% off >=${offer.schemeMinQtyForDiscount}"
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

            Spacer(modifier = Modifier.height(6.dp))

            val deliveryInfo = if (quotation.deliveryCharge != null && quotation.freeDeliveryThreshold != null) {
                "Delivery: ${Formatters.formatInr(quotation.deliveryCharge)} (Free >= ${Formatters.formatInr(quotation.freeDeliveryThreshold)})"
            } else {
                "Delivery fee policy: Missing"
            }
            Text(
                text = "Additional Costs: GST ${gstRatePercent}% • $deliveryInfo",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (quotation.tradeMargin.missingFields.isNotEmpty()) {
                Text(
                    text = "Missing cost/margin fields: ${quotation.tradeMargin.missingFields.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (inBasketQuantity > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "In Basket: $inBasketQuantity units from this supplier",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Add to basket row (excluded from purchase selection when unavailable)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (quotation.isEligibleForPurchase) {
                    QuantityStepper(
                        quantity = quantity,
                        onQuantityChanged = { quantity = it },
                        minQuantity = minQty,
                        maxQuantity = maxQty
                    )
                } else {
                    Text(
                        text = quotation.unavailabilityReason ?: "Unavailable for purchase",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = { onAddToBasket(quantity) },
                    enabled = quotation.isEligibleForPurchase,
                    modifier = Modifier.testTag("offer_add_to_basket_button_${offer.offerId}")
                ) {
                    Icon(imageVector = Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (quotation.isEligibleForPurchase) "Add to Basket" else "Unavailable")
                }
            }
        }
    }
}

@Composable
fun AlternativeBrandCard(
    alternative: AlternativeBrandQuotation,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("alternative_brand_card_${alternative.product.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${alternative.product.name} (${alternative.product.brand})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Manufacturer: ${alternative.manufacturerName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TrustScoreBadge(
                    score = alternative.supplierRating,
                    hasSufficientData = alternative.hasSufficientRatingData
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Unit Price (PTR)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = alternative.unitPricePtr?.let { Formatters.formatInr(it) } ?: "Unavailable",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (alternative.unitPricePtr != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
                Column(modifier = Modifier.weight(0.8f)) {
                    Text("MOQ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = alternative.moq?.let { "$it units" } ?: "Unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(modifier = Modifier.weight(0.9f)) {
                    Text("Trade Margin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = alternative.tradeMargin.marginPercent?.let { Formatters.formatPercent(it) } ?: "Unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (alternative.tradeMargin.marginPercent != null) Color(0xFF2A9D8F) else MaterialTheme.colorScheme.outline
                    )
                }
            }

            val deliverySummary = if (alternative.deliveryCharge != null && alternative.freeDeliveryThreshold != null) {
                "Delivery: ${Formatters.formatInr(alternative.deliveryCharge)} (Free >= ${Formatters.formatInr(alternative.freeDeliveryThreshold)})"
            } else {
                "Delivery policy: Not specified"
            }
            Text(
                text = "Additional Costs: GST ${alternative.gstRatePercent}% • $deliverySummary",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (alternative.missingFields.isNotEmpty()) {
                Text(
                    text = "Missing info: ${alternative.missingFields.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (alternative.hasEligibleOffer) {
                        "${alternative.availableSuppliersCount} supplier(s) • ${alternative.totalQuotedStock} quoted units"
                    } else {
                        "No active supplier quotations"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("alternative_brand_view_${alternative.product.id}")
                ) {
                    Text("View", fontSize = 12.sp)
                }
            }
        }
    }
}

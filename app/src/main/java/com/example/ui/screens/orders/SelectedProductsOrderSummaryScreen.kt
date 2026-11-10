package com.example.ui.screens.orders

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.EffectivePriceCalculation
import com.example.domain.repository.BasketItemDetail
import com.example.domain.repository.BasketWithDetails
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.screens.basket.BasketViewModel
import com.example.ui.theme.PharmaOSTheme
import com.example.util.Formatters

/**
 * Data representation of a selected pharmaceutical product in the order review screen.
 */
data class SelectedPharmaceuticalProduct(
    val itemId: Long,
    val productId: Long,
    val productName: String,
    val brand: String,
    val manufacturerName: String,
    val saltComposition: String,
    val packSize: String,
    val dosageForm: String, // Tablet, Injection, Syrup, etc.
    val batchNumber: String,
    val expiryDate: String,
    val hsnCode: String,
    val gstRatePercent: Double,
    val unitPricePtr: Double, // Price To Retailer
    val mrp: Double,
    val moq: Int = 1,
    val quantity: Int,
    val freeQuantity: Int = 0,
    val schemeDescription: String = "",
    val supplierName: String,
    val discountPercent: Double = 0.0
) {
    val lineGrossAmount: Double
        get() = unitPricePtr * quantity

    val lineDiscountAmount: Double
        get() = lineGrossAmount * (discountPercent / 100.0)

    val lineTaxableAmount: Double
        get() = lineGrossAmount - lineDiscountAmount

    val lineGstAmount: Double
        get() = lineTaxableAmount * (gstRatePercent / 100.0)

    val lineTotalAmount: Double
        get() = lineTaxableAmount + lineGstAmount

    val retailMarginPercent: Double
        get() = if (mrp > 0) ((mrp - unitPricePtr) / mrp) * 100.0 else 0.0
}

/**
 * Order value breakdown calculation for selected pharmaceutical items.
 */
data class OrderValueSummary(
    val totalItemsCount: Int,
    val totalUnitsCount: Int,
    val totalFreeUnitsCount: Int,
    val grossSubtotal: Double,
    val schemeSavings: Double,
    val netTaxableAmount: Double,
    val gstAmount: Double,
    val deliveryCharge: Double,
    val freeDeliveryThreshold: Double = 5000.0,
    val grandTotal: Double
) {
    val isFreeDeliveryUnlocked: Boolean
        get() = grossSubtotal >= freeDeliveryThreshold

    val amountNeededForFreeDelivery: Double
        get() = (freeDeliveryThreshold - grossSubtotal).coerceAtLeast(0.0)

    val deliveryProgress: Float
        get() = (grossSubtotal / freeDeliveryThreshold).toFloat().coerceIn(0f, 1f)
}

/**
 * Connected Jetpack Compose screen that observes Room database via [BasketViewModel],
 * displaying the selected pharmaceutical products list with quantity adjust controls and total order summary.
 */
@Composable
fun SelectedProductsOrderSummaryScreen(
    viewModel: BasketViewModel,
    onNavigateBack: () -> Unit,
    onProceedToCheckout: () -> Unit,
    onExploreCatalogue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val basket = uiState.basket

    val selectedProducts = remember(basket) {
        basket?.items?.map { it.toSelectedPharmaceuticalProduct() } ?: emptyList()
    }

    SelectedProductsScreenContent(
        products = selectedProducts,
        isLoading = uiState.isLoading,
        onQuantityChanged = { itemId, newQty ->
            viewModel.updateQuantity(itemId, newQty)
        },
        onRemoveItem = { itemId ->
            viewModel.removeItem(itemId)
        },
        onClearAll = {
            viewModel.clearBasket()
        },
        onNavigateBack = onNavigateBack,
        onProceedToCheckout = onProceedToCheckout,
        onExploreCatalogue = onExploreCatalogue,
        modifier = modifier
    )
}

/**
 * Stateless, reusable Jetpack Compose screen content displaying selected pharmaceutical products,
 * interactive quantity adjust controls, and total order value summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectedProductsScreenContent(
    products: List<SelectedPharmaceuticalProduct>,
    isLoading: Boolean = false,
    onQuantityChanged: (Long, Int) -> Unit,
    onRemoveItem: (Long) -> Unit,
    onClearAll: () -> Unit,
    onNavigateBack: () -> Unit,
    onProceedToCheckout: () -> Unit,
    onExploreCatalogue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var itemPendingRemoval by remember { mutableStateOf<SelectedPharmaceuticalProduct?>(null) }

    // Summary calculations
    val summary = remember(products) {
        calculateOrderValueSummary(products)
    }

    // Filtered list
    val filteredProducts = remember(products, searchQuery) {
        if (searchQuery.isBlank()) {
            products
        } else {
            val q = searchQuery.trim().lowercase()
            products.filter {
                it.productName.lowercase().contains(q) ||
                it.brand.lowercase().contains(q) ||
                it.saltComposition.lowercase().contains(q) ||
                it.supplierName.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        modifier = modifier.testTag("selected_products_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Selected Products",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${products.size} pharmaceutical item(s) selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("selected_products_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (products.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.testTag("clear_selected_products_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (products.isNotEmpty()) {
                TotalOrderValueStickyBottomBar(
                    summary = summary,
                    onProceedToCheckout = onProceedToCheckout
                )
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            LoadingState(
                message = "Loading selected pharmaceutical items...",
                modifier = Modifier.padding(paddingValues)
            )
        } else if (products.isEmpty()) {
            EmptyState(
                title = "No Products Selected",
                message = "Your wholesale procurement basket is currently empty. Browse the medicine catalogue to add trade products.",
                actionLabel = "Explore Medicine Catalogue",
                onActionClick = onExploreCatalogue,
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .testTag("selected_products_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Free Delivery Progress Banner
                item(key = "free_delivery_progress") {
                    FreeDeliveryProgressBanner(summary = summary)
                }

                // In-List Search Filter (if multiple products)
                if (products.size > 2) {
                    item(key = "search_selected_products") {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Filter selected medicines by salt, name...", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_selected_products_input")
                        )
                    }
                }

                // Group by supplier if multi-supplier order
                val itemsBySupplier = filteredProducts.groupBy { it.supplierName }

                itemsBySupplier.forEach { (supplierName, supplierProducts) ->
                    item(key = "supplier_header_$supplierName") {
                        SupplierHeaderRow(
                            supplierName = supplierName,
                            itemCount = supplierProducts.size
                        )
                    }

                    items(
                        items = supplierProducts,
                        key = { it.itemId }
                    ) { item ->
                        SelectedProductItemCard(
                            product = item,
                            onQuantityChanged = { newQty ->
                                if (newQty <= 0) {
                                    itemPendingRemoval = item
                                } else {
                                    onQuantityChanged(item.itemId, newQty)
                                }
                            },
                            onRemoveClick = {
                                itemPendingRemoval = item
                            }
                        )
                    }
                }

                // Detailed Order Value Summary Card at bottom of scrollable list
                item(key = "detailed_summary_card") {
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailedOrderValueSummaryCard(summary = summary)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Confirmation Dialog for Item Removal
    itemPendingRemoval?.let { item ->
        AlertDialog(
            onDismissRequest = { itemPendingRemoval = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Remove from Order?")
            },
            text = {
                Text("Are you sure you want to remove ${item.productName} (${item.brand}) from your selected products list?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveItem(item.itemId)
                        itemPendingRemoval = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_remove_item_button")
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { itemPendingRemoval = null }) {
                    Text("Keep Item")
                }
            }
        )
    }

    // Confirmation Dialog for Clear All
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Clear All Selected Products?") },
            text = {
                Text("This will remove all ${products.size} selected pharmaceutical products from your current order.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_clear_all_button")
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Card displaying a single selected pharmaceutical product with interactive quantity adjust controls.
 */
@Composable
fun SelectedProductItemCard(
    product: SelectedPharmaceuticalProduct,
    onQuantityChanged: (Int) -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("selected_product_card_${product.itemId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Medicine Name, Dosage Badge, Remove Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Medication,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = product.productName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${product.brand} • ${product.manufacturerName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onRemoveClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("remove_product_${product.itemId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove Item",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Salt composition & Pack size tag
            Text(
                text = product.saltComposition,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Batch, Expiry, Pack Size Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = product.packSize,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = "Batch: ${product.batchNumber}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFDCFCE7)
                ) {
                    Text(
                        text = "Exp: ${product.expiryDate}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF15803D),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Applied Scheme or Bonus free units badge
            if (product.schemeDescription.isNotBlank() || product.freeQuantity > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Percent,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (product.freeQuantity > 0)
                                "${product.schemeDescription} • +${product.freeQuantity} FREE units added"
                            else product.schemeDescription,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Pricing Row + Quantity Adjustment Stepper Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Unit Price & Margin Column
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = Formatters.formatInr(product.unitPricePtr),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = " / unit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MRP ${Formatters.formatInr(product.mrp)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            textDecoration = TextDecoration.LineThrough
                        )
                        if (product.retailMarginPercent > 0) {
                            Text(
                                text = "(${String.format("%.0f", product.retailMarginPercent)}% margin)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF16A34A),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Interactive Quantity Adjuster Controls
                QuantityAdjustControls(
                    quantity = product.quantity,
                    moq = product.moq,
                    onQuantityChanged = onQuantityChanged
                )
            }

            // Quick Wholesale Batch Preset Chips (+10, +25, +50, +100)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick add:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(10, 25, 50, 100).forEach { preset ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .clickable { onQuantityChanged(product.quantity + preset) }
                            .testTag("quick_add_${preset}_${product.itemId}")
                    ) {
                        Text(
                            text = "+$preset",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Subtotal: ${Formatters.formatInr(product.lineTotalAmount)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Interactive quantity adjust controls with minus, plus, and direct numeric input.
 */
@Composable
fun QuantityAdjustControls(
    quantity: Int,
    moq: Int,
    onQuantityChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Decrement button
        IconButton(
            onClick = {
                val newQty = quantity - 1
                onQuantityChanged(newQty)
            },
            modifier = Modifier
                .size(48.dp)
                .testTag("quantity_minus_button")
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (quantity <= 1) Icons.Default.DeleteOutline else Icons.Default.Remove,
                        contentDescription = "Decrease Quantity",
                        tint = if (quantity <= 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Clickable quantity pill (tap to enter number directly)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier
                .width(54.dp)
                .height(34.dp)
                .clickable { showEditDialog = true }
                .testTag("quantity_display_pill")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$quantity",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Increment button
        IconButton(
            onClick = {
                onQuantityChanged(quantity + 1)
            },
            modifier = Modifier
                .size(48.dp)
                .testTag("quantity_plus_button")
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase Quantity",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    // Direct Quantity Input Dialog
    if (showEditDialog) {
        var textValue by remember { mutableStateOf(quantity.toString()) }
        val focusManager = LocalFocusManager.current

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Set Order Quantity") },
            text = {
                Column {
                    Text("Enter wholesale order quantity (Units):", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                textValue = input
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val parsed = textValue.toIntOrNull() ?: quantity
                                onQuantityChanged(parsed.coerceAtLeast(1))
                                showEditDialog = false
                            }
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("direct_quantity_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = textValue.toIntOrNull() ?: quantity
                        onQuantityChanged(parsed.coerceAtLeast(1))
                        showEditDialog = false
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Visual banner showing progress toward Free Wholesale Delivery threshold.
 */
@Composable
fun FreeDeliveryProgressBanner(
    summary: OrderValueSummary,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (summary.isFreeDeliveryUnlocked) Color(0xFFF0FDF4) else Color(0xFFEFF6FF),
        border = BorderStroke(
            1.dp,
            if (summary.isFreeDeliveryUnlocked) Color(0xFFBBF7D0) else Color(0xFFBFDBFE)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (summary.isFreeDeliveryUnlocked) Icons.Default.CheckCircle else Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = if (summary.isFreeDeliveryUnlocked) Color(0xFF15803D) else Color(0xFF1D4ED8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (summary.isFreeDeliveryUnlocked)
                            "🎉 Free Wholesale Delivery Unlocked!"
                        else
                            "Add ${Formatters.formatInr(summary.amountNeededForFreeDelivery)} more for Free Delivery",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.isFreeDeliveryUnlocked) Color(0xFF166534) else Color(0xFF1E40AF)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { summary.deliveryProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (summary.isFreeDeliveryUnlocked) Color(0xFF16A34A) else Color(0xFF2563EB),
                trackColor = if (summary.isFreeDeliveryUnlocked) Color(0xFFDCFCE7) else Color(0xFFDBEAFE)
            )
        }
    }
}

/**
 * Supplier header grouping split order consignments.
 */
@Composable
fun SupplierHeaderRow(
    supplierName: String,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = supplierName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = "Verified Stockist",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = "$itemCount item(s)",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

/**
 * Comprehensive Order Value Summary Card showing gross subtotal, scheme discounts, taxes, and net payable.
 */
@Composable
fun DetailedOrderValueSummaryCard(
    summary: OrderValueSummary,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("detailed_order_value_summary_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Total Order Value Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Gross Subtotal (PTR Value)
            SummaryRow(
                label = "Gross Subtotal (${summary.totalUnitsCount} Units)",
                value = Formatters.formatInr(summary.grossSubtotal)
            )

            // Scheme Savings & Bonus Units
            if (summary.schemeSavings > 0 || summary.totalFreeUnitsCount > 0) {
                SummaryRow(
                    label = "Scheme Discounts (${summary.totalFreeUnitsCount} Free Units)",
                    value = "-${Formatters.formatInr(summary.schemeSavings)}",
                    valueColor = Color(0xFF15803D)
                )
            }

            // Net Taxable Value
            SummaryRow(
                label = "Taxable Turnover",
                value = Formatters.formatInr(summary.netTaxableAmount)
            )

            // GST Breakdown
            SummaryRow(
                label = "Pharma GST (CGST + SGST 12%)",
                value = Formatters.formatInr(summary.gstAmount)
            )

            // Delivery Charge
            SummaryRow(
                label = "Delivery / Freight",
                value = if (summary.isFreeDeliveryUnlocked) "FREE" else Formatters.formatInr(summary.deliveryCharge),
                valueColor = if (summary.isFreeDeliveryUnlocked) Color(0xFF15803D) else MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Grand Total (Net Payable)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Net Payable Amount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Includes all applicable GST & Trade Cess",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = Formatters.formatInr(summary.grandTotal),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Sticky Bottom Bar showing total payable and primary "Proceed to Checkout" action button.
 */
@Composable
fun TotalOrderValueStickyBottomBar(
    summary: OrderValueSummary,
    onProceedToCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Total Order Value",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Formatters.formatInr(summary.grandTotal),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (summary.schemeSavings > 0) {
                    Text(
                        text = "Saved ${Formatters.formatInr(summary.schemeSavings)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF15803D)
                    )
                }
            }

            Button(
                onClick = onProceedToCheckout,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("proceed_to_checkout_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCartCheckout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Checkout",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

/**
 * Calculates financial order value summary from selected products.
 */
fun calculateOrderValueSummary(products: List<SelectedPharmaceuticalProduct>): OrderValueSummary {
    val totalItems = products.size
    val totalUnits = products.sumOf { it.quantity }
    val totalFreeUnits = products.sumOf { it.freeQuantity }
    val grossSubtotal = products.sumOf { it.lineGrossAmount }
    val schemeSavings = products.sumOf { it.lineDiscountAmount }
    val netTaxable = products.sumOf { it.lineTaxableAmount }
    val gstAmount = products.sumOf { it.lineGstAmount }
    val isFreeDelivery = grossSubtotal >= 5000.0 || products.isEmpty()
    val deliveryCharge = if (isFreeDelivery) 0.0 else 250.0
    val grandTotal = if (products.isEmpty()) 0.0 else (netTaxable + gstAmount + deliveryCharge)

    return OrderValueSummary(
        totalItemsCount = totalItems,
        totalUnitsCount = totalUnits,
        totalFreeUnitsCount = totalFreeUnits,
        grossSubtotal = grossSubtotal,
        schemeSavings = schemeSavings,
        netTaxableAmount = netTaxable,
        gstAmount = gstAmount,
        deliveryCharge = deliveryCharge,
        grandTotal = grandTotal
    )
}

/**
 * Converts domain [BasketItemDetail] into [SelectedPharmaceuticalProduct].
 */
fun BasketItemDetail.toSelectedPharmaceuticalProduct(): SelectedPharmaceuticalProduct {
    return SelectedPharmaceuticalProduct(
        itemId = this.itemId,
        productId = this.productId,
        productName = this.productName,
        brand = this.brand,
        manufacturerName = "Authorized Wholesale Lab",
        saltComposition = this.saltComposition,
        packSize = this.packSize,
        dosageForm = "Tablet",
        batchNumber = "BATCH-2026-X1",
        expiryDate = "12/2026",
        hsnCode = this.hsnCode,
        gstRatePercent = this.gstRatePercent,
        unitPricePtr = this.unitPricePtr,
        mrp = this.mrp,
        moq = this.moq,
        quantity = this.quantity,
        freeQuantity = this.pricing.freeQuantity,
        schemeDescription = this.schemeDescription,
        supplierName = this.supplierName,
        discountPercent = this.pricing.savingsPercent
    )
}

// =========================================================================
// MOCK DATA & JETPACK COMPOSE PREVIEWS FOR ANDROID STUDIO
// =========================================================================

val SampleSelectedPharmaceuticalProducts = listOf(
    SelectedPharmaceuticalProduct(
        itemId = 1L,
        productId = 101L,
        productName = "Augmentin 625 Duo",
        brand = "Augmentin",
        manufacturerName = "GSK Pharmaceuticals Ltd.",
        saltComposition = "Amoxicillin (500mg) + Clavulanic Acid (125mg)",
        packSize = "Box of 10 Strips x 10 Tabs",
        dosageForm = "Tablet",
        batchNumber = "AUG-24K2",
        expiryDate = "11/2026",
        hsnCode = "30041010",
        gstRatePercent = 12.0,
        unitPricePtr = 142.50,
        mrp = 198.00,
        moq = 5,
        quantity = 20,
        freeQuantity = 2,
        schemeDescription = "Buy 10 Get 1 Free (10+1)",
        supplierName = "Cipla Wholesale Direct Hub",
        discountPercent = 9.09
    ),
    SelectedPharmaceuticalProduct(
        itemId = 2L,
        productId = 102L,
        productName = "Pan-D Gastro-Resistant",
        brand = "Pan-D",
        manufacturerName = "Alkem Laboratories",
        saltComposition = "Pantoprazole (40mg) + Domperidone (30mg SR)",
        packSize = "Strip of 15 Capsules",
        dosageForm = "Capsule",
        batchNumber = "PND-2025-V4",
        expiryDate = "08/2026",
        hsnCode = "30049099",
        gstRatePercent = 12.0,
        unitPricePtr = 118.00,
        mrp = 165.00,
        moq = 10,
        quantity = 30,
        freeQuantity = 3,
        schemeDescription = "Buy 10 Get 1 Free",
        supplierName = "Cipla Wholesale Direct Hub",
        discountPercent = 9.09
    ),
    SelectedPharmaceuticalProduct(
        itemId = 3L,
        productId = 103L,
        productName = "Mixtard 30/70 100IU",
        brand = "Mixtard",
        manufacturerName = "Novo Nordisk India",
        saltComposition = "Biphasic Isophane Insulin Injection IP",
        packSize = "Vial of 10ml (Cold Chain 2-8°C)",
        dosageForm = "Injection",
        batchNumber = "MIX-2024-C9",
        expiryDate = "06/2026",
        hsnCode = "30043110",
        gstRatePercent = 5.0,
        unitPricePtr = 185.00,
        mrp = 225.00,
        moq = 2,
        quantity = 15,
        freeQuantity = 0,
        schemeDescription = "Wholesale Volume Net Rate",
        supplierName = "Sun Pharma Direct Depo",
        discountPercent = 4.0
    )
)

@Preview(name = "Selected Products Screen - Light Mode", showBackground = true, heightDp = 800)
@Composable
private fun PreviewSelectedProductsScreenLight() {
    PharmaOSTheme {
        SelectedProductsScreenContent(
            products = SampleSelectedPharmaceuticalProducts,
            onQuantityChanged = { _, _ -> },
            onRemoveItem = {},
            onClearAll = {},
            onNavigateBack = {},
            onProceedToCheckout = {},
            onExploreCatalogue = {}
        )
    }
}

@Preview(name = "Selected Products Screen - Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, heightDp = 800)
@Composable
private fun PreviewSelectedProductsScreenDark() {
    PharmaOSTheme(darkTheme = true) {
        SelectedProductsScreenContent(
            products = SampleSelectedPharmaceuticalProducts,
            onQuantityChanged = { _, _ -> },
            onRemoveItem = {},
            onClearAll = {},
            onNavigateBack = {},
            onProceedToCheckout = {},
            onExploreCatalogue = {}
        )
    }
}

@Preview(name = "Product Item Card Preview", showBackground = true)
@Composable
private fun PreviewProductItemCard() {
    PharmaOSTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SelectedProductItemCard(
                product = SampleSelectedPharmaceuticalProducts[0],
                onQuantityChanged = {},
                onRemoveClick = {}
            )
        }
    }
}

@Preview(name = "Order Value Summary Card Preview", showBackground = true)
@Composable
private fun PreviewDetailedOrderSummaryCard() {
    PharmaOSTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            DetailedOrderValueSummaryCard(
                summary = calculateOrderValueSummary(SampleSelectedPharmaceuticalProducts)
            )
        }
    }
}

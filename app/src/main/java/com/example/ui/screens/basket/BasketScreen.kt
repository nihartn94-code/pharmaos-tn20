package com.example.ui.screens.basket

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.domain.repository.BasketItemDetail
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.components.PriceText
import com.example.ui.components.QuantityStepper
import com.example.ui.components.TrustScoreBadge
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasketScreen(
    viewModel: BasketViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToOptimizer: () -> Unit,
    onNavigateToCheckout: () -> Unit,
    onNavigateToCatalogue: () -> Unit,
    onNavigateToSelectedProductsSummary: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSaveDialog by remember { mutableStateOf(false) }
    var saveBasketName by remember { mutableStateOf("") }
    var showSavedBasketsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    val basket = uiState.basket

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Smart Basket",
                subtitle = if (basket != null && basket.items.isNotEmpty()) "${basket.items.size} line items" else null,
                onBackClick = onNavigateBack,
                actions = {
                    if (basket != null && basket.items.isNotEmpty()) {
                        IconButton(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.testTag("save_basket_template_button")
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = "Save Basket Template")
                        }
                    }
                    IconButton(
                        onClick = { showSavedBasketsSheet = true },
                        modifier = Modifier.testTag("view_saved_baskets_button")
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = "Saved Baskets")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (basket != null && basket.items.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Estimated Grand Total",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                PriceText(
                                    amount = basket.grandTotal,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            if (basket.totalSavings > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFDCFCE7)
                                ) {
                                    Text(
                                        text = "Saved ${Formatters.formatInr(basket.totalSavings)}",
                                        color = Color(0xFF15803D),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Detailed Order Value Summary link
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToSelectedProductsSummary() }
                                .testTag("view_selected_products_summary_link")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Review Quantities & Order Value Summary",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onNavigateToOptimizer,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("optimize_basket_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Best-Buy Optimizer", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            Button(
                                onClick = onNavigateToCheckout,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("checkout_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCartCheckout,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Checkout", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingState(message = "Loading your basket...", modifier = Modifier.padding(padding))
        } else if (basket == null || basket.items.isEmpty()) {
            EmptyState(
                title = "Your Basket is Empty",
                message = "Search the catalogue or use the OCR scanner to add trade items.",
                actionLabel = "Explore Catalogue",
                onActionClick = onNavigateToCatalogue,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Warning banner if any load report warnings
                uiState.loadReport?.let { rep ->
                    if (rep.priceChangeWarnings.isNotEmpty() || rep.unavailableWarnings.isNotEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Template Loaded with Adjustments:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                    rep.unavailableWarnings.forEach {
                                        Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                        }
                    }
                }

                // Group items by supplier for multi-supplier splitting visibility
                val itemsBySupplier = basket.items.groupBy { it.supplierId }

                for ((supplierId, supItems) in itemsBySupplier) {
                    val subtotalInfo = basket.supplierSubtotals[supplierId]

                    item {
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = subtotalInfo?.supplierName ?: "Supplier #$supplierId",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (subtotalInfo?.isFreeDeliveryMet == true) "Eligible for Free Delivery"
                                            else "Add ${Formatters.formatInr((subtotalInfo?.freeDeliveryThreshold ?: 0.0) - (subtotalInfo?.subtotal ?: 0.0))} for Free Delivery",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (subtotalInfo?.isFreeDeliveryMet == true) Color(0xFF15803D) else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    TrustScoreBadge(
                                        score = supItems.firstOrNull()?.trustScore,
                                        hasSufficientData = supItems.firstOrNull()?.hasSufficientTrustData == true
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    supItems.forEach { item ->
                                        BasketItemRow(
                                            item = item,
                                            onQuantityChanged = { newQty ->
                                                viewModel.updateQuantity(item.itemId, newQty)
                                            },
                                            onRemove = {
                                                viewModel.removeItem(item.itemId)
                                            }
                                        )
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Subtotal: ${Formatters.formatInr(subtotalInfo?.subtotal ?: 0.0)} | GST: ${Formatters.formatInr(subtotalInfo?.gstAmount ?: 0.0)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = if ((subtotalInfo?.deliveryCharge ?: 0.0) > 0) "Delivery: ${Formatters.formatInr(subtotalInfo?.deliveryCharge ?: 0.0)}" else "Free Delivery",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if ((subtotalInfo?.deliveryCharge ?: 0.0) > 0) MaterialTheme.colorScheme.primary else Color(0xFF15803D)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Save Named Basket Dialog
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("Save Basket as Template", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Save these items and quantities as a named reorder list (e.g. Monthly Standard Stock, Emergency Flu Season).")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = saveBasketName,
                            onValueChange = { saveBasketName = it },
                            label = { Text("Template Name") },
                            placeholder = { Text("e.g. Regular Weekly Reorder") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (saveBasketName.isNotBlank()) {
                                viewModel.saveNamedBasket(saveBasketName) {
                                    showSaveDialog = false
                                    saveBasketName = ""
                                }
                            }
                        }
                    ) {
                        Text("Save Template")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Saved Baskets Bottom Sheet
        if (showSavedBasketsSheet) {
            SavedBasketsBottomSheet(
                savedBaskets = uiState.savedBaskets,
                onDismiss = { showSavedBasketsSheet = false },
                onLoadBasket = { id ->
                    viewModel.loadSavedBasket(id)
                    showSavedBasketsSheet = false
                }
            )
        }
    }
}

@Composable
fun BasketItemRow(
    item: BasketItemDetail,
    onQuantityChanged: (Int) -> Unit,
    onRemove: () -> Unit
) {
    val isUnderMoq = item.quantity < item.moq
    val isOverStock = item.quantity > item.availableQuantity

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Brand: ${item.brand} • Pack: ${item.packSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove Item",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PriceText(amount = item.pricing.totalWithGst, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${Formatters.formatInr(item.pricing.effectiveUnitPrice)}/unit)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (item.pricing.freeQuantity > 0) {
                    Text(
                        text = "+${item.pricing.freeQuantity} Free units applied",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF15803D),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            QuantityStepper(
                quantity = item.quantity,
                onQuantityChanged = onQuantityChanged,
                minQuantity = 1,
                maxQuantity = item.availableQuantity
            )
        }

        if (isUnderMoq) {
            Text(
                text = "Minimum order quantity is ${item.moq} units.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (isOverStock) {
            Text(
                text = "Quantity exceeds currently available stock (${item.availableQuantity} units).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedBasketsBottomSheet(
    savedBaskets: List<com.example.domain.repository.NamedBasketSummary>,
    onDismiss: () -> Unit,
    onLoadBasket: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Saved Basket Templates",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (savedBaskets.isEmpty()) {
                Text(
                    text = "No saved basket templates yet. Use 'Save Template' from your basket to store frequent reorder lists.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(savedBaskets) { b ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLoadBasket(b.id) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(b.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "${b.itemCount} items • Saved on ${Formatters.formatDate(b.updatedAtEpochMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Button(onClick = { onLoadBasket(b.id) }) {
                                    Text("Load")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

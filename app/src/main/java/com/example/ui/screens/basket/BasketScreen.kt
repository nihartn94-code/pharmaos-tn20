package com.example.ui.screens.basket

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.repository.BasketItemDetail
import com.example.domain.repository.BasketWithDetails
import com.example.domain.repository.NamedBasketSummary
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.components.PriceText
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
    var localNameValidationError by remember { mutableStateOf<String?>(null) }
    var showSavedBasketsSheet by remember { mutableStateOf(false) }
    var showClearBasketConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.successMessage, uiState.messageNonce) {
        uiState.successMessage?.let {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.errorMessage, uiState.messageNonce) {
        uiState.errorMessage?.let {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    val basket = uiState.basket
    val totalUnits = basket?.items?.sumOf { it.quantity } ?: 0

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Smart Basket",
                subtitle = if (basket != null && basket.items.isNotEmpty()) {
                    "${basket.items.size} item(s) • $totalUnits units"
                } else null,
                onBackClick = onNavigateBack,
                actions = {
                    if (basket != null && basket.items.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.clearTemplateDialogState()
                                localNameValidationError = null
                                showSaveDialog = true
                            },
                            modifier = Modifier.testTag("save_basket_template_button")
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = "Save Basket Template")
                        }
                        IconButton(
                            onClick = { showClearBasketConfirm = true },
                            modifier = Modifier.testTag("basket_clear_button")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Clear Basket",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = { showSavedBasketsSheet = true },
                            modifier = Modifier.testTag("view_saved_baskets_button")
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = "Saved Baskets (${uiState.savedBaskets.size})")
                        }
                        if (uiState.savedBaskets.isNotEmpty()) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 4.dp, end = 4.dp)
                            ) {
                                Text(
                                    text = "${uiState.savedBaskets.size}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Subtotal: ${Formatters.formatInr(basket.subtotal)} • Tax & Charges: ${Formatters.formatInr(basket.totalGst + basket.totalDeliveryCharges)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Final Payable Amount",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                PriceText(
                                    amount = basket.grandTotal,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.testTag("basket_bottom_payable_amount")
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
                                    .heightIn(min = 48.dp)
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
                                    .heightIn(min = 48.dp)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.syncErrorMessage?.let { syncErr ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("basket_sync_error_banner"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SyncProblem,
                                contentDescription = "Sync Issue",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Offline / Sync error: $syncErr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(
                            onClick = { viewModel.refreshBasket() },
                            modifier = Modifier.testTag("basket_sync_retry_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                LoadingState(message = "Loading your basket...")
            } else if (basket == null || basket.items.isEmpty()) {
                EmptyState(
                    title = "Your Basket is Empty",
                    message = "Search the catalogue or load a saved template to add trade items.",
                    actionLabel = "Explore Catalogue",
                    onActionClick = onNavigateToCatalogue
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("basket_items_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Warning banner if any template load adjustments or price changes
                    uiState.loadReport?.let { rep ->
                        if (rep.priceChangeWarnings.isNotEmpty() || rep.unavailableWarnings.isNotEmpty()) {
                            item(key = "load_report_banner") {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("basket_load_report_banner")
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Template Loaded with Adjustments:",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            IconButton(
                                                onClick = { viewModel.dismissLoadReport() },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("dismiss_load_report_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Dismiss Notice",
                                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        rep.unavailableWarnings.forEach {
                                            Text(
                                                "• $it",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                        rep.priceChangeWarnings.forEach {
                                            Text(
                                                "• $it",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Group items by supplier for multi-supplier splitting visibility
                    val itemsBySupplier = basket.items.groupBy { it.supplierId }

                    itemsBySupplier.forEach { (supplierId, supItems) ->
                        val subtotalInfo = basket.supplierSubtotals[supplierId]

                        item(key = "supplier_$supplierId") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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
                                            val remainingForFree = ((subtotalInfo?.freeDeliveryThreshold ?: 0.0) - (subtotalInfo?.subtotal ?: 0.0)).coerceAtLeast(0.0)
                                            Text(
                                                text = if (subtotalInfo?.isFreeDeliveryMet == true) {
                                                    "Eligible for Free Delivery"
                                                } else {
                                                    "Add ${Formatters.formatInr(remainingForFree)} for Free Delivery"
                                                },
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

                                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        supItems.forEach { item ->
                                            key(item.itemId) {
                                                BasketItemRow(
                                                    item = item,
                                                    onAdjustQuantity = { delta ->
                                                        viewModel.adjustQuantity(item.itemId, delta)
                                                    },
                                                    onQuantityChanged = { newQty ->
                                                        viewModel.updateQuantity(item.itemId, newQty)
                                                    },
                                                    onRemove = {
                                                        viewModel.removeItem(item.itemId)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Subtotal: ${Formatters.formatInr(subtotalInfo?.subtotal ?: 0.0)} • Tax: ${Formatters.formatInr(subtotalInfo?.gstAmount ?: 0.0)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if ((subtotalInfo?.deliveryCharge ?: 0.0) > 0) {
                                                "Freight: ${Formatters.formatInr(subtotalInfo?.deliveryCharge ?: 0.0)}"
                                            } else {
                                                "Free Delivery"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if ((subtotalInfo?.deliveryCharge ?: 0.0) > 0) MaterialTheme.colorScheme.primary else Color(0xFF15803D)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Dedicated Order Financial Breakdown Card distinguishing Subtotal, Taxes, Charges & Final Payable
                    item(key = "basket_financial_breakdown") {
                        BasketFinancialBreakdownCard(basket = basket)
                    }
                }
            }
        }

        // Save Named Basket Dialog
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!uiState.isSavingTemplate) {
                        viewModel.clearTemplateDialogState()
                        localNameValidationError = null
                        showSaveDialog = false
                    }
                },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                modifier = Modifier.testTag("save_template_dialog"),
                title = { Text("Save Basket as Template", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Save your current ${basket?.items?.size ?: 0} selected product(s) and quantities for quick reordering.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = saveBasketName,
                            onValueChange = {
                                saveBasketName = it
                                localNameValidationError = null
                                if (uiState.templateSaveError != null) {
                                    viewModel.clearTemplateDialogState()
                                }
                            },
                            label = { Text("Template Name") },
                            placeholder = { Text("e.g. Regular Weekly Reorder") },
                            isError = localNameValidationError != null || uiState.templateSaveError != null,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_template_name_input")
                        )
                        val activeError = localNameValidationError ?: uiState.templateSaveError
                        if (activeError != null) {
                            Text(
                                text = activeError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("save_template_error_text")
                            )
                        }
                        if (uiState.duplicateTemplateCandidate != null) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.saveNamedBasket(
                                        name = saveBasketName,
                                        overwriteExisting = true
                                    ) {
                                        showSaveDialog = false
                                        saveBasketName = ""
                                    }
                                },
                                enabled = !uiState.isSavingTemplate,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("replace_existing_template_button")
                            ) {
                                Text("Replace Existing '${uiState.duplicateTemplateCandidate}'")
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !uiState.isSavingTemplate,
                        onClick = {
                            if (saveBasketName.isBlank()) {
                                localNameValidationError = "Please enter a template name."
                            } else {
                                viewModel.saveNamedBasket(saveBasketName, overwriteExisting = false) {
                                    showSaveDialog = false
                                    saveBasketName = ""
                                }
                            }
                        },
                        modifier = Modifier.testTag("confirm_save_template_button")
                    ) {
                        if (uiState.isSavingTemplate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Save Template")
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !uiState.isSavingTemplate,
                        onClick = {
                            viewModel.clearTemplateDialogState()
                            localNameValidationError = null
                            showSaveDialog = false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Clear Basket Confirm Dialog
        if (showClearBasketConfirm) {
            AlertDialog(
                onDismissRequest = { showClearBasketConfirm = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                title = { Text("Clear Entire Basket?", fontWeight = FontWeight.Bold) },
                text = { Text("This will remove all ${basket?.items?.size ?: 0} items from your active basket.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearBasket()
                            showClearBasketConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("confirm_clear_basket_button")
                    ) {
                        Text("Clear Basket")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearBasketConfirm = false }) {
                        Text("Keep Items")
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
                    viewModel.loadSavedBasket(id) {
                        showSavedBasketsSheet = false
                    }
                },
                onDeleteBasket = { id ->
                    viewModel.deleteSavedBasket(id)
                }
            )
        }
    }
}

@Composable
private fun BasketFinancialBreakdownCard(
    basket: BasketWithDetails
) {
    val totalUnits = basket.items.sumOf { it.quantity }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("basket_order_summary_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Order Cost & Tax Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            BreakdownRow(
                label = "Basket Subtotal ($totalUnits Paid Units)",
                value = Formatters.formatInr(basket.subtotal),
                valueModifier = Modifier.testTag("basket_subtotal_amount")
            )

            if (basket.totalSavings > 0) {
                BreakdownRow(
                    label = "Scheme & Trade Discounts",
                    value = "-${Formatters.formatInr(basket.totalSavings)}",
                    valueColor = Color(0xFF15803D)
                )
            }

            BreakdownRow(
                label = "Applicable Taxes (GST)",
                value = Formatters.formatInr(basket.totalGst),
                valueModifier = Modifier.testTag("basket_tax_amount")
            )

            BreakdownRow(
                label = "Additional Charges (Delivery / Freight)",
                value = if (basket.totalDeliveryCharges > 0) {
                    Formatters.formatInr(basket.totalDeliveryCharges)
                } else {
                    "Free (₹0.00)"
                },
                valueColor = if (basket.totalDeliveryCharges > 0) MaterialTheme.colorScheme.onSurface else Color(0xFF15803D),
                valueModifier = Modifier.testTag("basket_additional_charges_amount")
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Final Order Value (Payable)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Includes basket subtotal, GST & delivery charges",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = Formatters.formatInr(basket.grandTotal),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("basket_final_payable_amount")
                )
            }
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueModifier: Modifier = Modifier
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
            color = valueColor,
            modifier = valueModifier
        )
    }
}

@Composable
fun BasketItemRow(
    item: BasketItemDetail,
    onQuantityChanged: (Int) -> Unit,
    onRemove: () -> Unit,
    onAdjustQuantity: ((Int) -> Unit)? = null
) {
    val isUnderMoq = item.quantity < item.moq
    val isOverStock = item.quantity > item.availableQuantity

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("basket_item_row_${item.itemId}")
    ) {
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
                Text(
                    text = "PTR: ${Formatters.formatInr(item.unitPricePtr)}/unit • MOQ: ${item.moq} • Stock: ${item.availableQuantity}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("basket_remove_item_${item.itemId}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove ${item.productName}",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Product Subtotal: ${Formatters.formatInr(item.pricing.netAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("basket_item_subtotal_${item.itemId}")
                )
                Text(
                    text = "GST (${item.gstRatePercent.toInt()}%): ${Formatters.formatInr(item.pricing.gstAmount)} • Eff. ${Formatters.formatInr(item.pricing.effectiveUnitPrice)}/unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Line Total: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PriceText(
                        amount = item.pricing.totalWithGst,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.testTag("basket_item_total_${item.itemId}")
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

            BasketQuantityControls(
                itemId = item.itemId,
                productName = item.productName,
                quantity = item.quantity,
                moq = item.moq,
                availableQuantity = item.availableQuantity,
                onAdjustQuantity = onAdjustQuantity,
                onQuantityChanged = onQuantityChanged
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

@Composable
private fun BasketQuantityControls(
    itemId: Long,
    productName: String,
    quantity: Int,
    moq: Int,
    availableQuantity: Int,
    onQuantityChanged: (Int) -> Unit,
    onAdjustQuantity: ((Int) -> Unit)? = null
) {
    var showEditDialog by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(
            onClick = {
                if (onAdjustQuantity != null) {
                    onAdjustQuantity(-1)
                } else {
                    onQuantityChanged(quantity - 1)
                }
            },
            enabled = quantity > 1,
            modifier = Modifier
                .size(48.dp)
                .testTag("basket_minus_$itemId")
        ) {
            Surface(
                shape = CircleShape,
                color = if (quantity > 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease Quantity for $productName",
                        tint = if (quantity > 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .defaultMinSize(minWidth = 54.dp, minHeight = 36.dp)
                .clickable { showEditDialog = true }
                .testTag("basket_qty_$itemId")
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$quantity",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        IconButton(
            onClick = {
                if (onAdjustQuantity != null) {
                    onAdjustQuantity(1)
                } else {
                    onQuantityChanged(quantity + 1)
                }
            },
            enabled = quantity < availableQuantity,
            modifier = Modifier
                .size(48.dp)
                .testTag("basket_plus_$itemId")
        ) {
            Surface(
                shape = CircleShape,
                color = if (quantity < availableQuantity) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase Quantity for $productName",
                        tint = if (quantity < availableQuantity) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        var textValue by remember { mutableStateOf(quantity.toString()) }
        var inlineError by remember { mutableStateOf<String?>(null) }

        val submitQuantity: () -> Unit = {
            val parsed = textValue.trim().toIntOrNull()
            when {
                parsed == null -> inlineError = "Enter a valid whole number."
                parsed < 0 -> inlineError = "Quantity cannot be negative."
                parsed == 0 -> inlineError = "Quantity must be at least 1. Use Remove to delete."
                parsed < moq -> inlineError = "Minimum order quantity is $moq units."
                parsed > availableQuantity -> inlineError = "Exceeds available stock ($availableQuantity units)."
                else -> {
                    onQuantityChanged(parsed)
                    showEditDialog = false
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            title = { Text("Update Quantity", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "$productName (MOQ: $moq • Available: $availableQuantity)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { input ->
                            if (input.isEmpty() || input == "-" || input.all { it.isDigit() || it == '-' }) {
                                textValue = input
                                inlineError = null
                            }
                        },
                        isError = inlineError != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitQuantity() }),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("basket_qty_input_field")
                    )
                    if (inlineError != null) {
                        Text(
                            text = inlineError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = submitQuantity,
                    modifier = Modifier.testTag("basket_qty_confirm_button")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedBasketsBottomSheet(
    savedBaskets: List<NamedBasketSummary>,
    onDismiss: () -> Unit,
    onLoadBasket: (Long) -> Unit,
    onDeleteBasket: (Long) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("saved_baskets_sheet")
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
                    items(savedBaskets, key = { it.id }) { b ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("saved_basket_card_${b.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(b.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "${b.itemCount} item(s) • Saved on ${Formatters.formatDate(b.updatedAtEpochMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onDeleteBasket(b.id) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("delete_saved_basket_${b.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Template ${b.name}",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Button(
                                        onClick = { onLoadBasket(b.id) },
                                        modifier = Modifier.testTag("load_saved_basket_${b.id}")
                                    ) {
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
}

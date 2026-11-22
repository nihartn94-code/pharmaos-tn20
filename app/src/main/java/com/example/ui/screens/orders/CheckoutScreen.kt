package com.example.ui.screens.orders

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AccountStatus
import com.example.domain.model.AuthUser
import com.example.domain.model.EffectivePriceCalculation
import com.example.domain.model.UserRole
import com.example.domain.repository.BasketItemDetail
import com.example.domain.repository.BasketWithDetails
import com.example.domain.repository.SupplierSubtotal
import com.example.ui.components.AppTopBar
import com.example.ui.components.LoadingState
import com.example.ui.components.PriceText
import com.example.ui.theme.PharmaOSTheme
import com.example.util.Formatters
import kotlinx.coroutines.delay

@Composable
fun CheckoutScreen(
    viewModel: OrdersViewModel,
    onNavigateBack: () -> Unit,
    onProceedToPayment: (String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.loadBasketForCheckout()
    }
    CheckoutScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onProceedToPayment = onProceedToPayment,
        onClearMessages = { viewModel.clearMessages() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreenContent(
    uiState: OrdersUiState,
    onNavigateBack: () -> Unit,
    onProceedToPayment: (String, String) -> Unit,
    onClearMessages: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val fallbackDefaultAddress = "Shop 12, Medical Market, Mumbai, MH"
    val registeredAddress = uiState.currentUser?.address?.trim().orEmpty()
    val defaultAddress = registeredAddress.ifBlank { fallbackDefaultAddress }

    var deliveryAddress by remember {
        mutableStateOf(registeredAddress.ifBlank { fallbackDefaultAddress })
    }
    var hasEditedAddress by remember { mutableStateOf(false) }
    var addressError by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var isNavigatingToPayment by remember { mutableStateOf(false) }

    LaunchedEffect(registeredAddress) {
        if (!hasEditedAddress && registeredAddress.isNotBlank()) {
            deliveryAddress = registeredAddress
            addressError = null
        }
    }

    LaunchedEffect(isNavigatingToPayment) {
        if (isNavigatingToPayment) {
            delay(650)
            isNavigatingToPayment = false
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearMessages()
        }
    }

    val basket = uiState.basketForCheckout
    val hasItems = basket != null && basket.items.isNotEmpty()
    val itemsBySupplier = remember(basket?.items) {
        basket?.items?.groupBy { it.supplierId }.orEmpty()
    }
    val totalUnits = remember(basket?.items) {
        basket?.items?.sumOf { it.quantity } ?: 0
    }
    val totalFreeUnits = remember(basket?.items) {
        basket?.items?.sumOf { it.pricing.freeQuantity } ?: 0
    }
    val supplierCount = remember(basket?.supplierSubtotals, itemsBySupplier) {
        val countFromMap = basket?.supplierSubtotals?.size ?: 0
        if (countFromMap > 0) countFromMap else itemsBySupplier.size
    }

    fun proceedToPaymentStage() {
        if (isNavigatingToPayment || uiState.isPlacingOrder || !hasItems) return

        val trimmedAddress = deliveryAddress.trim()
        if (trimmedAddress.isBlank()) {
            addressError = "Please enter a delivery address to continue."
            return
        }

        addressError = null
        keyboardController?.hide()
        focusManager.clearFocus()
        isNavigatingToPayment = true
        onProceedToPayment(trimmedAddress, notes.trim())
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Order Review & Checkout",
                subtitle = "Final review before payment & confirmation",
                onBackClick = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (basket != null && hasItems) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Final Order Value",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${basket.items.size} product${if (basket.items.size == 1) "" else "s"} • $totalUnits unit${if (totalUnits == 1) "" else "s"} • $supplierCount supplier${if (supplierCount == 1) "" else "s"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("checkout_bottom_items_summary")
                                )
                            }
                            PriceText(
                                amount = basket.grandTotal,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.testTag("checkout_final_order_value")
                            )
                        }

                        Button(
                            onClick = { proceedToPaymentStage() },
                            enabled = !uiState.isPlacingOrder && !isNavigatingToPayment && hasItems,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("confirm_order_placement_button")
                        ) {
                            if (uiState.isPlacingOrder || isNavigatingToPayment) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Opening Payment...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            } else {
                                Text(
                                    text = "Continue to Payment",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (basket == null && (uiState.isCheckoutBasketLoading || uiState.isLoading)) {
            LoadingState(
                message = "Loading your order review...",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("checkout_loading_state")
            )
        } else if (basket == null || !hasItems) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .testTag("checkout_empty_state"),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCartCheckout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "No items ready for checkout",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Add medicines to your basket to review supplier allocations, taxes, and payment options.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .testTag("checkout_empty_back_button")
                        ) {
                            Text("Return to Basket")
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Stage Indicator: Step 1 (Order Review) -> Step 2 (Payment)
                CheckoutStageIndicator(currentStep = 1)

                // 1. Final Review Banner & Supplier Split Notice
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("checkout_review_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Final Order Review • $supplierCount Supplier Allocation${if (supplierCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Verify selected products, quantities, billing details, and delivery address. You will select payment method and authorize on the next step.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // 2. Billing & Buyer Identity Card
                uiState.currentUser?.let { buyer ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkout_billing_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Business,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Billing Details",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = buyer.role.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Text(
                                text = buyer.businessName.ifBlank { buyer.fullName },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${buyer.fullName} • ${buyer.phone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!buyer.gstin.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "GSTIN: ${buyer.gstin}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            if (buyer.address.isNotBlank()) {
                                Text(
                                    text = "Registered Address: ${buyer.address}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 3. Delivery & Shipping Address + Dispatch Notes
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("checkout_delivery_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
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
                                text = "Delivery & Shipping Address *",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(
                                onClick = {
                                    deliveryAddress = defaultAddress
                                    hasEditedAddress = true
                                    addressError = null
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier.testTag("checkout_use_default_address_button")
                            ) {
                                Text(
                                    text = if (registeredAddress.isNotBlank()) "Use Registered" else "Use Default",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        OutlinedTextField(
                            value = deliveryAddress,
                            onValueChange = {
                                deliveryAddress = it
                                hasEditedAddress = true
                                if (it.isNotBlank()) addressError = null
                            },
                            label = { Text("Physical Warehouse / Clinic / Pharmacy Address") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            isError = addressError != null,
                            supportingText = addressError?.let { err -> { Text(err) } },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            ),
                            singleLine = false,
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("checkout_address_input")
                        )

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Dispatch & Handling Notes (Optional)") },
                            placeholder = { Text("e.g. Cold chain insulated box required") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Notes,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            ),
                            singleLine = false,
                            maxLines = 2,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("checkout_notes_input")
                        )
                    }
                }

                // 4. Selected Products & Supplier Allocation Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Selected Products & Supplier Allocation",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "${basket.items.size} SKU${if (basket.items.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val orderedSuppliers = basket.supplierSubtotals.values.toList()
                orderedSuppliers.forEachIndexed { index, sub ->
                    val supplierItems = itemsBySupplier[sub.supplierId].orEmpty()
                    SupplierAllocationReviewCard(
                        allocationIndex = index + 1,
                        supplierSubtotal = sub,
                        items = supplierItems
                    )
                }

                // 5. Consolidated Final Order Value Summary Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("checkout_final_summary_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Final Order Value Summary",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "$totalUnits unit${if (totalUnits == 1) "" else "s"}" +
                                    if (totalFreeUnits > 0) " (+$totalFreeUnits free)" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                        SummaryChargeRow(
                            label = "Products Subtotal (Excl. GST)",
                            value = Formatters.formatInr(basket.subtotal),
                            valueTag = "checkout_summary_subtotal"
                        )

                        if (basket.totalSavings > 0.0) {
                            SummaryChargeRow(
                                label = "Trade Scheme & Margin Savings",
                                value = "-${Formatters.formatInr(basket.totalSavings)}",
                                valueColor = Color(0xFF10B981),
                                valueTag = "checkout_summary_savings"
                            )
                        }

                        SummaryChargeRow(
                            label = "Applicable GST",
                            value = Formatters.formatInr(basket.totalGst),
                            valueTag = "checkout_summary_gst"
                        )

                        SummaryChargeRow(
                            label = "Delivery & Logistics Charges",
                            value = if (basket.totalDeliveryCharges > 0.0) {
                                Formatters.formatInr(basket.totalDeliveryCharges)
                            } else {
                                "FREE"
                            },
                            valueColor = if (basket.totalDeliveryCharges > 0.0) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                Color(0xFF10B981)
                            },
                            valueTag = "checkout_summary_delivery"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Final Payable Amount",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Across $supplierCount purchase order${if (supplierCount == 1) "" else "s"} • Payment on next step",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            PriceText(
                                amount = basket.grandTotal,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.testTag("checkout_summary_grand_total")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SupplierAllocationReviewCard(
    allocationIndex: Int,
    supplierSubtotal: SupplierSubtotal,
    items: List<BasketItemDetail>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("checkout_supplier_card_${supplierSubtotal.supplierId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Supplier header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = supplierSubtotal.supplierName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "PO #$allocationIndex • ${items.size} item${if (items.size == 1) "" else "s"} allocated",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (supplierSubtotal.deliveryCharge <= 0.0) {
                        Color(0xFFDCFCE7)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = if (supplierSubtotal.deliveryCharge <= 0.0) {
                            "FREE Delivery"
                        } else {
                            "Delivery ${Formatters.formatInr(supplierSubtotal.deliveryCharge)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (supplierSubtotal.deliveryCharge <= 0.0) {
                            Color(0xFF15803D)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (items.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.forEachIndexed { idx, item ->
                        CheckoutLineItemRow(item = item)
                        if (idx < items.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            // Supplier charges summary
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SummaryChargeRow(
                    label = "Supplier Subtotal",
                    value = Formatters.formatInr(supplierSubtotal.subtotal)
                )
                SummaryChargeRow(
                    label = "Supplier GST",
                    value = Formatters.formatInr(supplierSubtotal.gstAmount)
                )
                SummaryChargeRow(
                    label = "Delivery Fee",
                    value = if (supplierSubtotal.deliveryCharge > 0.0) {
                        Formatters.formatInr(supplierSubtotal.deliveryCharge)
                    } else {
                        "FREE"
                    },
                    valueColor = if (supplierSubtotal.deliveryCharge > 0.0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        Color(0xFF10B981)
                    }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Supplier Order Total",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                PriceText(amount = supplierSubtotal.total)
            }
        }
    }
}

@Composable
private fun CheckoutLineItemRow(item: BasketItemDetail) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("checkout_item_${item.itemId}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = item.productName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.brand} • ${item.packSize}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "Qty: ${item.quantity}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("checkout_item_qty_${item.itemId}")
                    )
                }
                if (item.pricing.freeQuantity > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFDCFCE7)
                    ) {
                        Text(
                            text = "+${item.pricing.freeQuantity} Free",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "PTR ${Formatters.formatInr(item.unitPricePtr)} • ${item.gstRatePercent.toInt()}% GST",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Formatters.formatInr(item.pricing.netAmount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("checkout_item_subtotal_${item.itemId}")
            )
            Text(
                text = "Incl. GST ${Formatters.formatInr(item.pricing.totalWithGst)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryChargeRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueTag: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            modifier = if (valueTag != null) Modifier.testTag(valueTag) else Modifier
        )
    }
}

@Composable
internal fun CheckoutStageIndicator(
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("checkout_stage_indicator")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CheckoutStageStepBadge(
                stepNumber = 1,
                label = "Order Review",
                isActive = currentStep == 1,
                isCompleted = currentStep > 1
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = 1.5.dp,
                color = if (currentStep > 1) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                }
            )
            CheckoutStageStepBadge(
                stepNumber = 2,
                label = "Payment & Place Order",
                isActive = currentStep == 2,
                isCompleted = currentStep > 2
            )
        }
    }
}

@Composable
private fun CheckoutStageStepBadge(
    stepNumber: Int,
    label: String,
    isActive: Boolean,
    isCompleted: Boolean
) {
    val badgeBg = when {
        isCompleted -> Color(0xFF10B981)
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val badgeFg = when {
        isCompleted -> Color.White
        isActive -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = badgeBg,
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = badgeFg,
                        modifier = Modifier.size(14.dp)
                    )
                } else {
                    Text(
                        text = stepNumber.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isActive || isCompleted) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive || isCompleted) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Preview(name = "Checkout - Light Mode", showBackground = true)
@Preview(name = "Checkout - Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun CheckoutScreenPreview() {
    PharmaOSTheme {
        CheckoutScreenContent(
            uiState = OrdersUiState(
                isLoading = false,
                currentUser = AuthUser(
                    id = 1L,
                    fullName = "Rohan Sharma",
                    businessName = "Apex Pharma Distributors",
                    email = "rohan.sharma@apexpharma.com",
                    phone = "+919876543210",
                    gstin = "27ABCDE1234F1Z5",
                    address = "Shop 12, Medical Market, Mumbai, MH",
                    role = UserRole.STOCKIST,
                    status = AccountStatus.ACTIVE
                ),
                basketForCheckout = BasketWithDetails(
                    basketId = 1L,
                    items = listOf(
                        BasketItemDetail(
                            itemId = 101L,
                            productId = 1L,
                            productName = "Pan-D Capsule SR",
                            brand = "Alkem",
                            saltComposition = "Pantoprazole 40mg + Domperidone 30mg",
                            packSize = "15 Capsules",
                            hsnCode = "30049099",
                            gstRatePercent = 12.0,
                            offerId = 11L,
                            supplierId = 10L,
                            supplierName = "Alkem Lab Private Limited",
                            unitPricePtr = 137.0,
                            mrp = 199.0,
                            moq = 2,
                            availableQuantity = 250,
                            quantity = 6,
                            pricing = EffectivePriceCalculation(
                                regularQuantity = 6,
                                freeQuantity = 1,
                                totalReceivedQuantity = 7,
                                grossAmount = 822.0,
                                discountAmount = 0.0,
                                netAmount = 822.0,
                                effectiveUnitPrice = 117.43,
                                gstAmount = 98.64,
                                totalWithGst = 920.64,
                                savingsAmount = 110.0,
                                savingsPercent = 11.8
                            ),
                            schemeDescription = "Buy 5 Get 1 Free",
                            trustScore = 94,
                            hasSufficientTrustData = true
                        )
                    ),
                    subtotal = 822.0,
                    totalGst = 98.64,
                    totalDeliveryCharges = 0.0,
                    grandTotal = 920.64,
                    totalSavings = 110.0,
                    supplierSubtotals = mapOf(
                        10L to SupplierSubtotal(
                            supplierId = 10L,
                            supplierName = "Alkem Lab Private Limited",
                            subtotal = 822.0,
                            gstAmount = 98.64,
                            deliveryCharge = 0.0,
                            freeDeliveryThreshold = 500.0,
                            total = 920.64,
                            isFreeDeliveryMet = true
                        )
                    )
                )
            ),
            onNavigateBack = {},
            onProceedToPayment = { _, _ -> },
            onClearMessages = {}
        )
    }
}

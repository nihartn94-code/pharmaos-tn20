package com.example.ui.screens.orders

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AppTopBar
import com.example.ui.components.PriceText
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    viewModel: OrdersViewModel,
    onNavigateBack: () -> Unit,
    onOrderPlaced: (List<Long>) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val defaultAddress = "Shop 12, Medical Market, Mumbai, MH"
    var deliveryAddress by remember {
        mutableStateOf(uiState.currentUser?.address?.ifBlank { defaultAddress } ?: defaultAddress)
    }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadBasketForCheckout()
    }

    LaunchedEffect(uiState.currentUser) {
        val userAddr = uiState.currentUser?.address
        if (deliveryAddress.isBlank() && !userAddr.isNullOrBlank()) {
            deliveryAddress = userAddr
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    val basket = uiState.basketForCheckout

    fun submitOrder() {
        keyboardController?.hide()
        focusManager.clearFocus()
        val finalAddress = deliveryAddress.ifBlank { defaultAddress }
        viewModel.placeOrders(finalAddress, notes, onOrderPlaced)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Order Checkout & Review",
                subtitle = "Multi-supplier split order placement",
                onBackClick = onNavigateBack,
                actions = {
                    // Header Submit Action: Always available even with keyboard open
                    Button(
                        onClick = { submitOrder() },
                        enabled = !uiState.isPlacingOrder,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1D64EC),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("top_bar_submit_order_button")
                    ) {
                        if (uiState.isPlacingOrder) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Submit", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (basket != null) {
                Surface(
                    color = Color.White,
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Total Amount Payable (Debit to Ledger):",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B)
                                )
                                PriceText(amount = basket.grandTotal, style = MaterialTheme.typography.titleLarge)
                            }

                            // Dismiss keyboard quick button
                            TextButton(
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            ) {
                                Icon(Icons.Default.KeyboardHide, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF1D64EC))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hide Keyboard", fontSize = 12.sp, color = Color(0xFF1D64EC), fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Button(
                            onClick = { submitOrder() },
                            enabled = !uiState.isPlacingOrder,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1D64EC),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("confirm_order_placement_button")
                        ) {
                            if (uiState.isPlacingOrder) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Confirm & Place Orders (${basket.supplierSubtotals.size} Suppliers)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Supplier Splitting Notice
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFEFF6FF),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDBEAFE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = Color(0xFF1D64EC),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Your basket contains items from ${basket?.supplierSubtotals?.size ?: 1} different supplier(s). Separate purchase orders will be generated for direct tracking and compliance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1E40AF),
                        fontSize = 12.sp
                    )
                }
            }

            // Shipping Address Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Delivery & Shipping Address *",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF0F172A)
                    )
                    TextButton(
                        onClick = {
                            deliveryAddress = defaultAddress
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    ) {
                        Text("Use Default", fontSize = 12.sp, color = Color(0xFF1D64EC))
                    }
                }

                OutlinedTextField(
                    value = deliveryAddress,
                    onValueChange = { deliveryAddress = it },
                    label = { Text("Physical Warehouse / Clinic / Pharmacy Address") },
                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF1D64EC)) },
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
            }

            // Dispatch Notes Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Dispatch & Handling Notes",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF0F172A)
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Special instructions (e.g. Cold chain delivery required)") },
                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = Color(0xFF64748B)) },
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

            // Order Breakdown Header
            Text(
                text = "Order Breakdown by Supplier",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            basket?.supplierSubtotals?.values?.forEach { sub ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = sub.supplierName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Subtotal:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Formatters.formatInr(sub.subtotal), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("GST:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Formatters.formatInr(sub.gstAmount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Delivery Fee:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (sub.deliveryCharge > 0) Formatters.formatInr(sub.deliveryCharge) else "FREE",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (sub.deliveryCharge > 0) MaterialTheme.colorScheme.onSurface else Color(0xFF10B981)
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Supplier Order Total:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            PriceText(amount = sub.total)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Inline Submit Button inside the scrollable content
            Button(
                onClick = { submitOrder() },
                enabled = !uiState.isPlacingOrder,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1D64EC),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("inline_confirm_order_placement_button")
            ) {
                if (uiState.isPlacingOrder) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Confirm & Place Orders (${basket?.supplierSubtotals?.size ?: 1} Suppliers)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

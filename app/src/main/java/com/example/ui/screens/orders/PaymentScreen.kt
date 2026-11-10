package com.example.ui.screens.orders

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AuthUser
import com.example.domain.model.UserRole
import com.example.domain.repository.BasketWithDetails
import com.example.domain.repository.SupplierSubtotal
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.example.ui.components.AppTopBar
import com.example.ui.components.PriceText
import com.example.ui.theme.PharmaOSTheme
import com.example.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class UpiValidationResult {
    object Empty : UpiValidationResult()
    data class Invalid(val reason: String) : UpiValidationResult()
    data class Valid(val formatted: String, val provider: String) : UpiValidationResult()
}

/**
 * Validates UPI Virtual Payment Address (VPA) format.
 * Expected format: username@bankhandle (e.g. stockist@okhdfcbank, 9876543210@paytm)
 */
fun validateUpiId(input: String): UpiValidationResult {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return UpiValidationResult.Empty
    }
    if (!trimmed.contains("@")) {
        return UpiValidationResult.Invalid("UPI ID must contain '@' symbol (e.g. stockist@okhdfcbank)")
    }
    val parts = trimmed.split("@")
    if (parts.size != 2) {
        return UpiValidationResult.Invalid("UPI ID can only contain a single '@' character")
    }
    val username = parts[0]
    val handle = parts[1]

    if (username.isEmpty()) {
        return UpiValidationResult.Invalid("Username prefix before '@' is required")
    }
    if (username.length < 2) {
        return UpiValidationResult.Invalid("Username prefix must have at least 2 characters")
    }
    if (!username.matches(Regex("^[a-zA-Z0-9.\\-_]+$"))) {
        return UpiValidationResult.Invalid("Prefix can only contain letters, numbers, '.', '-', and '_'")
    }
    if (handle.isEmpty()) {
        return UpiValidationResult.Invalid("Bank/PSP handle after '@' is required (e.g. @okhdfcbank)")
    }
    if (handle.length < 2) {
        return UpiValidationResult.Invalid("Bank handle after '@' must have at least 2 characters")
    }
    if (!handle.matches(Regex("^[a-zA-Z0-9]+$"))) {
        return UpiValidationResult.Invalid("Bank handle can only contain letters/numbers (e.g. okhdfcbank, paytm)")
    }

    val provider = when {
        handle.contains("hdfc", ignoreCase = true) -> "HDFC Bank UPI"
        handle.contains("icici", ignoreCase = true) -> "ICICI Bank UPI"
        handle.contains("sbi", ignoreCase = true) -> "State Bank of India (SBI)"
        handle.contains("axis", ignoreCase = true) || handle.contains("axl", ignoreCase = true) -> "Axis Bank UPI"
        handle.contains("paytm", ignoreCase = true) -> "Paytm Payments Bank"
        handle.contains("ybl", ignoreCase = true) || handle.contains("ibl", ignoreCase = true) -> "PhonePe UPI"
        handle.contains("apl", ignoreCase = true) -> "Amazon Pay UPI"
        handle.contains("kotak", ignoreCase = true) -> "Kotak Mahindra Bank"
        handle.contains("barodampay", ignoreCase = true) || handle.contains("bob", ignoreCase = true) -> "Bank of Baroda UPI"
        else -> "NPCI Verified UPI"
    }

    return UpiValidationResult.Valid(
        formatted = trimmed.lowercase(),
        provider = provider
    )
}

enum class PaymentMethodType(
    val title: String,
    val subtitle: String,
    val tag: String,
    val icon: ImageVector
) {
    PHARMA_CREDIT(
        title = "Pharma Wholesale Credit Line",
        subtitle = "Net-30 deferred settlement with 0% interest",
        tag = "RECOMMENDED",
        icon = Icons.Default.AccountBalanceWallet
    ),
    UPI_INSTANT(
        title = "Instant UPI Payment",
        subtitle = "Google Pay, PhonePe, Paytm, BHIM & QR Code",
        tag = "INSTANT DISPATCH",
        icon = Icons.Default.PhoneAndroid
    ),
    NET_BANKING(
        title = "Corporate Net Banking / RTGS",
        subtitle = "Direct bank transfer to PharmaOS Escrow account",
        tag = "LARGE CONSIGNMENTS",
        icon = Icons.Default.AccountBalance
    ),
    CHEQUE_PDC(
        title = "Post-Dated Cheque (PDC)",
        subtitle = "Hand over 30-day verified PDC upon dispatch",
        tag = "30 DAYS TERMS",
        icon = Icons.Default.ReceiptLong
    ),
    CASH_ON_DELIVERY(
        title = "Cash / DD on Inward Delivery",
        subtitle = "Pay after warehouse dock inspection & QA verification",
        tag = "ON ARRIVAL",
        icon = Icons.Default.LocalAtm
    )
}

@Composable
fun PaymentScreen(
    viewModel: OrdersViewModel,
    deliveryAddress: String,
    orderNotes: String,
    onNavigateBack: () -> Unit,
    onPaymentSuccess: (List<Long>, String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadBasketForCheckout()
    }

    PaymentScreenContent(
        uiState = uiState,
        deliveryAddress = deliveryAddress,
        orderNotes = orderNotes,
        onNavigateBack = onNavigateBack,
        onAuthorizePayment = { method, refId, onDone ->
            viewModel.placeOrders(deliveryAddress, orderNotes) { ids ->
                onDone(ids)
            }
        },
        onClearMessages = { viewModel.clearMessages() },
        onPaymentSuccess = onPaymentSuccess
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreenContent(
    uiState: OrdersUiState,
    deliveryAddress: String,
    orderNotes: String,
    onNavigateBack: () -> Unit,
    onAuthorizePayment: (PaymentMethodType, String, (List<Long>) -> Unit) -> Unit,
    onClearMessages: () -> Unit,
    onPaymentSuccess: (List<Long>, String, String) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var selectedMethod by remember { mutableStateOf(PaymentMethodType.PHARMA_CREDIT) }
    var upiIdInput by remember { mutableStateOf("") }
    var upiHasBeenTouched by remember { mutableStateOf(false) }
    val upiValidation = remember(upiIdInput) { validateUpiId(upiIdInput) }

    var selectedBank by remember { mutableStateOf("HDFC Bank Corporate") }
    var chequeNumber by remember { mutableStateOf("") }
    var isProcessingPayment by remember { mutableStateOf(false) }

    val basket = uiState.basketForCheckout
    val grandTotal = basket?.grandTotal ?: 920.64
    val user = uiState.currentUser

    // 30 days due date for Credit Line
    val dueDateMillis = remember { System.currentTimeMillis() + (30L * 86400000L) }
    val dueDateString = remember(dueDateMillis) {
        SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(dueDateMillis))
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearMessages()
            isProcessingPayment = false
        }
    }

    fun executePayment() {
        if (isProcessingPayment) return

        keyboardController?.hide()
        focusManager.clearFocus()

        // Validate UPI ID if Instant UPI payment is chosen
        if (selectedMethod == PaymentMethodType.UPI_INSTANT) {
            upiHasBeenTouched = true
            when (val validation = upiValidation) {
                is UpiValidationResult.Empty -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Please enter your UPI ID (e.g. stockist@okhdfcbank)")
                    }
                    return
                }
                is UpiValidationResult.Invalid -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(validation.reason)
                    }
                    return
                }
                is UpiValidationResult.Valid -> {
                    // Valid UPI ID, proceed with payment
                }
            }
        }

        isProcessingPayment = true

        coroutineScope.launch {
            // Simulated payment gateway handshake / credit limit validation
            delay(900)
            val generatedTxnId = "TXN-POS-${(10000..99999).random()}"
            val methodLabel = if (selectedMethod == PaymentMethodType.UPI_INSTANT && upiValidation is UpiValidationResult.Valid) {
                "${selectedMethod.title} (${(upiValidation as UpiValidationResult.Valid).formatted})"
            } else {
                selectedMethod.title
            }
            onAuthorizePayment(selectedMethod, generatedTxnId) { orderIds ->
                isProcessingPayment = false
                onPaymentSuccess(orderIds, methodLabel, generatedTxnId)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Payment & Settlement",
                subtitle = "Choose B2B Wholesale Payment Method",
                onBackClick = onNavigateBack
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total Payable Amount",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            PriceText(
                                amount = grandTotal,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }

                        Button(
                            onClick = { executePayment() },
                            enabled = !isProcessingPayment && !uiState.isPlacingOrder,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1D64EC),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("pay_and_place_order_button")
                        ) {
                            if (isProcessingPayment || uiState.isPlacingOrder) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Authorizing...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = if (selectedMethod == PaymentMethodType.PHARMA_CREDIT) Icons.Default.Lock else Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (selectedMethod) {
                                        PaymentMethodType.PHARMA_CREDIT -> "Authorize Order"
                                        PaymentMethodType.CASH_ON_DELIVERY -> "Confirm COD Order"
                                        PaymentMethodType.CHEQUE_PDC -> "Confirm PDC Order"
                                        else -> "Pay Now"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "256-Bit SSL Encrypted • CDSCO & GST E-Invoice Compliant",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Order Summary & Price Breakdown Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Order Payable Summary",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFDCFCE7)
                            ) {
                                Text(
                                    text = "GST Compliant",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Medicines Subtotal", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Text(Formatters.formatInr(basket?.subtotal ?: 822.0), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Applicable GST (12% Pharma)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Text(Formatters.formatInr(basket?.totalGst ?: 98.64), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Cold-Chain & Warehouse Delivery", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            val deliveryFee = basket?.totalDeliveryCharges ?: 0.0
                            Text(
                                text = if (deliveryFee > 0) Formatters.formatInr(deliveryFee) else "FREE",
                                fontWeight = FontWeight.Bold,
                                color = if (deliveryFee > 0) MaterialTheme.colorScheme.onSurface else Color(0xFF10B981),
                                fontSize = 14.sp
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Total Settlement Amount",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Includes all taxes & batch COA records",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            PriceText(
                                amount = grandTotal,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }
            }

            // 2. Delivery & Commercial Identity Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFEFF6FF),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = user?.businessName ?: "Apex Pharma Distributors",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "GSTIN: ${user?.gstin ?: "27ABCDE1234F1Z5"} • Drug Lic: DL-20B/21B-89412",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Delivery to: ${deliveryAddress.ifBlank { "Shop 12, Medical Market, Mumbai, MH" }}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // 3. Payment Methods Header
            item {
                Text(
                    text = "Select Payment Method",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Payment Options
            items(PaymentMethodType.values().size) { index ->
                val method = PaymentMethodType.values()[index]
                val isSelected = selectedMethod == method

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFF1D64EC) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMethod = method }
                        .testTag("payment_method_${method.name.lowercase()}")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedMethod = method },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF1D64EC))
                            )

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF1D64EC) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = method.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = method.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (method == PaymentMethodType.PHARMA_CREDIT) Color(0xFFFEF3C7) else Color(0xFFF1F5F9)
                                    ) {
                                        Text(
                                            text = method.tag,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (method == PaymentMethodType.PHARMA_CREDIT) Color(0xFFB45309) else Color(0xFF475569),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = method.subtitle,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Expandable details for selected method
                        AnimatedVisibility(visible = isSelected) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 48.dp, top = 12.dp)
                            ) {
                                when (method) {
                                    PaymentMethodType.PHARMA_CREDIT -> {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFF0FDF4),
                                            border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Approved Credit Limit:", fontSize = 12.sp, color = Color(0xFF166534))
                                                    Text("₹2,50,000.00", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Available Balance:", fontSize = 12.sp, color = Color(0xFF166534))
                                                    Text("₹1,85,200.00", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "✓ Order will be released immediately. Settlement due on $dueDateString.",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF166534),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    PaymentMethodType.UPI_INSTANT -> {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            val isError = upiHasBeenTouched && upiValidation is UpiValidationResult.Invalid
                                            val isValid = upiValidation is UpiValidationResult.Valid

                                            OutlinedTextField(
                                                value = upiIdInput,
                                                onValueChange = {
                                                    upiIdInput = it
                                                    upiHasBeenTouched = true
                                                },
                                                label = { Text("Virtual Payment Address (UPI ID) *") },
                                                placeholder = { Text("e.g. stockist@okhdfcbank or 9876543210@paytm") },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.QrCode,
                                                        contentDescription = null,
                                                        tint = if (isError) MaterialTheme.colorScheme.error else if (isValid) Color(0xFF10B981) else Color(0xFF2563EB)
                                                    )
                                                },
                                                trailingIcon = {
                                                    when {
                                                        isValid -> {
                                                            Icon(
                                                                imageVector = Icons.Default.CheckCircle,
                                                                contentDescription = "Valid UPI ID",
                                                                tint = Color(0xFF10B981),
                                                                modifier = Modifier.size(22.dp)
                                                            )
                                                        }
                                                        isError -> {
                                                            Icon(
                                                                imageVector = Icons.Default.ErrorOutline,
                                                                contentDescription = "Invalid UPI ID",
                                                                tint = MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.size(22.dp)
                                                            )
                                                        }
                                                        upiIdInput.isNotEmpty() -> {
                                                            IconButton(onClick = { upiIdInput = ""; upiHasBeenTouched = false }) {
                                                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                                            }
                                                        }
                                                    }
                                                },
                                                isError = isError,
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Email,
                                                    imeAction = ImeAction.Done
                                                ),
                                                keyboardActions = KeyboardActions(
                                                    onDone = {
                                                        keyboardController?.hide()
                                                        focusManager.clearFocus()
                                                    }
                                                ),
                                                supportingText = {
                                                    when {
                                                        isValid -> {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(top = 2.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Verified,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFF10B981),
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = "Valid UPI ID • ${(upiValidation as UpiValidationResult.Valid).provider}",
                                                                    color = Color(0xFF15803D),
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    fontSize = 11.sp
                                                                )
                                                            }
                                                        }
                                                        isError -> {
                                                            Text(
                                                                text = (upiValidation as UpiValidationResult.Invalid).reason,
                                                                color = MaterialTheme.colorScheme.error,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                        else -> {
                                                            Text(
                                                                text = "Format: username@handle (e.g. apex@okhdfcbank, 9876543210@paytm)",
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = if (isValid) Color(0xFF10B981) else Color(0xFF1D64EC),
                                                    unfocusedBorderColor = if (isValid) Color(0xFF10B981) else MaterialTheme.colorScheme.outline
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("upi_id_input")
                                            )

                                            // Quick bank handle shortcut chips
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    text = "Quick Handle Shortcuts:",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                val handles = listOf("@okhdfcbank", "@okaxis", "@oksbi", "@okicici", "@paytm", "@ybl")
                                                LazyRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    items(handles) { handle ->
                                                        Surface(
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                                            modifier = Modifier
                                                                .clickable {
                                                                    upiHasBeenTouched = true
                                                                    val current = upiIdInput.trim()
                                                                    upiIdInput = if (current.isEmpty()) {
                                                                        "stockist$handle"
                                                                    } else if (current.contains("@")) {
                                                                        val prefix = current.substringBefore("@")
                                                                        "$prefix$handle"
                                                                    } else {
                                                                        "$current$handle"
                                                                    }
                                                                }
                                                                .testTag("upi_handle_chip_${handle.removePrefix("@")}")
                                                        ) {
                                                            Text(
                                                                text = handle,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = Color(0xFF1D64EC),
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // Live validation status confirmation card
                                            AnimatedVisibility(visible = isValid) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFFF0FDF4),
                                                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = null,
                                                            tint = Color(0xFF15803D),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "UPI address format verified for instant debit. Orders will be released immediately.",
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF166534),
                                                            lineHeight = 15.sp
                                                        )
                                                    }
                                                }
                                            }

                                            Text(
                                                text = "Supported: Google Pay, PhonePe, Paytm, BHIM, Amazon Pay & CRED",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    PaymentMethodType.NET_BANKING -> {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text("Virtual Escrow Account for NEFT / RTGS:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Bank: HDFC Bank Corporate Escrow", fontSize = 12.sp)
                                                Text("A/C Number: PHARMAOS8941209", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text("IFSC Code: HDFC0000001", fontSize = 12.sp)
                                                Text("Beneficiary: PharmaOS Escrow Private Limited", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }

                                    PaymentMethodType.CHEQUE_PDC -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedTextField(
                                                value = chequeNumber,
                                                onValueChange = { chequeNumber = it },
                                                placeholder = { Text("Enter Cheque Number (6 Digits)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Text(
                                                text = "Cheque will be collected by courier upon physical batch handover.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    PaymentMethodType.CASH_ON_DELIVERY -> {
                                        Text(
                                            text = "Please keep exact cash / Demand Draft ready upon logistics arrival at warehouse dock.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Regulatory & cGMP Assurance Banner
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "All consignments are backed by cGMP-certified manufacturer warranties. 100% credit note issued in case of batch recalls or transit breakage.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Payment Screen - Light Mode", showBackground = true)
@Preview(name = "Payment Screen - Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun PaymentScreenPreview() {
    PharmaOSTheme {
        PaymentScreenContent(
            uiState = OrdersUiState(
                isLoading = false,
                basketForCheckout = BasketWithDetails(
                    basketId = 1L,
                    items = emptyList(),
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
                ),
                currentUser = AuthUser(
                    id = 1L,
                    fullName = "Rohan Sharma",
                    businessName = "Apex Pharma Distributors",
                    email = "rohan.sharma@apexpharma.com",
                    phone = "+919876543210",
                    gstin = "27ABCDE1234F1Z5",
                    address = "Shop 12, Medical Market, Mumbai, MH",
                    role = UserRole.STOCKIST,
                    status = com.example.domain.model.AccountStatus.ACTIVE
                )
            ),
            deliveryAddress = "Shop 12, Medical Market, Mumbai, MH",
            orderNotes = "Please ensure insulated cold pack packaging.",
            onNavigateBack = {},
            onAuthorizePayment = { _, _, onDone -> onDone(listOf(101L)) },
            onClearMessages = {},
            onPaymentSuccess = { _, _, _ -> }
        )
    }
}

package com.example.ui.screens.orders

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
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import com.example.domain.model.OrderStatus
import com.example.domain.model.ReturnReason
import com.example.domain.model.TaxInvoiceData
import com.example.domain.model.UserRole
import com.example.ui.components.AppTopBar
import com.example.ui.components.PriceText
import com.example.ui.components.StatusChip
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailsScreen(
    orderId: Long,
    viewModel: OrdersViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBasket: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDispatchDialog by remember { mutableStateOf(false) }
    var ewayBillNumber by remember { mutableStateOf("EWB-2026-98124") }
    var vehicleNumber by remember { mutableStateOf("MH-12-AB-1234") }
    var transporterName by remember { mutableStateOf("SafeExpress Pharma Logistics") }

    var showPodDialog by remember { mutableStateOf(false) }
    var podReceivedByName by remember { mutableStateOf("") }
    var podNotes by remember { mutableStateOf("") }
    var podShortQty by remember { mutableIntStateOf(0) }
    var podDamagedQty by remember { mutableIntStateOf(0) }

    val context = LocalContext.current

    LaunchedEffect(orderId) {
        viewModel.loadOrderDetails(orderId)
    }

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

    val details = uiState.selectedOrderDetails

    Scaffold(
        topBar = {
            AppTopBar(
                title = details?.order?.orderNumber ?: "Order Details",
                subtitle = details?.let { "Placed on ${Formatters.formatDateTime(it.order.createdAtEpochMillis)}" },
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = onNavigateToBasket,
                        modifier = Modifier.testTag("order_details_basket_button")
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = "Open Basket")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (details == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val order = details.order
            val currentStatus = try { OrderStatus.valueOf(order.status) } catch (_: Exception) { OrderStatus.PLACED }
            val isSupplier = uiState.currentUser?.role?.isSupplier == true || uiState.currentUser?.role == UserRole.ADMIN
            val isBuyer = uiState.currentUser?.role?.isBuyer == true

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status Header Card
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
                                    Text("Order Status:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    StatusChip(statusString = order.status)
                                }
                                PriceText(amount = order.totalAmount, style = MaterialTheme.typography.titleLarge)
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Supplier: ${details.supplier?.businessName ?: "Supplier"} (${details.supplier?.fullName ?: ""})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Buyer: ${details.buyer?.businessName ?: "Buyer"} (${details.buyer?.fullName ?: ""})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Shipping Address: ${order.deliveryAddress}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Action buttons based on Role & Current Status
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isSupplier) {
                                    when (currentStatus) {
                                        OrderStatus.PLACED -> {
                                            Button(
                                                onClick = {
                                                    viewModel.updateOrderStatus(order.id, OrderStatus.ACCEPTED, "Order accepted by supplier")
                                                },
                                                modifier = Modifier.weight(1f).testTag("supplier_accept_button")
                                            ) {
                                                Text("Accept Order")
                                            }
                                        }
                                        OrderStatus.ACCEPTED -> {
                                            Button(
                                                onClick = {
                                                    viewModel.updateOrderStatus(order.id, OrderStatus.PACKED, "Order packed in cold-chain cartons")
                                                },
                                                modifier = Modifier.weight(1f).testTag("supplier_pack_button")
                                            ) {
                                                Text("Mark Packed")
                                            }
                                        }
                                        OrderStatus.PACKED -> {
                                            Button(
                                                onClick = { showDispatchDialog = true },
                                                modifier = Modifier.weight(1f).testTag("supplier_dispatch_button")
                                            ) {
                                                Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Dispatch Order")
                                            }
                                        }
                                        OrderStatus.DISPATCHED -> {
                                            Button(
                                                onClick = {
                                                    viewModel.updateOrderStatus(order.id, OrderStatus.DELIVERED, "Delivered to buyer facility")
                                                },
                                                modifier = Modifier.weight(1f).testTag("supplier_deliver_button")
                                            ) {
                                                Text("Mark Delivered")
                                            }
                                        }
                                        else -> {}
                                    }
                                }

                                if (isBuyer) {
                                    if (currentStatus == OrderStatus.DELIVERED && details.pod == null) {
                                        Button(
                                            onClick = { showPodDialog = true },
                                            modifier = Modifier.weight(1f).testTag("record_pod_button")
                                        ) {
                                            Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Record POD")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.reorderPastOrder(order.id, onNavigateToBasket)
                                        },
                                        modifier = Modifier.weight(1f).testTag("reorder_button")
                                    ) {
                                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Reorder")
                                    }
                                }
                            }
                        }
                    }
                }

                // E-Way Bill Information Card if Dispatched
                if (order.isEwayBillGenerated || !order.ewayBillNumber.isNullOrBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("E-Way Bill & Dispatch Logistics", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("E-Way Bill No: ${order.ewayBillNumber ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                                Text("Vehicle No: ${order.vehicleNumber ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                                Text("Transporter: ${order.transporterName ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                                if (order.expectedDeliveryEpochMillis != null) {
                                    Text("Expected Delivery: ${Formatters.formatDate(order.expectedDeliveryEpochMillis)}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // Proof of Delivery Record if Submitted
                details.pod?.let { pod ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Proof of Delivery (POD) Recorded", fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Received by: ${pod.receivedByName} on ${Formatters.formatDateTime(pod.receivedDateEpochMillis)}", style = MaterialTheme.typography.bodySmall)
                                if (pod.shortQuantity > 0 || pod.damagedQuantity > 0) {
                                    Text("Discrepancies: Short: ${pod.shortQuantity} units | Damaged: ${pod.damagedQuantity} units", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                                } else {
                                    Text("Delivered in full with zero discrepancies.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF166534))
                                }
                                if (pod.notes.isNotBlank()) {
                                    Text("Notes: ${pod.notes}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // Line Items
                item {
                    Text("Ordered Line Items (${details.items.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }

                items(details.items) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Brand: ${item.brand} • Pack: ${item.packSize} • HSN: ${item.hsnCode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                PriceText(amount = item.effectivePrice * item.orderedQuantity)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Qty: ${item.orderedQuantity} ${if (item.freeQuantity > 0) "+ ${item.freeQuantity} Free" else ""}", style = MaterialTheme.typography.bodySmall)
                                Text("Unit PTR: ${Formatters.formatInr(item.unitPricePtr)}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (!item.batchNumber.isNullOrBlank()) {
                                Text(
                                    text = "Batch: ${item.batchNumber} • Exp: ${Formatters.formatDate(item.expiryDateEpochMillis)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Status History Timeline
                item {
                    Text("Order Status Timeline", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }

                items(details.history) { hist ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(10.dp).padding(top = 4.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(hist.status, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(Formatters.formatDateTime(hist.changedAtEpochMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            if (hist.note.isNotBlank()) {
                                Text(hist.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Tax Invoice Section (Non-Fabricated + PDF Download & Share)
                item {
                    TaxInvoiceSection(
                        invoice = uiState.taxInvoice,
                        isLoading = uiState.isLoadingTaxInvoice,
                        errorMessage = uiState.taxInvoiceError,
                        isDownloading = uiState.isDownloadingTaxInvoice,
                        lastSavedPath = uiState.lastSavedInvoicePath,
                        onDownloadPdf = { viewModel.downloadTaxInvoicePdf(context, orderId) },
                        onSharePdf = {
                            viewModel.shareTaxInvoicePdf(context, orderId) { shareIntent ->
                                try {
                                    context.startActivity(shareIntent)
                                } catch (_: Exception) {
                                    // Handled gracefully if no PDF share target installed
                                }
                            }
                        }
                    )
                }
            }
        }

        // Dispatch Dialog
        if (showDispatchDialog) {
            AlertDialog(
                onDismissRequest = { showDispatchDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                title = { Text("Dispatch Order & Enter Logistics", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = ewayBillNumber,
                            onValueChange = { ewayBillNumber = it },
                            label = { Text("E-Way Bill Number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = vehicleNumber,
                            onValueChange = { vehicleNumber = it },
                            label = { Text("Vehicle Registration No") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = transporterName,
                            onValueChange = { transporterName = it },
                            label = { Text("Transporter / Courier") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val expDate = System.currentTimeMillis() + (3L * 24 * 60 * 60 * 1000)
                            viewModel.updateOrderStatus(
                                orderId = orderId,
                                newStatus = OrderStatus.DISPATCHED,
                                note = "Dispatched via $transporterName ($vehicleNumber). E-way bill: $ewayBillNumber",
                                dispatchEpochMillis = System.currentTimeMillis(),
                                expectedDeliveryEpochMillis = expDate,
                                ewayBillNumber = ewayBillNumber,
                                vehicleNumber = vehicleNumber,
                                transporterName = transporterName
                            )
                            showDispatchDialog = false
                        }
                    ) {
                        Text("Confirm Dispatch")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDispatchDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Record Proof of Delivery Dialog
        if (showPodDialog) {
            AlertDialog(
                onDismissRequest = { showPodDialog = false },
                title = { Text("Record Proof of Delivery (POD)", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = podReceivedByName,
                            onValueChange = { podReceivedByName = it },
                            label = { Text("Received By (Full Name) *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = podNotes,
                            onValueChange = { podNotes = it },
                            label = { Text("Notes / Inspection Remarks") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = podShortQty.toString(),
                                onValueChange = { podShortQty = it.toIntOrNull() ?: 0 },
                                label = { Text("Short Qty") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = podDamagedQty.toString(),
                                onValueChange = { podDamagedQty = it.toIntOrNull() ?: 0 },
                                label = { Text("Damaged Qty") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (podReceivedByName.isNotBlank()) {
                                viewModel.submitProofOfDelivery(
                                    orderId = orderId,
                                    receivedByName = podReceivedByName,
                                    notes = podNotes,
                                    photoUri = null,
                                    shortQty = podShortQty,
                                    damagedQty = podDamagedQty
                                ) {
                                    showPodDialog = false
                                }
                            }
                        }
                    ) {
                        Text("Submit POD")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPodDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun TaxInvoiceSection(
    invoice: TaxInvoiceData?,
    isLoading: Boolean,
    errorMessage: String?,
    isDownloading: Boolean,
    lastSavedPath: String?,
    onDownloadPdf: () -> Unit,
    onSharePdf: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tax_invoice_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tax Invoice", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                if (invoice != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (invoice.isEligible) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = when {
                                !invoice.isEligible -> "Not Eligible Yet"
                                invoice.isBackendAuthoritative -> "Backend Verified"
                                else -> "Eligible"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (invoice.isEligible) Color(0xFF15803D) else Color(0xFF475569),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when {
                isLoading && invoice == null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading billing & GST details...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                errorMessage != null && invoice == null -> {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                invoice != null && !invoice.isEligible -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tax_invoice_ineligible_notice")
                    ) {
                        Text(
                            text = invoice.eligibilityReason
                                ?: "Tax invoice is only available for packed, dispatched, or delivered orders.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                invoice != null -> {
                    Text(
                        text = "Invoice No: ${invoice.invoiceNumber ?: "Not Issued"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("tax_invoice_number_text")
                    )
                    Text(
                        text = "Invoice Date: ${Formatters.formatDate(invoice.invoiceDateEpochMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Seller: ${invoice.seller.legalName ?: "Unavailable"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Seller GSTIN: ${invoice.seller.gstin ?: "Not Registered / Unavailable"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("tax_invoice_seller_gstin")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Buyer: ${invoice.buyer.legalName ?: "Unavailable"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Buyer GSTIN: ${invoice.buyer.gstin ?: "Not Registered / Unavailable"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("tax_invoice_buyer_gstin")
                    )
                    if (!invoice.placeOfSupply.isNullOrBlank() || !invoice.gstTaxType.isNullOrBlank()) {
                        Text(
                            text = listOfNotNull(
                                invoice.gstTaxType?.let { "Tax Regime: $it" },
                                invoice.placeOfSupply?.let { "Place of Supply: $it" }
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Taxable Subtotal:", style = MaterialTheme.typography.bodySmall)
                        Text(Formatters.formatInr(invoice.subtotalTaxable), style = MaterialTheme.typography.bodySmall)
                    }
                    if (invoice.cgstTotal != null && invoice.sgstTotal != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("CGST:", style = MaterialTheme.typography.bodySmall)
                            Text(Formatters.formatInr(invoice.cgstTotal), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("SGST:", style = MaterialTheme.typography.bodySmall)
                            Text(Formatters.formatInr(invoice.sgstTotal), style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (invoice.igstTotal != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("IGST:", style = MaterialTheme.typography.bodySmall)
                            Text(Formatters.formatInr(invoice.igstTotal), style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total GST:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                invoice.totalGstAmount?.let { Formatters.formatInr(it) } ?: "Unavailable",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Delivery Charges:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (invoice.deliveryCharge > 0) Formatters.formatInr(invoice.deliveryCharge) else "FREE",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Invoice Total Amount:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        PriceText(amount = invoice.grandTotal)
                    }

                    if (invoice.missingFields.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEF3C7),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tax_invoice_missing_fields_warning")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFB45309),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Missing billing details (not fabricated): ${invoice.missingFields.joinToString(", ")}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF92400E)
                                )
                            }
                        }
                    }

                    if (!lastSavedPath.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Downloaded: $lastSavedPath",
                            fontSize = 11.sp,
                            color = Color(0xFF15803D),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDownloadPdf,
                            enabled = !isDownloading,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("download_tax_invoice_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isDownloading) "Generating..." else "Download PDF")
                        }

                        OutlinedButton(
                            onClick = onSharePdf,
                            enabled = !isDownloading,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_tax_invoice_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share PDF")
                        }
                    }
                }
            }
        }
    }
}


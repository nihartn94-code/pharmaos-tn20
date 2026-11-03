package com.example.ui.screens.ledger

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.components.PriceText
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditLedgerScreen(
    viewModel: LedgerViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPaymentDialog by remember { mutableStateOf(false) }
    var paymentAmount by remember { mutableStateOf("") }
    var paymentNotes by remember { mutableStateOf("") }
    var selectedBuyerId by remember { mutableStateOf<Long?>(null) }
    var buyerDropdownExpanded by remember { mutableStateOf(false) }

    val isSupplier = uiState.currentUser?.role?.isSupplier == true || uiState.currentUser?.role == com.example.domain.model.UserRole.ADMIN

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Buyer-Seller Credit Ledger",
                subtitle = "Running debit, credit, balance & audit statements",
                onBackClick = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (isSupplier) {
                FloatingActionButton(
                    onClick = { showPaymentDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("record_payment_fab")
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Payment, contentDescription = "Record Payment")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Record Payment", fontWeight = FontWeight.Bold)
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
            // Balance Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (uiState.currentUser?.role?.isSupplier == true) "Receivable from Counterparty" else "Total Outstanding Payable",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        PriceText(amount = uiState.outstandingBalance, style = MaterialTheme.typography.headlineMedium)
                    }
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Counterparty Filter Chips
            if (uiState.counterparties.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedCounterpartyId == null,
                            onClick = { viewModel.selectCounterparty(null) },
                            label = { Text("All Parties") }
                        )
                    }
                    items(uiState.counterparties) { cp ->
                        FilterChip(
                            selected = uiState.selectedCounterpartyId == cp.id,
                            onClick = { viewModel.selectCounterparty(cp.id) },
                            label = { Text(cp.businessName) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoading) {
                LoadingState(message = "Loading credit ledger entries...")
            } else if (uiState.entries.isEmpty()) {
                EmptyState(
                    title = "No Ledger Entries",
                    message = "Every confirmed purchase order debits the ledger, and returns or payments credit the balance.",
                    icon = Icons.Default.AccountBalanceWallet
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.entries, key = { it.id }) { entry ->
                        val isDebit = entry.entryType == "DEBIT"
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
                                        Text(entry.description, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "Ref: ${entry.referenceId} • ${Formatters.formatDateTime(entry.dateEpochMillis)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isDebit) Color(0xFFFEE2E2) else Color(0xFFDCFCE7)
                                    ) {
                                        Text(
                                            text = (if (isDebit) "+ " else "- ") + Formatters.formatInr(entry.amount),
                                            color = if (isDebit) Color(0xFFB91C1C) else Color(0xFF15803D),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Type: ${entry.entryType}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text("Balance After: ${Formatters.formatInr(entry.balanceAfter)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Record Payment Dialog
        if (showPaymentDialog) {
            val stockists = uiState.counterparties.filter { it.role == com.example.domain.model.UserRole.STOCKIST.name }
            val selectedBuyer = stockists.find { it.id == selectedBuyerId }

            AlertDialog(
                onDismissRequest = { showPaymentDialog = false },
                title = { Text("Record Payment Received", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Select Buyer / Stockist:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        ExposedDropdownMenuBox(
                            expanded = buyerDropdownExpanded,
                            onExpandedChange = { buyerDropdownExpanded = !buyerDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedBuyer?.businessName ?: "Select Buyer...",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = buyerDropdownExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = buyerDropdownExpanded,
                                onDismissRequest = { buyerDropdownExpanded = false }
                            ) {
                                stockists.forEach { buyer ->
                                    DropdownMenuItem(
                                        text = { Text("${buyer.businessName} (${buyer.fullName})") },
                                        onClick = {
                                            selectedBuyerId = buyer.id
                                            buyerDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = paymentAmount,
                            onValueChange = { paymentAmount = it },
                            label = { Text("Payment Amount (₹) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = paymentNotes,
                            onValueChange = { paymentNotes = it },
                            label = { Text("Payment Ref / Bank UTR / Cheque No") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amt = paymentAmount.toDoubleOrNull() ?: 0.0
                            val buyerId = selectedBuyerId
                            if (buyerId != null && amt > 0) {
                                viewModel.recordPayment(buyerId, amt, paymentNotes) {
                                    showPaymentDialog = false
                                    paymentAmount = ""
                                    paymentNotes = ""
                                }
                            }
                        }
                    ) {
                        Text("Record Credit")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPaymentDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

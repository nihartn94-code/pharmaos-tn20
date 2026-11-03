package com.example.ui.screens.returns

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ReturnStatus
import com.example.domain.model.UserRole
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.components.PriceText
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnsCreditNotesScreen(
    viewModel: ReturnsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }

    val isSupplier = uiState.currentUser?.role?.isSupplier == true || uiState.currentUser?.role == UserRole.ADMIN

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Returns & Credit Notes",
                subtitle = "${uiState.returns.size} returns • ${uiState.creditNotes.size} credit notes",
                onBackClick = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Return Claims (${uiState.returns.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Credit Notes (${uiState.creditNotes.size})") }
                )
            }

            if (selectedTab == 0) {
                if (uiState.returns.isEmpty()) {
                    EmptyState(
                        title = "No Return Claims",
                        message = "Buyers can raise returns against delivered order line items for damaged, expired, or recalled goods.",
                        icon = Icons.Default.AssignmentReturn
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.returns, key = { it.returnRecord.id }) { item ->
                            val ret = item.returnRecord
                            val isPending = ret.status == ReturnStatus.PENDING.name
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
                                        Text(item.productName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = when (ret.status) {
                                                ReturnStatus.APPROVED.name -> Color(0xFFDCFCE7)
                                                ReturnStatus.REJECTED.name -> Color(0xFFFEE2E2)
                                                else -> Color(0xFFFEF3C7)
                                            }
                                        ) {
                                            Text(
                                                text = ret.status,
                                                color = when (ret.status) {
                                                    ReturnStatus.APPROVED.name -> Color(0xFF15803D)
                                                    ReturnStatus.REJECTED.name -> Color(0xFFB91C1C)
                                                    else -> Color(0xFF92400E)
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Order: ${item.orderNumber} • Batch: ${ret.batchNumber}", style = MaterialTheme.typography.bodySmall)
                                    Text("Qty: ${ret.quantity} units • Reason: ${ret.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    if (item.creditNote != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFDCFCE7),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Credit Note: ${item.creditNote.creditNoteNumber}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                                                PriceText(amount = item.creditNote.totalAmount, style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }

                                    // Supplier Approval Action
                                    if (isPending && isSupplier) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { viewModel.reviewReturn(ret.id, true, "Goods accepted & credit note issued") },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                                                modifier = Modifier.weight(1f).testTag("approve_return_button")
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Approve & Issue CN")
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.reviewReturn(ret.id, false, "Inspection failed / invalid lot") },
                                                modifier = Modifier.weight(1f).testTag("reject_return_button")
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Reject")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (uiState.creditNotes.isEmpty()) {
                    EmptyState(
                        title = "No Credit Notes Issued",
                        message = "Credit notes are automatically issued with a unique number when returns are approved.",
                        icon = Icons.Default.ReceiptLong
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.creditNotes, key = { it.id }) { cn ->
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
                                        Text(cn.creditNoteNumber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        PriceText(amount = cn.totalAmount)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Issued on: ${Formatters.formatDateTime(cn.issuedAtEpochMillis)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text("Taxable: ${Formatters.formatInr(cn.amount)} | GST: ${Formatters.formatInr(cn.gstAmount)}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

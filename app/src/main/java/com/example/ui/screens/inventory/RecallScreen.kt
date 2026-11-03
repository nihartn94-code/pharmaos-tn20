package com.example.ui.screens.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.domain.model.RecallSeverity
import com.example.domain.model.UserRole
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyState
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecallScreen(
    viewModel: InventoryViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    var selectedProdId by remember { mutableStateOf(uiState.catalogueProducts.firstOrNull()?.id ?: 0L) }
    var batchNumbersCsv by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var selectedSeverity by remember { mutableStateOf(RecallSeverity.CRITICAL_CLASS_1) }
    var prodDropdownExpanded by remember { mutableStateOf(false) }

    val isSupplierOrAdmin = uiState.currentUser?.role?.isSupplier == true || uiState.currentUser?.role == UserRole.ADMIN

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Batch Recall Notices",
                subtitle = "${uiState.recalls.size} active recall directives",
                onBackClick = onNavigateBack
            )
        },
        floatingActionButton = {
            if (isSupplierOrAdmin) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.testTag("create_recall_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Publish Recall Notice")
                }
            }
        }
    ) { padding ->
        if (uiState.recalls.isEmpty()) {
            EmptyState(
                title = "Zero Active Recalls",
                message = "No batches are currently flagged for regulatory or manufacturer recall.",
                icon = Icons.Default.Warning,
                actionLabel = if (isSupplierOrAdmin) "Issue Recall Directive" else null,
                onActionClick = { showCreateDialog = true },
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(uiState.recalls, key = { it.recall.id }) { item ->
                    val rec = item.recall
                    val sev = try { RecallSeverity.valueOf(rec.severity) } catch (_: Exception) { RecallSeverity.CRITICAL_CLASS_1 }
                    val isCritical = sev == RecallSeverity.CRITICAL_CLASS_1

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCritical) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isCritical) Color(0xFFEF4444) else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rec.productName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Recalled Lots: ${rec.batchNumbersCsv}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isCritical) Color(0xFFFEE2E2) else Color(0xFFFEF3C7)
                                ) {
                                    Text(
                                        text = sev.displayName.take(12),
                                        color = if (isCritical) Color(0xFFB91C1C) else Color(0xFF92400E),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Reason: ${rec.reason}", style = MaterialTheme.typography.bodySmall)

                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Affected Inventory Found:", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "${item.affectedStockUnits} units in stock (${item.affectedBatches.size} lots)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.affectedStockUnits > 0) Color(0xFFB91C1C) else Color(0xFF15803D)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Issued: ${Formatters.formatDateTime(rec.recallDateEpochMillis)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }

        // Create Recall Dialog
        if (showCreateDialog) {
            val selectedProd = uiState.catalogueProducts.find { it.id == selectedProdId }

            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Issue Regulatory Batch Recall", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Select Product:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        ExposedDropdownMenuBox(
                            expanded = prodDropdownExpanded,
                            onExpandedChange = { prodDropdownExpanded = !prodDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedProd?.name ?: "Select Product",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = prodDropdownExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = prodDropdownExpanded,
                                onDismissRequest = { prodDropdownExpanded = false }
                            ) {
                                uiState.catalogueProducts.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text("${p.name} (${p.brand})") },
                                        onClick = {
                                            selectedProdId = p.id
                                            prodDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = batchNumbersCsv,
                            onValueChange = { batchNumbersCsv = it },
                            label = { Text("Batch Numbers (Comma-separated)") },
                            placeholder = { Text("e.g. B-101, B-102") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Reason for Recall") },
                            placeholder = { Text("e.g. Dissolution rate deviation") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Severity Class:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        RecallSeverity.values().forEach { s ->
                            FilterChip(
                                selected = selectedSeverity == s,
                                onClick = { selectedSeverity = s },
                                label = { Text(s.displayName, fontSize = 11.sp) }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (selectedProdId > 0 && batchNumbersCsv.isNotBlank() && reason.isNotBlank()) {
                                viewModel.createRecall(
                                    productId = selectedProdId,
                                    batchNumbersCsv = batchNumbersCsv,
                                    reason = reason,
                                    severity = selectedSeverity
                                ) {
                                    showCreateDialog = false
                                    batchNumbersCsv = ""
                                    reason = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Issue Recall Notice")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

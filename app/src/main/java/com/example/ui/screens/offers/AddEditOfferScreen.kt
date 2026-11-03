package com.example.ui.screens.offers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.SupplierOfferEntity
import com.example.domain.model.SchemeType
import com.example.ui.components.AppTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditOfferScreen(
    initialProductId: Long?,
    viewModel: OffersViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedProductId by remember { mutableStateOf(initialProductId ?: uiState.catalogueProducts.firstOrNull()?.id ?: 0L) }
    var unitPrice by remember { mutableStateOf("120.00") }
    var mrp by remember { mutableStateOf("160.00") }
    var moq by remember { mutableStateOf("10") }
    var availableQty by remember { mutableStateOf("500") }

    var selectedSchemeType by remember { mutableStateOf(SchemeType.NONE) }
    var schemeBuyQty by remember { mutableStateOf("10") }
    var schemeFreeQty by remember { mutableStateOf("1") }
    var schemeDiscountPercent by remember { mutableStateOf("5.0") }
    var schemeMinQty by remember { mutableStateOf("50") }

    var productDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val selectedProduct = uiState.catalogueProducts.find { it.id == selectedProductId }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Publish Trade Quote & Scheme",
                subtitle = "Set wholesale PTR, MOQ, stock & trade discounts",
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Product Selector Dropdown
            Text("Select Catalogue Medicine *", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            ExposedDropdownMenuBox(
                expanded = productDropdownExpanded,
                onExpandedChange = { productDropdownExpanded = !productDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedProduct?.let { "${it.name} (${it.brand} - ${it.strength})" } ?: "Select Product",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = productDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .testTag("offer_product_picker")
                )
                ExposedDropdownMenu(
                    expanded = productDropdownExpanded,
                    onDismissRequest = { productDropdownExpanded = false }
                ) {
                    uiState.catalogueProducts.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${p.name} (${p.brand} - ${p.strength})") },
                            onClick = {
                                selectedProductId = p.id
                                productDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = unitPrice,
                    onValueChange = { unitPrice = it },
                    label = { Text("PTR / Unit Price (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("offer_ptr_input")
                )

                OutlinedTextField(
                    value = mrp,
                    onValueChange = { mrp = it },
                    label = { Text("MRP (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("offer_mrp_input")
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = moq,
                    onValueChange = { moq = it },
                    label = { Text("Minimum Order Qty (MOQ) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("offer_moq_input")
                )

                OutlinedTextField(
                    value = availableQty,
                    onValueChange = { availableQty = it },
                    label = { Text("Available Stock Qty *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("offer_stock_input")
                )
            }

            // Promotional Trade Schemes
            Text("Promotional Trade Scheme", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SchemeType.values().forEach { scheme ->
                    FilterChip(
                        selected = selectedSchemeType == scheme,
                        onClick = { selectedSchemeType = scheme },
                        label = { Text(scheme.displayName) }
                    )
                }
            }

            if (selectedSchemeType == SchemeType.BUY_X_GET_Y_FREE) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Configure Buy X Get Y Free Scheme", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = schemeBuyQty,
                                onValueChange = { schemeBuyQty = it },
                                label = { Text("Buy Quantity (X)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = schemeFreeQty,
                                onValueChange = { schemeFreeQty = it },
                                label = { Text("Free Quantity (Y)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else if (selectedSchemeType == SchemeType.PERCENT_DISCOUNT_ABOVE_QTY) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Configure Bulk Quantity Discount", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = schemeDiscountPercent,
                                onValueChange = { schemeDiscountPercent = it },
                                label = { Text("Discount %") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = schemeMinQty,
                                onValueChange = { schemeMinQty = it },
                                label = { Text("Min Qty Threshold") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (selectedProductId <= 0) {
                        errorMessage = "Please select a product from the catalogue."
                        return@Button
                    }
                    val ptrVal = unitPrice.toDoubleOrNull() ?: 0.0
                    val mrpVal = mrp.toDoubleOrNull() ?: 0.0
                    val moqVal = moq.toIntOrNull() ?: 1
                    val stockVal = availableQty.toIntOrNull() ?: 0

                    if (ptrVal <= 0 || mrpVal <= 0) {
                        errorMessage = "Price and MRP must be positive numbers."
                        return@Button
                    }
                    if (ptrVal > mrpVal) {
                        errorMessage = "Unit PTR cannot be greater than MRP."
                        return@Button
                    }

                    val entity = SupplierOfferEntity(
                        productId = selectedProductId,
                        supplierId = uiState.currentUser?.id ?: 0L,
                        unitPricePtr = ptrVal,
                        mrp = mrpVal,
                        moq = moqVal,
                        availableQuantity = stockVal,
                        schemeType = selectedSchemeType.name,
                        schemeBuyQty = schemeBuyQty.toIntOrNull() ?: 0,
                        schemeFreeQty = schemeFreeQty.toIntOrNull() ?: 0,
                        schemeDiscountPercent = schemeDiscountPercent.toDoubleOrNull() ?: 0.0,
                        schemeMinQtyForDiscount = schemeMinQty.toIntOrNull() ?: 0,
                        validTillEpochMillis = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                    )
                    viewModel.saveOffer(entity) {
                        onNavigateBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_offer_button")
            ) {
                Text("Publish Quote")
            }
        }
    }
}

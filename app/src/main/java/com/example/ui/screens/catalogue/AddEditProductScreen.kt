package com.example.ui.screens.catalogue

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.data.local.entity.ProductEntity
import com.example.domain.model.DosageForm
import com.example.domain.model.StorageCondition
import com.example.ui.components.AppTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductScreen(
    viewModel: CatalogueViewModel,
    existingProduct: ProductEntity? = null,
    onNavigateBack: () -> Unit
) {
    var name by remember { mutableStateOf(existingProduct?.name ?: "") }
    var brand by remember { mutableStateOf(existingProduct?.brand ?: "") }
    var saltComposition by remember { mutableStateOf(existingProduct?.saltComposition ?: "") }
    var strength by remember { mutableStateOf(existingProduct?.strength ?: "") }
    var selectedDosageForm by remember {
        mutableStateOf(
            existingProduct?.dosageForm?.let {
                try { DosageForm.valueOf(it) } catch (_: Exception) { DosageForm.TABLET }
            } ?: DosageForm.TABLET
        )
    }
    var category by remember { mutableStateOf(existingProduct?.therapeuticCategory ?: "") }
    var manufacturer by remember { mutableStateOf(existingProduct?.manufacturerName ?: "") }
    var packSize by remember { mutableStateOf(existingProduct?.packSize ?: "10x10 Tablets") }
    var hsnCode by remember { mutableStateOf(existingProduct?.hsnCode ?: "3004") }
    var gstRate by remember { mutableStateOf(existingProduct?.gstRatePercent?.toString() ?: "12.0") }
    var selectedStorage by remember {
        mutableStateOf(
            existingProduct?.storageCondition?.let {
                try { StorageCondition.valueOf(it) } catch (_: Exception) { StorageCondition.ROOM_TEMPERATURE }
            } ?: StorageCondition.ROOM_TEMPERATURE
        )
    }
    var isRx by remember { mutableStateOf(existingProduct?.isRx ?: false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (existingProduct == null) "Add Medicine to Catalogue" else "Edit Medicine",
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

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Product / Trade Name *") },
                placeholder = { Text("e.g. Paracetamol 650mg") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_name_input")
            )

            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text("Brand Name *") },
                placeholder = { Text("e.g. Dolo, Crocin, Calpol") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_brand_input")
            )

            OutlinedTextField(
                value = saltComposition,
                onValueChange = { saltComposition = it },
                label = { Text("Active Salt Composition (Normalized) *") },
                placeholder = { Text("e.g. Paracetamol 650mg, Caffeine 50mg") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_salt_input")
            )

            OutlinedTextField(
                value = strength,
                onValueChange = { strength = it },
                label = { Text("Strength *") },
                placeholder = { Text("e.g. 650 mg") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_strength_input")
            )

            Text("Dosage Form *", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DosageForm.values()) { form ->
                    FilterChip(
                        selected = selectedDosageForm == form,
                        onClick = { selectedDosageForm = form },
                        label = { Text(form.displayName) }
                    )
                }
            }

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Therapeutic Category *") },
                placeholder = { Text("e.g. Analgesic, Antibiotic, Antipyretic") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_category_input")
            )

            OutlinedTextField(
                value = manufacturer,
                onValueChange = { manufacturer = it },
                label = { Text("Manufacturer Name *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_manufacturer_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = packSize,
                    onValueChange = { packSize = it },
                    label = { Text("Pack Size *") },
                    placeholder = { Text("10x10") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = hsnCode,
                    onValueChange = { hsnCode = it },
                    label = { Text("HSN Code *") },
                    placeholder = { Text("3004") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = gstRate,
                onValueChange = { gstRate = it },
                label = { Text("GST Rate (%) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Storage Condition *", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StorageCondition.values()) { storage ->
                    FilterChip(
                        selected = selectedStorage == storage,
                        onClick = { selectedStorage = storage },
                        label = { Text(storage.label) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Prescription Required (Rx)", fontWeight = FontWeight.Bold)
                    Text("Enable if scheduled prescription drug", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Switch(checked = isRx, onCheckedChange = { isRx = it })
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (name.isBlank() || brand.isBlank() || saltComposition.isBlank() || strength.isBlank() || category.isBlank() || manufacturer.isBlank()) {
                        errorMessage = "Please fill in all mandatory fields."
                        return@Button
                    }
                    val gst = gstRate.toDoubleOrNull() ?: 12.0
                    val entity = ProductEntity(
                        id = existingProduct?.id ?: 0L,
                        name = name.trim(),
                        brand = brand.trim(),
                        saltComposition = saltComposition.trim(),
                        strength = strength.trim(),
                        dosageForm = selectedDosageForm.name,
                        therapeuticCategory = category.trim(),
                        manufacturerName = manufacturer.trim(),
                        packSize = packSize.trim(),
                        hsnCode = hsnCode.trim(),
                        gstRatePercent = gst,
                        storageCondition = selectedStorage.name,
                        isRx = isRx,
                        createdBy = existingProduct?.createdBy ?: 0L
                    )
                    viewModel.saveProduct(entity) {
                        onNavigateBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_product_button")
            ) {
                Text(if (existingProduct == null) "Add Medicine" else "Save Changes")
            }
        }
    }
}

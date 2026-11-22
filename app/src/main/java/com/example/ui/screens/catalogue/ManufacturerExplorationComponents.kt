package com.example.ui.screens.catalogue

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

internal fun manufacturerTestTagSuffix(name: String): String =
    name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

@Composable
internal fun ExploreModeSegmentedBar(
    exploreMode: ExploreMode,
    productsCount: Int,
    manufacturersCount: Int,
    onModeSelected: (ExploreMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFE2E8F0).copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExploreModeTab(
                label = "Products ($productsCount)",
                icon = Icons.Default.Medication,
                selected = exploreMode == ExploreMode.PRODUCTS,
                onClick = { onModeSelected(ExploreMode.PRODUCTS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("explore_mode_products")
            )
            ExploreModeTab(
                label = "Manufacturers ($manufacturersCount)",
                icon = Icons.Default.Factory,
                selected = exploreMode == ExploreMode.MANUFACTURERS,
                onClick = { onModeSelected(ExploreMode.MANUFACTURERS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("explore_mode_manufacturers")
            )
        }
    }
}

@Composable
private fun ExploreModeTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color.White else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color(0xFF1D64EC) else Color(0xFF64748B),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color(0xFF0F172A) else Color(0xFF475569),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun ManufacturerDirectoryList(
    manufacturers: List<ManufacturerSummary>,
    onManufacturerClick: (ManufacturerSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("manufacturer_directory_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(manufacturers, key = { it.name.lowercase() }) { manufacturer ->
            ManufacturerSummaryCard(
                manufacturer = manufacturer,
                onClick = { onManufacturerClick(manufacturer) }
            )
        }
    }
}

@Composable
internal fun ManufacturerSummaryCard(
    manufacturer: ManufacturerSummary,
    onClick: () -> Unit
) {
    val initials = manufacturer.name
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "MF" }

    val tagKey = manufacturerTestTagSuffix(manufacturer.name)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("manufacturer_card_$tagKey"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE0F2FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = Color(0xFF0284C7),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = manufacturer.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified Manufacturer",
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    val categorySummary = manufacturer.therapeuticCategories
                        .take(2)
                        .joinToString(" • ")
                        .ifBlank { "Pharmaceutical Catalogue" }
                    Text(
                        text = categorySummary,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFEFF6FF)
                ) {
                    val skuLabel = if (manufacturer.productCount == 1) "1 SKU" else "${manufacturer.productCount} SKUs"
                    Text(
                        text = skuLabel,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D4ED8),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            // Dosage forms + strength highlights
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                manufacturer.dosageForms.take(3).forEach { form ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = form.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF334155),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (manufacturer.dosageForms.size > 3) {
                    Text(
                        text = "+${manufacturer.dosageForms.size - 3} more",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Footer: Starting PTR, margin & Explore Products CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    manufacturer.minOfferPrice?.let { minPtr ->
                        Text(
                            text = "From ₹${String.format(Locale.US, "%.2f", minPtr)}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                    }
                    manufacturer.maxMarginPercent?.let { margin ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFDCFCE7)
                        ) {
                            Text(
                                text = "Up to $margin% Margin",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF047857),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Explore Products",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D64EC)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Explore ${manufacturer.name} products",
                        tint = Color(0xFF1D64EC),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SelectedManufacturerHeaderCard(
    manufacturerName: String,
    summary: ManufacturerSummary?,
    matchingCount: Int,
    onClearManufacturer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("selected_manufacturer_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
        border = BorderStroke(1.dp, Color(0xFFBAE6FD))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0284C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Factory,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = manufacturerName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("selected_manufacturer_title")
                        )
                        val totalSkus = summary?.productCount ?: matchingCount
                        val formsText = summary?.dosageForms
                            ?.take(3)
                            ?.joinToString(", ") { it.displayName }
                            .orEmpty()
                        val subtitle = buildString {
                            append("$matchingCount of $totalSkus products")
                            if (formsText.isNotBlank()) append(" • $formsText")
                        }
                        Text(
                            text = subtitle,
                            fontSize = 11.5.sp,
                            color = Color(0xFF475569),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onClearManufacturer() }
                        .testTag("clear_manufacturer_button"),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFF7DD3FC))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "All Manufacturers",
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "All Labs",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0284C7)
                        )
                    }
                }
            }

            if (summary != null && summary.therapeuticCategories.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = summary.therapeuticCategories.take(3).joinToString(" • "),
                        fontSize = 11.sp,
                        color = Color(0xFF0369A1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    summary.minOfferPrice?.let { minPtr ->
                        Text(
                            text = "PTR from ₹${String.format(Locale.US, "%.2f", minPtr)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActiveFiltersSummaryRow(
    selectedStrength: String?,
    selectedSaltFilter: String,
    selectedDosageFormName: String?,
    availableStrengths: List<String>,
    onSelectStrength: (String?) -> Unit,
    onClearSaltFilter: () -> Unit,
    onClearDosageForm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasActiveChips = !selectedStrength.isNullOrBlank() ||
        selectedSaltFilter.isNotBlank() ||
        !selectedDosageFormName.isNullOrBlank()

    if (!hasActiveChips && availableStrengths.isEmpty()) return

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedSaltFilter.isNotBlank()) {
            item(key = "active_salt") {
                RemovableFilterPill(
                    label = "Salt: $selectedSaltFilter",
                    onRemove = onClearSaltFilter,
                    testTag = "active_filter_salt"
                )
            }
        }
        if (!selectedDosageFormName.isNullOrBlank()) {
            item(key = "active_dosage") {
                RemovableFilterPill(
                    label = "Form: $selectedDosageFormName",
                    onRemove = onClearDosageForm,
                    testTag = "active_filter_dosage"
                )
            }
        }
        items(availableStrengths.take(8), key = { "str_$it" }) { strength ->
            val isSelected = selectedStrength.equals(strength, ignoreCase = true)
            val tagKey = manufacturerTestTagSuffix(strength)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) Color(0xFF0284C7) else Color(0xFFF1F5F9))
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color(0xFF0284C7) else Color(0xFFCBD5E1),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onSelectStrength(strength) }
                    .padding(horizontal = 11.dp, vertical = 5.dp)
                    .testTag("strength_chip_$tagKey"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strength,
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else Color(0xFF334155)
                )
            }
        }
    }
}

@Composable
private fun RemovableFilterPill(
    label: String,
    onRemove: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onRemove() }
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFDBEAFE),
        border = BorderStroke(1.dp, Color(0xFF93C5FD))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E40AF)
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear $label",
                tint = Color(0xFF1E40AF),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

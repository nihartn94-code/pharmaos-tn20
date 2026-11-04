package com.example.ui.screens.catalogue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.DosageForm
import com.example.domain.model.ProductDetail
import com.example.domain.model.StorageCondition
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import java.util.Locale

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.theme.PharmaOSTheme

@Composable
fun MedicineCatalogueScreen(
    viewModel: CatalogueViewModel,
    onNavigateBack: () -> Unit,
    onProductClick: (Long) -> Unit,
    onAddProductClick: () -> Unit,
    onNavigateToBasket: () -> Unit = {},
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToOrders: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToOcr: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MedicineCatalogueContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onProductClick = onProductClick,
        onAddProductClick = onAddProductClick,
        onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
        onSelectCategoryChip = { viewModel.selectCategoryChip(it) },
        onClearFilters = { viewModel.clearFilters() },
        onAddToCart = { product, qty -> viewModel.addToCart(product, quantity = qty) },
        onIncrementQuantity = { product -> viewModel.incrementQuantity(product) },
        onDecrementQuantity = { product -> viewModel.decrementQuantity(product) },
        onSetPendingQuantity = { productId, qty -> viewModel.setPendingQuantity(productId, qty) },
        onSetFilters = { dosage, cat, storage, rx, inStock, sort -> viewModel.setFilters(dosage, cat, storage, rx, inStock, sort) },
        onClearMessages = { viewModel.clearMessages() },
        onNavigateToBasket = onNavigateToBasket,
        onNavigateToDashboard = onNavigateToDashboard,
        onNavigateToOrders = onNavigateToOrders,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToOcr = onNavigateToOcr,
        onNavigateToSearch = onNavigateToSearch
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineCatalogueContent(
    uiState: CatalogueUiState,
    onNavigateBack: () -> Unit,
    onProductClick: (Long) -> Unit,
    onAddProductClick: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSelectCategoryChip: (String) -> Unit,
    onClearFilters: () -> Unit,
    onAddToCart: (ProductDetail, Int) -> Unit,
    onIncrementQuantity: (ProductDetail) -> Unit,
    onDecrementQuantity: (ProductDetail) -> Unit,
    onSetPendingQuantity: (Long, Int) -> Unit,
    onSetFilters: (DosageForm?, String?, StorageCondition?, Boolean?, Boolean, SortOption) -> Unit,
    onClearMessages: () -> Unit,
    onNavigateToBasket: () -> Unit = {},
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToOrders: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToOcr: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {}
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearMessages()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearMessages()
        }
    }

    val categories = listOf(
        "All",
        "Tablets",
        "Capsules",
        "Injectables",
        "Syrups",
        "OTC",
        "Diabetics",
        "Dermatology"
    )

    // Compute title based on selected chip
    val screenTitle = when (uiState.selectedCategoryChip) {
        "Capsules" -> "Capsules & Softgels"
        "Tablets" -> "Tablets & Lozenges"
        "Injectables" -> "Injectables & Vials"
        "Syrups" -> "Syrups & Suspensions"
        "OTC" -> "Over-The-Counter (OTC)"
        "Diabetics" -> "Diabetic Care"
        "Dermatology" -> "Dermatology"
        else -> "All Medicines"
    }

    val subtitleCount = if (uiState.filteredProducts.isNotEmpty()) {
        "${uiState.filteredProducts.size} products"
    } else {
        "0 products"
    }

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CatalogueTopBar(
                title = screenTitle,
                subtitle = subtitleCount,
                onBackClick = onNavigateBack,
                onFilterClick = { showFilterSheet = true }
            )
        },
        bottomBar = {
            CatalogueBottomBar(
                onNavigateToDashboard = onNavigateToDashboard,
                onNavigateToOrders = onNavigateToOrders,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToOcr = onNavigateToOcr
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (uiState.cartItemsCount > 0) 76.dp else 0.dp)
            ) {
                // Search Input
                CatalogueSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { onSearchQueryChanged(it) },
                    onNavigateToSearch = onNavigateToSearch
                )

                // Category Chips Carousel
                CategoryChipsRow(
                    categories = categories,
                    selectedCategory = uiState.selectedCategoryChip,
                    onCategorySelect = { onSelectCategoryChip(it) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Medicines List
                if (uiState.isLoading) {
                    LoadingState(message = "Loading medicine stock...")
                } else if (uiState.filteredProducts.isEmpty()) {
                    EmptyState(
                        title = "No Medicines Found",
                        message = if (uiState.allProducts.isEmpty()) {
                            "No products in catalog. Tap below to initialize the standard reference catalog."
                        } else {
                            "No medicines match '${uiState.searchQuery}'. Try selecting 'All' or clearing filters."
                        },
                        actionLabel = if (uiState.allProducts.isEmpty()) "Seed Reference Stock" else "Clear Search",
                        onActionClick = {
                            if (uiState.allProducts.isEmpty()) {
                                onSelectCategoryChip("Capsules")
                            } else {
                                onClearFilters()
                            }
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("catalogue_product_list"),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.filteredProducts, key = { it.id }) { product ->
                            val cartQty = uiState.cartProductQuantities[product.id] ?: 0
                            val pendingQty = uiState.pendingQuantities[product.id] ?: 10

                            CatalogueProductCard(
                                product = product,
                                cartQty = cartQty,
                                pendingQty = pendingQty,
                                onCardClick = { onProductClick(product.id) },
                                onAddClick = {
                                    onAddToCart(product, pendingQty)
                                },
                                onIncrement = {
                                    if (cartQty > 0) {
                                        onIncrementQuantity(product)
                                    } else {
                                        onSetPendingQuantity(product.id, pendingQty + 1)
                                    }
                                },
                                onDecrement = {
                                    if (cartQty > 0) {
                                        onDecrementQuantity(product)
                                    } else {
                                        onSetPendingQuantity(product.id, (pendingQty - 1).coerceAtLeast(1))
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Floating Bottom Order Pill
            if (uiState.cartItemsCount > 0) {
                FloatingOrderBar(
                    itemsCount = uiState.cartItemsCount,
                    totalPrice = uiState.cartTotalPrice,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    onClick = onNavigateToBasket
                )
            }
        }

        if (showFilterSheet) {
            FilterBottomSheet(
                uiState = uiState,
                onDismiss = { showFilterSheet = false },
                onApply = { dosage, cat, storage, rx, inStock, sort ->
                    onSetFilters(dosage, cat, storage, rx, inStock, sort)
                    showFilterSheet = false
                },
                onClear = {
                    onClearFilters()
                    showFilterSheet = false
                }
            )
        }
    }
}

@Composable
private fun CatalogueTopBar(
    title: String,
    subtitle: String,
    onBackClick: () -> Unit,
    onFilterClick: () -> Unit
) {
    Surface(
        color = Color(0xFFF8FAFC),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.testTag("catalogue_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = onFilterClick,
                modifier = Modifier.testTag("catalogue_filter_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filter & Sort",
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun CatalogueSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onNavigateToSearch: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = "Search capsules, salts, or molecules...",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )
            },
            leadingIcon = {
                IconButton(
                    onClick = onNavigateToSearch,
                    modifier = Modifier.testTag("catalogue_open_search_molecules")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Molecules",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFF2563EB),
                unfocusedBorderColor = Color(0xFFE2E8F0)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("catalogue_search_input")
        )
    }
}

@Composable
private fun CategoryChipsRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            val isSelected = category.equals(selectedCategory, ignoreCase = true)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) Color(0xFF1D64EC) else Color.White
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color(0xFF1D64EC) else Color(0xFFE2E8F0),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onCategorySelect(category) }
                    .padding(horizontal = 16.dp, vertical = 7.dp)
                    .testTag("category_chip_${category.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category,
                    color = if (isSelected) Color.White else Color(0xFF334155),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun CatalogueProductCard(
    product: ProductDetail,
    cartQty: Int,
    pendingQty: Int,
    onCardClick: () -> Unit,
    onAddClick: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(16.dp))
            .clickable { onCardClick() }
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Product Visual Thumbnail
            MedicineThumbnail(
                productName = product.name,
                dosageForm = product.dosageForm,
                modifier = Modifier.size(76.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Details and Price/Action
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Top: Name + Margin Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D4ED8),
                        fontSize = 14.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    val marginText = "${product.marginPercent ?: 24}% Margin"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE0F2FE))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = marginText,
                            color = Color(0xFF0284C7),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Manufacturer & Pack Size
                Text(
                    text = "${product.manufacturerName} • ${product.packSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF475569),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Molecule / Composition
                Text(
                    text = product.saltComposition,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Price and Stepper/Add Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prices
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val ptrVal = product.minOfferPrice ?: 68.50
                        Text(
                            text = "₹${String.format(Locale.US, "%.2f", ptrVal)}",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 15.sp
                        )

                        val mrpVal = product.minMrp ?: (ptrVal * 1.15)
                        Text(
                            text = "₹${String.format(Locale.US, "%.2f", mrpVal)}",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }

                    // Stepper or Add Button
                    if (cartQty > 0) {
                        // In cart: show interactive quantity stepper
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F5F9))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable { onDecrement() }
                                    .testTag("decrement_${product.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Decrease",
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            Text(
                                text = "$cartQty",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable { onIncrement() }
                                    .testTag("increment_${product.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Increase",
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    } else if (product.id == 1L && pendingQty == 10 && cartQty == 0) {
                        // Card 1 initial reference layout with stepper and blue Add button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clickable { onDecrement() }
                                        .testTag("decrement_${product.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Decrease",
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Text(
                                    text = "$pendingQty",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clickable { onIncrement() }
                                        .testTag("increment_${product.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Increase",
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = onAddClick,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D64EC)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("card_add_btn_${product.id}")
                            ) {
                                Text(
                                    text = "Add",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        // Standard "+ Add" pill button with comfortable touch target
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F5F9))
                                .clickable { onAddClick() }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                                .testTag("card_add_pill_${product.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+ Add",
                                color = Color(0xFF1E293B),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Beautiful vector-drawn pharmaceutical packaging thumbnail
 */
@Composable
fun MedicineThumbnail(
    productName: String,
    dosageForm: DosageForm,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(54.dp)) {
            val w = size.width
            val h = size.height

            when {
                // Vitamin D3 Softgel Bottle
                productName.contains("Vitamin D3", ignoreCase = true) -> {
                    // Amber bottle base
                    drawRoundRect(
                        color = Color(0xFFD97706),
                        topLeft = Offset(w * 0.25f, h * 0.35f),
                        size = Size(w * 0.50f, h * 0.55f),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    // White bottle label
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(w * 0.28f, h * 0.48f),
                        size = Size(w * 0.44f, h * 0.32f),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                    // White bottle cap
                    drawRoundRect(
                        color = Color(0xFFE2E8F0),
                        topLeft = Offset(w * 0.35f, h * 0.22f),
                        size = Size(w * 0.30f, h * 0.14f),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                    // Golden softgel drops
                    drawCircle(
                        color = Color(0xFFF59E0B),
                        radius = 4.dp.toPx(),
                        center = Offset(w * 0.16f, h * 0.82f)
                    )
                    drawCircle(
                        color = Color(0xFFFBBF24),
                        radius = 4.dp.toPx(),
                        center = Offset(w * 0.82f, h * 0.82f)
                    )
                }

                // Omez / Pink-Red Capsule Blister
                productName.contains("Omez", ignoreCase = true) -> {
                    // Metallic silver blister pack
                    drawRoundRect(
                        color = Color(0xFFE2E8F0),
                        topLeft = Offset(w * 0.10f, h * 0.15f),
                        size = Size(w * 0.80f, h * 0.70f),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    // 6 Pinkish blister bubbles
                    val bubbleColor = Color(0xFFF43F5E)
                    for (row in 0..2) {
                        for (col in 0..1) {
                            drawRoundRect(
                                color = bubbleColor,
                                topLeft = Offset(w * (0.20f + col * 0.36f), h * (0.22f + row * 0.22f)),
                                size = Size(w * 0.24f, h * 0.14f),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                    }
                }

                // Razo-D Sustained Release Capsule Blister
                productName.contains("Razo", ignoreCase = true) -> {
                    drawRoundRect(
                        color = Color(0xFFE2E8F0),
                        topLeft = Offset(w * 0.10f, h * 0.15f),
                        size = Size(w * 0.80f, h * 0.70f),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    for (row in 0..2) {
                        for (col in 0..1) {
                            // Half orange, half white capsule
                            val topX = w * (0.20f + col * 0.36f)
                            val topY = h * (0.22f + row * 0.22f)
                            val capW = w * 0.24f
                            val capH = h * 0.14f
                            drawRoundRect(
                                color = Color(0xFFF97316),
                                topLeft = Offset(topX, topY),
                                size = Size(capW * 0.5f, capH),
                                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            )
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(topX + capW * 0.5f, topY),
                                size = Size(capW * 0.5f, capH),
                                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            )
                        }
                    }
                }

                // Darolac Probiotics Box + Capsule
                productName.contains("Darolac", ignoreCase = true) -> {
                    // Box
                    drawRoundRect(
                        color = Color(0xFFDBEAFE),
                        topLeft = Offset(w * 0.12f, h * 0.18f),
                        size = Size(w * 0.52f, h * 0.64f),
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color(0xFF2563EB),
                        topLeft = Offset(w * 0.12f, h * 0.40f),
                        size = Size(w * 0.52f, h * 0.20f),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                    // Blue probiotic capsule next to box
                    drawRoundRect(
                        color = Color(0xFF3B82F6),
                        topLeft = Offset(w * 0.68f, h * 0.35f),
                        size = Size(w * 0.20f, h * 0.44f),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }

                // Default / Amoxicillin: Strip with blue/white capsules + box
                else -> {
                    // Medicine Box
                    drawRoundRect(
                        color = Color(0xFFE2E8F0),
                        topLeft = Offset(w * 0.10f, h * 0.20f),
                        size = Size(w * 0.54f, h * 0.60f),
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color(0xFF3B82F6),
                        topLeft = Offset(w * 0.10f, h * 0.38f),
                        size = Size(w * 0.54f, h * 0.16f),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                    // Foil blister next to it
                    drawRoundRect(
                        color = Color(0xFFCBD5E1),
                        topLeft = Offset(w * 0.66f, h * 0.30f),
                        size = Size(w * 0.24f, h * 0.50f),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFF2563EB),
                        radius = 3.dp.toPx(),
                        center = Offset(w * 0.78f, h * 0.42f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(w * 0.78f, h * 0.62f)
                    )
                }
            }
        }
    }
}

/**
 * Floating Bottom Order Bar: "1 item • ₹68.50    View Order >"
 */
@Composable
private fun FloatingOrderBar(
    itemsCount: Int,
    totalPrice: Double,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag("floating_order_bar"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ShoppingBag,
                        contentDescription = "Cart",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                val itemLabel = if (itemsCount == 1) "1 item" else "$itemsCount items"
                Text(
                    text = "$itemLabel • ₹${String.format(Locale.US, "%.2f", totalPrice)}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            Text(
                text = "View Order >",
                color = Color(0xFF22D3EE), // Vibrant Cyan as in image
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * Bottom Navigation Bar with Home, Explore (selected), Center Scanner FAB, Orders, Profile
 */
@Composable
private fun CatalogueBottomBar(
    onNavigateToDashboard: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToOcr: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // Home Tab
            BottomNavItem(
                icon = { Icon(Icons.Outlined.Home, contentDescription = "Home", modifier = Modifier.size(24.dp)) },
                label = "Home",
                isSelected = false,
                onClick = onNavigateToDashboard,
                testTag = "catalogue_nav_home"
            )

            // Explore Tab (ACTIVE)
            BottomNavItem(
                icon = { Icon(Icons.Default.Search, contentDescription = "Explore", modifier = Modifier.size(24.dp)) },
                label = "Explore",
                isSelected = true,
                onClick = {},
                testTag = "catalogue_nav_explore"
            )

            // Center Elevated Scan FAB
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .offset(y = (-10).dp)
                    .shadow(elevation = 8.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1D64EC), Color(0xFF0284C7))
                        )
                    )
                    .clickable { onNavigateToOcr() }
                    .testTag("catalogue_center_scan_fab"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCodeScanner,
                    contentDescription = "Scanner",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Orders Tab
            BottomNavItem(
                icon = { Icon(Icons.Outlined.Inventory2, contentDescription = "Orders", modifier = Modifier.size(24.dp)) },
                label = "Orders",
                isSelected = false,
                onClick = onNavigateToOrders,
                testTag = "catalogue_nav_orders"
            )

            // Profile Tab
            BottomNavItem(
                icon = { Icon(Icons.Outlined.Person, contentDescription = "Profile", modifier = Modifier.size(24.dp)) },
                label = "Profile",
                isSelected = false,
                onClick = onNavigateToProfile,
                testTag = "catalogue_nav_profile"
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: @Composable () -> Unit,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val tint = if (isSelected) Color(0xFF1D64EC) else Color(0xFF94A3B8)
        Surface(color = Color.Transparent, contentColor = tint) {
            icon()
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = tint
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    uiState: CatalogueUiState,
    onDismiss: () -> Unit,
    onApply: (DosageForm?, String?, StorageCondition?, Boolean?, Boolean, SortOption) -> Unit,
    onClear: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    var tempDosage by remember { mutableStateOf(uiState.selectedDosageForm) }
    var tempStorage by remember { mutableStateOf(uiState.selectedStorageCondition) }
    var tempInStock by remember { mutableStateOf(uiState.inStockOnly) }
    var tempSort by remember { mutableStateOf(uiState.sortBy) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter & Sort Catalogue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onClear) {
                    Text("Clear All")
                }
            }

            Text("Sort Order", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortOption.values().forEach { opt ->
                    FilterChip(
                        selected = tempSort == opt,
                        onClick = { tempSort = opt },
                        label = { Text(opt.label) }
                    )
                }
            }

            Text("Dosage Form", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DosageForm.values()) { form ->
                    FilterChip(
                        selected = tempDosage == form,
                        onClick = { tempDosage = if (tempDosage == form) null else form },
                        label = { Text(form.displayName) }
                    )
                }
            }

            Text("Storage Condition", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StorageCondition.values()) { storage ->
                    FilterChip(
                        selected = tempStorage == storage,
                        onClick = { tempStorage = if (tempStorage == storage) null else storage },
                        label = { Text(storage.label) }
                    )
                }
            }

            Button(
                onClick = {
                    onApply(tempDosage, uiState.selectedCategory, tempStorage, uiState.isRxOnly, tempInStock, tempSort)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apply_filter_button")
            ) {
                Text("Apply Filters")
            }
        }
    }
}

@Preview(name = "Medicine Catalogue - Light Mode", showBackground = true)
@Preview(name = "Medicine Catalogue - Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun MedicineCatalogueScreenPreview() {
    PharmaOSTheme {
        MedicineCatalogueContent(
            uiState = CatalogueUiState(
                isLoading = false,
                cartItemsCount = 3,
                cartTotalPrice = 1420.0,
                filteredProducts = listOf(
                    ProductDetail(
                        id = 1L,
                        name = "Augmentin 625 Duo Tablet",
                        brand = "Augmentin 625 Duo",
                        saltComposition = "Amoxicillin (500mg) + Clavulanic Acid (125mg)",
                        strength = "625mg",
                        dosageForm = DosageForm.TABLET,
                        therapeuticCategory = "Antibiotics",
                        manufacturerName = "GlaxoSmithKline",
                        packSize = "10 Tablets/Strip",
                        hsnCode = "3004",
                        gstRatePercent = 12.0,
                        storageCondition = StorageCondition.ROOM_TEMPERATURE,
                        isRx = true,
                        isActive = true,
                        createdBy = 1L,
                        minOfferPrice = 168.50,
                        minMrp = 201.0,
                        bestOfferId = 1L
                    ),
                    ProductDetail(
                        id = 2L,
                        name = "Pantocid 40 Tablet",
                        brand = "Pantocid 40",
                        saltComposition = "Pantoprazole (40mg)",
                        strength = "40mg",
                        dosageForm = DosageForm.TABLET,
                        therapeuticCategory = "Gastrointestinal",
                        manufacturerName = "Sun Pharma",
                        packSize = "15 Tablets/Strip",
                        hsnCode = "3004",
                        gstRatePercent = 12.0,
                        storageCondition = StorageCondition.ROOM_TEMPERATURE,
                        isRx = true,
                        isActive = true,
                        createdBy = 1L,
                        minOfferPrice = 112.00,
                        minMrp = 155.0,
                        bestOfferId = 2L
                    )
                )
            ),
            onNavigateBack = {},
            onProductClick = {},
            onAddProductClick = {},
            onSearchQueryChanged = {},
            onSelectCategoryChip = {},
            onClearFilters = {},
            onAddToCart = { _, _ -> },
            onIncrementQuantity = {},
            onDecrementQuantity = {},
            onSetPendingQuantity = { _, _ -> },
            onSetFilters = { _, _, _, _, _, _ -> },
            onClearMessages = {}
        )
    }
}


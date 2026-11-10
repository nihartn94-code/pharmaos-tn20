package com.example.ui.screens.orders

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.filled.ReceiptLong
import com.example.ui.theme.PharmaOSTheme

@Composable
fun MyOrdersScreen(
    viewModel: OrdersViewModel,
    onNavigateBack: () -> Unit,
    onOrderClick: (Long) -> Unit,
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToCatalogue: () -> Unit = {},
    onNavigateToOcr: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MyOrdersContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onOrderClick = onOrderClick,
        onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
        onClearSearch = { viewModel.onClearSearchQuery() },
        onSelectFilterTab = { viewModel.selectFilterTab(it) },
        onSelectSortOption = { viewModel.selectSortOption(it) },
        onSelectViewMode = { viewModel.setActiveViewMode(it) },
        onReorderClick = { orderId ->
            viewModel.reorderPastOrder(orderId) {
                onNavigateToCatalogue()
            }
        },
        onNavigateToDashboard = onNavigateToDashboard,
        onNavigateToCatalogue = onNavigateToCatalogue,
        onNavigateToOcr = onNavigateToOcr,
        onNavigateToProfile = onNavigateToProfile
    )
}

@Composable
fun MyOrdersContent(
    uiState: OrdersUiState,
    onNavigateBack: () -> Unit,
    onOrderClick: (Long) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSelectFilterTab: (String) -> Unit,
    onSelectSortOption: (String) -> Unit,
    onSelectViewMode: (String) -> Unit = {},
    onReorderClick: ((Long) -> Unit)? = null,
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToCatalogue: () -> Unit = {},
    onNavigateToOcr: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    var showTrackingDialog by remember { mutableStateOf<OrderPipelineItem?>(null) }
    var showDossierDialog by remember { mutableStateOf<OrderPipelineItem?>(null) }
    var showManageDialog by remember { mutableStateOf<OrderPipelineItem?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    val filterTabs = listOf(
        "All Orders (24)",
        "Under Production (6)",
        "Ready for QC (5)",
        "In Transit (8)",
        "Dispatched (5)"
    )

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            Column {
                OrderPipelineTopBar(
                    onBackClick = onNavigateBack
                )

                // View Mode Toggle: Order History (Room) vs Live Manufacturing Pipeline
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .background(Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isHistory = uiState.activeViewMode == "HISTORY"
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (isHistory) Color.White else Color.Transparent,
                        shadowElevation = if (isHistory) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectViewMode("HISTORY") }
                            .testTag("tab_order_history_mode")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = if (isHistory) Color(0xFF1D64EC) else Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Order History (${uiState.transactions.size})",
                                fontSize = 13.sp,
                                fontWeight = if (isHistory) FontWeight.Bold else FontWeight.Medium,
                                color = if (isHistory) Color(0xFF1D64EC) else Color(0xFF64748B)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (!isHistory) Color.White else Color.Transparent,
                        shadowElevation = if (!isHistory) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectViewMode("PIPELINE") }
                            .testTag("tab_pipeline_mode")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalShipping,
                                contentDescription = null,
                                tint = if (!isHistory) Color(0xFF1D64EC) else Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Live Pipeline",
                                fontSize = 13.sp,
                                fontWeight = if (!isHistory) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isHistory) Color(0xFF1D64EC) else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            OrderPipelineBottomBar(
                selectedTab = "orders",
                onSelectTab = { tab ->
                    when (tab) {
                        "home" -> onNavigateToDashboard()
                        "explore" -> onNavigateToCatalogue()
                        "scan" -> onNavigateToOcr()
                        "orders" -> { /* Already on Orders */ }
                        "profile" -> onNavigateToProfile()
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.activeViewMode == "HISTORY") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                com.example.ui.components.OrderHistoryList(
                    transactions = uiState.transactions,
                    onOrderClick = onOrderClick,
                    onReorderClick = onReorderClick,
                    onTrackClick = onOrderClick,
                    headerContent = {
                        PipelineHeaderSection(
                            companyName = uiState.currentUser?.businessName ?: "Apex Life Sciences",
                            activeCount = uiState.transactions.size
                        )
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .testTag("order_pipeline_screen"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // 1. Order Pipeline Header & Live Dispatch Badge
            item {
                PipelineHeaderSection(
                    companyName = uiState.currentUser?.businessName ?: "Apex Life Sciences",
                    activeCount = uiState.kpis.activeOrders
                )
            }

            // 2. Search & Filter Bar Row
            item {
                OrderSearchAndFilterRow(
                    query = uiState.searchQuery,
                    onQueryChange = { onSearchQueryChanged(it) },
                    onClear = { onClearSearch() },
                    onFilterClick = {
                        // Toggle quick filter between All and In Transit
                        val next = if (uiState.selectedFilterTab.startsWith("All")) "In Transit (8)" else "All Orders (24)"
                        onSelectFilterTab(next)
                    }
                )
            }

            // 3. Executive Summary 2x2 Metric Grid
            item {
                ExecutiveSummarySection(
                    kpis = uiState.kpis
                )
            }

            // 4. Horizontal Order Status Filter Chips
            item {
                OrderStatusTabsRow(
                    tabs = filterTabs,
                    selectedTab = uiState.selectedFilterTab,
                    onTabSelect = { onSelectFilterTab(it) }
                )
            }

            // 5. Filtered Pipeline Orders List (Primary Cards)
            if (uiState.filteredPipelineItems.isEmpty()) {
                item {
                    EmptyPipelineCard(
                        searchQuery = uiState.searchQuery,
                        onReset = { onClearSearch() }
                    )
                }
            } else {
                items(uiState.filteredPipelineItems, key = { it.id }) { item ->
                    PipelineOrderCard(
                        item = item,
                        onCardClick = {
                            if (item.rawOrderId != null) {
                                onOrderClick(item.rawOrderId)
                            } else {
                                onOrderClick(item.id)
                            }
                        },
                        onActionClick = {
                            when (item.actionText) {
                                "Track >" -> showTrackingDialog = item
                                "Dossier >" -> showDossierDialog = item
                                "Manage >" -> showManageDialog = item
                                else -> {
                                    if (item.rawOrderId != null) onOrderClick(item.rawOrderId)
                                    else onOrderClick(item.id)
                                }
                            }
                        }
                    )
                }
            }

            // 6. Active Consignments Section Header with Sort Dropdown
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Active Consignments",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEFF6FF)
                        ) {
                            Text(
                                text = "${uiState.activeConsignments.size} Filtered",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D64EC),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { showSortMenu = true }
                                .padding(4.dp)
                                .testTag("orders_sort_dropdown_btn")
                        ) {
                            Text(
                                text = "${uiState.selectedSort} ▾",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1D64EC)
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sort by ETA") },
                                onClick = {
                                    onSelectSortOption("Sort by ETA")
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Progress") },
                                onClick = {
                                    onSelectSortOption("Sort by Progress")
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Order ID") },
                                onClick = {
                                    onSelectSortOption("Sort by Order ID")
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // 7. Active Consignments Items (In Transit / Monitored Shipments)
            items(uiState.activeConsignments, key = { "consignment_${it.id}" }) { consignment ->
                PipelineOrderCard(
                    item = consignment,
                    onCardClick = {
                        if (consignment.rawOrderId != null) {
                            onOrderClick(consignment.rawOrderId)
                        } else {
                            onOrderClick(consignment.id)
                        }
                    },
                    onActionClick = {
                        when (consignment.actionText) {
                            "Track >" -> showTrackingDialog = consignment
                            "Dossier >" -> showDossierDialog = consignment
                            "Manage >" -> showManageDialog = consignment
                            else -> {
                                if (consignment.rawOrderId != null) onOrderClick(consignment.rawOrderId)
                                else onOrderClick(consignment.id)
                            }
                        }
                    }
                )
            }

            // 8. Cold-Chain Refrigerated Fleet Active Banner
            item {
                ColdChainFleetStatusCard()
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        }
    }

    // Modal Dialog: Live Route & Temperature Tracking
    showTrackingDialog?.let { item ->
        TrackingDetailsDialog(
            item = item,
            onDismiss = { showTrackingDialog = null },
            onFullDetails = {
                showTrackingDialog = null
                if (item.rawOrderId != null) onOrderClick(item.rawOrderId)
                else onOrderClick(item.id)
            }
        )
    }

    // Modal Dialog: Batch Serialization & Quality Dossier
    showDossierDialog?.let { item ->
        QualityDossierDialog(
            item = item,
            onDismiss = { showDossierDialog = null },
            onFullDetails = {
                showDossierDialog = null
                if (item.rawOrderId != null) onOrderClick(item.rawOrderId)
                else onOrderClick(item.id)
            }
        )
    }

    // Modal Dialog: Warehouse Staging & Bay Management
    showManageDialog?.let { item ->
        WarehouseManageDialog(
            item = item,
            onDismiss = { showManageDialog = null },
            onFullDetails = {
                showManageDialog = null
                if (item.rawOrderId != null) onOrderClick(item.rawOrderId)
                else onOrderClick(item.id)
            }
        )
    }
}

/* =========================================================================
   TOP APP BAR (BRANDED MATCHING REFERENCE)
   ========================================================================= */

@Composable
private fun OrderPipelineTopBar(
    onBackClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.testTag("orders_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Centered PharmaOS Logo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 36.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0284C7)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        val strokeW = 2.5.dp.toPx()
                        drawLine(
                            color = Color.White,
                            start = Offset(0f, size.height / 2),
                            end = Offset(size.width, size.height / 2),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(size.width / 2, 0f),
                            end = Offset(size.width / 2, size.height),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Pharma",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "OS",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E6FFE)
                        )
                    }
                    Text(
                        text = "CONNECT • TRADE • GROW",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 1.1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/* =========================================================================
   HEADER SECTION: Order Pipeline Title & Status Badge
   ========================================================================= */

@Composable
private fun PipelineHeaderSection(
    companyName: String,
    activeCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Order Pipeline 📦",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = companyName,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = " • $activeCount Active Shipments",
                    fontSize = 12.sp,
                    color = Color(0xFF1D64EC),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Live Dispatch Badge matching reference
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFEFF6FF),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1D64EC))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "DISPATCH ON SCHEDULE",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D64EC),
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/* =========================================================================
   SEARCH & FILTER INPUT ROW
   ========================================================================= */

@Composable
private fun OrderSearchAndFilterRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onFilterClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = "Search orders, PO numbers, buyer, SKU...",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
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
                focusedContainerColor = Color(0xFFF1F5F9),
                unfocusedContainerColor = Color(0xFFF1F5F9),
                focusedBorderColor = Color(0xFF1D64EC),
                unfocusedBorderColor = Color(0xFFF1F5F9)
            ),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("orders_search_input")
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Filter button matching reference
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEFF6FF))
                .border(1.dp, Color(0xFFDBEAFE), RoundedCornerShape(12.dp))
                .clickable { onFilterClick() }
                .testTag("orders_tune_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Filter Orders",
                tint = Color(0xFF1D64EC),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/* =========================================================================
   EXECUTIVE SUMMARY 2x2 METRIC GRID
   ========================================================================= */

@Composable
private fun ExecutiveSummarySection(
    kpis: PipelineKpis
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section Subheader
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EXECUTIVE SUMMARY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 0.8.sp
            )
            Text(
                text = "Updated 2m ago",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
        }

        // Row 1: Active Orders & Pending QC
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExecutiveKpiCard(
                modifier = Modifier.weight(1f),
                title = "ACTIVE ORDERS",
                value = "${kpis.activeOrders}",
                trendText = "▲ ${kpis.activeOrdersTrend}",
                trendColor = Color(0xFF1D64EC),
                icon = Icons.Default.LocalShipping
            )

            ExecutiveKpiCard(
                modifier = Modifier.weight(1f),
                title = "PENDING QC",
                value = "${kpis.pendingQc}",
                trendText = "• ${kpis.pendingQcAudits}",
                trendColor = Color(0xFF1D64EC),
                icon = Icons.Default.FactCheck
            )
        }

        // Row 2: Dispatched & Dispatch Value
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExecutiveKpiCard(
                modifier = Modifier.weight(1f),
                title = "DISPATCHED",
                value = "${kpis.dispatchedLots}",
                valueSuffix = " Lots",
                trendText = kpis.dispatchedOnTime,
                trendColor = Color(0xFF64748B),
                icon = Icons.Default.Verified
            )

            ExecutiveKpiCard(
                modifier = Modifier.weight(1f),
                title = "DISPATCH VALUE",
                value = kpis.dispatchValueRupeesLakhs,
                trendText = kpis.dispatchTargetPercent,
                trendColor = Color(0xFF1D64EC),
                icon = Icons.Default.AccountBalanceWallet
            )
        }
    }
}

@Composable
private fun ExecutiveKpiCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    valueSuffix: String = "",
    trendText: String,
    trendColor: Color,
    icon: ImageVector
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.5.sp
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF1D64EC),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                if (valueSuffix.isNotEmpty()) {
                    Text(
                        text = valueSuffix,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = trendText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = trendColor
            )
        }
    }
}

/* =========================================================================
   STATUS FILTER TABS ROW
   ========================================================================= */

@Composable
private fun OrderStatusTabsRow(
    tabs: List<String>,
    selectedTab: String,
    onTabSelect: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tabs) { tab ->
            val isSelected = tab.equals(selectedTab, ignoreCase = true) ||
                    (selectedTab.startsWith("All") && tab.startsWith("All"))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) Color(0xFF1D64EC) else Color.White)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color(0xFF1D64EC) else Color(0xFFE2E8F0),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onTabSelect(tab) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .testTag("pipeline_tab_${tab.take(6).lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) Color.White else Color(0xFF334155)
                )
            }
        }
    }
}

/* =========================================================================
   PIPELINE ORDER CARD (MATCHING REFERENCE EXACTLY)
   ========================================================================= */

@Composable
private fun PipelineOrderCard(
    item: OrderPipelineItem,
    onCardClick: () -> Unit,
    onActionClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("pipeline_card_${item.orderNumber}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Row 1: Leading Icon + Title / PO Subtitle + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading Icon Container
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when (item.iconKind) {
                        PipelineIconKind.CAPSULE -> Icons.Default.Medication
                        PipelineIconKind.VIAL -> Icons.Default.Vaccines
                        PipelineIconKind.PALLET -> Icons.Default.Inventory2
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF1D64EC),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.buyerName,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.orderNumber} • ${item.productSummary}",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Right Status Pill Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(item.status.bgHex)
                ) {
                    Text(
                        text = item.status.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(item.status.textHex),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Stage Name & Progress %
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.stageName,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF334155)
                )
                Text(
                    text = "${item.progressPercent}%",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D64EC)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Linear Progress Bar
            LinearProgressIndicator(
                progress = { item.progressPercent / 100f },
                color = Color(0xFF1D64EC),
                trackColor = Color(0xFFE2E8F0),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Row 3: Telemetry Footer (Courier, Temp/COA, ETA) + Action Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.tag1,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.tag2,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.tag3,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = item.actionText,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D64EC),
                    modifier = Modifier
                        .clickable { onActionClick() }
                        .padding(start = 6.dp)
                        .testTag("action_${item.orderNumber}")
                )
            }
        }
    }
}

/* =========================================================================
   COLD-CHAIN FLEET ALERT CARD
   ========================================================================= */

@Composable
private fun ColdChainFleetStatusCard() {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEFF6FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AcUnit,
                    contentDescription = null,
                    tint = Color(0xFF1D64EC),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Refrigerated Fleet Active",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "Fleet avg 5.2°C • 7/7 vans in range",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFEFF6FF)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF1D64EC),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Validated",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D64EC)
                    )
                }
            }
        }
    }
}

/* =========================================================================
   EMPTY STATE FOR PIPELINE SEARCH
   ========================================================================= */

@Composable
private fun EmptyPipelineCard(
    searchQuery: String,
    onReset: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No Orders Found",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (searchQuery.isNotBlank()) "No orders match '$searchQuery'." else "No orders in this stage.",
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onReset,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Clear Filters")
            }
        }
    }
}

/* =========================================================================
   MODAL DIALOGS: Tracking, Quality Dossier & Warehouse Staging
   ========================================================================= */

@Composable
private fun TrackingDetailsDialog(
    item: OrderPipelineItem,
    onDismiss: () -> Unit,
    onFullDetails: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalShipping, contentDescription = null, tint = Color(0xFF1D64EC))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Shipment Tracking: ${item.orderNumber}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Consignee: ${item.buyerName}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("Product: ${item.productSummary}", fontSize = 12.5.sp, color = Color(0xFF64748B))
                HorizontalDivider(color = Color(0xFFE2E8F0))
                Text("Current Stage: ${item.stageName} (${item.progressPercent}%)", fontWeight = FontWeight.Medium, color = Color(0xFF1D64EC), fontSize = 13.sp)
                Text("Carrier: ${item.tag1}", fontSize = 12.sp)
                Text("Temperature Telemetry: ${item.tag2}", fontSize = 12.sp)
                Text("Estimated Delivery: ${item.tag3}", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Milestones:", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        Text("✓ Warehouse Dispatch: Dispatched on schedule", fontSize = 11.sp, color = Color(0xFF15803D))
                        Text("✓ Sorting Hub 1: Passed scan verification", fontSize = 11.sp, color = Color(0xFF15803D))
                        Text("✓ Transit Hub 2: Passed with GPS temperature 4.2°C", fontSize = 11.sp, color = Color(0xFF15803D))
                        Text("• Delivery Hub: Out for local fulfillment tomorrow", fontSize = 11.sp, color = Color(0xFF1D64EC))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onFullDetails) {
                Text("View Order Details")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun QualityDossierDialog(
    item: OrderPipelineItem,
    onDismiss: () -> Unit,
    onFullDetails: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FactCheck, contentDescription = null, tint = Color(0xFF1D64EC))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Quality Dossier: ${item.orderNumber}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Client: ${item.buyerName}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("Drug: ${item.productSummary}", fontSize = 12.5.sp, color = Color(0xFF64748B))
                HorizontalDivider(color = Color(0xFFE2E8F0))
                Text("Quality Check Status: QA Check Passed (44%)", fontWeight = FontWeight.Medium, color = Color(0xFF0369A1), fontSize = 13.sp)
                Text("Batch Serialization Code: +DT-0020", fontSize = 12.sp)
                Text("Certificate of Analysis (COA): NABL Certified", fontSize = 12.sp)
                Text("Assay Release Window: 19 Nov, 9 AM", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF0FDF4),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓ Digital Dossier signed by QC Head. Good Manufacturing Practice (WHO-GMP) compliance verified.",
                        fontSize = 11.sp,
                        color = Color(0xFF166534),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onFullDetails) {
                Text("Open Order")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun WarehouseManageDialog(
    item: OrderPipelineItem,
    onDismiss: () -> Unit,
    onFullDetails: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color(0xFF1D64EC))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pallet Staging: ${item.orderNumber}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Distributor: ${item.buyerName}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("Consignment: ${item.productSummary}", fontSize = 12.5.sp, color = Color(0xFF64748B))
                HorizontalDivider(color = Color(0xFFE2E8F0))
                Text("Staging Stage: Pallet Staging (18%)", fontWeight = FontWeight.Medium, color = Color(0xFF475569), fontSize = 13.sp)
                Text("Commercial Value: ₹8.5L", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Staging Location: Bay 3 (Pallet Rack #12)", fontSize = 12.sp)
                Text("Dispatch Window: Scheduled container loading", fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(onClick = onFullDetails) {
                Text("Manage PO")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/* =========================================================================
   BOTTOM NAVIGATION BAR (MATCHING SYSTEM STANDARD)
   ========================================================================= */

@Composable
private fun OrderPipelineBottomBar(
    selectedTab: String,
    onSelectTab: (String) -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home
            BottomNavItem(
                label = "Home",
                icon = Icons.Default.Home,
                selected = selectedTab == "home",
                testTag = "orders_nav_home",
                onClick = { onSelectTab("home") }
            )

            // 2. Explore
            BottomNavItem(
                label = "Explore",
                icon = Icons.Default.Search,
                selected = selectedTab == "explore",
                testTag = "orders_nav_explore",
                onClick = { onSelectTab("explore") }
            )

            // 3. Center Scanner Floating Action Button
            Box(
                modifier = Modifier
                    .offset(y = (-10).dp)
                    .size(54.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xFF1E6FFE))
                    .clickable { onSelectTab("scan") }
                    .testTag("orders_nav_scan"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(26.dp)) {
                    val w = size.width
                    val h = size.height
                    val cornerLen = 7.dp.toPx()
                    val strokeW = 2.5.dp.toPx()

                    // Top-Left corner
                    drawLine(Color.White, Offset(0f, 0f), Offset(cornerLen, 0f), strokeW, StrokeCap.Round)
                    drawLine(Color.White, Offset(0f, 0f), Offset(0f, cornerLen), strokeW, StrokeCap.Round)

                    // Top-Right corner
                    drawLine(Color.White, Offset(w, 0f), Offset(w - cornerLen, 0f), strokeW, StrokeCap.Round)
                    drawLine(Color.White, Offset(w, 0f), Offset(w, cornerLen), strokeW, StrokeCap.Round)

                    // Bottom-Left corner
                    drawLine(Color.White, Offset(0f, h), Offset(cornerLen, h), strokeW, StrokeCap.Round)
                    drawLine(Color.White, Offset(0f, h), Offset(0f, h - cornerLen), strokeW, StrokeCap.Round)

                    // Bottom-Right corner
                    drawLine(Color.White, Offset(w, h), Offset(w - cornerLen, h), strokeW, StrokeCap.Round)
                    drawLine(Color.White, Offset(w, h), Offset(w, h - cornerLen), strokeW, StrokeCap.Round)

                    // Center scanner laser horizontal line
                    drawLine(Color.White, Offset(w * 0.2f, h / 2), Offset(w * 0.8f, h / 2), strokeW, StrokeCap.Round)
                }
            }

            // 4. Orders (Active)
            BottomNavItem(
                label = "Orders",
                icon = Icons.Default.Inventory2,
                selected = selectedTab == "orders",
                testTag = "orders_nav_orders",
                onClick = { onSelectTab("orders") }
            )

            // 5. Profile
            BottomNavItem(
                label = "Profile",
                icon = Icons.Outlined.Person,
                selected = selectedTab == "profile",
                testTag = "orders_nav_profile",
                onClick = { onSelectTab("profile") }
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Color(0xFF1E6FFE) else Color(0xFF94A3B8),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color(0xFF1E6FFE) else Color(0xFF94A3B8)
        )
    }
}

@Preview(name = "Order Pipeline - Light Mode", showBackground = true)
@Preview(name = "Order Pipeline - Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun MyOrdersScreenPreview() {
    PharmaOSTheme {
        MyOrdersContent(
            uiState = OrdersUiState(
                isLoading = false,
                kpis = PipelineKpis(),
                filteredPipelineItems = listOf(
                    OrderPipelineItem(
                        id = 1L,
                        orderNumber = "PO-44820",
                        buyerName = "MedPlus Wholesale Ltd.",
                        productSummary = "Paracetamol 500mg",
                        status = PipelineStatusType.IN_TRANSIT,
                        stageName = "Hub 2 Passed",
                        progressPercent = 65,
                        tag1 = "BlueDart",
                        tag2 = "4.2°C",
                        tag3 = "Tomorrow, 11:30 AM",
                        actionText = "Track >",
                        iconKind = PipelineIconKind.CAPSULE,
                        isColdChain = true
                    ),
                    OrderPipelineItem(
                        id = 2L,
                        orderNumber = "PO-44815",
                        buyerName = "Fortis Healthcare Chain",
                        productSummary = "Amoxicillin 250mg",
                        status = PipelineStatusType.QA_CHECK,
                        stageName = "Batch Serialization",
                        progressPercent = 44,
                        tag1 = "+DT-0020",
                        tag2 = "COA Verified",
                        tag3 = "19 Nov, 9 AM",
                        actionText = "Dossier >",
                        iconKind = PipelineIconKind.VIAL
                    )
                ),
                activeConsignments = listOf(
                    OrderPipelineItem(
                        id = 3L,
                        orderNumber = "PO-44809",
                        buyerName = "Zydus Distribution",
                        productSummary = "Cefixime 200mg",
                        status = PipelineStatusType.STAGING,
                        stageName = "Pallet Staging",
                        progressPercent = 16,
                        tag1 = "₹8.5L",
                        tag2 = "12 Pallets",
                        tag3 = "Bay 3",
                        actionText = "Manage >",
                        iconKind = PipelineIconKind.PALLET
                    )
                )
            ),
            onNavigateBack = {},
            onOrderClick = {},
            onSearchQueryChanged = {},
            onClearSearch = {},
            onSelectFilterTab = {},
            onSelectSortOption = {},
            onNavigateToDashboard = {},
            onNavigateToCatalogue = {},
            onNavigateToOcr = {},
            onNavigateToProfile = {}
        )
    }
}


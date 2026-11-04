package com.example.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.theme.PharmaOSTheme
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToCatalogue: () -> Unit,
    onNavigateToBasket: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToBatches: () -> Unit,
    onNavigateToShortages: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onNavigateToOcr: () -> Unit,
    onNavigateToOffers: () -> Unit,
    onNavigateToTrustScore: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToReturns: () -> Unit = {},
    onNavigateToOptimizer: () -> Unit = {},
    onNavigateToCompliance: () -> Unit = {},
    onNavigateToSearch: () -> Unit = onNavigateToCatalogue,
    onLoggedOut: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreenContent(
        uiState = uiState,
        onNavigateToCatalogue = onNavigateToCatalogue,
        onNavigateToBasket = onNavigateToBasket,
        onNavigateToOrders = onNavigateToOrders,
        onNavigateToBatches = onNavigateToBatches,
        onNavigateToShortages = onNavigateToShortages,
        onNavigateToLedger = onNavigateToLedger,
        onNavigateToOcr = onNavigateToOcr,
        onNavigateToOffers = onNavigateToOffers,
        onNavigateToTrustScore = onNavigateToTrustScore,
        onNavigateToNotifications = onNavigateToNotifications,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToReturns = onNavigateToReturns,
        onNavigateToOptimizer = onNavigateToOptimizer,
        onNavigateToCompliance = onNavigateToCompliance,
        onNavigateToSearch = onNavigateToSearch,
        onLogout = { viewModel.logout(onLoggedOut) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreenContent(
    uiState: DashboardUiState,
    onNavigateToCatalogue: () -> Unit,
    onNavigateToBasket: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToBatches: () -> Unit,
    onNavigateToShortages: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onNavigateToOcr: () -> Unit,
    onNavigateToOffers: () -> Unit,
    onNavigateToTrustScore: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToReturns: () -> Unit = {},
    onNavigateToOptimizer: () -> Unit = {},
    onNavigateToCompliance: () -> Unit = {},
    onNavigateToSearch: () -> Unit = onNavigateToCatalogue,
    onLogout: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("home") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
            ) {
                DashboardDrawerContent(
                    currentUser = uiState.currentUser,
                    cartItemCount = uiState.cartItemCount,
                    onCloseDrawer = {
                        coroutineScope.launch { drawerState.close() }
                    },
                    onNavigateToCatalogue = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToCatalogue()
                    },
                    onNavigateToOrders = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToOrders()
                    },
                    onNavigateToBasket = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToBasket()
                    },
                    onNavigateToOptimizer = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToOptimizer()
                    },
                    onNavigateToOcr = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToOcr()
                    },
                    onNavigateToBatches = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToBatches()
                    },
                    onNavigateToShortages = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToShortages()
                    },
                    onNavigateToTrustScore = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToTrustScore()
                    },
                    onNavigateToLedger = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToLedger()
                    },
                    onNavigateToReturns = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToReturns()
                    },
                    onNavigateToOffers = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToOffers()
                    },
                    onNavigateToCompliance = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToCompliance()
                    },
                    onNavigateToSettings = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                    onLogout = {
                        coroutineScope.launch { drawerState.close() }
                        onLogout()
                    }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        PharmaOSBrandHeader()
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("dashboard_hamburger_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Navigation Menu",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        // Circular Notification Button
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9))
                                .clickable { onNavigateToNotifications() }
                                .testTag("dashboard_notifications_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            BadgedBox(
                                badge = {
                                    if (uiState.unreadNotificationsCount > 0) {
                                        Badge(
                                            containerColor = Color(0xFFEF4444),
                                            contentColor = Color.White
                                        ) {
                                            Text("${uiState.unreadNotificationsCount}")
                                        }
                                    } else {
                                        // Small red dot indicator matching mockup
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFEF4444))
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = "Notifications",
                                    tint = Color(0xFF1E293B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Circular Cart Button
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9))
                                .clickable { onNavigateToBasket() }
                                .testTag("dashboard_cart_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            BadgedBox(
                                badge = {
                                    if (uiState.cartItemCount > 0) {
                                        Badge(
                                            containerColor = Color(0xFF2563EB),
                                            contentColor = Color.White
                                        ) {
                                            Text("${uiState.cartItemCount}")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ShoppingCart,
                                    contentDescription = "Shopping Cart",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                PharmaOSBottomBar(
                    selectedTab = selectedTab,
                    onSelectTab = { tab ->
                        selectedTab = tab
                        when (tab) {
                            "home" -> { /* Already on home */ }
                            "explore" -> onNavigateToCatalogue()
                            "scan" -> onNavigateToOcr()
                            "orders" -> onNavigateToOrders()
                            "profile" -> onNavigateToSettings()
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Greeting & Special Offer Card Row
                GreetingAndOfferRow(
                    userName = uiState.currentUser?.fullName?.ifBlank { "Rohan Sharma" } ?: "Rohan Sharma",
                    roleName = uiState.currentUser?.role?.displayName?.split("/")?.firstOrNull()?.trim() ?: "Stockist",
                    onRoleClick = onNavigateToSettings,
                    onOfferClick = onNavigateToCatalogue
                )

                // Health Overview Section
                HealthOverviewSection(
                    onViewDetails = onNavigateToCatalogue
                )

                // Search Bar
                SearchBarPill(
                    onClick = onNavigateToSearch
                )

                // Category Grid (4x2)
                CategoryGrid(
                    onCategoryClick = { onNavigateToCatalogue() }
                )

                // Top Deals Section
                TopDealsSection(
                    onViewAllClick = onNavigateToCatalogue,
                    onDealClick = { onNavigateToCatalogue() }
                )

                // Track Your Orders Banner
                TrackOrdersBanner(
                    onClick = onNavigateToOrders
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PharmaOSBrandHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Teal Rounded Square Logo Badge with White Cross
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF00A896)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(18.dp)) {
                val strokeW = 4.dp.toPx()
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

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Pharma",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp
                )
                Text(
                    text = "OS",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1E6FFE),
                    fontSize = 20.sp
                )
            }
            Text(
                text = "CONNECT • TRADE • GROW",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.3.sp
            )
        }
    }
}

@Composable
private fun GreetingAndOfferRow(
    userName: String,
    roleName: String,
    onRoleClick: () -> Unit,
    onOfferClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Greeting & Role
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Good Morning,",
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Normal
            )
            Text(
                text = userName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF1F5F9),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .clickable { onRoleClick() }
                    .testTag("dashboard_role_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = roleName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Role Selector",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right: Special Offer Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2)),
            modifier = Modifier
                .width(190.dp)
                .clickable { onOfferClick() }
                .testTag("dashboard_special_offer_card"),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCCD5))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 3D Capsule Pill Graphic
                Canvas(
                    modifier = Modifier
                        .size(38.dp)
                        .rotate(-30f)
                ) {
                    val pillWidth = size.width * 0.9f
                    val pillHeight = size.height * 0.46f
                    val corner = pillHeight / 2

                    // White half
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(0f, (size.height - pillHeight) / 2),
                        size = Size(pillWidth, pillHeight),
                        cornerRadius = CornerRadius(corner, corner)
                    )
                    // Cyan/Blue half
                    drawRoundRect(
                        color = Color(0xFF0284C7),
                        topLeft = Offset(pillWidth / 2, (size.height - pillHeight) / 2),
                        size = Size(pillWidth / 2, pillHeight),
                        cornerRadius = CornerRadius(corner, corner)
                    )
                    // Green small leaf dot
                    drawCircle(
                        color = Color(0xFF22C55E),
                        radius = 4.dp.toPx(),
                        center = Offset(pillWidth * 0.35f, size.height / 2)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Magenta SPECIAL OFFER badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFE11D48)
                    ) {
                        Text(
                            text = "SPECIAL OFFER",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Flat 12% OFF",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "on Selected Brands",
                        fontSize = 9.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun HealthOverviewSection(
    onViewDetails: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onViewDetails() }
                .testTag("dashboard_health_overview"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDCFCE7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Health Overview",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View Details",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
            )
        }

        // 4 Metric Cards in horizontal row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HealthMetricCard(
                title = "Calories Today",
                value = "1,240",
                subtitle = "/ 2,500 kcal",
                icon = Icons.Default.LocalFireDepartment,
                iconColor = Color(0xFFEA580C),
                iconBg = Color(0xFFFFEDD5),
                modifier = Modifier.weight(1f)
            )
            HealthMetricCard(
                title = "Steps",
                value = "6,842",
                subtitle = "/ 10,000",
                icon = Icons.Default.DirectionsWalk,
                iconColor = Color(0xFF0284C7),
                iconBg = Color(0xFFE0F2FE),
                modifier = Modifier.weight(1f)
            )
            HealthMetricCard(
                title = "Water",
                value = "6",
                subtitle = "/ 8 glasses",
                icon = Icons.Default.WaterDrop,
                iconColor = Color(0xFF2563EB),
                iconBg = Color(0xFFDBEAFE),
                modifier = Modifier.weight(1f)
            )
            HealthMetricCard(
                title = "Sleep",
                value = "7h 20m",
                subtitle = "/ 8h",
                icon = Icons.Default.Bedtime,
                iconColor = Color(0xFF7C3AED),
                iconBg = Color(0xFFEDE9FE),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HealthMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 9.sp,
                color = Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = subtitle,
                fontSize = 8.5.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
private fun SearchBarPill(
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onClick() }
            .testTag("dashboard_search_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Search by molecule, brand or product...",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice Search",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CategoryGrid(
    onCategoryClick: (String) -> Unit
) {
    val row1 = listOf(
        CategoryItem("Tablets", Color(0xFFE0F2FE), Color(0xFF0284C7), "tablet"),
        CategoryItem("Capsules", Color(0xFFFFE4E6), Color(0xFFE11D48), "capsule"),
        CategoryItem("Injectables", Color(0xFFCCFBF1), Color(0xFF0D9488), "injectable"),
        CategoryItem("Syrups", Color(0xFFFEF3C7), Color(0xFFD97706), "syrup")
    )

    val row2 = listOf(
        CategoryItem("OTC", Color(0xFFFFEDD5), Color(0xFFEA580C), "otc"),
        CategoryItem("Diabetics", Color(0xFFDCFCE7), Color(0xFF16A34A), "diabetic"),
        CategoryItem("Dermatology", Color(0xFFF3E8FF), Color(0xFF9333EA), "derma"),
        CategoryItem("More", Color(0xFFF1F5F9), Color(0xFF64748B), "more")
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            row1.forEach { item ->
                CategoryTile(item = item, onClick = { onCategoryClick(item.title) })
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            row2.forEach { item ->
                CategoryTile(item = item, onClick = { onCategoryClick(item.title) })
            }
        }
    }
}

private data class CategoryItem(
    val title: String,
    val bgColor: Color,
    val iconColor: Color,
    val type: String
)

@Composable
private fun CategoryTile(
    item: CategoryItem,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .testTag("dashboard_category_${item.title.lowercase()}")
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(item.bgColor),
            contentAlignment = Alignment.Center
        ) {
            when (item.type) {
                "tablet" -> {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawCircle(
                            color = item.iconColor,
                            radius = size.width / 2,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                        drawLine(
                            color = item.iconColor,
                            start = Offset(size.width * 0.2f, size.height * 0.2f),
                            end = Offset(size.width * 0.8f, size.height * 0.8f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
                "capsule" -> {
                    Canvas(modifier = Modifier.size(24.dp).rotate(45f)) {
                        val w = size.width * 0.85f
                        val h = size.height * 0.44f
                        val r = h / 2
                        drawRoundRect(
                            color = item.iconColor,
                            topLeft = Offset((size.width - w) / 2, (size.height - h) / 2),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(r, r)
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(size.width / 2, (size.height - h) / 2),
                            end = Offset(size.width / 2, (size.height + h) / 2),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }
                "injectable" -> {
                    Canvas(modifier = Modifier.size(24.dp).rotate(-45f)) {
                        drawLine(
                            color = item.iconColor,
                            start = Offset(size.width / 2, 0f),
                            end = Offset(size.width / 2, size.height * 0.8f),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = item.iconColor,
                            start = Offset(size.width * 0.3f, size.height * 0.8f),
                            end = Offset(size.width * 0.7f, size.height * 0.8f),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
                "syrup" -> {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawRoundRect(
                            color = item.iconColor,
                            topLeft = Offset(size.width * 0.25f, size.height * 0.3f),
                            size = Size(size.width * 0.5f, size.height * 0.65f),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                        drawRoundRect(
                            color = item.iconColor,
                            topLeft = Offset(size.width * 0.35f, size.height * 0.1f),
                            size = Size(size.width * 0.3f, size.height * 0.2f),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
                }
                "otc" -> {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawRoundRect(
                            color = item.iconColor,
                            topLeft = Offset(size.width * 0.2f, size.height * 0.25f),
                            size = Size(size.width * 0.6f, size.height * 0.65f),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                        drawLine(
                            color = item.iconColor,
                            start = Offset(size.width * 0.25f, size.height * 0.18f),
                            end = Offset(size.width * 0.75f, size.height * 0.18f),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
                "diabetic" -> {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawCircle(
                            color = item.iconColor,
                            radius = size.width / 2.2f,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                        drawLine(
                            color = item.iconColor,
                            start = Offset(size.width * 0.32f, size.height * 0.5f),
                            end = Offset(size.width * 0.46f, size.height * 0.66f),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = item.iconColor,
                            start = Offset(size.width * 0.46f, size.height * 0.66f),
                            end = Offset(size.width * 0.72f, size.height * 0.36f),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
                "derma" -> {
                    Canvas(modifier = Modifier.size(24.dp).rotate(-35f)) {
                        drawRoundRect(
                            color = item.iconColor,
                            topLeft = Offset(size.width * 0.3f, size.height * 0.1f),
                            size = Size(size.width * 0.4f, size.height * 0.7f),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "More",
                        tint = item.iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF334155),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TopDealsSection(
    onViewAllClick: () -> Unit,
    onDealClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Top Deals",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "View All ➔",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2563EB),
                modifier = Modifier
                    .clickable { onViewAllClick() }
                    .testTag("dashboard_top_deals_view_all")
            )
        }

        // Deal Cards Row (Horizontal Scroll)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DealCard(
                name = "Paracetamol 500mg",
                company = "Cipla",
                price = "₹ 42.00",
                discount = "12% OFF",
                boxColor = Color(0xFFE0F2FE),
                accentColor = Color(0xFF0284C7),
                testTag = "dashboard_deal_paracetamol",
                onClick = { onDealClick("Paracetamol 500mg") }
            )

            DealCard(
                name = "Amoxicillin 250mg",
                company = "Alkem",
                price = "₹ 68.50",
                discount = "10% OFF",
                boxColor = Color(0xFFDCFCE7),
                accentColor = Color(0xFF16A34A),
                testTag = "dashboard_deal_amoxicillin",
                onClick = { onDealClick("Amoxicillin 250mg") }
            )

            DealCard(
                name = "Vitamin D3",
                company = "Sun Pharma",
                price = "₹ 120.00",
                discount = "8% OFF",
                boxColor = Color(0xFFFFEDD5),
                accentColor = Color(0xFFEA580C),
                testTag = "dashboard_deal_vitamin_d3",
                onClick = { onDealClick("Vitamin D3") }
            )
        }
    }
}

@Composable
private fun DealCard(
    name: String,
    company: String,
    price: String,
    discount: String,
    boxColor: Color,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .width(150.dp)
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            // Illustrated Medicine Packaging
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF8FAFC)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Box illustration
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = boxColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                        modifier = Modifier.size(52.dp, 44.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(4.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(3.dp)
                                    .background(accentColor, RoundedCornerShape(1.dp))
                            )
                            Text(
                                text = name.split(" ").firstOrNull() ?: "",
                                fontSize = 6.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                maxLines = 1
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .height(2.dp)
                                    .background(Color(0xFF94A3B8), RoundedCornerShape(1.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Mini Blister Strip / Bottle
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFF1F5F9),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.size(24.dp, 36.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(2.dp),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(accentColor))
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(accentColor))
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(accentColor))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = company,
                fontSize = 10.sp,
                color = Color(0xFF64748B),
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = price,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFDCFCE7)
                ) {
                    Text(
                        text = discount,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF15803D),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackOrdersBanner(
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("dashboard_track_orders_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2563EB)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Track Your Orders",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E3A8A)
                )
                Text(
                    text = "Real-time updates, always.",
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B)
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF2563EB),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PharmaOSBottomBar(
    selectedTab: String,
    onSelectTab: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
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
                testTag = "dashboard_nav_home",
                onClick = { onSelectTab("home") }
            )

            // 2. Explore
            BottomNavItem(
                label = "Explore",
                icon = Icons.Default.Search,
                selected = selectedTab == "explore",
                testTag = "dashboard_nav_explore",
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
                    .testTag("dashboard_nav_scan"),
                contentAlignment = Alignment.Center
            ) {
                // Barcode/QR Scanning brackets icon
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

            // 4. Orders
            BottomNavItem(
                label = "Orders",
                icon = Icons.Default.Inventory2,
                selected = selectedTab == "orders",
                testTag = "dashboard_nav_orders",
                onClick = { onSelectTab("orders") }
            )

            // 5. Profile
            BottomNavItem(
                label = "Profile",
                icon = Icons.Outlined.Person,
                selected = selectedTab == "profile",
                testTag = "dashboard_nav_profile",
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

@Composable
private fun DashboardDrawerContent(
    currentUser: com.example.domain.model.AuthUser?,
    cartItemCount: Int = 0,
    onCloseDrawer: () -> Unit,
    onNavigateToCatalogue: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToBasket: () -> Unit,
    onNavigateToOptimizer: () -> Unit,
    onNavigateToOcr: () -> Unit,
    onNavigateToBatches: () -> Unit,
    onNavigateToShortages: () -> Unit,
    onNavigateToTrustScore: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onNavigateToReturns: () -> Unit,
    onNavigateToOffers: () -> Unit,
    onNavigateToCompliance: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var showSupportDialog by remember { mutableStateOf(false) }
    var showSubscriptionDialog by remember { mutableStateOf(false) }

    val fullName = currentUser?.fullName?.ifBlank { "Rohan Sharma" } ?: "Rohan Sharma"
    val initials = remember(fullName) {
        val parts = fullName.trim().split("\\s+".toRegex())
        when {
            parts.size >= 2 -> "${parts[0].firstOrNull()?.uppercaseChar() ?: 'R'}${parts[1].firstOrNull()?.uppercaseChar() ?: 'S'}"
            parts.isNotEmpty() && parts[0].isNotEmpty() -> parts[0].take(2).uppercase()
            else -> "RS"
        }
    }
    val roleName = currentUser?.role?.displayName ?: "Authorized Stockist"

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .defaultMinSize(minHeight = minHeight)
                .padding(vertical = 20.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Upper Content Section
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Top Header: Brand on left, circular close button on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PharmaOSBrandHeader()

                    Surface(
                        shape = CircleShape,
                        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF1F5F9),
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onCloseDrawer() }
                            .testTag("drawer_close_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close navigation drawer",
                                tint = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 2. Profile Card: Avatar RS + Green dot, Rohan Sharma + GOLD badge, Authorized Stockist, ID
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF0F7FF)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) MaterialTheme.colorScheme.outline else Color(0xFFE0F2FE)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToSettings() }
                        .testTag("drawer_profile_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar with online status dot
                        Box(modifier = Modifier.size(46.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color(0xFF007DFE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                                    .border(2.dp, if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color.White, CircleShape)
                                    .align(Alignment.BottomEnd)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = fullName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFEF3C7)
                                ) {
                                    Text(
                                        text = "GOLD",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFD97706),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = roleName,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = currentUser?.id?.let { "ID: POS-${it + 89410}" } ?: "ID: POS-89412",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0284C7)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Manufacturer Catalog (featured cyan card with 240+ Labs badge)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF0F9FF)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) Color(0xFF0284C7) else Color(0xFFBAE6FD)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCatalogue() }
                        .testTag("drawer_item_catalog")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0284C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Medication,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Manufacturer Catalog",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFDCFCE7)
                        ) {
                            Text(
                                text = "240+ Labs",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF059669),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 4. Basket (row with hot pink badge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onNavigateToBasket() }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .testTag("drawer_item_basket"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFF1F2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ShoppingBag,
                            contentDescription = null,
                            tint = Color(0xFFF43F5E),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Basket",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF43F5E)
                    ) {
                        Text(
                            text = "${if (cartItemCount > 0) cartItemCount else 3} items",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 5. Premium Subscription (warm amber card with PRO badge)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF3B2A10) else Color(0xFFFFFBEB)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) Color(0xFF78350F) else Color(0xFFFDE68A)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSubscriptionDialog = true }
                        .testTag("drawer_item_subscription")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF59E0B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Premium Subscription",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isDark) Color(0xFFFDE68A) else Color(0xFF78350F)
                            )
                            Text(
                                text = "Wholesale priority access",
                                fontSize = 10.sp,
                                color = if (isDark) Color(0xFFFCD34D) else Color(0xFF92400E)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFD97706)
                        ) {
                            Text(
                                text = "PRO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Subtle horizontal line under Premium Subscription
                HorizontalDivider(
                    thickness = 0.8.dp,
                    color = if (isDark) MaterialTheme.colorScheme.outline.copy(alpha = 0.4f) else Color(0xFFF1F5F9),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // 6. Order History
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onNavigateToOrders() }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .testTag("drawer_item_orders"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFEFF6FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Assignment,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Order History",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Real-Time Inventory Dashboard
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onNavigateToBatches() }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .testTag("drawer_item_inventory_dashboard"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFEF3C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Inventory Dashboard",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Stock levels & critical flags",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDC2626)
                    ) {
                        Text(
                            text = "LIVE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 7. Saved Prescriptions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onNavigateToOcr() }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .testTag("drawer_item_prescriptions"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFECFDF5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Saved Prescriptions",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "12",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 8. Support & Help (with active green dot)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showSupportDialog = true }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .testTag("drawer_item_support"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF5F3FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = null,
                            tint = Color(0xFF7C3AED),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Support & Help",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 9. Settings
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onNavigateToSettings() }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .testTag("drawer_item_settings"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Lower Section: Subtle divider, Outlined Log Out button & Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp)
            ) {
                // Subtle horizontal line above Log Out
                HorizontalDivider(
                    thickness = 0.8.dp,
                    color = if (isDark) MaterialTheme.colorScheme.outline.copy(alpha = 0.4f) else Color(0xFFF1F5F9),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 10. Outlined Log Out button
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) Color(0xFF4C0519).copy(alpha = 0.35f) else Color.White,
                    border = BorderStroke(1.2.dp, Color(0xFFFECDD3)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable { onLogout() }
                        .testTag("drawer_logout_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = Color(0xFFE11D48),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Log Out",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFE11D48)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 11. Footer: PharmaOS v2.4.0 • Enterprise | Connected
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PharmaOS v2.4.0 • Enterprise",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "Connected",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669)
                    )
                }
            }
        }
    }

    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = { showSupportDialog = false },
            title = { Text("PharmaOS 24/7 Priority Support", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your stockist account is linked to our Dedicated Pharmacy Desk.", style = MaterialTheme.typography.bodyMedium)
                    Text("• Hotline: 1800-890-PHARMA (Toll-Free)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("• Priority WhatsApp: +91 98765 43210", fontSize = 13.sp)
                    Text("• Desk Email: priority@pharmaos.in", fontSize = 13.sp)
                    Text("Average response time: < 3 minutes", color = Color(0xFF059669), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSupportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showSubscriptionDialog) {
        AlertDialog(
            onDismissRequest = { showSubscriptionDialog = false },
            title = { Text("PharmaOS PRO Wholesale", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("You currently enjoy GOLD Pro wholesale tier privileges:", style = MaterialTheme.typography.bodyMedium)
                    Text("✓ Priority supplier fulfillment & batch reservation", fontSize = 13.sp)
                    Text("✓ Real-time AI shortage alerts & best-buy routing", fontSize = 13.sp)
                    Text("✓ 30-day revolving credit line with zero platform fees", fontSize = 13.sp)
                    Text("Tier Active: Valid through 31 Dec 2026", color = Color(0xFFD97706), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubscriptionDialog = false }) {
                    Text("Got It")
                }
            }
        )
    }
}

@Preview(name = "Dashboard - Light Mode", showBackground = true)
@Preview(name = "Dashboard - Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun DashboardScreenPreview() {
    PharmaOSTheme {
        DashboardScreenContent(
            uiState = DashboardUiState(
                isLoading = false,
                currentUser = com.example.domain.model.AuthUser(
                    id = 1L,
                    fullName = "Rohan Sharma",
                    businessName = "Apex Pharma Distributors",
                    email = "rohan.sharma@apexpharma.com",
                    phone = "+919876543210",
                    gstin = "27ABCDE1234F1Z5",
                    address = "Shop 12, Medical Market, Mumbai, MH",
                    role = com.example.domain.model.UserRole.STOCKIST,
                    status = com.example.domain.model.AccountStatus.ACTIVE
                ),
                kpis = com.example.domain.repository.DashboardKpis(
                    role = com.example.domain.model.UserRole.STOCKIST,
                    openOrdersCount = 12,
                    basketItemsCount = 3,
                    nearExpiryBatchesCount = 4,
                    shortageAlertsCount = 2,
                    ledgerBalance = 64800.0
                ),
                cartItemCount = 3,
                unreadNotificationsCount = 2
            ),
            onNavigateToCatalogue = {},
            onNavigateToBasket = {},
            onNavigateToOrders = {},
            onNavigateToBatches = {},
            onNavigateToShortages = {},
            onNavigateToLedger = {},
            onNavigateToOcr = {},
            onNavigateToOffers = {},
            onNavigateToTrustScore = {},
            onNavigateToNotifications = {},
            onNavigateToSettings = {},
            onNavigateToReturns = {},
            onNavigateToOptimizer = {},
            onNavigateToCompliance = {},
            onNavigateToSearch = {},
            onLogout = {}
        )
    }
}


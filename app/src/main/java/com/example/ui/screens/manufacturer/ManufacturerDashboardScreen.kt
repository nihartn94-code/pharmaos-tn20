package com.example.ui.screens.manufacturer

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.BannerStatus
import com.example.domain.model.ManufacturerDashboardSummary
import com.example.domain.model.WorkspaceType
import com.example.ui.components.ManufacturerBottomBar
import com.example.ui.components.OfferBannerShowcaseCard
import com.example.ui.components.WorkspaceAccessDeniedScreen
import com.example.ui.components.WorkspaceSwitcherChip
import com.example.ui.components.WorkspaceSwitcherDialog
import com.example.ui.theme.PharmaOSTheme

@Composable
fun ManufacturerDashboardScreen(
    viewModel: ManufacturerWorkspaceViewModel,
    onNavigateBanners: () -> Unit,
    onNavigatePromotions: () -> Unit,
    onNavigateUpdates: () -> Unit,
    onNavigateProfile: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onNavigateRealTimeInventory: () -> Unit,
    onNavigateBatches: () -> Unit,
    onNavigateRecalls: () -> Unit,
    onNavigateOrders: () -> Unit,
    onSwitchToCompanyWorkspace: () -> Unit,
    onReturnToAuthorizedWorkspace: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (uiState.user != null && !uiState.isAuthorized) {
        WorkspaceAccessDeniedScreen(
            title = "Manufacturer Workspace Restricted",
            reason = "Only Pharmaceutical Organization employees with Manufacturer permissions can access the Manufacturer Workspace.",
            onReturnToAuthorizedWorkspace = onReturnToAuthorizedWorkspace
        )
        return
    }
    ManufacturerDashboardContent(
        uiState = uiState,
        onNavigateBanners = onNavigateBanners,
        onNavigatePromotions = onNavigatePromotions,
        onNavigateUpdates = onNavigateUpdates,
        onNavigateProfile = onNavigateProfile,
        onNavigateNotifications = onNavigateNotifications,
        onNavigateRealTimeInventory = onNavigateRealTimeInventory,
        onNavigateBatches = onNavigateBatches,
        onNavigateRecalls = onNavigateRecalls,
        onNavigateOrders = onNavigateOrders,
        onSwitchToCompany = { viewModel.switchToCompanyWorkspace(onSwitchToCompanyWorkspace) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManufacturerDashboardContent(
    uiState: ManufacturerWorkspaceUiState,
    onNavigateBanners: () -> Unit,
    onNavigatePromotions: () -> Unit,
    onNavigateUpdates: () -> Unit,
    onNavigateProfile: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onNavigateRealTimeInventory: () -> Unit,
    onNavigateBatches: () -> Unit,
    onNavigateRecalls: () -> Unit,
    onNavigateOrders: () -> Unit,
    onSwitchToCompany: () -> Unit
) {
    var showWorkspaceDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Manufacturer Console",
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.testTag("manufacturer_dashboard_title")
                        )
                        Text(
                            text = uiState.summary.organizationName.ifBlank { "Pharmaceutical Organization" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    WorkspaceSwitcherChip(
                        currentWorkspace = WorkspaceType.MANUFACTURER,
                        canSwitch = uiState.canSwitchToCompany,
                        onClick = { showWorkspaceDialog = true }
                    )
                    IconButton(
                        onClick = onNavigateNotifications,
                        modifier = Modifier.testTag("mfg_notifications_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (uiState.unreadNotificationsCount > 0) {
                                    Badge { Text(uiState.unreadNotificationsCount.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                        }
                    }
                }
            )
        },
        bottomBar = {
            ManufacturerBottomBar(
                selectedTab = "home",
                onNavigateHome = {},
                onNavigateProducts = onNavigateBanners,
                onNavigatePromotions = onNavigatePromotions,
                onNavigateUpdates = onNavigateUpdates,
                onNavigateProfile = onNavigateProfile
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("manufacturer_dashboard_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Factory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Plant Promotions & Product Pipeline",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = "Publish verified Stockist offer banners, run wholesale trade campaigns, announce approved formulations, and submit new medicines for Company Catalogue approval.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MfgKpiCard("Active Banners", uiState.summary.activeBannersCount.toString(), "Live to Stockists", Modifier.weight(1f), onNavigateBanners)
                        MfgKpiCard("Active Campaigns", uiState.summary.activeCampaignsCount.toString(), "Trade schemes", Modifier.weight(1f), onNavigatePromotions)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MfgKpiCard("Pending Approvals", uiState.summary.pendingSubmissionsCount.toString(), "${uiState.summary.rejectedSubmissionsCount} rejected", Modifier.weight(1f), onNavigateUpdates)
                        MfgKpiCard("Approved SKUs", uiState.summary.approvedProductsCount.toString(), "${uiState.summary.publishedAnnouncementsCount} updates live", Modifier.weight(1f), onNavigateBanners)
                    }
                }
            }

            item {
                Text(
                    text = "Manufacturer Growth Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MfgActionRowCard(
                        icon = Icons.Default.LocalOffer,
                        title = "Offer Banner Management",
                        subtitle = "Create, preview, schedule, activate & archive Stockist banners (${uiState.banners.size} total)",
                        testTag = "mfg_quick_banners_card",
                        onClick = onNavigateBanners
                    )
                    MfgActionRowCard(
                        icon = Icons.Default.Campaign,
                        title = "Promotional Campaigns & Schemes",
                        subtitle = "Configure bulk slabs, discount schemes & seasonal offers",
                        testTag = "mfg_quick_campaigns_card",
                        onClick = onNavigatePromotions
                    )
                    MfgActionRowCard(
                        icon = Icons.Default.AddCircle,
                        title = "Medicine Announcements & Submissions",
                        subtitle = "Announce approved medicines or submit new medicines for Company approval",
                        testTag = "mfg_quick_updates_card",
                        onClick = onNavigateUpdates
                    )
                }
            }

            if (uiState.banners.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Latest Offer Banner Preview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onNavigateBanners) { Text("Manage All") }
                    }
                    val featured = uiState.banners.firstOrNull { it.effectiveStatus() == BannerStatus.ACTIVE }
                        ?: uiState.banners.first()
                    OfferBannerShowcaseCard(
                        banner = featured,
                        showStatusPill = true,
                        onActionClick = onNavigateBanners
                    )
                }
            }

            item {
                Text(
                    text = "Preserved Plant & Supply Operations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MfgOperationalChip(Icons.Default.Inventory, "Real-Time Inventory", Modifier.weight(1f), "mfg_op_inventory", onNavigateRealTimeInventory)
                    MfgOperationalChip(Icons.Default.Science, "Batches & Expiry", Modifier.weight(1f), "mfg_op_batches", onNavigateBatches)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MfgOperationalChip(Icons.Default.Warning, "Batch Recalls", Modifier.weight(1f), "mfg_op_recalls", onNavigateRecalls)
                    MfgOperationalChip(Icons.Default.Factory, "Incoming Orders (${uiState.summary.incomingOrdersCount})", Modifier.weight(1f), "mfg_op_orders", onNavigateOrders)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showWorkspaceDialog) {
        WorkspaceSwitcherDialog(
            organizationName = uiState.summary.organizationName,
            currentWorkspace = WorkspaceType.MANUFACTURER,
            onSelectWorkspace = { target ->
                showWorkspaceDialog = false
                if (target == WorkspaceType.COMPANY) onSwitchToCompany()
            },
            onDismiss = { showWorkspaceDialog = false }
        )
    }
}

@Composable
private fun MfgKpiCard(
    title: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MfgActionRowCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MfgOperationalChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ManufacturerDashboardPreview() {
    PharmaOSTheme {
        ManufacturerDashboardContent(
            uiState = ManufacturerWorkspaceUiState(
                isAuthorized = true,
                canSwitchToCompany = true,
                summary = ManufacturerDashboardSummary(
                    organizationName = "Bharat Bio-Pharma Formulations",
                    activeBannersCount = 2,
                    activeCampaignsCount = 1,
                    pendingSubmissionsCount = 1,
                    approvedProductsCount = 14
                )
            ),
            onNavigateBanners = {},
            onNavigatePromotions = {},
            onNavigateUpdates = {},
            onNavigateProfile = {},
            onNavigateNotifications = {},
            onNavigateRealTimeInventory = {},
            onNavigateBatches = {},
            onNavigateRecalls = {},
            onNavigateOrders = {},
            onSwitchToCompany = {}
        )
    }
}

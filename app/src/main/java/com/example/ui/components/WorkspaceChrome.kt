package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.model.BannerStatus
import com.example.domain.model.MedicineAnnouncement
import com.example.domain.model.OfferBanner
import com.example.domain.model.SyncHealthStatus
import com.example.domain.model.WorkspaceSyncState
import com.example.domain.model.WorkspaceType
import com.example.util.Formatters

/**
 * Shared route-guard fallback rendered whenever an unauthorized role or deep-link
 * attempts to access a restricted workspace screen.
 */
@Composable
fun WorkspaceAccessDeniedScreen(
    title: String = "Workspace Access Restricted",
    reason: String,
    primaryActionLabel: String = "Return to Authorized Workspace",
    onReturnToAuthorizedWorkspace: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .testTag("workspace_access_denied_screen"),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Access Restricted",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onReturnToAuthorizedWorkspace,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("workspace_access_denied_return_btn")
                    ) {
                        Text(primaryActionLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Compact workspace badge & switcher trigger shown on Manufacturer/Company headers
 * when a Pharmaceutical Organization employee holds BOTH Manufacturer & Company permissions.
 */
@Composable
fun WorkspaceSwitcherChip(
    currentWorkspace: WorkspaceType,
    canSwitch: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = canSwitch) { onClick() }
            .testTag("workspace_switcher_chip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (currentWorkspace == WorkspaceType.MANUFACTURER) {
                    Icons.Default.Factory
                } else {
                    Icons.Default.Apartment
                },
                contentDescription = currentWorkspace.displayName,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = currentWorkspace.badgeLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (canSwitch) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Switch Workspace",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun WorkspaceSwitcherDialog(
    organizationName: String,
    currentWorkspace: WorkspaceType,
    onSelectWorkspace: (WorkspaceType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Switch Workspace", fontWeight = FontWeight.Bold)
                Text(
                    text = organizationName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Your account belongs to a single Pharmaceutical Organization with both Manufacturer and Company permissions. Switch seamlessly without signing out:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                WorkspaceSwitchOptionCard(
                    title = "Manufacturer Workspace",
                    subtitle = "Offer Banners, Promotions, Medicine Announcements, New Submissions & Plant Inventory",
                    icon = Icons.Default.Factory,
                    isSelected = currentWorkspace == WorkspaceType.MANUFACTURER,
                    testTag = "switch_to_manufacturer_option",
                    onClick = { onSelectWorkspace(WorkspaceType.MANUFACTURER) }
                )
                WorkspaceSwitchOptionCard(
                    title = "Company Workspace",
                    subtitle = "Medicine Catalogue, Compulsory Approvals Queue, Master Data & Team Roles",
                    icon = Icons.Default.Apartment,
                    isSelected = currentWorkspace == WorkspaceType.COMPANY,
                    testTag = "switch_to_company_option",
                    onClick = { onSelectWorkspace(WorkspaceType.COMPANY) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun WorkspaceSwitchOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Model representing a single KPI card rendered in [ResponsiveKpiGrid].
 */
data class DashboardKpiCardModel(
    val title: String,
    val value: String,
    val caption: String,
    val icon: ImageVector,
    val accentColor: Color? = null,
    val testTag: String = "",
    val onClick: () -> Unit
)

/**
 * Responsive dashboard KPI grid that guarantees equal card heights per row using
 * `IntrinsicSize.Max` + `fillMaxHeight()` on standard viewports, and automatically
 * collapses into a full-width single-column stack on narrow screens (<340dp) or
 * high accessibility font scales (>=1.45f) so text never clips or overlaps.
 */
@Composable
fun ResponsiveKpiGrid(
    items: List<DashboardKpiCardModel>,
    modifier: Modifier = Modifier
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val useSingleColumn = maxWidth < 340.dp || fontScale >= 1.45f
        val columns = if (useSingleColumn) 1 else 2

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items.chunked(columns).forEach { rowItems ->
                if (columns == 1) {
                    AlignedKpiCard(
                        model = rowItems.first(),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { model ->
                            AlignedKpiCard(
                                model = model,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                        if (rowItems.size < columns) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlignedKpiCard(
    model: DashboardKpiCardModel,
    modifier: Modifier = Modifier
) {
    val accent = model.accentColor ?: MaterialTheme.colorScheme.primary
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { model.onClick() }
            .let { base -> if (model.testTag.isNotBlank()) base.testTag(model.testTag) else base }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accent.copy(alpha = 0.12f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = model.icon,
                            contentDescription = model.title,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = model.caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Compact sync status / stale-cache banner surfaced whenever remote synchronization is
 * running, partially failed, failed, or displaying cached data with a retry action.
 */
@Composable
fun WorkspaceSyncStatusBanner(
    syncState: WorkspaceSyncState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!syncState.isSyncing && !syncState.hasWarningOrError) return
    val isError = syncState.status == SyncHealthStatus.FAILED
    val containerColor = when {
        syncState.isSyncing -> MaterialTheme.colorScheme.secondaryContainer
        isError -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = when {
        syncState.isSyncing -> MaterialTheme.colorScheme.onSecondaryContainer
        isError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .testTag("workspace_sync_status_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (syncState.isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Sync status warning",
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val headline = when (syncState.status) {
                    SyncHealthStatus.SYNCING -> "Synchronizing workspace data..."
                    SyncHealthStatus.PARTIAL_SUCCESS -> "Partial sync — some cached data may be stale"
                    SyncHealthStatus.FAILED -> "Sync failed — showing cached data"
                    SyncHealthStatus.CANCELLED -> "Sync cancelled"
                    else -> "Showing cached workspace data"
                }
                Text(
                    text = headline,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                val detail = syncState.errorMessage ?: syncState.lastSuccessfulSyncEpochMillis?.let { ts ->
                    "Last synced ${Formatters.formatDateTime(ts)}"
                }
                if (!detail.isNullOrBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (syncState.canRetry && !syncState.isSyncing) {
                OutlinedButton(
                    onClick = onRetry,
                    border = BorderStroke(1.dp, contentColor.copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("workspace_sync_retry_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry sync",
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }
        }
    }
}

private data class WorkspaceNavDestination(
    val id: String,
    val label: String,
    val contentDescription: String,
    val icon: ImageVector,
    val badgeCount: Int = 0,
    val badgeTestTag: String? = null,
    val testTag: String,
    val onClick: () -> Unit
)

/**
 * Shared responsive 5-destination bottom navigation bar for Manufacturer & Company workspaces.
 *
 * - Standard width (`>= 360.dp`) and standard font scale (`<= 1.3f`): renders Material 3 [NavigationBar].
 * - Narrow screens (`< 360.dp`, e.g. 320dp) or high accessibility font scales (`> 1.3f`, e.g. 1.5x–2.0x):
 *   automatically switches to a horizontally scrollable accessible destination bar so every destination
 *   keeps a full readable label (no cramped single-line ellipsis) and >= 48dp touch targets.
 */
@Composable
private fun AdaptiveWorkspaceNavigationBar(
    barTestTag: String,
    selectedId: String,
    destinations: List<WorkspaceNavDestination>
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useScrollableCompactBar = maxWidth < 360.dp || fontScale > 1.3f
        if (!useScrollableCompactBar) {
            val labelStyle = MaterialTheme.typography.labelMedium
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                windowInsets = WindowInsets.navigationBars,
                modifier = Modifier.testTag(barTestTag)
            ) {
                destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = selectedId == dest.id,
                        onClick = dest.onClick,
                        icon = {
                            WorkspaceNavIconWithBadge(
                                icon = dest.icon,
                                contentDescription = dest.contentDescription,
                                badgeCount = dest.badgeCount,
                                badgeTestTag = dest.badgeTestTag
                            )
                        },
                        label = {
                            Text(
                                text = dest.label,
                                style = labelStyle,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag(dest.testTag)
                    )
                }
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(barTestTag)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    destinations.forEach { dest ->
                        val isSelected = selectedId == dest.id
                        val containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Transparent
                        }
                        val tintColor = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = containerColor,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 68.dp, minHeight = 52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { dest.onClick() }
                                .testTag(dest.testTag)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                WorkspaceNavIconWithBadge(
                                    icon = dest.icon,
                                    contentDescription = dest.contentDescription,
                                    badgeCount = dest.badgeCount,
                                    badgeTestTag = dest.badgeTestTag,
                                    tint = tintColor
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dest.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = tintColor,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceNavIconWithBadge(
    icon: ImageVector,
    contentDescription: String,
    badgeCount: Int,
    badgeTestTag: String?,
    tint: Color = Color.Unspecified
) {
    if (badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge(
                    modifier = if (badgeTestTag != null) Modifier.testTag(badgeTestTag) else Modifier
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        ) {
            if (tint == Color.Unspecified) {
                Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
            } else {
                Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(24.dp))
            }
        }
    } else {
        if (tint == Color.Unspecified) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
        } else {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * Shared Stockist Workspace Bottom Navigation:
 * Home | Explore | Scan | Orders | Profile
 *
 * Adapts seamlessly between the signature elevated-scanner bar on standard displays
 * and an accessible horizontally scrollable 5-destination bar on narrow screens (<360dp)
 * or large accessibility font scales (>1.3x), using Material 3 theme tokens in Light & Dark mode.
 */
@Composable
fun StockistWorkspaceBottomBar(
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    homeTestTag: String = "dashboard_nav_home",
    exploreTestTag: String = "dashboard_nav_explore",
    scanTestTag: String = "dashboard_nav_scan",
    ordersTestTag: String = "dashboard_nav_orders",
    profileTestTag: String = "dashboard_nav_profile",
    barTestTag: String = "stockist_bottom_nav"
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useScrollableCompactBar = maxWidth < 360.dp || fontScale > 1.3f
        if (useScrollableCompactBar) {
            AdaptiveWorkspaceNavigationBar(
                barTestTag = barTestTag,
                selectedId = selectedTab,
                destinations = listOf(
                    WorkspaceNavDestination("home", "Home", "Home", Icons.Default.Home, testTag = homeTestTag, onClick = { onSelectTab("home") }),
                    WorkspaceNavDestination("explore", "Explore", "Explore", Icons.Default.Search, testTag = exploreTestTag, onClick = { onSelectTab("explore") }),
                    WorkspaceNavDestination("scan", "Scan", "OCR Scanner", Icons.Default.QrCodeScanner, testTag = scanTestTag, onClick = { onSelectTab("scan") }),
                    WorkspaceNavDestination("orders", "Orders", "Orders", Icons.Default.Inventory2, testTag = ordersTestTag, onClick = { onSelectTab("orders") }),
                    WorkspaceNavDestination("profile", "Profile", "Profile", Icons.Outlined.Person, testTag = profileTestTag, onClick = { onSelectTab("profile") })
                )
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(barTestTag)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StockistNavTabItem("Home", Icons.Default.Home, selectedTab == "home", homeTestTag) { onSelectTab("home") }
                    StockistNavTabItem("Explore", Icons.Default.Search, selectedTab == "explore", exploreTestTag) { onSelectTab("explore") }

                    Box(
                        modifier = Modifier
                            .offset(y = (-8).dp)
                            .size(54.dp)
                            .shadow(8.dp, CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onSelectTab("scan") }
                            .testTag(scanTestTag),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(26.dp)) {
                            val w = size.width
                            val h = size.height
                            val cornerLen = 7.dp.toPx()
                            val strokeW = 2.5.dp.toPx()
                            drawLine(Color.White, Offset(0f, 0f), Offset(cornerLen, 0f), strokeW, StrokeCap.Round)
                            drawLine(Color.White, Offset(0f, 0f), Offset(0f, cornerLen), strokeW, StrokeCap.Round)
                            drawLine(Color.White, Offset(w, 0f), Offset(w - cornerLen, 0f), strokeW, StrokeCap.Round)
                            drawLine(Color.White, Offset(w, 0f), Offset(w, cornerLen), strokeW, StrokeCap.Round)
                            drawLine(Color.White, Offset(0f, h), Offset(cornerLen, h), strokeW, StrokeCap.Round)
                            drawLine(Color.White, Offset(0f, h), Offset(0f, h - cornerLen), strokeW, StrokeCap.Round)
                            drawLine(Color.White, Offset(w, h), Offset(w - cornerLen, h), strokeW, StrokeCap.Round)
                            drawLine(Color.White, Offset(w, h), Offset(w, h - cornerLen), strokeW, StrokeCap.Round)
                            drawLine(Color.White, Offset(w * 0.2f, h / 2), Offset(w * 0.8f, h / 2), strokeW, StrokeCap.Round)
                        }
                    }

                    StockistNavTabItem("Orders", Icons.Default.Inventory2, selectedTab == "orders", ordersTestTag) { onSelectTab("orders") }
                    StockistNavTabItem("Profile", Icons.Outlined.Person, selectedTab == "profile", profileTestTag) { onSelectTab("profile") }
                }
            }
        }
    }
}

@Composable
private fun StockistNavTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Manufacturer Workspace Bottom Navigation:
 * Home | Products | Offers | Updates | Profile
 */
@Composable
fun ManufacturerBottomBar(
    selectedTab: String,
    onNavigateHome: () -> Unit,
    onNavigateProducts: () -> Unit,
    onNavigatePromotions: () -> Unit,
    onNavigateUpdates: () -> Unit,
    onNavigateProfile: () -> Unit
) {
    AdaptiveWorkspaceNavigationBar(
        barTestTag = "manufacturer_bottom_nav",
        selectedId = selectedTab,
        destinations = listOf(
            WorkspaceNavDestination(
                id = "home",
                label = "Home",
                contentDescription = "Manufacturer Home",
                icon = Icons.Default.Home,
                testTag = "mfg_nav_home",
                onClick = onNavigateHome
            ),
            WorkspaceNavDestination(
                id = "products",
                label = "Products",
                contentDescription = "Products & Banners",
                icon = Icons.Default.Inventory2,
                testTag = "mfg_nav_products",
                onClick = onNavigateProducts
            ),
            WorkspaceNavDestination(
                id = "promotions",
                label = "Offers",
                contentDescription = "Promotions & Offers",
                icon = Icons.Default.LocalOffer,
                testTag = "mfg_nav_promotions",
                onClick = onNavigatePromotions
            ),
            WorkspaceNavDestination(
                id = "updates",
                label = "Updates",
                contentDescription = "Updates & Submissions",
                icon = Icons.Default.Campaign,
                testTag = "mfg_nav_updates",
                onClick = onNavigateUpdates
            ),
            WorkspaceNavDestination(
                id = "profile",
                label = "Profile",
                contentDescription = "Profile",
                icon = Icons.Default.Person,
                testTag = "mfg_nav_profile",
                onClick = onNavigateProfile
            )
        )
    )
}

/**
 * Company Workspace Bottom Navigation:
 * Home | Catalogue | Approvals (with BadgedBox count) | Manage | Profile
 */
@Composable
fun CompanyBottomBar(
    selectedTab: String,
    pendingApprovalsCount: Int = 0,
    onNavigateHome: () -> Unit,
    onNavigateCatalogue: () -> Unit,
    onNavigateApprovals: () -> Unit,
    onNavigateManagement: () -> Unit,
    onNavigateProfile: () -> Unit
) {
    AdaptiveWorkspaceNavigationBar(
        barTestTag = "company_bottom_nav",
        selectedId = selectedTab,
        destinations = listOf(
            WorkspaceNavDestination(
                id = "home",
                label = "Home",
                contentDescription = "Company Home",
                icon = Icons.Default.Home,
                testTag = "company_nav_home",
                onClick = onNavigateHome
            ),
            WorkspaceNavDestination(
                id = "catalogue",
                label = "Catalogue",
                contentDescription = "Catalogue",
                icon = Icons.Default.Medication,
                testTag = "company_nav_catalogue",
                onClick = onNavigateCatalogue
            ),
            WorkspaceNavDestination(
                id = "approvals",
                label = "Approvals",
                contentDescription = if (pendingApprovalsCount > 0) {
                    "Approvals, $pendingApprovalsCount pending"
                } else {
                    "Approvals"
                },
                icon = Icons.Default.Approval,
                badgeCount = pendingApprovalsCount,
                badgeTestTag = "company_nav_approvals_badge",
                testTag = "company_nav_approvals",
                onClick = onNavigateApprovals
            ),
            WorkspaceNavDestination(
                id = "management",
                label = "Manage",
                contentDescription = "Master Data & Management",
                icon = Icons.Default.Tune,
                testTag = "company_nav_management",
                onClick = onNavigateManagement
            ),
            WorkspaceNavDestination(
                id = "profile",
                label = "Profile",
                contentDescription = "Profile",
                icon = Icons.Default.Person,
                testTag = "company_nav_profile",
                onClick = onNavigateProfile
            )
        )
    )
}

/**
 * Reusable dynamic Offer Banner Card rendered in:
 * 1) Manufacturer Banner Management live preview
 * 2) Stockist Dashboard active promotional banner carousel
 */
@Composable
fun OfferBannerShowcaseCard(
    banner: OfferBanner,
    showStatusPill: Boolean = false,
    onActionClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val effectiveStatus = banner.effectiveStatus()
    val gradientColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary
    )
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onActionClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
        ) {
            if (!banner.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = banner.imageUrl,
                    contentDescription = banner.title,
                    contentScale = ContentScale.Crop,
                    alpha = 0.25f,
                    modifier = Modifier.matchParentSize()
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = banner.offerType.displayName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (showStatusPill) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when (effectiveStatus) {
                                BannerStatus.ACTIVE -> Color(0xFF10B981)
                                BannerStatus.SCHEDULED -> Color(0xFFF59E0B)
                                BannerStatus.EXPIRED -> Color(0xFFEF4444)
                                else -> Color(0xFF64748B)
                            }
                        ) {
                            Text(
                                text = effectiveStatus.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "Valid till ${Formatters.formatDate(banner.endDateEpochMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.92f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = banner.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (banner.subtitle.isNotBlank()) {
                    Text(
                        text = banner.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = banner.discountOrOfferSummary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = banner.organizationName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        banner.linkedProductName?.let { prod ->
                            Text(
                                text = "Linked: $prod",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.84f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = banner.ctaText.ifBlank { "View Offer" },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MedicineAnnouncementCard(
    announcement: MedicineAnnouncement,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = announcement.category.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Text(
                    text = Formatters.formatDate(announcement.publishedAtEpochMillis ?: announcement.createdAtEpochMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = announcement.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = announcement.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${announcement.linkedProductName} • ${announcement.organizationName}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

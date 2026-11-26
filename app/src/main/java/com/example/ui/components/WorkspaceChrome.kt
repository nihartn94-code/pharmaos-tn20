package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val labelStyle = MaterialTheme.typography.labelMedium
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        windowInsets = WindowInsets.navigationBars,
        modifier = Modifier.testTag("manufacturer_bottom_nav")
    ) {
        NavigationBarItem(
            selected = selectedTab == "home",
            onClick = onNavigateHome,
            icon = { Icon(Icons.Default.Home, contentDescription = "Manufacturer Home", modifier = Modifier.size(24.dp)) },
            label = { Text("Home", style = labelStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("mfg_nav_home")
        )
        NavigationBarItem(
            selected = selectedTab == "products",
            onClick = onNavigateProducts,
            icon = { Icon(Icons.Default.Inventory2, contentDescription = "Products & Banners", modifier = Modifier.size(24.dp)) },
            label = { Text("Products", style = labelStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("mfg_nav_products")
        )
        NavigationBarItem(
            selected = selectedTab == "promotions",
            onClick = onNavigatePromotions,
            icon = { Icon(Icons.Default.LocalOffer, contentDescription = "Promotions & Offers", modifier = Modifier.size(24.dp)) },
            label = { Text("Offers", style = labelStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("mfg_nav_promotions")
        )
        NavigationBarItem(
            selected = selectedTab == "updates",
            onClick = onNavigateUpdates,
            icon = { Icon(Icons.Default.Campaign, contentDescription = "Updates & Submissions", modifier = Modifier.size(24.dp)) },
            label = { Text("Updates", style = labelStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("mfg_nav_updates")
        )
        NavigationBarItem(
            selected = selectedTab == "profile",
            onClick = onNavigateProfile,
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile", modifier = Modifier.size(24.dp)) },
            label = { Text("Profile", style = labelStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("mfg_nav_profile")
        )
    }
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
    val labelStyle = MaterialTheme.typography.labelMedium
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        windowInsets = WindowInsets.navigationBars,
        modifier = Modifier.testTag("company_bottom_nav")
    ) {
        NavigationBarItem(
            selected = selectedTab == "home",
            onClick = onNavigateHome,
            icon = { Icon(Icons.Default.Home, contentDescription = "Company Home", modifier = Modifier.size(24.dp)) },
            label = { Text("Home", style = labelStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("company_nav_home")
        )
        NavigationBarItem(
            selected = selectedTab == "catalogue",
            onClick = onNavigateCatalogue,
            icon = { Icon(Icons.Default.Medication, contentDescription = "Catalogue", modifier = Modifier.size(24.dp)) },
            label = { Text("Catalogue", style = labelStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("company_nav_catalogue")
        )
        NavigationBarItem(
            selected = selectedTab == "approvals",
            onClick = onNavigateApprovals,
            icon = {
                BadgedBox(
                    badge = {
                        if (pendingApprovalsCount > 0) {
                            Badge(
                                modifier = Modifier.testTag("company_nav_approvals_badge")
                            ) {
                                Text(
                                    text = if (pendingApprovalsCount > 99) "99+" else pendingApprovalsCount.toString(),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Approval,
                        contentDescription = if (pendingApprovalsCount > 0) {
                            "Approvals, $pendingApprovalsCount pending"
                        } else {
                            "Approvals"
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            label = {
                Text(
                    text = "Approvals",
                    style = labelStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            },
            modifier = Modifier.testTag("company_nav_approvals")
        )
        NavigationBarItem(
            selected = selectedTab == "management",
            onClick = onNavigateManagement,
            icon = { Icon(Icons.Default.Tune, contentDescription = "Master Data & Management", modifier = Modifier.size(24.dp)) },
            label = { Text("Manage", style = labelStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("company_nav_management")
        )
        NavigationBarItem(
            selected = selectedTab == "profile",
            onClick = onNavigateProfile,
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile", modifier = Modifier.size(24.dp)) },
            label = { Text("Profile", style = labelStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("company_nav_profile")
        )
    }
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

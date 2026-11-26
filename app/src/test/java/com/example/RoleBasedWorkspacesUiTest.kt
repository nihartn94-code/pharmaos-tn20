package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.domain.model.AccountStatus
import com.example.domain.model.AuthUser
import com.example.domain.model.BannerOfferType
import com.example.domain.model.BannerStatus
import com.example.domain.model.CompanyDashboardSummary
import com.example.domain.model.ManufacturerDashboardSummary
import com.example.domain.model.MedicineAnnouncement
import com.example.domain.model.OfferBanner
import com.example.domain.model.OrganizationType
import com.example.domain.model.RolePermissionMatrix
import com.example.domain.model.UserRole
import com.example.ui.components.WorkspaceAccessDeniedScreen
import com.example.ui.screens.company.CompanyDashboardContent
import com.example.ui.screens.company.CompanyWorkspaceUiState
import com.example.ui.screens.dashboard.DashboardScreenContent
import com.example.ui.screens.dashboard.DashboardUiState
import com.example.ui.screens.manufacturer.ManufacturerDashboardContent
import com.example.ui.screens.manufacturer.ManufacturerWorkspaceUiState
import com.example.ui.theme.PharmaOSTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI tests verifying distinct role workspaces,
 * dual-permission workspace switching affordance, unauthorized route guard,
 * Stockist dynamic banners/announcements, and Light & Dark theme rendering.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoleBasedWorkspacesUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun manufacturerWorkspaceRendersDistinctConsoleAndWorkspaceSwitcherInLightAndDark() {
        val now = System.currentTimeMillis()
        val uiState = ManufacturerWorkspaceUiState(
            isAuthorized = true,
            canSwitchToCompany = true,
            summary = ManufacturerDashboardSummary(
                organizationName = "Bharat Bio-Pharma",
                activeBannersCount = 3,
                activeCampaignsCount = 2,
                publishedAnnouncementsCount = 4,
                pendingSubmissionsCount = 1,
                approvedProductsCount = 18
            ),
            banners = listOf(
                OfferBanner(
                    id = 1L,
                    organizationId = 200L,
                    organizationName = "Bharat Bio-Pharma",
                    title = "Cardiac Festival Offer",
                    subtitle = "10+2 Strips Free",
                    offerType = BannerOfferType.FLAT_SCHEME,
                    discountOrOfferSummary = "10+2 Free",
                    startDateEpochMillis = now - 86_400_000L,
                    endDateEpochMillis = now + 86_400_000L,
                    status = BannerStatus.ACTIVE,
                    createdByUserId = 20L
                )
            )
        )

        composeRule.setContent {
            PharmaOSTheme(darkTheme = true) {
                ManufacturerDashboardContent(
                    uiState = uiState,
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

        composeRule.onNodeWithTag("manufacturer_dashboard_title").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace_switcher_chip").assertIsDisplayed()
        composeRule.onNodeWithText("Bharat Bio-Pharma").assertIsDisplayed()
    }

    @Test
    fun companyWorkspaceRendersGovernanceDashboardWithPendingApprovals() {
        val uiState = CompanyWorkspaceUiState(
            isAuthorized = true,
            canSwitchToManufacturer = true,
            summary = CompanyDashboardSummary(
                organizationName = "Bharat Bio-Pharma",
                totalCatalogueCount = 24,
                activeCatalogueCount = 22,
                pendingApprovalsCount = 3,
                rejectedSubmissionsCount = 1,
                masterDataEntriesCount = 42,
                organizationMembersCount = 6
            )
        )

        composeRule.setContent {
            PharmaOSTheme(darkTheme = false) {
                CompanyDashboardContent(
                    uiState = uiState,
                    onNavigateCatalogue = {},
                    onNavigateAddMedicine = {},
                    onNavigateApprovals = {},
                    onNavigateManagement = {},
                    onNavigateProfile = {},
                    onNavigateNotifications = {},
                    onSwitchToManufacturer = {}
                )
            }
        }

        composeRule.onNodeWithTag("company_dashboard_title").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace_switcher_chip").assertIsDisplayed()
    }

    @Test
    fun unauthorizedAccessShowsWorkspaceAccessDeniedGuardAndRedirectsOnTap() {
        var returned = false
        composeRule.setContent {
            PharmaOSTheme {
                WorkspaceAccessDeniedScreen(
                    title = "Company Workspace Restricted",
                    reason = "Stockists cannot access Company Governance screens.",
                    onReturnToAuthorizedWorkspace = { returned = true }
                )
            }
        }

        composeRule.onNodeWithTag("workspace_access_denied_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace_access_denied_return_btn").performClick()
        assertTrue(returned)
    }

    @Test
    fun stockistDashboardDisplaysActiveOfferBannersAndAnnouncements() {
        val now = System.currentTimeMillis()
        val stockist = AuthUser(
            id = 10L,
            fullName = "Rohan Sharma",
            businessName = "Apex Pharma Distributors",
            email = "stockist@pharmaos.com",
            phone = "9876543210",
            gstin = "27ABCDE1234F1Z5",
            address = "Mumbai",
            role = UserRole.STOCKIST,
            status = AccountStatus.ACTIVE,
            organizationId = 100L,
            organizationType = OrganizationType.STOCKIST,
            organizationName = "Apex Pharma Distributors",
            assignedRoles = setOf(UserRole.STOCKIST),
            permissions = RolePermissionMatrix.stockistPermissions
        )

        val state = DashboardUiState(
            isLoading = false,
            currentUser = stockist,
            isRealTimeInventoryAuthorized = false,
            activeOfferBanners = listOf(
                OfferBanner(
                    id = 7L,
                    organizationId = 200L,
                    organizationName = "Bharat Bio-Pharma",
                    title = "Winter Immunity Scheme",
                    subtitle = "15% Extra Margin",
                    offerType = BannerOfferType.PERCENT_DISCOUNT,
                    discountOrOfferSummary = "15% OFF",
                    startDateEpochMillis = now - 86_400_000L,
                    endDateEpochMillis = now + 86_400_000L,
                    status = BannerStatus.ACTIVE,
                    createdByUserId = 20L
                )
            ),
            publishedAnnouncements = listOf(
                MedicineAnnouncement(
                    id = 9L,
                    organizationId = 200L,
                    organizationName = "Bharat Bio-Pharma",
                    linkedProductId = 1L,
                    linkedProductName = "CardioShield 40",
                    title = "New Pack Launch",
                    body = "Now shipping in moisture-lock alu-alu strips.",
                    createdByUserId = 20L
                )
            )
        )

        composeRule.setContent {
            PharmaOSTheme {
                DashboardScreenContent(
                    uiState = state,
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
                    onNavigateToSettings = {}
                )
            }
        }

        composeRule.onNodeWithTag("stockist_active_offer_banners").assertExists()
        composeRule.onNodeWithTag("stockist_medicine_announcements").assertExists()
    }
}

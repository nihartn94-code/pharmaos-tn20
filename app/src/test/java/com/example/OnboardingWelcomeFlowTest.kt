package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.repository.PharmaRepositoryImpl
import com.example.domain.model.AccountStatus
import com.example.domain.model.AuthUser
import com.example.domain.model.UserRole
import com.example.domain.repository.DashboardKpis
import com.example.domain.repository.LaunchDestination
import com.example.ui.screens.auth.WelcomeScreen
import com.example.ui.screens.dashboard.DashboardScreenContent
import com.example.ui.screens.dashboard.DashboardUiState
import com.example.ui.theme.PharmaOSTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class OnboardingWelcomeFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var repository: PharmaRepositoryImpl

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreManager = DataStoreManager(context)
        // Reset persisted flags so every test starts from a deterministic fresh-install state
        dataStoreManager.clearAuthSession()
        dataStoreManager.setOnboardingCompleted(false)
        dataStoreManager.setAuthExecutionMode(AuthExecutionMode.DEVELOPMENT_LOCAL_MODE)

        repository = PharmaRepositoryImpl(
            db = db,
            dataStoreManager = dataStoreManager
        )
    }

    @After
    fun tearDown() = runBlocking {
        dataStoreManager.clearAuthSession()
        dataStoreManager.setOnboardingCompleted(false)
        db.close()
    }

    @Test
    fun freshInstallation_routesToWelcomeScreen_andShowsNoSpecialOffer() {
        runBlocking {
            assertFalse(repository.isOnboardingCompleted())
            assertNull(repository.getApiUsersMe())
            assertEquals(LaunchDestination.WELCOME, repository.resolveLaunchDestination())
        }

        var getStartedClicked = false
        var signInClicked = false

        composeTestRule.setContent {
            PharmaOSTheme {
                WelcomeScreen(
                    onGetStarted = { getStartedClicked = true },
                    onSignIn = { signInClicked = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("welcome_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Welcome to PharmaOS").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_get_started_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_sign_in_button").performScrollTo().assertIsDisplayed()

        // Ensure Special Offer box is completely absent from Welcome Screen
        composeTestRule.onNodeWithText("SPECIAL OFFER").assertDoesNotExist()
        composeTestRule.onNodeWithText("Flat 12% OFF").assertDoesNotExist()
        composeTestRule.onNodeWithTag("dashboard_special_offer_card").assertDoesNotExist()

        composeTestRule.onNodeWithTag("welcome_get_started_button").performScrollTo().performClick()
        assertTrue(getStartedClicked)

        composeTestRule.onNodeWithTag("welcome_sign_in_button").performScrollTo().performClick()
        assertTrue(signInClicked)
    }

    @Test
    fun firstTimeRegistration_adminSetupCompleted_persistsAndRoutesToDashboardOnRestart() = runBlocking {
        // First registered user becomes Master Admin and completes initial setup immediately
        val adminRes = repository.postApiAuthRegister(
            fullName = "Asha Patel",
            businessName = "Asha Pharma Admin",
            email = "asha.admin@pharmaos.com",
            phone = "9876500001",
            gstin = "27AAAAA0000A1Z5",
            address = "Mumbai, MH",
            password = "Password@123",
            role = UserRole.ADMIN
        )
        assertTrue(adminRes.isSuccess)
        assertTrue(repository.isOnboardingCompleted())
        assertEquals(LaunchDestination.DASHBOARD, repository.resolveLaunchDestination())

        // Simulate app restart with new repository instance backed by same DB & DataStore
        val restartedRepo = PharmaRepositoryImpl(db = db, dataStoreManager = DataStoreManager(context))
        assertTrue(restartedRepo.isOnboardingCompleted())
        assertNotNull(restartedRepo.getApiUsersMe())
        assertEquals(LaunchDestination.DASHBOARD, restartedRepo.resolveLaunchDestination())
    }

    @Test
    fun nonAdminRegistration_completesSetupAtCompliance_andNeverRedirectsToWelcome() = runBlocking {
        // Seed an admin first so second registration keeps STOCKIST role
        repository.postApiAuthRegister(
            fullName = "Master Admin",
            businessName = "PharmaOS HQ",
            email = "admin@pharmaos.com",
            phone = "9876500000",
            gstin = "27AAAAA0000A1Z5",
            address = "Mumbai",
            password = "Password@123",
            role = UserRole.ADMIN
        )
        // Simulate fresh non-admin user before onboarding completion
        repository.logout()
        repository.setOnboardingCompleted(false)
        assertEquals(LaunchDestination.WELCOME, repository.resolveLaunchDestination())

        // Non-admin registers -> pending compliance verification
        val regResult = repository.postApiAuthRegister(
            fullName = "Rohan Sharma",
            businessName = "Apex Medicals",
            email = "rohan.stockist@pharmaos.com",
            phone = "9876500002",
            gstin = "27ABCDE1234F1Z5",
            address = "Pune, MH",
            password = "Password@123",
            role = UserRole.STOCKIST
        )
        assertTrue(regResult.isSuccess)
        val stockist = regResult.getOrThrow()
        assertEquals(UserRole.STOCKIST, stockist.role)
        assertFalse(repository.isOnboardingCompleted())

        // If app restarts before compliance verification completes, do not redirect to Welcome or Dashboard prematurely
        val midSetupRepo = PharmaRepositoryImpl(db = db, dataStoreManager = DataStoreManager(context))
        assertEquals(LaunchDestination.COMPLIANCE_VERIFICATION, midSetupRepo.resolveLaunchDestination())

        // Complete account setup via compliance verification
        val complianceRes = midSetupRepo.saveComplianceVerification(
            userId = stockist.id,
            gstin = "27ABCDE1234F1Z5",
            drugLicenseNumber = "20B/MH/104928",
            drugLicenseIssueDate = "01/01/2023",
            drugLicenseExpiryDate = "31/12/2028",
            drugLicenseDocUri = "content://docs/dl.pdf",
            whoGmpCertificateNumber = null,
            whoGmpIssueDate = null,
            whoGmpExpiryDate = null,
            whoGmpDocUri = null,
            isWhoGmpApplicable = false
        )
        assertTrue(complianceRes.isSuccess)
        assertTrue(midSetupRepo.isOnboardingCompleted())
        assertEquals(LaunchDestination.DASHBOARD, midSetupRepo.resolveLaunchDestination())
    }

    @Test
    fun logout_preservesOnboardingStatus_andRoutesToLoginThenDashboardOnReturningUser() = runBlocking {
        // Complete setup & login
        repository.postApiAuthRegister(
            fullName = "Asha Patel",
            businessName = "Asha Pharma Admin",
            email = "returning@pharmaos.com",
            phone = "9876500003",
            gstin = "27AAAAA0000A1Z5",
            address = "Mumbai",
            password = "Password@123",
            role = UserRole.ADMIN
        )
        assertTrue(repository.isOnboardingCompleted())
        assertEquals(LaunchDestination.DASHBOARD, repository.resolveLaunchDestination())

        // Logout: clears user session, MUST NOT erase onboarding completion status
        repository.logout()
        assertNull(repository.getApiUsersMe())
        assertTrue(repository.getOnboardingCompletedFlow().first())
        assertEquals(LaunchDestination.LOGIN, repository.resolveLaunchDestination())

        // App restart while logged out -> goes straight to LOGIN, never WELCOME
        val restartedAfterLogout = PharmaRepositoryImpl(db = db, dataStoreManager = DataStoreManager(context))
        assertTrue(restartedAfterLogout.isOnboardingCompleted())
        assertNull(restartedAfterLogout.getApiUsersMe())
        assertEquals(LaunchDestination.LOGIN, restartedAfterLogout.resolveLaunchDestination())

        // Login again -> goes to DASHBOARD, onboarding status remains persisted
        val loginRes = restartedAfterLogout.postApiAuthLogin(
            email = "returning@pharmaos.com",
            password = "Password@123",
            expectedRole = UserRole.ADMIN
        )
        assertTrue(loginRes.isSuccess)
        assertTrue(restartedAfterLogout.isOnboardingCompleted())
        assertEquals(LaunchDestination.DASHBOARD, restartedAfterLogout.resolveLaunchDestination())

        // Returning user restarts app while logged in -> directly DASHBOARD
        val returningLaunchRepo = PharmaRepositoryImpl(db = db, dataStoreManager = DataStoreManager(context))
        assertEquals(LaunchDestination.DASHBOARD, returningLaunchRepo.resolveLaunchDestination())
    }

    @Test
    fun dashboardScreen_removesSpecialOfferBox_whilePreservingTopDealsPromotions() {
        composeTestRule.setContent {
            PharmaOSTheme {
                DashboardScreenContent(
                    uiState = DashboardUiState(
                        isLoading = false,
                        currentUser = AuthUser(
                            id = 1L,
                            fullName = "Rohan Sharma",
                            businessName = "Apex Pharma Distributors",
                            email = "rohan.sharma@apexpharma.com",
                            phone = "+919876543210",
                            gstin = "27ABCDE1234F1Z5",
                            address = "Mumbai, MH",
                            role = UserRole.STOCKIST,
                            status = AccountStatus.ACTIVE
                        ),
                        kpis = DashboardKpis(role = UserRole.STOCKIST)
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
                    onNavigateToSettings = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Greeting and Role Selector remain intact
        composeTestRule.onNodeWithText("Good Morning,").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_greeting_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_role_pill").assertIsDisplayed()

        // Special Offer box is completely removed
        composeTestRule.onNodeWithTag("dashboard_special_offer_card").assertDoesNotExist()
        composeTestRule.onNodeWithText("SPECIAL OFFER").assertDoesNotExist()
        composeTestRule.onNodeWithText("Flat 12% OFF").assertDoesNotExist()
        composeTestRule.onNodeWithText("on Selected Brands").assertDoesNotExist()

        // Other promotional sections (Top Deals) remain intact
        composeTestRule.onNodeWithText("Track Your Orders").performScrollTo()
        composeTestRule.onNodeWithText("Top Deals").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_deal_paracetamol").performScrollTo().assertIsDisplayed()
    }
}

package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.AppDatabaseMigrations
import com.example.data.local.DataStoreManager
import com.example.data.local.SecureTokenStorage
import com.example.data.local.entity.UserEntity
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.repository.PharmaRepositoryImpl
import com.example.data.repository.WorkspaceRepositoryImpl
import com.example.domain.model.AccountStatus
import com.example.domain.model.AuthUser
import com.example.domain.model.OrganizationType
import com.example.domain.model.UserRole
import com.example.domain.model.WorkspaceType
import com.example.domain.repository.DashboardKpis
import com.example.domain.repository.SessionRestorationResult
import com.example.ui.navigation.AccountAuthorizationRequiredScreen
import com.example.ui.navigation.StartupSessionRecoveryScreen
import com.example.ui.screens.auth.AuthUiState
import com.example.ui.screens.auth.AuthViewModel
import com.example.ui.screens.auth.RegisterScreenContent
import com.example.ui.screens.compliance.ComplianceUiState
import com.example.ui.screens.compliance.ComplianceVerificationScreen
import com.example.ui.screens.compliance.ComplianceViewModel
import com.example.ui.screens.dashboard.DashboardScreenContent
import com.example.ui.screens.dashboard.DashboardUiState
import com.example.ui.screens.dashboard.DashboardViewModel
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class StockistCrashAndSessionRestorationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var testTokenStorage: SecureTokenStorage
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var repository: PharmaRepositoryImpl
    private lateinit var workspaceRepository: WorkspaceRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        testTokenStorage = SecureTokenStorage.createForUnitTest(context, "test_stockist_crash_tokens")
        testTokenStorage.saveTokens(null, null)
        dataStoreManager = DataStoreManager(context, secureTokenStorage = testTokenStorage)
        dataStoreManager.clearAuthSession()
        dataStoreManager.setWelcomeCompleted(false)
        dataStoreManager.setOnboardingCompleted(false)
        dataStoreManager.setAuthExecutionMode(AuthExecutionMode.DEVELOPMENT_LOCAL_MODE)

        repository = PharmaRepositoryImpl(db = db, dataStoreManager = dataStoreManager)
        workspaceRepository = WorkspaceRepositoryImpl(
            db = db,
            dataStoreManager = dataStoreManager,
            pharmaRepository = repository
        )
    }

    @After
    fun tearDown() = runBlocking {
        dataStoreManager.clearAuthSession()
        dataStoreManager.setWelcomeCompleted(false)
        dataStoreManager.setOnboardingCompleted(false)
        testTokenStorage.saveTokens(null, null)
        db.close()
    }

    private fun idleMainLooperAndCoroutines(cycles: Int = 5) {
        repeat(cycles) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(60)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun waitUntil(timeoutMs: Long = 5000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(30)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    // -------------------------------------------------------------------------
    // Scenarios 1, 2, 3 & 13:
    // Fresh install -> Stockist registration on empty DB -> stays STOCKIST (never ADMIN)
    // -> Compliance -> Stockist Dashboard init with WorkspaceRepository (ZERO crash!)
    // -> Close and reopen app -> Restores Stockist Dashboard cleanly without crash loop
    // -------------------------------------------------------------------------
    @Test
    fun freshInstall_stockistRegistration_compliance_dashboard_andAppRestart_noCrashLoop() {
        runBlocking {
            // Fresh install routes to Welcome
            assertTrue(repository.resolveSessionRestoration() is SessionRestorationResult.Welcome)

            // Register Stockist on completely empty DB -> MUST remain STOCKIST (never promoted to ADMIN)
            val regRes = repository.postApiAuthRegister(
                fullName = "Kiran Deshmukh",
                businessName = "Kiran Pharma Distributors",
                email = "kiran.stockist@pharmaos.test",
                phone = "9820011223",
                gstin = "27AABCK1234F1Z5",
                address = "Andheri East, Mumbai",
                password = "Password@123",
                role = UserRole.STOCKIST
            )
            assertTrue("Stockist registration should succeed: ${regRes.exceptionOrNull()}", regRes.isSuccess)
            val stockist = regRes.getOrThrow()
            assertEquals(UserRole.STOCKIST, stockist.role)
            assertEquals(OrganizationType.STOCKIST, stockist.organizationType)
            assertTrue(stockist.organizationId > 0L)
            assertEquals(setOf(WorkspaceType.STOCKIST), stockist.authorizedWorkspaces().toSet())
            assertTrue(repository.requiresComplianceForUser(stockist))

            // Before compliance completes, reopening routes to ComplianceRequired (not Welcome or Dashboard)
            val preComplianceRestore = repository.resolveSessionRestoration()
            assertTrue(preComplianceRestore is SessionRestorationResult.ComplianceRequired)

            // Complete Stockist compliance (WHO-GMP not required for Stockist)
            val compRes = repository.saveComplianceVerification(
                userId = stockist.id,
                gstin = "27AABCK1234F1Z5",
                drugLicenseNumber = "20B/MH/884210",
                drugLicenseIssueDate = "01/04/2024",
                drugLicenseExpiryDate = "31/03/2029",
                drugLicenseDocUri = null,
                whoGmpCertificateNumber = null,
                whoGmpIssueDate = null,
                whoGmpExpiryDate = null,
                whoGmpDocUri = null,
                isWhoGmpApplicable = false
            )
            assertTrue("Compliance submission should succeed: ${compRes.exceptionOrNull()}", compRes.isSuccess)
            assertFalse(repository.requiresComplianceForUser(stockist))

            // Simulate closing and reopening the app with fresh repository & workspace instances
            val reopenedDataStore = DataStoreManager(context, secureTokenStorage = testTokenStorage)
            val reopenedRepo = PharmaRepositoryImpl(db = db, dataStoreManager = reopenedDataStore)
            val reopenedWorkspaceRepo = WorkspaceRepositoryImpl(
                db = db,
                dataStoreManager = reopenedDataStore,
                pharmaRepository = reopenedRepo
            )
            val restored = reopenedRepo.resolveSessionRestoration()
            assertTrue("Expected AuthorizedWorkspace on reopen, got $restored", restored is SessionRestorationResult.AuthorizedWorkspace)
            val restoredUser = (restored as SessionRestorationResult.AuthorizedWorkspace).user
            assertEquals(UserRole.STOCKIST, restoredUser.role)
            assertEquals(stockist.id, restoredUser.id)

            // Construct DashboardViewModel with valid WorkspaceRepository — previously crashed with NPE on bannerClockMillis!
            val uncaught = CopyOnWriteArrayList<Throwable>()
            val prevDefault = Thread.getDefaultUncaughtExceptionHandler()
            val prevCurrent = Thread.currentThread().uncaughtExceptionHandler
            val capture = Thread.UncaughtExceptionHandler { _, t -> uncaught.add(t) }
            Thread.setDefaultUncaughtExceptionHandler(capture)
            Thread.currentThread().uncaughtExceptionHandler = capture
            try {
                val vm = DashboardViewModel(
                    repository = reopenedRepo,
                    workspaceRepository = reopenedWorkspaceRepo
                )
                idleMainLooperAndCoroutines(6)
                assertEquals(UserRole.STOCKIST, vm.uiState.value.currentUser?.role)
                assertNull(vm.uiState.value.errorMessage)
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(prevDefault)
                Thread.currentThread().uncaughtExceptionHandler = prevCurrent
            }
            assertTrue("Expected zero uncaught exceptions, got: $uncaught", uncaught.isEmpty())
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 13: Dedicated DashboardViewModel initialization-order regression test
    // -------------------------------------------------------------------------
    @Test
    fun dashboardViewModel_withWorkspaceRepository_initializesWithoutCrash() {
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        val prevThreadHandler = Thread.currentThread().uncaughtExceptionHandler
        val captureHandler = Thread.UncaughtExceptionHandler { _, throwable ->
            uncaught.add(throwable)
        }
        Thread.setDefaultUncaughtExceptionHandler(captureHandler)
        Thread.currentThread().uncaughtExceptionHandler = captureHandler
        try {
            val vm = DashboardViewModel(
                repository = repository,
                workspaceRepository = workspaceRepository
            )
            idleMainLooperAndCoroutines(6)
            assertNotNull(vm.uiState.value)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(prevHandler)
            Thread.currentThread().uncaughtExceptionHandler = prevThreadHandler
        }
        if (uncaught.isNotEmpty()) {
            throw AssertionError(
                "Uncaught exception during DashboardViewModel init: ${uncaught.first()}",
                uncaught.first()
            )
        }
    }

    // -------------------------------------------------------------------------
    // Scenarios 4, 5 & 8:
    // Manufacturer & Company registration keep distinct roles and share PHARMACEUTICAL_ORGANIZATION;
    // Dual-role workspace switching between Manufacturer & Company is preserved.
    // -------------------------------------------------------------------------
    @Test
    fun manufacturerAndCompanyRegistration_sharePharmaOrg_andDualRoleWorkspaceSwitchingWorks() = runBlocking {
        val mfgRes = repository.postApiAuthRegister(
            fullName = "Meera Joshi",
            businessName = "Cipla BioLabs",
            email = "meera.mfg@ciplabiolabs.test",
            phone = "9811100001",
            gstin = "27AABCC1234F1Z5",
            address = "Vikhroli, Mumbai",
            password = "Password@123",
            role = UserRole.MANUFACTURER
        )
        assertTrue(mfgRes.isSuccess)
        val mfg = mfgRes.getOrThrow()
        assertEquals(UserRole.MANUFACTURER, mfg.role)
        assertEquals(OrganizationType.PHARMACEUTICAL_ORGANIZATION, mfg.organizationType)
        assertEquals(setOf(WorkspaceType.MANUFACTURER), mfg.authorizedWorkspaces().toSet())

        repository.logout()

        val compRes = repository.postApiAuthRegister(
            fullName = "Vikram Nair",
            businessName = "Cipla BioLabs",
            email = "vikram.company@ciplabiolabs.test",
            phone = "9811100002",
            gstin = "27AABCC1234F1Z5",
            address = "Vikhroli, Mumbai",
            password = "Password@123",
            role = UserRole.PHARMA_COMPANY
        )
        assertTrue(compRes.isSuccess)
        val comp = compRes.getOrThrow()
        assertEquals(UserRole.PHARMA_COMPANY, comp.role)
        assertEquals(OrganizationType.PHARMACEUTICAL_ORGANIZATION, comp.organizationType)
        assertEquals("Company and Manufacturer of same enterprise must share organizationId", mfg.organizationId, comp.organizationId)
        assertEquals(setOf(WorkspaceType.COMPANY), comp.authorizedWorkspaces().toSet())

        // Verify dual-role Manufacturer + Company account can switch between both workspaces
        val dualEntity = db.userDao().getUserById(comp.id)!!.copy(
            assignedRolesCsv = "MANUFACTURER,PHARMA_COMPANY",
            permissionsCsv = com.example.domain.model.PharmaPermission.toCsv(
                com.example.domain.model.RolePermissionMatrix.manufacturerPermissions +
                    com.example.domain.model.RolePermissionMatrix.companyPermissions
            )
        )
        db.userDao().updateUser(dualEntity)
        val dualUser = repository.getApiUsersMe()!!
        assertEquals(setOf(WorkspaceType.MANUFACTURER, WorkspaceType.COMPANY), dualUser.authorizedWorkspaces().toSet())

        val switchToMfg = workspaceRepository.switchActiveWorkspace(WorkspaceType.MANUFACTURER)
        assertTrue(switchToMfg.isSuccess)
        assertEquals(WorkspaceType.MANUFACTURER, workspaceRepository.getActiveWorkspaceFlow().first())

        val switchToComp = workspaceRepository.switchActiveWorkspace(WorkspaceType.COMPANY)
        assertTrue(switchToComp.isSuccess)
        assertEquals(WorkspaceType.COMPANY, workspaceRepository.getActiveWorkspaceFlow().first())
    }

    // -------------------------------------------------------------------------
    // Scenarios 6 & 7:
    // Per-account onboarding isolation (second Stockist does NOT inherit first Stockist's compliance)
    // and returning verified Stockist skips Welcome & Compliance directly to Dashboard.
    // -------------------------------------------------------------------------
    @Test
    fun perAccountOnboardingIsolation_andReturningVerifiedStockistSkipsWelcomeAndCompliance() = runBlocking {
        // Account 1 registers and completes compliance
        val stockist1 = repository.postApiAuthRegister(
            fullName = "Stockist One",
            businessName = "One Med Distributors",
            email = "stockist1@pharmaos.test",
            phone = "9822200001",
            gstin = "27AAACO1111F1Z5",
            address = "Pune",
            password = "Password@123",
            role = UserRole.STOCKIST
        ).getOrThrow()
        assertTrue(
            repository.saveComplianceVerification(
                userId = stockist1.id,
                gstin = "27AAACO1111F1Z5",
                drugLicenseNumber = "20B/MH/111111",
                drugLicenseIssueDate = "01/01/2024",
                drugLicenseExpiryDate = "31/12/2028",
                drugLicenseDocUri = null,
                whoGmpCertificateNumber = null,
                whoGmpIssueDate = null,
                whoGmpExpiryDate = null,
                whoGmpDocUri = null,
                isWhoGmpApplicable = false
            ).isSuccess
        )
        assertTrue(repository.resolveSessionRestoration() is SessionRestorationResult.AuthorizedWorkspace)

        // Logout -> Welcome stays completed -> routes to Login (not Welcome)
        repository.logout()
        assertTrue(repository.isWelcomeCompleted())
        assertTrue(repository.resolveSessionRestoration() is SessionRestorationResult.Login)

        // Account 2 registers -> MUST NOT inherit Account 1's compliance completion!
        val stockist2 = repository.postApiAuthRegister(
            fullName = "Stockist Two",
            businessName = "Two Med Distributors",
            email = "stockist2@pharmaos.test",
            phone = "9822200002",
            gstin = "27AAACT2222F1Z5",
            address = "Nagpur",
            password = "Password@123",
            role = UserRole.STOCKIST
        ).getOrThrow()
        assertTrue("Second account must require its own compliance verification", repository.requiresComplianceForUser(stockist2))
        assertTrue(repository.resolveSessionRestoration() is SessionRestorationResult.ComplianceRequired)

        // Logout and log back in as Account 1 (already verified) -> goes straight to AuthorizedWorkspace
        repository.logout()
        val loginRes = repository.postApiAuthLogin(
            email = "stockist1@pharmaos.test",
            password = "Password@123",
            expectedRole = UserRole.STOCKIST
        )
        assertTrue(loginRes.isSuccess)
        assertFalse(repository.requiresComplianceForUser(loginRes.getOrThrow()))
        assertTrue(repository.resolveSessionRestoration() is SessionRestorationResult.AuthorizedWorkspace)
    }

    // -------------------------------------------------------------------------
    // Scenarios 9, 10, 11 & Task 3:
    // Duplicate email, duplicate phone, invalid input, and Admin self-registration
    // fail cleanly with isLoading = false and no crash.
    // -------------------------------------------------------------------------
    @Test
    fun registrationExceptions_duplicateEmailPhoneInvalidInputsAndAdminSelfReg_handledSafely() {
        val authVm = AuthViewModel(repository)
        idleMainLooperAndCoroutines(3)

        // 1. Admin self-registration blocked
        authVm.register(
            fullName = "Admin Attempt",
            businessName = "Rogue Admin",
            email = "rogue.admin@pharmaos.test",
            phone = "9833300001",
            gstin = null,
            password = "Password@123",
            role = UserRole.ADMIN,
            onSuccess = { throw AssertionError("Admin self-registration must not succeed") }
        )
        assertFalse(authVm.uiState.value.isLoading)
        assertTrue(authVm.uiState.value.errorMessage?.contains("Administrator", ignoreCase = true) == true)

        // 2. Invalid short password
        authVm.register(
            fullName = "Short Pass",
            businessName = "Test Meds",
            email = "valid@pharmaos.test",
            phone = "9833300002",
            gstin = null,
            password = "123",
            role = UserRole.STOCKIST,
            onSuccess = { throw AssertionError("Short password must fail validation") }
        )
        assertFalse(authVm.uiState.value.isLoading)
        assertTrue(authVm.uiState.value.errorMessage?.contains("8 characters", ignoreCase = true) == true)

        // 3. Valid registration succeeds
        var registeredUser: AuthUser? = null
        authVm.register(
            fullName = "Primary Stockist",
            businessName = "Primary Meds",
            email = "primary@pharmaos.test",
            phone = "9833300003",
            gstin = "27AAACP3333F1Z5",
            password = "Password@123",
            role = UserRole.STOCKIST,
            onSuccess = { registeredUser = it }
        )
        waitUntil { registeredUser != null || (!authVm.uiState.value.isLoading && authVm.uiState.value.errorMessage != null) }
        assertNotNull("Expected registration to succeed, error=${authVm.uiState.value.errorMessage}", registeredUser)
        assertEquals(UserRole.STOCKIST, registeredUser!!.role)
        assertFalse(authVm.uiState.value.isLoading)

        // 4. Duplicate email fails cleanly
        authVm.clearMessages()
        authVm.register(
            fullName = "Dup Email",
            businessName = "Other Meds",
            email = "primary@pharmaos.test",
            phone = "9833300099",
            gstin = null,
            password = "Password@123",
            role = UserRole.STOCKIST,
            onSuccess = { throw AssertionError("Duplicate email must fail") }
        )
        waitUntil { !authVm.uiState.value.isLoading && authVm.uiState.value.errorMessage != null }
        assertFalse(authVm.uiState.value.isLoading)
        assertTrue(authVm.uiState.value.errorMessage?.contains("already exists", ignoreCase = true) == true)

        // 5. Duplicate phone fails cleanly
        authVm.clearMessages()
        authVm.register(
            fullName = "Dup Phone",
            businessName = "Other Meds 2",
            email = "unique.email@pharmaos.test",
            phone = "9833300003",
            gstin = null,
            password = "Password@123",
            role = UserRole.STOCKIST,
            onSuccess = { throw AssertionError("Duplicate phone must fail") }
        )
        waitUntil { !authVm.uiState.value.isLoading && authVm.uiState.value.errorMessage != null }
        assertFalse(authVm.uiState.value.isLoading)
        assertTrue(authVm.uiState.value.errorMessage?.contains("mobile number", ignoreCase = true) == true)
    }

    // -------------------------------------------------------------------------
    // Scenario 12:
    // Production mode registration when backend is unavailable fails cleanly with NO partial session.
    // -------------------------------------------------------------------------
    @Test
    fun productionModeRegistration_whenBackendUnavailable_leavesNoPartialSession() = runBlocking {
        dataStoreManager.setAuthExecutionMode(AuthExecutionMode.PRODUCTION_BACKEND_HTTP)
        val prodRepo = PharmaRepositoryImpl(db = db, dataStoreManager = dataStoreManager)

        val regRes = prodRepo.postApiAuthRegister(
            fullName = "Prod Stockist",
            businessName = "Prod Pharmacy",
            email = "prod.stockist@pharmaos.test",
            phone = "9844400001",
            gstin = "27AAACP4444F1Z5",
            address = "Mumbai",
            password = "Password@123",
            role = UserRole.STOCKIST
        )
        assertTrue("Production registration without backend must fail cleanly", regRes.isFailure)
        assertNull("No partial authenticated session may be persisted on failure", prodRepo.getApiUsersMe())
    }

    // -------------------------------------------------------------------------
    // Scenario 14:
    // Empty Stockist dashboard (0 products, 0 banners, 0 orders, 0 inventory, 0 basket) loads cleanly.
    // -------------------------------------------------------------------------
    @Test
    fun emptyStockistDashboard_loadsCleanlyWithZeroDataAndNoCrash() {
        runBlocking {
            val stockist = repository.postApiAuthRegister(
                fullName = "Empty State User",
                businessName = "Fresh Stockist",
                email = "empty.stockist@pharmaos.test",
                phone = "9855500001",
                gstin = "27AAACE5555F1Z5",
                address = "Mumbai",
                password = "Password@123",
                role = UserRole.STOCKIST
            ).getOrThrow()
            repository.saveComplianceVerification(
                userId = stockist.id,
                gstin = "27AAACE5555F1Z5",
                drugLicenseNumber = "20B/MH/555555",
                drugLicenseIssueDate = "01/01/2024",
                drugLicenseExpiryDate = "31/12/2028",
                drugLicenseDocUri = null,
                whoGmpCertificateNumber = null,
                whoGmpIssueDate = null,
                whoGmpExpiryDate = null,
                whoGmpDocUri = null,
                isWhoGmpApplicable = false
            )
        }
        val vm = DashboardViewModel(repository = repository, workspaceRepository = workspaceRepository)
        idleMainLooperAndCoroutines(6)

        val state = vm.uiState.value
        assertEquals(UserRole.STOCKIST, state.currentUser?.role)
        assertTrue(state.activeOfferBanners.isEmpty())
        assertTrue(state.publishedAnnouncements.isEmpty())
        assertTrue(state.pendingOrdersSummary.pendingOrders.isEmpty())
        assertEquals(0, state.cartItemCount)
        assertNull(state.errorMessage)
    }

    // -------------------------------------------------------------------------
    // Scenarios 15, 16 & 17:
    // Stale userId, missing Production tokens, and unauthorized/suspended accounts
    // recover safely without crash loops or wiping database records.
    // -------------------------------------------------------------------------
    @Test
    fun sessionRestoration_handlesStaleUserId_missingProdTokens_andUnauthorizedAccountSafely() = runBlocking {
        // Seed a valid user in DB to prove recovery NEVER wipes DB records
        val stockist = repository.postApiAuthRegister(
            fullName = "Preserved Stockist",
            businessName = "Preserved Meds",
            email = "preserved@pharmaos.test",
            phone = "9866600001",
            gstin = "27AAACP6666F1Z5",
            address = "Mumbai",
            password = "Password@123",
            role = UserRole.STOCKIST
        ).getOrThrow()
        repository.setWelcomeCompleted(true)

        // 15. Stale / nonexistent userId in DataStore -> clears only auth pointer, returns Login, preserves DB user!
        dataStoreManager.setLoggedInUserId(999999L)
        val staleResult = repository.resolveSessionRestoration()
        assertTrue("Expected Login for stale userId, got $staleResult", staleResult is SessionRestorationResult.Login)
        assertNotNull("DB records must never be wiped during session recovery", db.userDao().getUserById(stockist.id))

        // 16. Production mode with saved userId but missing/corrupted encrypted tokens -> returns Login, preserves DB!
        dataStoreManager.setAuthExecutionMode(AuthExecutionMode.PRODUCTION_BACKEND_HTTP)
        dataStoreManager.setLoggedInUserId(stockist.id)
        testTokenStorage.saveTokens(null, null)
        val missingTokenResult = repository.resolveSessionRestoration()
        assertTrue("Expected Login when production tokens missing, got $missingTokenResult", missingTokenResult is SessionRestorationResult.Login)
        assertNotNull("DB records must remain intact after missing-token recovery", db.userDao().getUserById(stockist.id))

        // 17. Suspended / zero-permission account -> returns UnauthorizedAccount (never AuthorizedWorkspace or bounce loop)
        dataStoreManager.setAuthExecutionMode(AuthExecutionMode.DEVELOPMENT_LOCAL_MODE)
        val suspendedEntity = db.userDao().getUserById(stockist.id)!!.copy(
            status = AccountStatus.SUSPENDED.name,
            permissionsCsv = ""
        )
        db.userDao().updateUser(suspendedEntity)
        dataStoreManager.setLoggedInUserId(stockist.id)
        val unauthorizedResult = repository.resolveSessionRestoration()
        assertTrue(
            "Expected UnauthorizedAccount for suspended/zero-permission user, got $unauthorizedResult",
            unauthorizedResult is SessionRestorationResult.UnauthorizedAccount
        )
    }

    // -------------------------------------------------------------------------
    // Scenario 18:
    // ComplianceViewModel rejects unauthenticated submission (no userId=1L fallback)
    // and blocks production demo autofill / fake client GSTIN verification.
    // -------------------------------------------------------------------------
    @Test
    fun complianceViewModel_rejectsUnauthenticatedSubmit_andBlocksProductionDemoAutofill() {
        runBlocking {
            dataStoreManager.clearAuthSession()
            dataStoreManager.setAuthExecutionMode(AuthExecutionMode.PRODUCTION_BACKEND_HTTP)
        }
        val vm = ComplianceViewModel(repository)
        idleMainLooperAndCoroutines(5)

        // Demo autofill blocked in Production mode
        vm.scanGstinDemo()
        assertEquals("", vm.uiState.value.gstin)
        assertTrue(vm.uiState.value.errorMessage?.contains("Development Local Mode", ignoreCase = true) == true)

        // GSTIN format check in Production mode NEVER marks isGstinVerified = true on client
        vm.setGstin("27AABCK1234F1Z5")
        vm.verifyGstin()
        assertTrue(vm.uiState.value.isGstinFormatValid)
        assertFalse("Production mode must not fake client-side GSTIN verification", vm.uiState.value.isGstinVerified)

        // Populate valid fields and attempt submission while unauthenticated -> MUST reject, never use userId = 1L!
        vm.updateDrugLicenseDetails("20B/MH/998877", "01/01/2024", "31/12/2028")
        vm.setWhoGmpApplicable(false)
        var callbackInvoked = false
        vm.submitCompliance { callbackInvoked = true }
        idleMainLooperAndCoroutines(5)

        assertFalse("Unauthenticated compliance submission must not succeed", callbackInvoked)
        assertFalse(vm.uiState.value.isCompleted)
        assertTrue(vm.uiState.value.errorMessage?.contains("session is not active", ignoreCase = true) == true)
        runBlocking {
            assertNull("No record may be written under fallback userId = 1L", db.complianceDao().getComplianceByUserId(1L))
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 19:
    // Fresh v6 database creation and existing v6 database reopen without destructive fallback.
    // (Upgrade 5->6 is verified in RoomMigration5To6Test.)
    // -------------------------------------------------------------------------
    @Test
    fun freshV6Database_andReopenExistingV6Database_workWithoutDestructiveFallback() = runBlocking {
        val dbName = "pharmaos_v6_fresh_reopen_test.db"
        context.deleteDatabase(dbName)
        try {
            val firstOpen = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(*AppDatabaseMigrations.ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            val insertedId = firstOpen.userDao().insertUser(
                UserEntity(
                    fullName = "V6 Stockist",
                    businessName = "V6 Pharma",
                    email = "v6.stockist@pharmaos.test",
                    phone = "9877700001",
                    gstin = "27AAACV7777F1Z5",
                    address = "Mumbai",
                    passwordHash = "hash",
                    passwordSalt = "salt",
                    role = UserRole.STOCKIST.name,
                    status = AccountStatus.ACTIVE.name,
                    organizationId = 10L,
                    organizationName = "V6 Pharma",
                    organizationType = OrganizationType.STOCKIST.name,
                    assignedRolesCsv = UserRole.STOCKIST.name,
                    permissionsCsv = com.example.domain.model.PharmaPermission.toCsv(
                        com.example.domain.model.RolePermissionMatrix.stockistPermissions
                    )
                )
            )
            firstOpen.close()

            val reopened = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(*AppDatabaseMigrations.ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            val loaded = reopened.userDao().getUserById(insertedId)
            assertNotNull(loaded)
            assertEquals("v6.stockist@pharmaos.test", loaded!!.email)
            reopened.close()
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 20:
    // Light mode, Dark mode, and large accessibility font scale (1.5x) rendering
    // for Register, Compliance, Stockist Dashboard, and Recovery/Authorization screens.
    // -------------------------------------------------------------------------
    @Test
    fun lightDarkAndLargeFontScale_renderKeyAuthComplianceDashboardAndRecoveryScreens() {
        val sampleStockist = AuthUser(
            id = 42L,
            fullName = "Kiran Deshmukh",
            businessName = "Kiran Pharma Distributors",
            email = "kiran@pharmaos.test",
            phone = "9820011223",
            gstin = "27AABCK1234F1Z5",
            address = "Mumbai",
            role = UserRole.STOCKIST,
            status = AccountStatus.ACTIVE,
            organizationId = 10L,
            organizationName = "Kiran Pharma Distributors",
            organizationType = OrganizationType.STOCKIST,
            assignedRoles = setOf(UserRole.STOCKIST),
            permissions = com.example.domain.model.RolePermissionMatrix.stockistPermissions
        )

        val screenIndex = androidx.compose.runtime.mutableIntStateOf(0)

        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 1.5f)
            ) {
                when (screenIndex.intValue) {
                    0 -> PharmaOSTheme(darkTheme = true) {
                        StartupSessionRecoveryScreen(
                            title = "Unable to Restore Session",
                            message = "Storage read failed temporarily.",
                            onRetry = {},
                            onReturnToLogin = {}
                        )
                    }
                    1 -> PharmaOSTheme(darkTheme = false) {
                        AccountAuthorizationRequiredScreen(
                            user = sampleStockist,
                            isChecking = false,
                            onRetryVerification = {},
                            onSignOut = {}
                        )
                    }
                    2 -> PharmaOSTheme(darkTheme = false) {
                        RegisterScreenContent(
                            uiState = AuthUiState(isFirstUser = true),
                            onRoleSelected = {},
                            onRegister = { _, _, _, _, _, _, _, _, _, _ -> },
                            onGoogleSignIn = { _, _, _, _ -> },
                            onClearMessages = {},
                            onNavigateBack = {},
                            onRegisterSuccess = {}
                        )
                    }
                    else -> PharmaOSTheme(darkTheme = true) {
                        DashboardScreenContent(
                            uiState = DashboardUiState(
                                isLoading = false,
                                currentUser = sampleStockist,
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
            }
        }

        // 0. Dark Mode + 1.5x font scale: StartupSessionRecoveryScreen
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("startup_error_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("startup_retry_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("startup_return_to_login_button").assertIsDisplayed()

        // 1. Light Mode + 1.5x font scale: AccountAuthorizationRequiredScreen
        composeTestRule.runOnUiThread { screenIndex.intValue = 1 }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("startup_unauthorized_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unauthorized_retry_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unauthorized_signout_button").assertIsDisplayed()

        // 2. Light Mode + 1.5x font scale: RegisterScreen (Stockist/Company/Manufacturer tabs only; NO Admin tab!)
        composeTestRule.runOnUiThread { screenIndex.intValue = 2 }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("register_role_stockist").assertIsDisplayed()
        composeTestRule.onNodeWithTag("register_role_company").assertIsDisplayed()
        composeTestRule.onNodeWithTag("register_role_manufacturer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("register_role_admin").assertDoesNotExist()

        // 3. Dark Mode + 1.5x font scale: Stockist DashboardScreen
        composeTestRule.runOnUiThread { screenIndex.intValue = 3 }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_greeting_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_role_pill").assertIsDisplayed()
    }
}

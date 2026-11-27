package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.local.entity.UserEntity
import com.example.data.remote.config.ApiConfig
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.remote.interceptor.TokenProvider
import com.example.data.remote.source.AuthRemoteDataSource
import com.example.data.remote.source.PharmaRemoteDataSource
import com.example.data.repository.PharmaRepositoryImpl
import com.example.di.NetworkModule
import com.example.domain.model.AccountStatus
import com.example.domain.model.ContactVerificationChannel
import com.example.domain.model.UserAddress
import com.example.domain.model.UserRole
import com.example.ui.screens.settings.ProfileSettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.theme.PharmaOSTheme
import com.example.util.PasswordHasher
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class ProfileSettingsRevisionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var localRepository: PharmaRepositoryImpl

    @Before
    fun setup() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            dataStoreManager = DataStoreManager(
                context = context,
                secureTokenStorage = com.example.data.local.SecureTokenStorage.createForUnitTest(
                    context,
                    prefsFileName = "test_profile_settings_tokens_${System.nanoTime()}"
                )
            )
            dataStoreManager.clearAuthSession()
            dataStoreManager.setOnboardingCompleted(true)
            dataStoreManager.setAuthExecutionMode(AuthExecutionMode.DEVELOPMENT_LOCAL_MODE)
            dataStoreManager.setBackendBaseUrl(ApiConfig.PLACEHOLDER_UNCONFIGURED_BASE_URL)
            localRepository = PharmaRepositoryImpl(db = db, dataStoreManager = dataStoreManager)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            dataStoreManager.clearAuthSession()
            dataStoreManager.setAuthExecutionMode(AuthExecutionMode.DEVELOPMENT_LOCAL_MODE)
            dataStoreManager.setBackendBaseUrl(ApiConfig.PLACEHOLDER_UNCONFIGURED_BASE_URL)
            db.close()
        }
    }

    private fun waitForUiCondition(timeoutMs: Long = 5000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) {
                break
            }
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out after ${timeoutMs}ms waiting for UI condition")
            }
            Thread.sleep(25)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
    }

    private suspend fun seedAndLoginUser(
        fullName: String = "Ananya Verma",
        businessName: String = "Verma Care Pharmacy",
        email: String = "ananya@vermacare.in",
        phone: String = "9820112233",
        role: UserRole = UserRole.STOCKIST,
        emailVerified: Boolean = false,
        phoneVerified: Boolean = false
    ): Long {
        val hash = PasswordHasher.hashPassword("Password@123")
        val id = db.userDao().insertUser(
            UserEntity(
                fullName = fullName,
                businessName = businessName,
                email = email,
                phone = phone,
                gstin = "27AABCV1234F1Z5",
                address = "Unit 12, Dadar East, Mumbai",
                passwordHash = hash.hashHex,
                passwordSalt = hash.saltHex,
                role = role.name,
                status = AccountStatus.ACTIVE.name,
                emailVerified = emailVerified,
                phoneVerified = phoneVerified,
                profileBio = "Retail & hospital supply pharmacy"
            )
        )
        dataStoreManager.setAuthTokens("session-token-$id", null)
        dataStoreManager.setLoggedInUserId(id)
        return id
    }

    private fun buildConnectedRepository(interceptor: Interceptor): PharmaRepositoryImpl {
        val module = NetworkModule(
            tokenProvider = object : TokenProvider {
                override fun getAccessToken(): String =
                    runBlocking { dataStoreManager.getAccessToken() ?: "jwt-token" }
            },
            initialBaseUrl = ApiConfig.EMULATOR_DEFAULT_BASE_URL,
            extraInterceptors = listOf(interceptor)
        )
        return PharmaRepositoryImpl(
            db = db,
            dataStoreManager = dataStoreManager,
            authRemoteDataSource = AuthRemoteDataSource(module),
            pharmaRemoteDataSource = PharmaRemoteDataSource(module),
            networkModule = module
        )
    }

    @Test
    fun updateProfile_validatesAndPersistsChanges_andKeepsEditedContactsUnverified() {
        val userId = runBlocking {
            seedAndLoginUser(emailVerified = true, phoneVerified = true)
        }
        val viewModel = SettingsViewModel(localRepository, dataStoreManager)

        composeTestRule.setContent {
            PharmaOSTheme {
                ProfileSettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onNavigateToSupplierSettings = {},
                    onNavigateToCsv = {},
                    onNavigateToAdminUsers = {},
                    onLoggedOut = {}
                )
            }
        }

        waitForUiCondition {
            viewModel.uiState.value.currentUser?.id == userId
        }

        composeTestRule.onNodeWithTag("profile_full_name_text").assertTextEquals("Ananya Verma")
        composeTestRule.onNodeWithTag("profile_business_name_text").assertTextEquals("Verma Care Pharmacy")
        composeTestRule.onNodeWithTag("profile_role_badge").assertTextContains("Stockist", substring = true)

        // Open Edit Profile dialog
        composeTestRule.onNodeWithTag("edit_profile_button").performScrollTo().performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("edit_profile_dialog").fetchSemanticsNodes().isNotEmpty()
        }

        // Enter invalid email first to confirm inline validation blocks saving
        composeTestRule.onNodeWithTag("profile_input_email").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("profile_input_email").performTextInput("invalid-email")
        composeTestRule.onNodeWithTag("save_profile_button").performClick()

        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("profile_validation_error").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("profile_validation_error").assertIsDisplayed()

        // Fill valid updated profile fields
        composeTestRule.onNodeWithTag("profile_input_full_name").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("profile_input_full_name").performTextInput("Dr. Ananya Deshmukh")

        composeTestRule.onNodeWithTag("profile_input_business_name").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("profile_input_business_name").performTextInput("Deshmukh MedPlus Distributors")

        composeTestRule.onNodeWithTag("profile_input_email").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("profile_input_email").performTextInput("ananya.new@deshmukhpharma.in")

        composeTestRule.onNodeWithTag("profile_input_phone").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("profile_input_phone").performTextInput("9890554433")

        composeTestRule.onNodeWithTag("profile_input_bio").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("profile_input_bio").performTextInput("Cold-chain certified stockist")

        composeTestRule.onNodeWithTag("save_profile_button").performClick()

        // Wait for dialog dismissal and live reactive refresh
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("edit_profile_dialog").fetchSemanticsNodes().isEmpty() &&
                viewModel.uiState.value.currentUser?.fullName == "Dr. Ananya Deshmukh"
        }

        composeTestRule.onNodeWithTag("profile_full_name_text").performScrollTo()
            .assertTextEquals("Dr. Ananya Deshmukh")
        composeTestRule.onNodeWithTag("profile_business_name_text")
            .assertTextEquals("Deshmukh MedPlus Distributors")
        composeTestRule.onNodeWithTag("profile_bio_text")
            .assertTextEquals("Cold-chain certified stockist")

        composeTestRule.onNodeWithTag("profile_email_text").performScrollTo()
            .assertTextEquals("ananya.new@deshmukhpharma.in")
        composeTestRule.onNodeWithTag("profile_phone_text")
            .assertTextEquals("9890554433")

        // Editing email/phone must honestly clear verified state to Unverified
        composeTestRule.onNodeWithTag("email_verification_status").assertTextEquals("Unverified")
        composeTestRule.onNodeWithTag("phone_verification_status").assertTextEquals("Unverified")

        val persisted = runBlocking { db.userDao().getUserById(userId) }
        assertNotNull(persisted)
        assertEquals("Dr. Ananya Deshmukh", persisted!!.fullName)
        assertEquals("Deshmukh MedPlus Distributors", persisted.businessName)
        assertEquals("ananya.new@deshmukhpharma.in", persisted.email)
        assertEquals("9890554433", persisted.phone)
        assertEquals("Cold-chain certified stockist", persisted.profileBio)
        assertFalse(persisted.emailVerified)
        assertFalse(persisted.phoneVerified)
    }

    @Test
    fun addEditSetDefaultAndDeleteAddress_endToEndUiAndRoomPersistence() {
        val userId = runBlocking { seedAndLoginUser() }
        val viewModel = SettingsViewModel(localRepository, dataStoreManager)

        composeTestRule.setContent {
            PharmaOSTheme {
                ProfileSettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onNavigateToSupplierSettings = {},
                    onNavigateToCsv = {},
                    onNavigateToAdminUsers = {},
                    onLoggedOut = {}
                )
            }
        }

        waitForUiCondition { viewModel.uiState.value.currentUser?.id == userId }

        // Initially empty addresses state
        composeTestRule.onNodeWithTag("empty_addresses_card").performScrollTo().assertIsDisplayed()

        // 1. ADD FIRST ADDRESS via UI form (all 9 fields)
        composeTestRule.onNodeWithTag("add_address_button").performScrollTo().performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("address_form_dialog").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("address_input_name").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("address_input_name").performTextInput("Main Pharmacy")
        composeTestRule.onNodeWithTag("address_input_contact_person").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("address_input_contact_person").performTextInput("Ananya Verma")
        composeTestRule.onNodeWithTag("address_input_phone").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("address_input_phone").performTextInput("9820112233")
        composeTestRule.onNodeWithTag("address_input_line1").performScrollTo().performTextInput("Shop 4, Health Plaza")
        composeTestRule.onNodeWithTag("address_input_line2").performScrollTo().performTextInput("Near Civil Hospital")
        composeTestRule.onNodeWithTag("address_input_city").performScrollTo().performTextInput("Pune")
        composeTestRule.onNodeWithTag("address_input_state").performScrollTo().performTextInput("Maharashtra")
        composeTestRule.onNodeWithTag("address_input_postal_code").performScrollTo().performTextInput("411001")
        composeTestRule.onNodeWithTag("address_input_country").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("address_input_country").performTextInput("India")

        composeTestRule.onNodeWithTag("save_address_button").performClick()

        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("address_form_dialog").fetchSemanticsNodes().isEmpty() &&
                viewModel.uiState.value.addresses.size == 1
        }

        val firstAddress = viewModel.uiState.value.addresses.single()
        assertEquals("Main Pharmacy", firstAddress.addressName)
        assertEquals("Shop 4, Health Plaza", firstAddress.addressLine1)
        assertEquals("Near Civil Hospital", firstAddress.addressLine2)
        assertEquals("Pune", firstAddress.city)
        assertEquals("Maharashtra", firstAddress.state)
        assertEquals("411001", firstAddress.postalCode)
        assertEquals("India", firstAddress.country)
        assertTrue("First address should automatically become default", firstAddress.isDefault)

        // Scroll further down so the address card sits well above the bottom SnackbarHost
        composeTestRule.onNodeWithTag("dark_theme_toggle").performScrollTo()
        composeTestRule.onNodeWithTag("address_card_${firstAddress.id}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("address_default_badge_${firstAddress.id}").assertIsDisplayed()

        // 2. EDIT ADDRESS via UI
        composeTestRule.onNodeWithTag("edit_address_${firstAddress.id}").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("address_form_dialog").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("address_input_name").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("address_input_name").performTextInput("Flagship Pharmacy")
        composeTestRule.onNodeWithTag("address_input_line1").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("address_input_line1").performTextInput("Suite 101, MedPark Tower")
        composeTestRule.onNodeWithTag("address_input_postal_code").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("address_input_postal_code").performTextInput("411004")

        composeTestRule.onNodeWithTag("save_address_button").performClick()

        waitForUiCondition {
            !viewModel.uiState.value.isSavingAddress &&
                viewModel.uiState.value.addresses.firstOrNull { it.id == firstAddress.id }?.addressName == "Flagship Pharmacy"
        }

        val editedInDb = runBlocking { db.userAddressDao().getAddressById(firstAddress.id) }
        assertNotNull(editedInDb)
        assertEquals("Flagship Pharmacy", editedInDb!!.addressName)
        assertEquals("Suite 101, MedPark Tower", editedInDb.addressLine1)
        assertEquals("411004", editedInDb.postalCode)

        // 3. ADD SECOND ADDRESS & SET AS DEFAULT
        composeTestRule.onNodeWithTag("add_address_button").performScrollTo().performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("address_form_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("address_input_name").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("address_input_name").performTextInput("Cold Storage Warehouse")
        composeTestRule.onNodeWithTag("address_input_contact_person").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("address_input_contact_person").performTextInput("Ramesh Kulkarni")
        composeTestRule.onNodeWithTag("address_input_phone").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("address_input_phone").performTextInput("9822998877")
        composeTestRule.onNodeWithTag("address_input_line1").performScrollTo().performTextInput("Plot 88, MIDC Bhosari")
        composeTestRule.onNodeWithTag("address_input_city").performScrollTo().performTextInput("Pune")
        composeTestRule.onNodeWithTag("address_input_state").performScrollTo().performTextInput("Maharashtra")
        composeTestRule.onNodeWithTag("address_input_postal_code").performScrollTo().performTextInput("411026")
        composeTestRule.onNodeWithTag("save_address_button").performClick()

        waitForUiCondition {
            !viewModel.uiState.value.isSavingAddress &&
                viewModel.uiState.value.addresses.size == 2
        }

        val secondAddress = viewModel.uiState.value.addresses.first { it.id != firstAddress.id }
        assertFalse(secondAddress.isDefault)

        // Tap Set Default on second address (scroll dark_theme_toggle so buttons clear SnackbarHost)
        composeTestRule.onNodeWithTag("dark_theme_toggle").performScrollTo()
        composeTestRule.onNodeWithTag("set_default_address_${secondAddress.id}").performClick()
        waitForUiCondition {
            !viewModel.uiState.value.isSavingAddress &&
                viewModel.uiState.value.addresses.firstOrNull { it.id == secondAddress.id }?.isDefault == true
        }

        composeTestRule.onNodeWithTag("address_default_badge_${secondAddress.id}").assertIsDisplayed()

        val allInDbAfterDefault = runBlocking { db.userAddressDao().getAddressesForUser(userId) }
        assertTrue(allInDbAfterDefault.first { it.id == secondAddress.id }.isDefault)
        assertFalse(allInDbAfterDefault.first { it.id == firstAddress.id }.isDefault)

        // Primary user address synced to new default
        val updatedUserRow = runBlocking { db.userDao().getUserById(userId) }
        assertTrue(updatedUserRow?.address?.contains("Plot 88, MIDC Bhosari") == true)

        // 4. DELETE DEFAULT ADDRESS -> remaining address promoted to default
        composeTestRule.onNodeWithTag("dark_theme_toggle").performScrollTo()
        composeTestRule.onNodeWithTag("delete_address_${secondAddress.id}").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("delete_address_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("confirm_delete_address_button").performClick()

        waitForUiCondition {
            !viewModel.uiState.value.isSavingAddress &&
                viewModel.uiState.value.addresses.size == 1 &&
                viewModel.uiState.value.addresses.single().id == firstAddress.id
        }

        val remainingInDb = runBlocking { db.userAddressDao().getAddressesForUser(userId) }
        assertEquals(1, remainingInDb.size)
        assertEquals(firstAddress.id, remainingInDb.single().id)
        assertTrue("Remaining address must be promoted to default", remainingInDb.single().isDefault)
    }

    @Test
    fun openLegalDocuments_unconfiguredAndConfiguredBackendUrls_neverFabricatesApprovals() {
        val userId = runBlocking { seedAndLoginUser() }
        val viewModel = SettingsViewModel(localRepository, dataStoreManager)
        val openedUrls = mutableListOf<String>()

        composeTestRule.setContent {
            PharmaOSTheme {
                ProfileSettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onNavigateToSupplierSettings = {},
                    onNavigateToCsv = {},
                    onNavigateToAdminUsers = {},
                    onLoggedOut = {},
                    onOpenExternalLegalUrl = { openedUrls.add(it) }
                )
            }
        }

        waitForUiCondition { viewModel.uiState.value.currentUser?.id == userId }

        // All 6 canonical legal documents must be listed
        val expectedIds = listOf(
            "terms_and_conditions",
            "privacy_policy",
            "refund_cancellation",
            "shipping_delivery",
            "disclaimer",
            "regulatory_compliance"
        )
        expectedIds.forEach { docId ->
            composeTestRule.onNodeWithTag("legal_doc_$docId").performScrollTo().assertIsDisplayed()
        }

        // With unconfigured backend URL, clicking Terms & Conditions shows honest unconfigured notice
        composeTestRule.onNodeWithTag("legal_doc_terms_and_conditions").performScrollTo().performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("legal_document_viewer").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("legal_viewer_title").assertTextEquals("Terms and Conditions")
        composeTestRule.onNodeWithTag("legal_viewer_unconfigured_notice").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("legal_doc_open_url_button").fetchSemanticsNodes().isEmpty())

        composeTestRule.onNodeWithTag("legal_viewer_close_button").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("legal_document_viewer").fetchSemanticsNodes().isEmpty()
        }

        // Configure an HTTPS backend URL -> legal documents resolve to real hosted paths
        viewModel.configureBackendUrl("https://api.pharmaos.example.com")
        waitForUiCondition {
            viewModel.uiState.value.legalDocuments
                .firstOrNull { it.id == "privacy_policy" }
                ?.configuredUrl == "https://api.pharmaos.example.com/legal/privacy-policy"
        }

        composeTestRule.onNodeWithTag("legal_doc_privacy_policy").performScrollTo().performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("legal_document_viewer").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("legal_viewer_url")
            .assertTextEquals("https://api.pharmaos.example.com/legal/privacy-policy")
        composeTestRule.onNodeWithTag("legal_doc_open_url_button").performClick()

        assertEquals(
            listOf("https://api.pharmaos.example.com/legal/privacy-policy"),
            openedUrls
        )
    }

    @Test
    fun failedBackendApiRequests_doNotCorruptLocalCache_andSurfaceErrors() = runBlocking {
        val userId = seedAndLoginUser(
            fullName = "Original Name",
            businessName = "Original Pharmacy",
            email = "orig@pharmacy.in",
            phone = "9811112222",
            emailVerified = false,
            phoneVerified = false
        )
        dataStoreManager.setAuthExecutionMode(AuthExecutionMode.PRODUCTION_BACKEND_HTTP)
        dataStoreManager.setBackendBaseUrl(ApiConfig.EMULATOR_DEFAULT_BASE_URL)

        // Seed one cached address locally so we can verify failed PUT/DELETE/PATCH do not touch it
        val existingAddrId = db.userAddressDao().insertAddress(
            com.example.data.local.entity.UserAddressEntity(
                id = 90L,
                userId = userId,
                addressName = "Cached HQ",
                contactPerson = "Original Name",
                phone = "9811112222",
                addressLine1 = "10 Old Road",
                addressLine2 = "",
                city = "Mumbai",
                state = "Maharashtra",
                postalCode = "400001",
                country = "India",
                isDefault = true
            )
        )

        val failingRepo = buildConnectedRepository(Interceptor { chain ->
            val req = chain.request()
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Internal Server Error")
                .body("""{"success":false,"message":"Backend profile service unavailable"}""".toResponseBody("application/json".toMediaType()))
                .build()
        })

        // 1. Failed profile update must NOT overwrite Room user cache
        val profileFail = failingRepo.putApiUserProfile(
            fullName = "Should Not Save",
            businessName = "Corrupted Biz",
            email = "bad@save.in",
            phone = "9999999999",
            gstin = null,
            address = "Unit 12, Dadar East, Mumbai",
            profileBio = "Should not persist"
        )
        assertTrue(profileFail.isFailure)
        val cachedUser = db.userDao().getUserById(userId)!!
        assertEquals("Original Name", cachedUser.fullName)
        assertEquals("Original Pharmacy", cachedUser.businessName)
        assertEquals("orig@pharmacy.in", cachedUser.email)

        // 2. Failed address create must NOT insert into Room
        val createAddrFail = failingRepo.postApiUserAddress(
            UserAddress(
                userId = userId,
                addressName = "Ghost Warehouse",
                contactPerson = "Ghost",
                phone = "9800000000",
                addressLine1 = "99 Nowhere St",
                city = "Delhi",
                state = "Delhi",
                postalCode = "110001",
                country = "India",
                isDefault = false
            )
        )
        assertTrue(createAddrFail.isFailure)
        assertEquals(1, db.userAddressDao().getAddressesForUser(userId).size)

        // 3. Failed address delete must NOT delete from Room
        val deleteAddrFail = failingRepo.deleteApiUserAddress(existingAddrId)
        assertTrue(deleteAddrFail.isFailure)
        assertNotNull(db.userAddressDao().getAddressById(existingAddrId))

        // 4. Failed verification must NEVER mark email/phone verified
        val verifyFail = failingRepo.confirmContactVerification(
            channel = ContactVerificationChannel.EMAIL,
            value = "orig@pharmacy.in",
            code = "123456"
        )
        assertTrue(verifyFail.isFailure)
        assertFalse(db.userDao().getUserById(userId)!!.emailVerified)
    }

    @Test
    fun contactVerification_onlyMarksVerifiedAfterConfirmedBackendResponse() {
        val userId = runBlocking {
            seedAndLoginUser(
                fullName = "Kiran Patel",
                businessName = "Patel Medicos",
                email = "kiran@patelmedicos.in",
                phone = "9825012345",
                emailVerified = false,
                phoneVerified = false
            )
        }

        // First verify that in DEVELOPMENT_LOCAL_MODE, requesting verification fails honestly without faking verified state
        val localResult = runBlocking {
            localRepository.requestContactVerification(ContactVerificationChannel.EMAIL, "kiran@patelmedicos.in")
        }
        assertTrue(localResult.isFailure)
        assertFalse(runBlocking { db.userDao().getUserById(userId)!!.emailVerified })

        // Now connect to a live backend mock in PRODUCTION_BACKEND_HTTP mode
        runBlocking {
            dataStoreManager.setAuthExecutionMode(AuthExecutionMode.PRODUCTION_BACKEND_HTTP)
            dataStoreManager.setBackendBaseUrl(ApiConfig.EMULATOR_DEFAULT_BASE_URL)
        }

        val emailConfirmedOnServer = AtomicBoolean(false)
        val connectedRepo = buildConnectedRepository(Interceptor { chain ->
            val req = chain.request()
            val path = req.url.encodedPath
            when {
                path == "/api/v1/users/me/verify-contact/request" && req.method == "POST" -> {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """
                            {
                              "success": true,
                              "message": "Verification code sent to kiran@patelmedicos.in",
                              "data": {
                                "channel": "EMAIL",
                                "value": "kiran@patelmedicos.in",
                                "verified": false,
                                "message": "OTP dispatched to kiran@patelmedicos.in"
                              }
                            }
                            """.trimIndent().toResponseBody("application/json".toMediaType())
                        )
                        .build()
                }
                path == "/api/v1/users/me/verify-contact/confirm" && req.method == "POST" -> {
                    emailConfirmedOnServer.set(true)
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """
                            {
                              "success": true,
                              "message": "Email verified",
                              "data": {
                                "channel": "EMAIL",
                                "value": "kiran@patelmedicos.in",
                                "verified": true,
                                "message": "Confirmed"
                              }
                            }
                            """.trimIndent().toResponseBody("application/json".toMediaType())
                        )
                        .build()
                }
                path == "/api/v1/users/me" && req.method == "GET" -> {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """
                            {
                              "success": true,
                              "message": "OK",
                              "data": {
                                "id": $userId,
                                "fullName": "Kiran Patel",
                                "businessName": "Patel Medicos",
                                "email": "kiran@patelmedicos.in",
                                "phone": "9825012345",
                                "gstin": "27AABCV1234F1Z5",
                                "address": "Unit 12, Dadar East, Mumbai",
                                "role": "STOCKIST",
                                "status": "ACTIVE",
                                "emailVerified": ${emailConfirmedOnServer.get()},
                                "phoneVerified": false,
                                "profileBio": "Retail & hospital supply pharmacy"
                              }
                            }
                            """.trimIndent().toResponseBody("application/json".toMediaType())
                        )
                        .build()
                }
                path == "/api/v1/legal/documents" -> {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"success":true,"data":[]}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                else -> {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"success":true,"data":[]}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            }
        })

        val viewModel = SettingsViewModel(connectedRepo, dataStoreManager)

        composeTestRule.setContent {
            PharmaOSTheme {
                ProfileSettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onNavigateToSupplierSettings = {},
                    onNavigateToCsv = {},
                    onNavigateToAdminUsers = {},
                    onLoggedOut = {}
                )
            }
        }

        waitForUiCondition { viewModel.uiState.value.currentUser?.id == userId }

        composeTestRule.onNodeWithTag("email_verification_status").performScrollTo().assertTextEquals("Unverified")
        composeTestRule.onNodeWithTag("verify_email_button").performClick()

        waitForUiCondition {
            !viewModel.uiState.value.isVerifyingContact &&
                viewModel.uiState.value.pendingVerificationChannel == ContactVerificationChannel.EMAIL
        }
        composeTestRule.onNodeWithTag("contact_verification_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("verification_code_input").performTextInput("482910")
        composeTestRule.onNodeWithTag("confirm_verification_button").performClick()

        waitForUiCondition {
            !viewModel.uiState.value.isVerifyingContact &&
                viewModel.uiState.value.pendingVerificationChannel == null &&
                viewModel.uiState.value.currentUser?.emailVerified == true
        }

        composeTestRule.onNodeWithTag("email_verification_status").performScrollTo().assertTextEquals("Verified")
        composeTestRule.onNodeWithTag("phone_verification_status").assertTextEquals("Unverified")
        assertTrue(runBlocking { db.userDao().getUserById(userId)!!.emailVerified })
        assertFalse(runBlocking { db.userDao().getUserById(userId)!!.phoneVerified })
    }
}

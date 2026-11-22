package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.local.entity.BatchEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ProductInventoryEntity
import com.example.data.local.entity.UserEntity
import com.example.data.remote.config.ApiConfig
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.remote.interceptor.TokenProvider
import com.example.data.remote.source.AuthRemoteDataSource
import com.example.data.remote.source.PharmaRemoteDataSource
import com.example.data.repository.PharmaRepositoryImpl
import com.example.data.repository.ProductRepository
import com.example.di.NetworkModule
import com.example.domain.model.AccountStatus
import com.example.domain.model.DosageForm
import com.example.domain.model.StockLevelStatus
import com.example.domain.model.StorageCondition
import com.example.domain.model.UserRole
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.dashboard.DashboardViewModel
import com.example.ui.screens.inventory.BatchExpiryScreen
import com.example.ui.screens.inventory.InventoryDashboardScreen
import com.example.ui.screens.inventory.InventoryViewModel
import com.example.ui.screens.products.ProductListScreen
import com.example.ui.screens.products.ProductListViewModel
import com.example.ui.theme.PharmaOSTheme
import com.example.util.Formatters
import com.example.util.PasswordHasher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class RealTimeInventoryRbacTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var repository: PharmaRepositoryImpl
    private lateinit var productRepository: ProductRepository

    private val now = System.currentTimeMillis()
    private val mfgEpoch = now - 45L * 86400000L
    private val expiryHealthyEpoch = now + 240L * 86400000L
    private val expiryNearEpoch = now + 20L * 86400000L

    @Before
    fun setup() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            dataStoreManager = DataStoreManager(context)
            dataStoreManager.clearAuthSession()
            dataStoreManager.setOnboardingCompleted(true)
            dataStoreManager.setAuthExecutionMode(AuthExecutionMode.DEVELOPMENT_LOCAL_MODE)
            repository = PharmaRepositoryImpl(db = db, dataStoreManager = dataStoreManager)
            productRepository = ProductRepository(db.productDao(), db.productInventoryDao())
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            dataStoreManager.clearAuthSession()
            dataStoreManager.setAuthExecutionMode(AuthExecutionMode.DEVELOPMENT_LOCAL_MODE)
            db.close()
        }
    }

    private fun waitForUiCondition(timeoutMs: Long = 5000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.waitForIdle()
            if (predicate()) {
                composeTestRule.waitForIdle()
                return
            }
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out after ${timeoutMs}ms waiting for UI condition")
            }
            Thread.sleep(25)
        }
        composeTestRule.waitForIdle()
    }

    private suspend fun insertUser(
        fullName: String,
        businessName: String,
        email: String,
        role: UserRole,
        gstin: String
    ): Long {
        val hash = PasswordHasher.hashPassword("Password@123")
        return db.userDao().insertUser(
            UserEntity(
                fullName = fullName,
                businessName = businessName,
                email = email,
                phone = "9800000001",
                gstin = gstin,
                address = "Mumbai, MH",
                passwordHash = hash.hashHex,
                passwordSalt = hash.saltHex,
                role = role.name,
                status = AccountStatus.ACTIVE.name
            )
        )
    }

    private suspend fun insertProduct(
        name: String,
        brand: String,
        salt: String,
        strength: String,
        dosageForm: DosageForm,
        category: String,
        storage: StorageCondition,
        createdBy: Long
    ): Long {
        return db.productDao().insertProduct(
            ProductEntity(
                name = name,
                brand = brand,
                saltComposition = salt,
                strength = strength,
                dosageForm = dosageForm.name,
                therapeuticCategory = category,
                manufacturerName = brand,
                packSize = "10 Units",
                hsnCode = "30049099",
                gstRatePercent = 12.0,
                storageCondition = storage.name,
                isRx = true,
                isActive = true,
                createdBy = createdBy
            )
        )
    }

    private suspend fun loginSession(userId: Long, token: String) {
        dataStoreManager.setAuthTokens(token, null)
        dataStoreManager.setLoggedInUserId(userId)
    }

    private suspend fun seedUsersAndInventory(): Triple<Long, Long, Long> {
        val stockistId = insertUser(
            fullName = "Rohan Stockist",
            businessName = "Apex Medico Distributors",
            email = "stockist@apex.test",
            role = UserRole.STOCKIST,
            gstin = "27AAAAS0001A1Z5"
        )
        val brandId = insertUser(
            fullName = "Meera Brand Lead",
            businessName = "SunBrand Therapeutics",
            email = "brand@sunbrand.test",
            role = UserRole.PHARMA_COMPANY,
            gstin = "27AAAAB0002B1Z5"
        )
        val manufacturerId = insertUser(
            fullName = "Vikram Plant Head",
            businessName = "CiplaGen Manufacturing",
            email = "mfg@ciplagen.test",
            role = UserRole.MANUFACTURER,
            gstin = "27AAAAM0003C1Z5"
        )

        val prodBrandId = insertProduct(
            name = "BrandCef 500mg Tablet",
            brand = "SunBrand",
            salt = "Cefuroxime Axetil 500mg",
            strength = "500mg",
            dosageForm = DosageForm.TABLET,
            category = "Antibiotics",
            storage = StorageCondition.ROOM_TEMPERATURE,
            createdBy = brandId
        )
        val prodMfgId = insertProduct(
            name = "MfgInsulin 40IU Vial",
            brand = "CiplaGen",
            salt = "Human Insulin 40IU",
            strength = "40IU",
            dosageForm = DosageForm.INJECTION,
            category = "Diabetology",
            storage = StorageCondition.REFRIGERATED_2_8,
            createdBy = manufacturerId
        )

        db.batchDao().insertBatch(
            BatchEntity(
                id = 101L,
                productId = prodBrandId,
                supplierId = brandId,
                batchNumber = "BRN-LOT-901",
                mfgDateEpochMillis = mfgEpoch,
                expiryDateEpochMillis = expiryHealthyEpoch,
                initialQuantity = 200,
                remainingQuantity = 120,
                storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                mrp = 260.0,
                ptr = 195.0
            )
        )
        db.batchDao().insertBatch(
            BatchEntity(
                id = 202L,
                productId = prodMfgId,
                supplierId = manufacturerId,
                batchNumber = "MFG-LOT-777",
                mfgDateEpochMillis = mfgEpoch,
                expiryDateEpochMillis = expiryNearEpoch,
                initialQuantity = 100,
                remainingQuantity = 15,
                storageCondition = StorageCondition.REFRIGERATED_2_8.name,
                mrp = 320.0,
                ptr = 240.0
            )
        )

        db.productInventoryDao().insertInventory(
            ProductInventoryEntity(
                sku = "SKU-SECRET-MFG-01",
                productName = "MfgInsulin 40IU Vial",
                currentStock = 15,
                reorderThreshold = 30,
                dosageForm = "Injection",
                packSize = "10ml Vial",
                unitPricePtr = 240.0,
                mrp = 320.0,
                productId = prodMfgId
            )
        )

        return Triple(stockistId, brandId, manufacturerId)
    }

    private fun buildConnectedRepository(interceptor: Interceptor): PharmaRepositoryImpl {
        val module = NetworkModule(
            tokenProvider = object : TokenProvider {
                override fun getAccessToken(): String = runBlocking { dataStoreManager.getAccessToken() ?: "jwt-token" }
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
    fun stockist_cannotAccessRepositoryInventory_orMutateManufacturerStock_orCallBackendApi() = runBlocking {
        val (stockistId, _, _) = seedUsersAndInventory()
        val backendCalls = AtomicInteger(0)

        val connectedRepo = buildConnectedRepository(Interceptor { chain ->
            backendCalls.incrementAndGet()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(403)
                .message("Forbidden")
                .body(
                    """{"status":403,"error":"Forbidden","message":"Stockists are not authorized for real-time inventory."}"""
                        .toResponseBody("application/json".toMediaType())
                )
                .build()
        })

        dataStoreManager.setAuthExecutionMode(AuthExecutionMode.PRODUCTION_BACKEND_HTTP)
        loginSession(stockistId, "stockist-jwt-token")

        // Read flows strictly emit empty for stockists
        assertTrue(connectedRepo.getRealTimeInventoryFlow().first().isEmpty())
        assertTrue(connectedRepo.getApiBatchesFlow().first().isEmpty())
        assertTrue(connectedRepo.getApiBatches().isEmpty())
        assertTrue(connectedRepo.getProductInventoryFlow().first().isEmpty())
        assertTrue(connectedRepo.getLowStockProductInventoryFlow().first().isEmpty())
        assertNull(connectedRepo.getProductInventoryBySku("SKU-SECRET-MFG-01"))

        // Refresh and write operations fail with 403 SecurityException and never leak unauthorized API calls
        val refreshResult = connectedRepo.refreshRealTimeInventory()
        assertTrue(refreshResult.isFailure)
        assertTrue(refreshResult.exceptionOrNull()?.message?.contains("403") == true)
        assertEquals("Client RBAC must block unauthorized stockist before hitting backend", 0, backendCalls.get())

        val adjustResult = connectedRepo.adjustBatchStock(202L, 999, "Unauthorized attempt")
        assertTrue(adjustResult.isFailure)
        assertEquals(15, db.batchDao().getBatchById(202L)?.remainingQuantity)

        val createResult = connectedRepo.postApiBatches(
            BatchEntity(
                productId = 1L,
                supplierId = stockistId,
                batchNumber = "ILLEGAL-LOT",
                mfgDateEpochMillis = mfgEpoch,
                expiryDateEpochMillis = expiryHealthyEpoch,
                initialQuantity = 50,
                remainingQuantity = 50,
                storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                mrp = 100.0,
                ptr = 80.0
            )
        )
        assertTrue(createResult.isFailure)

        val updateSkuResult = connectedRepo.updateProductInventoryStock("SKU-SECRET-MFG-01", 500)
        assertTrue(updateSkuResult.isFailure)
        assertEquals(15, db.productInventoryDao().getInventoryBySku("SKU-SECRET-MFG-01")?.currentStock)

        // Dashboard stats must not expose manufacturer near-expiry batches to stockists
        val stats = connectedRepo.getDashboardStats()
        assertEquals(0, stats.nearExpiryBatchesCount)
    }

    @Test
    fun stockist_dashboardHidesInventoryDrawerEntryAndShortageInventoryWidget() {
        runBlocking {
            val (stockistId, _, _) = seedUsersAndInventory()
            loginSession(stockistId, "stockist-token")
        }

        val dashboardVm = DashboardViewModel(repository)

        composeTestRule.setContent {
            PharmaOSTheme {
                DashboardScreen(
                    viewModel = dashboardVm,
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
                    onLoggedOut = {}
                )
            }
        }

        waitForUiCondition {
            !dashboardVm.uiState.value.isLoading && dashboardVm.uiState.value.currentUser?.role == UserRole.STOCKIST
        }

        assertFalse(dashboardVm.uiState.value.isRealTimeInventoryAuthorized)
        assertTrue(dashboardVm.uiState.value.inventoryItems.isEmpty())

        // Manufacturer SKU inventory must NOT be exposed on Stockist home screen
        assertTrue(composeTestRule.onAllNodesWithText("MfgInsulin 40IU Vial", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("SKU-SECRET-MFG-01", substring = true).fetchSemanticsNodes().isEmpty())

        // Open drawer and confirm Real-Time Inventory Dashboard entry is completely hidden
        composeTestRule.onNodeWithTag("dashboard_hamburger_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(composeTestRule.onAllNodesWithTag("drawer_item_inventory_dashboard").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun stockist_directAccessToInventoryDashboard_showsAccessRestricted_andNoManufacturerData() {
        runBlocking {
            val (stockistId, _, _) = seedUsersAndInventory()
            loginSession(stockistId, "stockist-token")
        }

        val inventoryVm = InventoryViewModel(repository)
        var backClicked = false

        composeTestRule.setContent {
            PharmaOSTheme {
                InventoryDashboardScreen(
                    viewModel = inventoryVm,
                    onNavigateBack = { backClicked = true },
                    onNavigateToRecalls = {},
                    onNavigateToBasket = {},
                    onNavigateToProducts = {}
                )
            }
        }

        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("inventory_access_restricted_view").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("inventory_access_restricted_view").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Brand & Manufacturer Access Only").onFirst().assertIsDisplayed()

        // No inventory actions or manufacturer/brand data exposed
        assertTrue(composeTestRule.onAllNodesWithTag("add_stock_batch_fab").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithTag("inventory_refresh_button").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("BRN-LOT-901", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("MFG-LOT-777", substring = true).fetchSemanticsNodes().isEmpty())

        composeTestRule.onNodeWithTag("inventory_restricted_back_button").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun stockist_directAccessToBatchExpiry_showsAccessRestricted_andBlocksProductListMutations() {
        runBlocking {
            val (stockistId, _, _) = seedUsersAndInventory()
            loginSession(stockistId, "stockist-token")
        }

        val inventoryVm = InventoryViewModel(repository)
        val productListVm = ProductListViewModel(productRepository = productRepository, pharmaRepository = repository)

        composeTestRule.setContent {
            PharmaOSTheme {
                BatchExpiryScreen(
                    viewModel = inventoryVm,
                    onNavigateBack = {},
                    onNavigateToRecalls = {},
                    onAddBatchClick = {}
                )
            }
        }

        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("batch_expiry_access_restricted_view").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("batch_expiry_access_restricted_view").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("add_batch_fab").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("MFG-LOT-777", substring = true).fetchSemanticsNodes().isEmpty())

        // Verify ProductListViewModel also blocks stockist access & mutations
        waitForUiCondition { productListVm.uiState.value.isAuthResolved }
        assertFalse(productListVm.uiState.value.isInventoryAuthorized)
        assertTrue(productListVm.uiState.value.products.isEmpty())
        productListVm.adjustStock("SKU-SECRET-MFG-01", 10)
        runBlocking {
            assertEquals(15, db.productInventoryDao().getInventoryBySku("SKU-SECRET-MFG-01")?.currentStock)
        }
    }

    @Test
    fun stockist_directAccessToProductListScreen_rendersAccessRestrictedUi() {
        runBlocking {
            val (stockistId, _, _) = seedUsersAndInventory()
            loginSession(stockistId, "stockist-token")
        }

        val productListVm = ProductListViewModel(productRepository = productRepository, pharmaRepository = repository)

        composeTestRule.setContent {
            PharmaOSTheme {
                ProductListScreen(
                    viewModel = productListVm,
                    onNavigateBack = {}
                )
            }
        }

        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("product_inventory_access_restricted_view").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("product_inventory_access_restricted_view").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("add_product_button").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("SKU-SECRET-MFG-01", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun brandAccount_viewsOwnRealTimeInventory_withAllFields_isolatedFromManufacturer_andRefreshesOnUpdate() {
        runBlocking {
            val (_, brandId, _) = seedUsersAndInventory()
            loginSession(brandId, "brand-token")
        }

        val inventoryVm = InventoryViewModel(repository)

        composeTestRule.setContent {
            PharmaOSTheme {
                InventoryDashboardScreen(
                    viewModel = inventoryVm,
                    onNavigateBack = {},
                    onNavigateToRecalls = {},
                    onNavigateToBasket = {},
                    onNavigateToProducts = {}
                )
            }
        }

        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("inventory_item_101").fetchSemanticsNodes().isNotEmpty()
        }

        // Brand sees its own batch with quantity, batch number, manufacturing date, expiry date, and status
        composeTestRule.onNodeWithTag("inventory_item_101").assertIsDisplayed()
        composeTestRule.onNodeWithTag("inventory_item_qty_101").assertTextContains("120", substring = true)
        composeTestRule.onNodeWithTag("inventory_item_batch_101").assertTextContains("BRN-LOT-901", substring = true)
        composeTestRule.onNodeWithTag("inventory_item_mfg_101")
            .assertTextContains("Mfg: ${Formatters.formatDate(mfgEpoch)}", substring = true)
        composeTestRule.onNodeWithTag("inventory_item_expiry_101")
            .assertTextContains(Formatters.formatDate(expiryHealthyEpoch), substring = true)
        composeTestRule.onNodeWithTag("inventory_item_status_101")
            .assertTextContains(StockLevelStatus.HEALTHY.label, substring = true)

        // Brand must NOT see another Manufacturer's batch
        assertTrue(composeTestRule.onAllNodesWithText("MFG-LOT-777", substring = true).fetchSemanticsNodes().isEmpty())

        // Update stock and verify UI refreshes immediately with new quantity and status
        inventoryVm.adjustBatchStock(101L, 8, "Dispatch audit")
        waitForUiCondition {
            inventoryVm.uiState.value.inventoryItems.firstOrNull { it.id == 101L }?.remainingQuantity == 8
        }
        composeTestRule.onNodeWithTag("inventory_item_qty_101").assertTextContains("8", substring = true)
        composeTestRule.onNodeWithTag("inventory_item_status_101")
            .assertTextContains(StockLevelStatus.CRITICAL_LOW.label, substring = true)
    }

    @Test
    fun brandAccount_withNoBatches_displaysEmptyState() {
        runBlocking {
            val brandId = insertUser(
                fullName = "Empty Brand",
                businessName = "NovaBrand Pharma",
                email = "empty@novabrand.test",
                role = UserRole.PHARMA_COMPANY,
                gstin = "27AAAAN0099N1Z5"
            )
            loginSession(brandId, "empty-brand-token")
        }

        val inventoryVm = InventoryViewModel(repository)

        composeTestRule.setContent {
            PharmaOSTheme {
                InventoryDashboardScreen(
                    viewModel = inventoryVm,
                    onNavigateBack = {},
                    onNavigateToRecalls = {},
                    onNavigateToBasket = {},
                    onNavigateToProducts = {}
                )
            }
        }

        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("inventory_empty_state").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("inventory_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithText("No Inventory Records").assertIsDisplayed()
    }

    @Test
    fun manufacturerAccount_dashboardShowsInventoryDrawerEntry() {
        runBlocking {
            val (_, _, manufacturerId) = seedUsersAndInventory()
            loginSession(manufacturerId, "mfg-token")
        }

        val dashboardVm = DashboardViewModel(repository)

        composeTestRule.setContent {
            PharmaOSTheme {
                DashboardScreen(
                    viewModel = dashboardVm,
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
                    onLoggedOut = {}
                )
            }
        }

        waitForUiCondition {
            !dashboardVm.uiState.value.isLoading && dashboardVm.uiState.value.isRealTimeInventoryAuthorized
        }

        composeTestRule.onNodeWithTag("dashboard_hamburger_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_inventory_dashboard").assertIsDisplayed()
    }

    @Test
    fun manufacturerAccount_connectedBackend_showsErrorOnFailure_thenDisplaysAndRefreshesBackendInventory() {
        var manufacturerId = 0L
        var productId = 0L
        runBlocking {
            manufacturerId = insertUser(
                fullName = "Vikram Plant Head",
                businessName = "CiplaGen Manufacturing",
                email = "mfg@ciplagen.test",
                role = UserRole.MANUFACTURER,
                gstin = "27AAAAM0003C1Z5"
            )
            productId = insertProduct(
                name = "RemoteAmox 625 Tablet",
                brand = "CiplaGen",
                salt = "Amoxicillin 500mg + Clavulanic 125mg",
                strength = "625mg",
                dosageForm = DosageForm.TABLET,
                category = "Antibiotics",
                storage = StorageCondition.ROOM_TEMPERATURE,
                createdBy = manufacturerId
            )
            dataStoreManager.setAuthExecutionMode(AuthExecutionMode.PRODUCTION_BACKEND_HTTP)
            loginSession(manufacturerId, "mfg-live-jwt")
        }

        val fetchCount = AtomicInteger(0)
        val serverRemainingQty = AtomicInteger(85)

        val connectedRepo = buildConnectedRepository(Interceptor { chain ->
            val req = chain.request()
            val path = req.url.encodedPath
            when {
                path == "/api/v1/batches" && req.method == "GET" -> {
                    val attempt = fetchCount.incrementAndGet()
                    if (attempt == 1) {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(503)
                            .message("Service Unavailable")
                            .body(
                                """{"status":503,"error":"Service Unavailable","message":"Inventory warehouse service temporarily unavailable"}"""
                                    .toResponseBody("application/json".toMediaType())
                            )
                            .build()
                    } else {
                        val qty = serverRemainingQty.get()
                        val json = """
                            {
                              "success": true,
                              "message": "OK",
                              "data": [
                                {
                                  "id": 501,
                                  "productId": $productId,
                                  "supplierId": $manufacturerId,
                                  "batchNumber": "SRV-MFG-501",
                                  "mfgDateEpochMillis": $mfgEpoch,
                                  "expiryDateEpochMillis": $expiryHealthyEpoch,
                                  "initialQuantity": 100,
                                  "remainingQuantity": $qty,
                                  "storageCondition": "ROOM_TEMPERATURE",
                                  "mrp": 210.0,
                                  "ptr": 165.0
                                }
                              ]
                            }
                        """.trimIndent()
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(json.toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                }
                path == "/api/v1/inventory" && req.method == "GET" -> {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"success":true,"message":"OK","data":[]}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                path == "/api/v1/batches/501/stock" && req.method == "PATCH" -> {
                    serverRemainingQty.set(7)
                    val json = """
                        {
                          "success": true,
                          "message": "Updated",
                          "data": {
                            "id": 501,
                            "productId": $productId,
                            "supplierId": $manufacturerId,
                            "batchNumber": "SRV-MFG-501",
                            "mfgDateEpochMillis": $mfgEpoch,
                            "expiryDateEpochMillis": $expiryHealthyEpoch,
                            "initialQuantity": 100,
                            "remainingQuantity": 7,
                            "storageCondition": "ROOM_TEMPERATURE",
                            "mrp": 210.0,
                            "ptr": 165.0
                          }
                        }
                    """.trimIndent()
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(json.toResponseBody("application/json".toMediaType()))
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

        val inventoryVm = InventoryViewModel(connectedRepo)

        composeTestRule.setContent {
            PharmaOSTheme {
                InventoryDashboardScreen(
                    viewModel = inventoryVm,
                    onNavigateBack = {},
                    onNavigateToRecalls = {},
                    onNavigateToBasket = {},
                    onNavigateToProducts = {}
                )
            }
        }

        // 1. First fetch returns HTTP 503 -> Error state with Retry button is shown
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("inventory_error_state").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("inventory_error_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("inventory_retry_button").assertIsDisplayed()

        // 2. Click Retry -> HTTP 200 returns actual backend batch -> Displayed with all fields
        composeTestRule.onNodeWithTag("inventory_retry_button").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("inventory_item_501").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("inventory_item_501").assertIsDisplayed()
        composeTestRule.onNodeWithTag("inventory_item_batch_501").assertTextContains("SRV-MFG-501", substring = true)
        composeTestRule.onNodeWithTag("inventory_item_qty_501").assertTextContains("85", substring = true)
        composeTestRule.onNodeWithTag("inventory_item_mfg_501")
            .assertTextContains("Mfg: ${Formatters.formatDate(mfgEpoch)}", substring = true)
        composeTestRule.onNodeWithTag("inventory_item_status_501")
            .assertTextContains(StockLevelStatus.HEALTHY.label, substring = true)

        // 3. Backend stock changes remotely -> Refresh button pulls latest stock & recalculates status
        serverRemainingQty.set(12)
        composeTestRule.onNodeWithTag("inventory_refresh_button").performClick()
        waitForUiCondition {
            inventoryVm.uiState.value.inventoryItems.firstOrNull { it.id == 501L }?.remainingQuantity == 12
        }
        composeTestRule.onNodeWithTag("inventory_item_qty_501").assertTextContains("12", substring = true)
        composeTestRule.onNodeWithTag("inventory_item_status_501")
            .assertTextContains(StockLevelStatus.LOW_STOCK.label, substring = true)

        // 4. Manufacturer adjusts stock via PATCH /api/v1/batches/501/stock -> UI updates to CRITICAL_LOW
        inventoryVm.adjustBatchStock(501L, 7, "Plant shipment dispatch")
        waitForUiCondition {
            inventoryVm.uiState.value.inventoryItems.firstOrNull { it.id == 501L }?.remainingQuantity == 7
        }
        composeTestRule.onNodeWithTag("inventory_item_qty_501").assertTextContains("7", substring = true)
        composeTestRule.onNodeWithTag("inventory_item_status_501")
            .assertTextContains(StockLevelStatus.CRITICAL_LOW.label, substring = true)
    }
}

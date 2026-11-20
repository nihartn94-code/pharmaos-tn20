package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SupplierOfferEntity
import com.example.data.local.entity.SupplierSettingsEntity
import com.example.data.local.entity.UserEntity
import com.example.data.remote.config.ApiConfig
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.remote.interceptor.TokenProvider
import com.example.data.remote.source.AuthRemoteDataSource
import com.example.data.remote.source.PharmaRemoteDataSource
import com.example.data.repository.PharmaRepositoryImpl
import com.example.di.NetworkModule
import com.example.domain.model.AccountStatus
import com.example.domain.model.DosageForm
import com.example.domain.model.SchemeType
import com.example.domain.model.StorageCondition
import com.example.domain.model.UserRole
import com.example.ui.screens.basket.BasketScreen
import com.example.ui.screens.basket.BasketViewModel
import com.example.ui.screens.catalogue.CatalogueViewModel
import com.example.ui.screens.catalogue.MedicineCatalogueScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.dashboard.DashboardViewModel
import com.example.ui.screens.orders.MyOrdersScreen
import com.example.ui.screens.orders.OrdersViewModel
import com.example.ui.theme.PharmaOSTheme
import com.example.util.Formatters
import com.example.util.PasswordHasher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class BasketCartFunctionalityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var repository: PharmaRepositoryImpl

    private val futureExpiry = System.currentTimeMillis() + 180L * 24 * 3600 * 1000

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

            repository = PharmaRepositoryImpl(
                db = db,
                dataStoreManager = dataStoreManager
            )
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
                throw AssertionError("Timed out after $timeoutMs ms waiting for UI condition")
            }
            Thread.sleep(25)
        }
        composeTestRule.waitForIdle()
    }

    private suspend fun insertUser(
        fullName: String,
        businessName: String,
        email: String,
        role: UserRole
    ): Long {
        val hash = PasswordHasher.hashPassword("Password@123")
        return db.userDao().insertUser(
            UserEntity(
                fullName = fullName,
                businessName = businessName,
                email = email,
                phone = "9876543210",
                gstin = "27ABCDE1234F1Z5",
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
        salt: String = "Paracetamol",
        strength: String = "650mg",
        gstRate: Double = 12.0,
        createdBy: Long = 1L
    ): Long {
        return db.productDao().insertProduct(
            ProductEntity(
                name = name,
                brand = brand,
                saltComposition = salt,
                strength = strength,
                dosageForm = DosageForm.TABLET.name,
                therapeuticCategory = "General",
                manufacturerName = "Cipla Ltd",
                packSize = "15 Tabs",
                hsnCode = "3004",
                gstRatePercent = gstRate,
                storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                isRx = true,
                isActive = true,
                createdBy = createdBy
            )
        )
    }

    private suspend fun insertOffer(
        productId: Long,
        supplierId: Long,
        unitPricePtr: Double,
        mrp: Double = unitPricePtr * 1.4,
        moq: Int = 5,
        availableQty: Int = 100,
        schemeType: SchemeType = SchemeType.NONE,
        buyQty: Int = 0,
        freeQty: Int = 0
    ): Long {
        return db.offerDao().insertOffer(
            SupplierOfferEntity(
                productId = productId,
                supplierId = supplierId,
                unitPricePtr = unitPricePtr,
                mrp = mrp,
                moq = moq,
                availableQuantity = availableQty,
                schemeType = schemeType.name,
                schemeBuyQty = buyQty,
                schemeFreeQty = freeQty,
                validTillEpochMillis = futureExpiry,
                isActive = true
            )
        )
    }

    @Test
    fun globalBasketIcons_onHomeOrdersExplore_showLiveCountAndNavigate() {
        var buyerId = 0L
        var supplierId = 0L
        var p1 = 0L
        var o1 = 0L
        runBlocking {
            buyerId = insertUser("Rohan", "Apex Medical", "buyer@test.com", UserRole.STOCKIST)
            supplierId = insertUser("Vikram", "MedSupply Direct", "sup@test.com", UserRole.PHARMA_COMPANY)
            dataStoreManager.setLoggedInUserId(buyerId)
            p1 = insertProduct("Dolo 650", "Dolo", createdBy = supplierId)
            o1 = insertOffer(p1, supplierId, unitPricePtr = 30.0, moq = 5, availableQty = 50)
            repository.postApiBasketItems(p1, o1, 7)
        }

        var homeBasketNavigated = false
        var ordersBasketNavigated = false
        var catalogueBasketNavigated = false
        var activeTab by mutableStateOf("HOME")

        val dashboardVm = DashboardViewModel(repository)
        val ordersVm = OrdersViewModel(repository)
        val catalogueVm = CatalogueViewModel(repository)

        composeTestRule.setContent {
            PharmaOSTheme {
                when (activeTab) {
                    "HOME" -> DashboardScreen(
                        viewModel = dashboardVm,
                        onNavigateToCatalogue = {},
                        onNavigateToBasket = { homeBasketNavigated = true },
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
                    "ORDERS" -> MyOrdersScreen(
                        viewModel = ordersVm,
                        onNavigateBack = {},
                        onOrderClick = {},
                        onNavigateToBasket = { ordersBasketNavigated = true }
                    )
                    else -> MedicineCatalogueScreen(
                        viewModel = catalogueVm,
                        onNavigateBack = {},
                        onProductClick = {},
                        onAddProductClick = {},
                        onNavigateToBasket = { catalogueBasketNavigated = true }
                    )
                }
            }
        }

        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("dashboard_cart_badge").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("dashboard_cart_badge").assertTextContains("7")
        composeTestRule.onNodeWithTag("dashboard_cart_button").performClick()
        assertTrue(homeBasketNavigated)

        // Verify Orders Screen basket icon + badge
        activeTab = "ORDERS"
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("orders_basket_badge").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("orders_basket_badge").assertTextContains("7")
        composeTestRule.onNodeWithTag("orders_basket_button").performClick()
        assertTrue(ordersBasketNavigated)

        // Verify Explore (Catalogue) Screen basket icon + badge + live update
        activeTab = "EXPLORE"
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("catalogue_basket_badge").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("catalogue_basket_badge").assertTextContains("7")
        composeTestRule.onNodeWithTag("catalogue_basket_button").performClick()
        assertTrue(catalogueBasketNavigated)

        // Update basket in DB and verify immediate badge refresh without leaving screen
        runBlocking {
            val itemId = repository.getApiBasket().items.first().itemId
            repository.putApiBasketItemQuantity(itemId, 12)
        }
        waitForUiCondition {
            runCatching {
                composeTestRule.onNodeWithTag("catalogue_basket_badge").assertTextContains("12")
            }.isSuccess
        }
    }

    @Test
    fun basketScreen_removeItem_updatesImmediatelyAndPersistsCorrectly() {
        var item1Id = 0L
        var item2Id = 0L
        runBlocking {
            val buyerId = insertUser("Buyer", "Retail Shop", "buyer2@test.com", UserRole.STOCKIST)
            val supId = insertUser("Sup", "National Hub", "sup2@test.com", UserRole.PHARMA_COMPANY)
            dataStoreManager.setLoggedInUserId(buyerId)

            val p1 = insertProduct("Augmentin 625", "Augmentin", createdBy = supId)
            val p2 = insertProduct("Pan-D Capsule", "Pan-D", salt = "Pantoprazole", createdBy = supId)
            val o1 = insertOffer(p1, supId, unitPricePtr = 100.0, moq = 2, availableQty = 50)
            val o2 = insertOffer(p2, supId, unitPricePtr = 80.0, moq = 5, availableQty = 50)

            repository.postApiBasketItems(p1, o1, 4)
            repository.postApiBasketItems(p2, o2, 5)
            val initial = repository.getApiBasket()
            item1Id = initial.items.first { it.productName == "Augmentin 625" }.itemId
            item2Id = initial.items.first { it.productName == "Pan-D Capsule" }.itemId
        }

        val basketVm = BasketViewModel(repository)
        composeTestRule.setContent {
            PharmaOSTheme {
                BasketScreen(
                    viewModel = basketVm,
                    onNavigateBack = {},
                    onNavigateToOptimizer = {},
                    onNavigateToCheckout = {},
                    onNavigateToCatalogue = {}
                )
            }
        }

        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("basket_item_row_$item1Id").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("basket_item_row_$item2Id").assertIsDisplayed()

        // Remove first item
        composeTestRule.onNodeWithTag("basket_remove_item_$item1Id").performClick()

        // Item 1 must disappear immediately and Item 2 must stay
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("basket_item_row_$item1Id").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("basket_item_row_$item2Id").assertIsDisplayed()

        runBlocking {
            val persisted = repository.getApiBasket()
            assertEquals(1, persisted.items.size)
            assertEquals("Pan-D Capsule", persisted.items.first().productName)
        }
    }

    @Test
    fun basketScreen_quantityAdjustment_enforcesMoqStockAndNoNegative_andRefreshesTotalsImmediately() {
        var itemId = 0L
        runBlocking {
            val buyerId = insertUser("Buyer", "Retail Hub", "buyer3@test.com", UserRole.STOCKIST)
            val supId = insertUser("Sup", "Cipla Supply", "sup3@test.com", UserRole.PHARMA_COMPANY)
            dataStoreManager.setLoggedInUserId(buyerId)
            db.analyticsDao().insertOrUpdateSupplierSettings(
                SupplierSettingsEntity(
                    supplierId = supId,
                    deliveryCharge = 150.0,
                    freeDeliveryThreshold = 1000.0
                )
            )

            val p1 = insertProduct("Azithral 500", "Azithral", salt = "Azithromycin", gstRate = 12.0, createdBy = supId)
            val o1 = insertOffer(p1, supId, unitPricePtr = 100.0, moq = 5, availableQty = 12)
            repository.postApiBasketItems(p1, o1, 5) // Subtotal 500, GST 60, Delivery 150 -> Payable 710
            itemId = repository.getApiBasket().items.first().itemId
        }

        val basketVm = BasketViewModel(repository)
        composeTestRule.setContent {
            PharmaOSTheme {
                BasketScreen(
                    viewModel = basketVm,
                    onNavigateBack = {},
                    onNavigateToOptimizer = {},
                    onNavigateToCheckout = {},
                    onNavigateToCatalogue = {}
                )
            }
        }

        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("basket_qty_$itemId").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("basket_qty_$itemId").assertTextContains("5")
        composeTestRule.onNodeWithTag("basket_item_subtotal_$itemId").assertTextContains(Formatters.formatInr(500.0), substring = true)
        composeTestRule.onNodeWithTag("basket_bottom_payable_amount").assertTextContains(Formatters.formatInr(710.0), substring = true)

        // Attempt decrease below MOQ (5 -> 4): must reject cleanly and keep qty 5
        composeTestRule.onNodeWithTag("basket_minus_$itemId").performClick()
        waitForUiCondition { basketVm.uiState.value.basket?.items?.firstOrNull()?.quantity == 5 }
        runBlocking {
            assertEquals(5, repository.getApiBasket().items.first().quantity)
        }

        // Increase quantity 5 -> 6: immediate UI refresh
        composeTestRule.onNodeWithTag("basket_plus_$itemId").performClick()
        waitForUiCondition {
            runCatching {
                composeTestRule.onNodeWithTag("basket_qty_$itemId").assertTextContains("6")
                composeTestRule.onNodeWithTag("basket_item_subtotal_$itemId").assertTextContains(Formatters.formatInr(600.0), substring = true)
                composeTestRule.onNodeWithTag("basket_bottom_payable_amount").assertTextContains(Formatters.formatInr(822.0), substring = true) // 600 + 72 GST + 150 delivery
            }.isSuccess
        }

        // Direct update via quantity dialog to cross free delivery threshold (10 units * 100 = 1000 -> Free delivery!)
        composeTestRule.onNodeWithTag("basket_qty_$itemId").performClick()
        composeTestRule.onNodeWithTag("basket_qty_input_field").performTextClearance()
        composeTestRule.onNodeWithTag("basket_qty_input_field").performTextInput("10")
        composeTestRule.onNodeWithTag("basket_qty_confirm_button").performClick()

        waitForUiCondition {
            runCatching {
                composeTestRule.onNodeWithTag("basket_qty_$itemId").assertTextContains("10")
                composeTestRule.onNodeWithTag("basket_bottom_payable_amount").assertTextContains(Formatters.formatInr(1120.0)) // 1000 + 120 GST + 0 delivery
            }.isSuccess
        }

        // Verify negative and exceeding stock are rejected cleanly at repository level
        runBlocking {
            val negRes = repository.putApiBasketItemQuantity(itemId, -3)
            assertTrue(negRes.isFailure)
            assertTrue(negRes.exceptionOrNull()?.message?.contains("negative", ignoreCase = true) == true)

            val overStockRes = repository.putApiBasketItemQuantity(itemId, 13)
            assertTrue(overStockRes.isFailure)
            assertTrue(overStockRes.exceptionOrNull()?.message?.contains("available stock", ignoreCase = true) == true)
            assertEquals(10, repository.getApiBasket().items.first().quantity)
        }
    }

    @Test
    fun basketScreen_breakdownCard_clearlyDistinguishesSubtotalTaxesChargesAndPayable() {
        runBlocking {
            val buyerId = insertUser("Buyer", "Apollo Hub", "buyer4@test.com", UserRole.STOCKIST)
            val supId = insertUser("Sup", "Sun Direct", "sup4@test.com", UserRole.PHARMA_COMPANY)
            dataStoreManager.setLoggedInUserId(buyerId)
            db.analyticsDao().insertOrUpdateSupplierSettings(
                SupplierSettingsEntity(
                    supplierId = supId,
                    deliveryCharge = 200.0,
                    freeDeliveryThreshold = 2000.0
                )
            )

            val p1 = insertProduct("Metformin 500", "Glycomet", gstRate = 5.0, createdBy = supId)
            val o1 = insertOffer(p1, supId, unitPricePtr = 100.0, moq = 1, availableQty = 50)
            repository.postApiBasketItems(p1, o1, 4) // Subtotal 400, GST 20, Delivery 200, Final Payable 620
        }

        val basketVm = BasketViewModel(repository)
        composeTestRule.setContent {
            PharmaOSTheme {
                BasketScreen(
                    viewModel = basketVm,
                    onNavigateBack = {},
                    onNavigateToOptimizer = {},
                    onNavigateToCheckout = {},
                    onNavigateToCatalogue = {}
                )
            }
        }

        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("basket_items_list").fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to financial breakdown card inside LazyColumn
        composeTestRule.onNodeWithTag("basket_subtotal_amount").assertTextContains(Formatters.formatInr(400.0))
        composeTestRule.onNodeWithTag("basket_tax_amount").assertTextContains(Formatters.formatInr(20.0))
        composeTestRule.onNodeWithTag("basket_additional_charges_amount").assertTextContains(Formatters.formatInr(200.0))
        composeTestRule.onNodeWithTag("basket_final_payable_amount").assertTextContains(Formatters.formatInr(620.0))
    }

    @Test
    fun saveBasketAsTemplate_savesPersistsHandlesDuplicatesAndLoadsReliably() {
        var itemId = 0L
        runBlocking {
            val buyerId = insertUser("Buyer", "MedCare", "buyer5@test.com", UserRole.STOCKIST)
            val supId = insertUser("Sup", "Zydus Hub", "sup5@test.com", UserRole.PHARMA_COMPANY)
            dataStoreManager.setLoggedInUserId(buyerId)

            val p1 = insertProduct("Telma 40", "Telma", salt = "Telmisartan", createdBy = supId)
            val o1 = insertOffer(p1, supId, unitPricePtr = 120.0, moq = 5, availableQty = 80)
            repository.postApiBasketItems(p1, o1, 8)
            itemId = repository.getApiBasket().items.first().itemId
        }

        val basketVm = BasketViewModel(repository)
        composeTestRule.setContent {
            PharmaOSTheme {
                BasketScreen(
                    viewModel = basketVm,
                    onNavigateBack = {},
                    onNavigateToOptimizer = {},
                    onNavigateToCheckout = {},
                    onNavigateToCatalogue = {}
                )
            }
        }

        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("basket_item_row_$itemId").fetchSemanticsNodes().isNotEmpty()
        }

        // 1. Open Save Template dialog & test blank name validation
        composeTestRule.onNodeWithTag("save_basket_template_button").performClick()
        composeTestRule.onNodeWithTag("confirm_save_template_button").performClick()
        composeTestRule.onNodeWithTag("save_template_error_text").assertIsDisplayed()

        // 2. Save valid template
        composeTestRule.onNodeWithTag("save_template_name_input").performTextInput("Monthly Cardiac Stock")
        composeTestRule.onNodeWithTag("confirm_save_template_button").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("save_template_dialog").fetchSemanticsNodes().isEmpty()
        }

        val savedId = runBlocking {
            val saved = repository.getApiSavedBasketsFlow().first()
            assertEquals(1, saved.size)
            assertEquals("Monthly Cardiac Stock", saved.first().name)
            assertEquals(1, saved.first().itemCount)
            saved.first().id
        }

        // 3. Test duplicate template name (case-insensitive) -> must show duplicate error & Replace option
        composeTestRule.onNodeWithTag("save_basket_template_button").performClick()
        composeTestRule.onNodeWithTag("save_template_name_input").performTextInput("monthly cardiac stock")
        composeTestRule.onNodeWithTag("confirm_save_template_button").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("replace_existing_template_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("save_template_error_text").assertTextContains("already exists", substring = true)

        // Update basket qty to 12, then replace existing template
        runBlocking { repository.putApiBasketItemQuantity(itemId, 12) }
        composeTestRule.onNodeWithTag("replace_existing_template_button").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("save_template_dialog").fetchSemanticsNodes().isEmpty()
        }

        // 4. Clear active basket, reopen saved template, and verify qty=12 restored
        composeTestRule.onNodeWithTag("basket_clear_button").performClick()
        composeTestRule.onNodeWithTag("confirm_clear_basket_button").performClick()
        waitForUiCondition {
            runBlocking { repository.getApiBasket().items.isEmpty() }
        }

        composeTestRule.onNodeWithTag("view_saved_baskets_button").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("saved_baskets_sheet").fetchSemanticsNodes().isNotEmpty()
        }

        val latestSavedId = runBlocking { repository.getApiSavedBasketsFlow().first().first().id }
        composeTestRule.onNodeWithTag("load_saved_basket_$latestSavedId").performClick()

        waitForUiCondition {
            runBlocking { repository.getApiBasket().items.firstOrNull()?.quantity == 12 }
        }
        val restored = runBlocking { repository.getApiBasket() }
        assertEquals(1, restored.items.size)
        assertEquals("Telma 40", restored.items.first().productName)
        assertEquals(12, restored.items.first().quantity)
        assertTrue(latestSavedId != 0L && savedId != 0L)
    }

    @Test
    fun productionBackendHttpMode_syncsUpdatesSavesAndHandlesFailuresGracefully() {
        runBlocking {
            val buyerId = insertUser("API Buyer", "API Med Hub", "apibuyer@test.com", UserRole.STOCKIST)
            dataStoreManager.setLoggedInUserId(buyerId)
            dataStoreManager.setAuthExecutionMode(AuthExecutionMode.PRODUCTION_BACKEND_HTTP)
        }

        var failSaveTemplate = true
        var failBasketGet = false
        var currentQty = 10
        val requests = mutableListOf<String>()

        val mockInterceptor = Interceptor { chain ->
            val req = chain.request()
            val path = req.url.encodedPath
            requests.add("${req.method} $path")

            when {
                req.method == "GET" && path == "/api/v1/basket" -> {
                    if (failBasketGet) {
                        jsonResponse(req, 503, """{"status":503,"message":"Backend temporarily offline"}""")
                    } else {
                        val sub = currentQty * 30.0
                        val gst = sub * 0.12
                        val grand = sub + gst
                        jsonResponse(
                            req,
                            200,
                            """{"success":true,"data":{"basketId":88,"items":[{"itemId":501,"productId":701,"productName":"Remote Dolo 650","brand":"Dolo","saltComposition":"Paracetamol","packSize":"15 Tabs","hsnCode":"3004","gstRatePercent":12.0,"offerId":901,"supplierId":801,"supplierName":"Micro Hub","unitPricePtr":30.0,"mrp":45.0,"moq":5,"availableQuantity":100,"quantity":$currentQty,"effectiveUnitPrice":30.0,"lineTotalWithGst":$grand,"schemeDescription":"Standard"}],"subtotal":$sub,"totalGst":$gst,"totalDeliveryCharges":0.0,"grandTotal":$grand,"totalSavings":0.0}}"""
                        )
                    }
                }
                req.method == "GET" && path == "/api/v1/basket/saved" -> jsonResponse(
                    req,
                    200,
                    """{"success":true,"data":[]}"""
                )
                req.method == "PUT" && path == "/api/v1/basket/items/501" -> {
                    currentQty = 11
                    val sub = currentQty * 30.0
                    val gst = sub * 0.12
                    val grand = sub + gst
                    jsonResponse(
                        req,
                        200,
                        """{"success":true,"data":{"basketId":88,"items":[{"itemId":501,"productId":701,"productName":"Remote Dolo 650","brand":"Dolo","saltComposition":"Paracetamol","packSize":"15 Tabs","hsnCode":"3004","gstRatePercent":12.0,"offerId":901,"supplierId":801,"supplierName":"Micro Hub","unitPricePtr":30.0,"mrp":45.0,"moq":5,"availableQuantity":100,"quantity":11,"effectiveUnitPrice":30.0,"lineTotalWithGst":$grand,"schemeDescription":"Standard"}],"subtotal":$sub,"totalGst":$gst,"totalDeliveryCharges":0.0,"grandTotal":$grand,"totalSavings":0.0}}"""
                    )
                }
                req.method == "POST" && path == "/api/v1/basket/saved" -> {
                    if (failSaveTemplate) {
                        jsonResponse(req, 500, """{"status":500,"message":"Database write failed on server"}""")
                    } else {
                        jsonResponse(
                            req,
                            200,
                            """{"success":true,"data":{"id":999,"name":"Remote Template","itemCount":1,"updatedAtEpochMillis":1700000000000}}"""
                        )
                    }
                }
                else -> jsonResponse(req, 404, """{"status":404,"message":"Not Found"}""")
            }
        }

        val module = NetworkModule(
            tokenProvider = object : TokenProvider {
                override fun getAccessToken(): String = "jwt-token"
            },
            initialBaseUrl = ApiConfig.EMULATOR_DEFAULT_BASE_URL,
            extraInterceptors = listOf(mockInterceptor)
        )
        val apiRepo = PharmaRepositoryImpl(
            db = db,
            dataStoreManager = dataStoreManager,
            authRemoteDataSource = AuthRemoteDataSource(module),
            pharmaRemoteDataSource = PharmaRemoteDataSource(module),
            networkModule = module
        )

        val basketVm = BasketViewModel(apiRepo)
        composeTestRule.setContent {
            PharmaOSTheme {
                BasketScreen(
                    viewModel = basketVm,
                    onNavigateBack = {},
                    onNavigateToOptimizer = {},
                    onNavigateToCheckout = {},
                    onNavigateToCatalogue = {}
                )
            }
        }

        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("basket_item_row_501").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("basket_qty_501").assertTextContains("10")

        // Increase qty via backend PUT
        composeTestRule.onNodeWithTag("basket_plus_501").performClick()
        waitForUiCondition {
            runCatching { composeTestRule.onNodeWithTag("basket_qty_501").assertTextContains("11") }.isSuccess
        }
        assertTrue(requests.contains("PUT /api/v1/basket/items/501"))

        // Attempt Save Template while backend returns 500 -> dialog MUST stay open & display server error (never claim success!)
        composeTestRule.onNodeWithTag("save_basket_template_button").performClick()
        composeTestRule.onNodeWithTag("save_template_name_input").performTextInput("Remote Template")
        composeTestRule.onNodeWithTag("confirm_save_template_button").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("save_template_error_text").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("save_template_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_template_error_text").assertTextContains("Database write failed", substring = true)

        // Now backend recovers -> tap Save Template again -> succeeds and updates saved list
        failSaveTemplate = false
        composeTestRule.onNodeWithTag("confirm_save_template_button").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("save_template_dialog").fetchSemanticsNodes().isEmpty()
        }
        runBlocking {
            val saved = apiRepo.getApiSavedBasketsFlow().first()
            assertEquals(1, saved.size)
            assertEquals("Remote Template", saved.first().name)
        }

        // Verify offline/503 sync banner & retry behavior
        failBasketGet = true
        basketVm.refreshBasket()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("basket_sync_error_banner").fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(basketVm.uiState.value.syncErrorMessage.isNullOrBlank())

        failBasketGet = false
        composeTestRule.onNodeWithTag("basket_sync_retry_button").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("basket_sync_error_banner").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun jsonResponse(request: Request, code: Int, json: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error $code")
            .body(json.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }
}

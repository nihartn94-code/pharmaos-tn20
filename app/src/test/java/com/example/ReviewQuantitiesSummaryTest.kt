package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SupplierOfferEntity
import com.example.data.local.entity.SupplierSettingsEntity
import com.example.data.local.entity.UserEntity
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.remote.source.AuthRemoteDataSource
import com.example.data.remote.source.PharmaRemoteDataSource
import com.example.data.repository.PharmaRepositoryImpl
import com.example.di.NetworkModule
import com.example.domain.model.AccountStatus
import com.example.domain.model.DosageForm
import com.example.domain.model.SchemeType
import com.example.domain.model.StorageCondition
import com.example.domain.model.UserRole
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.basket.BasketScreen
import com.example.ui.screens.basket.BasketViewModel
import com.example.ui.screens.orders.SelectedProductsOrderSummaryScreen
import com.example.ui.theme.PharmaOSTheme
import com.example.util.Formatters
import com.example.util.PasswordHasher
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class ReviewQuantitiesSummaryTest {

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
            dataStoreManager = DataStoreManager(
                context = context,
                secureTokenStorage = com.example.data.local.SecureTokenStorage.createForUnitTest(
                    context,
                    prefsFileName = "test_review_qty_tokens_${System.nanoTime()}"
                )
            )
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
        availableQty: Int = 500,
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

    private suspend fun seedSingleProductBasket(
        unitPrice: Double = 100.0,
        gstRate: Double = 12.0,
        moq: Int = 5,
        availableQty: Int = 500,
        initialQty: Int = 10,
        deliveryCharge: Double = 150.0,
        freeDeliveryThreshold: Double = 5000.0
    ): Long {
        val buyerId = insertUser("Rohan", "Apex Medical", "buyer@test.com", UserRole.STOCKIST)
        val supplierId = insertUser("Vikram", "Cipla Wholesale Hub", "sup@test.com", UserRole.PHARMA_COMPANY)
        dataStoreManager.setLoggedInUserId(buyerId)
        db.analyticsDao().insertOrUpdateSupplierSettings(
            SupplierSettingsEntity(
                supplierId = supplierId,
                deliveryCharge = deliveryCharge,
                freeDeliveryThreshold = freeDeliveryThreshold
            )
        )
        val p1 = insertProduct("Augmentin 625 Duo", "Augmentin", gstRate = gstRate, createdBy = supplierId)
        val o1 = insertOffer(p1, supplierId, unitPricePtr = unitPrice, moq = moq, availableQty = availableQty)
        val addRes = repository.postApiBasketItems(p1, o1, initialQty)
        assertTrue(addRes.isSuccess)
        return repository.getApiBasket().items.first().itemId
    }

    @Test
    fun quickAdd_updatesQuantityProductSubtotalAndOverallBasketImmediately() {
        val itemId = runBlocking {
            seedSingleProductBasket(
                unitPrice = 100.0,
                gstRate = 12.0,
                moq = 5,
                availableQty = 100,
                initialQty = 10,
                deliveryCharge = 150.0,
                freeDeliveryThreshold = 5000.0
            )
        }

        val viewModel = BasketViewModel(repository)
        composeTestRule.setContent {
            PharmaOSTheme {
                SelectedProductsOrderSummaryScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onProceedToCheckout = {},
                    onExploreCatalogue = {}
                )
            }
        }

        // Initial: qty 10 -> taxable 1000, GST 120, line total 1120, delivery 150 -> grandTotal 1270
        waitForUiCondition { viewModel.uiState.value.basket?.items?.firstOrNull()?.quantity == 10 }
        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("10")
        composeTestRule.onNodeWithTag("product_subtotal_$itemId").assertTextContains(Formatters.formatInr(1120.0), substring = true)
        composeTestRule.onNodeWithTag("selected_products_header_summary").assertTextContains("1 item(s) • 10 total units")
        composeTestRule.onNodeWithTag("sticky_total_order_value").assertTextContains(Formatters.formatInr(1270.0))

        // Tap Quick Add +10 WITHOUT leaving screen -> qty 20 -> taxable 2000, GST 240, line total 2240, delivery 150 -> grandTotal 2390
        composeTestRule.onNodeWithTag("quick_add_10_$itemId").performClick()
        waitForUiCondition { viewModel.uiState.value.basket?.items?.firstOrNull()?.quantity == 20 }
        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("20")
        composeTestRule.onNodeWithTag("product_subtotal_$itemId").assertTextContains(Formatters.formatInr(2240.0), substring = true)
        composeTestRule.onNodeWithTag("selected_products_header_summary").assertTextContains("1 item(s) • 20 total units")
        composeTestRule.onNodeWithTag("sticky_total_order_value").assertTextContains(Formatters.formatInr(2390.0))

        // Tap Quick Add +50 WITHOUT leaving screen -> qty 70 -> taxable 7000 (>= 5000 free delivery!), GST 840 -> grandTotal 7840
        composeTestRule.onNodeWithTag("quick_add_50_$itemId").performClick()
        waitForUiCondition { viewModel.uiState.value.basket?.items?.firstOrNull()?.quantity == 70 }
        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("70")
        composeTestRule.onNodeWithTag("product_subtotal_$itemId").assertTextContains(Formatters.formatInr(7840.0), substring = true)
        composeTestRule.onNodeWithTag("selected_products_header_summary").assertTextContains("1 item(s) • 70 total units")
        composeTestRule.onNodeWithTag("sticky_total_order_value").assertTextContains(Formatters.formatInr(7840.0))

        // Scroll to detailed summary card and verify exact figures
        composeTestRule.onNodeWithTag("selected_products_list")
            .performScrollToNode(hasTestTag("detailed_order_value_summary_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("summary_net_taxable").assertTextContains(Formatters.formatInr(7000.0))
        composeTestRule.onNodeWithTag("summary_gst_amount").assertTextContains(Formatters.formatInr(840.0))
        composeTestRule.onNodeWithTag("summary_delivery_charge").assertTextContains("FREE")
        composeTestRule.onNodeWithTag("summary_grand_total").assertTextContains(Formatters.formatInr(7840.0))

        // Verify Quick Add exceeding stock (70 + 50 = 120 > 100) is rejected cleanly and UI stays at 70
        composeTestRule.onNodeWithTag("selected_products_list")
            .performScrollToNode(hasTestTag("selected_product_card_$itemId"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("quick_add_50_$itemId").performClick()
        waitForUiCondition { viewModel.uiState.value.errorMessage?.contains("exceeds available stock", ignoreCase = true) == true }
        assertEquals(70, viewModel.uiState.value.basket?.items?.firstOrNull()?.quantity)
        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("70")
    }

    @Test
    fun increaseAndDecreaseButtons_updateImmediatelyAndRespectMoq() {
        val itemId = runBlocking {
            seedSingleProductBasket(
                unitPrice = 200.0,
                gstRate = 12.0,
                moq = 5,
                availableQty = 50,
                initialQty = 6,
                deliveryCharge = 100.0,
                freeDeliveryThreshold = 5000.0
            )
        }

        val viewModel = BasketViewModel(repository)
        composeTestRule.setContent {
            PharmaOSTheme {
                SelectedProductsOrderSummaryScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onProceedToCheckout = {},
                    onExploreCatalogue = {}
                )
            }
        }

        waitForUiCondition { viewModel.uiState.value.basket?.items?.firstOrNull()?.quantity == 6 }
        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("6")

        // Increase: 6 -> 7 (taxable 1400, GST 168, lineTotal 1568, delivery 100 -> grandTotal 1668)
        composeTestRule.onNodeWithTag("quantity_plus_$itemId").performClick()
        waitForUiCondition { viewModel.uiState.value.basket?.items?.firstOrNull()?.quantity == 7 }
        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("7")
        composeTestRule.onNodeWithTag("product_subtotal_$itemId").assertTextContains(Formatters.formatInr(1568.0), substring = true)
        composeTestRule.onNodeWithTag("selected_products_header_summary").assertTextContains("1 item(s) • 7 total units")
        composeTestRule.onNodeWithTag("sticky_total_order_value").assertTextContains(Formatters.formatInr(1668.0))

        // Decrease: 7 -> 6 -> 5 (MOQ)
        composeTestRule.onNodeWithTag("quantity_minus_$itemId").performClick()
        waitForUiCondition { viewModel.uiState.value.basket?.items?.firstOrNull()?.quantity == 6 }
        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("6")

        composeTestRule.onNodeWithTag("quantity_minus_$itemId").performClick()
        waitForUiCondition { viewModel.uiState.value.basket?.items?.firstOrNull()?.quantity == 5 }
        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("5")
        composeTestRule.onNodeWithTag("product_subtotal_$itemId").assertTextContains(Formatters.formatInr(1120.0), substring = true)
        composeTestRule.onNodeWithTag("sticky_total_order_value").assertTextContains(Formatters.formatInr(1220.0))

        // Attempting to decrease below MOQ (5 -> 4) must keep quantity at 5 and report error
        composeTestRule.onNodeWithTag("quantity_minus_$itemId").performClick()
        waitForUiCondition { viewModel.uiState.value.errorMessage?.contains("MOQ", ignoreCase = true) == true }
        assertEquals(5, viewModel.uiState.value.basket?.items?.firstOrNull()?.quantity)
        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("5")
    }

    @Test
    fun multipleProducts_quickAddAndAdjustmentsTargetExactProductWithoutCorruptingOthers() {
        var itemA = 0L
        var itemB = 0L
        runBlocking {
            val buyerId = insertUser("Rohan", "Apex Medical", "buyer@test.com", UserRole.STOCKIST)
            val supplierId = insertUser("Vikram", "Cipla Wholesale Hub", "sup@test.com", UserRole.PHARMA_COMPANY)
            dataStoreManager.setLoggedInUserId(buyerId)
            db.analyticsDao().insertOrUpdateSupplierSettings(
                SupplierSettingsEntity(
                    supplierId = supplierId,
                    deliveryCharge = 0.0,
                    freeDeliveryThreshold = 0.0
                )
            )
            val p1 = insertProduct("Dolo 650", "Dolo", gstRate = 12.0, createdBy = supplierId)
            val p2 = insertProduct("Pan-D", "Pan-D", gstRate = 5.0, createdBy = supplierId)
            val o1 = insertOffer(p1, supplierId, unitPricePtr = 100.0, moq = 5, availableQty = 200)
            val o2 = insertOffer(p2, supplierId, unitPricePtr = 200.0, moq = 2, availableQty = 200)
            repository.postApiBasketItems(p1, o1, 10)
            repository.postApiBasketItems(p2, o2, 5)
            val basket = repository.getApiBasket()
            itemA = basket.items.first { it.productId == p1 }.itemId
            itemB = basket.items.first { it.productId == p2 }.itemId
        }

        val viewModel = BasketViewModel(repository)
        composeTestRule.setContent {
            PharmaOSTheme {
                SelectedProductsOrderSummaryScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onProceedToCheckout = {},
                    onExploreCatalogue = {}
                )
            }
        }

        waitForUiCondition { viewModel.uiState.value.basket?.items?.size == 2 }
        // Initial: A=10 (1000 + 120 = 1120), B=5 (1000 + 50 = 1050) -> total units = 15, grandTotal = 2170
        composeTestRule.onNodeWithTag("selected_products_header_summary").assertTextContains("2 item(s) • 15 total units")
        composeTestRule.onNodeWithTag("sticky_total_order_value").assertTextContains(Formatters.formatInr(2170.0))

        // Quick Add +25 on Product A ONLY -> A=35 (3500 + 420 = 3920), B stays 5 (1050) -> units = 40, grandTotal = 4970
        composeTestRule.onNodeWithTag("quick_add_25_$itemA").performClick()
        waitForUiCondition {
            val items = viewModel.uiState.value.basket?.items.orEmpty()
            items.firstOrNull { it.itemId == itemA }?.quantity == 35 &&
                items.firstOrNull { it.itemId == itemB }?.quantity == 5
        }
        composeTestRule.onNodeWithTag("quantity_display_$itemA").assertTextContains("35")
        composeTestRule.onNodeWithTag("product_subtotal_$itemA").assertTextContains(Formatters.formatInr(3920.0), substring = true)
        composeTestRule.onNodeWithTag("selected_products_header_summary").assertTextContains("2 item(s) • 40 total units")
        composeTestRule.onNodeWithTag("sticky_total_order_value").assertTextContains(Formatters.formatInr(4970.0))

        // Scroll to Product B and tap +1 on Product B -> B=6 (1200 + 60 = 1260), A stays 35 (3920) -> units = 41, grandTotal = 5180
        composeTestRule.onNodeWithTag("selected_products_list")
            .performScrollToNode(hasTestTag("selected_product_card_$itemB"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("quantity_display_$itemB").assertTextContains("5")
        composeTestRule.onNodeWithTag("quantity_plus_$itemB").performClick()
        waitForUiCondition {
            val items = viewModel.uiState.value.basket?.items.orEmpty()
            items.firstOrNull { it.itemId == itemA }?.quantity == 35 &&
                items.firstOrNull { it.itemId == itemB }?.quantity == 6
        }
        composeTestRule.onNodeWithTag("quantity_display_$itemB").assertTextContains("6")
        composeTestRule.onNodeWithTag("product_subtotal_$itemB").assertTextContains(Formatters.formatInr(1260.0), substring = true)
        composeTestRule.onNodeWithTag("selected_products_header_summary").assertTextContains("2 item(s) • 41 total units")
        composeTestRule.onNodeWithTag("sticky_total_order_value").assertTextContains(Formatters.formatInr(5180.0))
    }

    @Test
    fun rapidRepeatedTaps_accumulateAllDeltasWithoutDroppingOrRacing() {
        val itemId = runBlocking {
            seedSingleProductBasket(
                unitPrice = 50.0,
                gstRate = 12.0,
                moq = 5,
                availableQty = 500,
                initialQty = 10,
                deliveryCharge = 0.0,
                freeDeliveryThreshold = 0.0
            )
        }

        val viewModel = BasketViewModel(repository)
        composeTestRule.setContent {
            PharmaOSTheme {
                SelectedProductsOrderSummaryScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onProceedToCheckout = {},
                    onExploreCatalogue = {}
                )
            }
        }

        waitForUiCondition { viewModel.uiState.value.basket?.items?.firstOrNull()?.quantity == 10 }

        // Fire rapid consecutive taps back-to-back without waiting between them:
        // 5x (+1) + 3x (+10) + 2x (-1) = +33 => 10 + 33 = 43
        repeat(5) {
            composeTestRule.onNodeWithTag("quantity_plus_$itemId").performClick()
        }
        repeat(3) {
            composeTestRule.onNodeWithTag("quick_add_10_$itemId").performClick()
        }
        repeat(2) {
            composeTestRule.onNodeWithTag("quantity_minus_$itemId").performClick()
        }

        waitForUiCondition(timeoutMs = 8000) {
            viewModel.uiState.value.basket?.items?.firstOrNull()?.quantity == 43
        }

        // Verify persisted Room DB value matches UI state exactly
        val persistedQty = runBlocking {
            repository.getApiBasket().items.first { it.itemId == itemId }.quantity
        }
        assertEquals(43, persistedQty)

        // 43 * 50 = 2150 taxable, 12% GST = 258, total = 2408
        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("43")
        composeTestRule.onNodeWithTag("product_subtotal_$itemId").assertTextContains(Formatters.formatInr(2408.0), substring = true)
        composeTestRule.onNodeWithTag("selected_products_header_summary").assertTextContains("1 item(s) • 43 total units")
        composeTestRule.onNodeWithTag("sticky_total_order_value").assertTextContains(Formatters.formatInr(2408.0))
    }

    @Test
    fun navigationAwayAndBack_andApplicationRestart_preservePersistedQuantitiesAndTotals() {
        val itemId = runBlocking {
            seedSingleProductBasket(
                unitPrice = 100.0,
                gstRate = 12.0,
                moq = 5,
                availableQty = 500,
                initialQty = 10,
                deliveryCharge = 0.0,
                freeDeliveryThreshold = 0.0
            )
        }

        var currentScreen by mutableStateOf("SUMMARY_FIRST")
        var firstVm = BasketViewModel(repository)
        var returnedVm: BasketViewModel? = null
        var restartedVm: BasketViewModel? = null

        composeTestRule.setContent {
            PharmaOSTheme {
                when (currentScreen) {
                    "SUMMARY_FIRST" -> SelectedProductsOrderSummaryScreen(
                        viewModel = firstVm,
                        onNavigateBack = { currentScreen = "OTHER_SCREEN" },
                        onProceedToCheckout = {},
                        onExploreCatalogue = {}
                    )
                    "OTHER_SCREEN" -> Box(modifier = Modifier.testTag("other_screen_placeholder")) {
                        Text("Other Screen")
                    }
                    "SUMMARY_RETURNED" -> SelectedProductsOrderSummaryScreen(
                        viewModel = returnedVm!!,
                        onNavigateBack = {},
                        onProceedToCheckout = {},
                        onExploreCatalogue = {}
                    )
                    "SUMMARY_AFTER_RESTART" -> SelectedProductsOrderSummaryScreen(
                        viewModel = restartedVm!!,
                        onNavigateBack = {},
                        onProceedToCheckout = {},
                        onExploreCatalogue = {}
                    )
                }
            }
        }

        // Edit quantity on Summary screen: 10 + 25 + 1 = 36 (3600 + 432 GST = 4032)
        waitForUiCondition { firstVm.uiState.value.basket?.items?.firstOrNull()?.quantity == 10 }
        composeTestRule.onNodeWithTag("quick_add_25_$itemId").performClick()
        composeTestRule.onNodeWithTag("quantity_plus_$itemId").performClick()
        waitForUiCondition { firstVm.uiState.value.basket?.items?.firstOrNull()?.quantity == 36 }
        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("36")
        composeTestRule.onNodeWithTag("sticky_total_order_value").assertTextContains(Formatters.formatInr(4032.0))

        // Navigate away
        composeTestRule.onNodeWithTag("selected_products_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("other_screen_placeholder").assertIsDisplayed()

        // Navigate back with a fresh BasketViewModel instance (simulating NavHost route re-entry)
        returnedVm = BasketViewModel(repository)
        currentScreen = "SUMMARY_RETURNED"
        waitForUiCondition { returnedVm!!.uiState.value.basket?.items?.firstOrNull()?.quantity == 36 }
        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("36")
        composeTestRule.onNodeWithTag("product_subtotal_$itemId").assertTextContains(Formatters.formatInr(4032.0), substring = true)
        composeTestRule.onNodeWithTag("sticky_total_order_value").assertTextContains(Formatters.formatInr(4032.0))

        // Simulate full application restart: brand-new repository + brand-new ViewModel over the same DB
        val restartedRepository = PharmaRepositoryImpl(
            db = db,
            dataStoreManager = dataStoreManager
        )
        restartedVm = BasketViewModel(restartedRepository)
        currentScreen = "SUMMARY_AFTER_RESTART"
        waitForUiCondition { restartedVm!!.uiState.value.basket?.items?.firstOrNull()?.quantity == 36 }
        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("36")
        composeTestRule.onNodeWithTag("product_subtotal_$itemId").assertTextContains(Formatters.formatInr(4032.0), substring = true)
        composeTestRule.onNodeWithTag("selected_products_header_summary").assertTextContains("1 item(s) • 36 total units")
        composeTestRule.onNodeWithTag("sticky_total_order_value").assertTextContains(Formatters.formatInr(4032.0))
    }

    @Test
    fun failedBackendUpdate_surfacesErrorWithoutFalseSuccessAndKeepsUiSyncedWithPersistedState() {
        var itemId = 0L
        lateinit var prodRepo: PharmaRepositoryImpl

        runBlocking {
            val buyerId = insertUser("Rohan", "Apex Medical", "buyer@test.com", UserRole.STOCKIST)
            val supplierId = insertUser("Vikram", "Cipla Wholesale Hub", "sup@test.com", UserRole.PHARMA_COMPANY)
            dataStoreManager.setLoggedInUserId(buyerId)
            dataStoreManager.setAuthTokens("valid_test_token")
            db.analyticsDao().insertOrUpdateSupplierSettings(
                SupplierSettingsEntity(
                    supplierId = supplierId,
                    deliveryCharge = 0.0,
                    freeDeliveryThreshold = 0.0
                )
            )
            val p1 = insertProduct("Augmentin 625 Duo", "Augmentin", gstRate = 12.0, createdBy = supplierId)
            val o1 = insertOffer(p1, supplierId, unitPricePtr = 100.0, moq = 5, availableQty = 200)
            repository.postApiBasketItems(p1, o1, 10)
            itemId = repository.getApiBasket().items.first().itemId

            // Switch to PRODUCTION_BACKEND_HTTP with an interceptor that fails PUT quantity updates with 503
            val failingInterceptor = Interceptor { chain ->
                val req = chain.request()
                val path = req.url.encodedPath
                when {
                    req.method == "PUT" && path.contains("/basket/items/") -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(503)
                            .message("Service Unavailable")
                            .body("""{"message":"Backend temporarily unavailable"}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    req.method == "GET" && path.endsWith("/basket/saved") -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""[]""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    req.method == "GET" && path.endsWith("/basket") -> {
                        val json = """
                            {
                              "basketId": 1,
                              "items": [
                                {
                                  "itemId": $itemId,
                                  "productId": $p1,
                                  "productName": "Augmentin 625 Duo",
                                  "brand": "Augmentin",
                                  "saltComposition": "Paracetamol",
                                  "packSize": "15 Tabs",
                                  "hsnCode": "3004",
                                  "gstRatePercent": 12.0,
                                  "offerId": $o1,
                                  "supplierId": $supplierId,
                                  "supplierName": "Cipla Wholesale Hub",
                                  "unitPricePtr": 100.0,
                                  "mrp": 140.0,
                                  "moq": 5,
                                  "availableQuantity": 200,
                                  "quantity": 10
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
                    else -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(500)
                            .message("Unexpected")
                            .body("""{"message":"Unexpected request"}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                }
            }

            val networkModule = NetworkModule(
                tokenProvider = dataStoreManager,
                initialBaseUrl = "https://api.pharmaos.example/",
                extraInterceptors = listOf(failingInterceptor)
            )
            dataStoreManager.setAuthExecutionMode(AuthExecutionMode.PRODUCTION_BACKEND_HTTP)
            prodRepo = PharmaRepositoryImpl(
                db = db,
                dataStoreManager = dataStoreManager,
                authRemoteDataSource = AuthRemoteDataSource(networkModule),
                pharmaRemoteDataSource = PharmaRemoteDataSource(networkModule),
                networkModule = networkModule
            )
        }

        val viewModel = BasketViewModel(prodRepo)
        composeTestRule.setContent {
            PharmaOSTheme {
                SelectedProductsOrderSummaryScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onProceedToCheckout = {},
                    onExploreCatalogue = {}
                )
            }
        }

        waitForUiCondition {
            !viewModel.uiState.value.isRefreshing &&
                viewModel.uiState.value.basket?.items?.firstOrNull()?.quantity == 10
        }
        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("10")

        // Tap Quick Add +10 -> backend returns 503
        composeTestRule.onNodeWithTag("quick_add_10_$itemId").performClick()
        waitForUiCondition {
            viewModel.uiState.value.errorMessage != null
        }

        // Must NOT claim success, must keep persisted quantity at 10, and UI must stay at 10 (1120.00)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.successMessage)
        val persistedQty = runBlocking { prodRepo.getApiBasket().items.first { it.itemId == itemId }.quantity }
        assertEquals(10, persistedQty)
        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("10")
        composeTestRule.onNodeWithTag("sticky_total_order_value").assertTextContains(Formatters.formatInr(1120.0))
    }

    @Test
    fun slowInitialBackendRefresh_doesNotBlockImmediateQuickAddOrOverwriteNewerQuantity() {
        var itemId = 0L
        var p1 = 0L
        var o1 = 0L
        var supplierId = 0L
        lateinit var prodRepo: PharmaRepositoryImpl

        runBlocking {
            val buyerId = insertUser("Rohan", "Apex Medical", "buyer.slow@test.com", UserRole.STOCKIST)
            supplierId = insertUser("Vikram", "Cipla Wholesale Hub", "sup.slow@test.com", UserRole.PHARMA_COMPANY)
            dataStoreManager.setLoggedInUserId(buyerId)
            db.analyticsDao().insertOrUpdateSupplierSettings(
                SupplierSettingsEntity(
                    supplierId = supplierId,
                    deliveryCharge = 0.0,
                    freeDeliveryThreshold = 500.0
                )
            )
            p1 = insertProduct("Augmentin 625 Duo", "Augmentin", gstRate = 12.0, createdBy = supplierId)
            o1 = insertOffer(p1, supplierId, unitPricePtr = 100.0, moq = 5, availableQty = 200)
            assertTrue(repository.postApiBasketItems(p1, o1, 10).isSuccess)
            itemId = repository.getApiBasket().items.first().itemId

            val slowInitialSyncInterceptor = Interceptor { chain ->
                val req = chain.request()
                val path = req.url.encodedPath
                when {
                    req.method == "GET" && path.endsWith("/basket/saved") -> {
                        // Simulate slow secondary saved-templates refresh
                        Thread.sleep(500)
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"success":true,"data":[]}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    req.method == "PUT" && path.contains("/basket/items/") -> {
                        val json = """
                            {
                              "success": true,
                              "data": {
                                "basketId": 1,
                                "items": [
                                  {
                                    "itemId": $itemId,
                                    "productId": $p1,
                                    "productName": "Augmentin 625 Duo",
                                    "brand": "Augmentin",
                                    "saltComposition": "Paracetamol",
                                    "packSize": "15 Tabs",
                                    "hsnCode": "3004",
                                    "gstRatePercent": 12.0,
                                    "offerId": $o1,
                                    "supplierId": $supplierId,
                                    "supplierName": "Cipla Wholesale Hub",
                                    "unitPricePtr": 100.0,
                                    "mrp": 140.0,
                                    "moq": 5,
                                    "availableQuantity": 200,
                                    "quantity": 20,
                                    "effectiveUnitPrice": 100.0,
                                    "lineTotalWithGst": 2240.0
                                  }
                                ]
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
                    req.method == "GET" && path.endsWith("/basket") -> {
                        val json = """
                            {
                              "success": true,
                              "data": {
                                "basketId": 1,
                                "items": [
                                  {
                                    "itemId": $itemId,
                                    "productId": $p1,
                                    "productName": "Augmentin 625 Duo",
                                    "brand": "Augmentin",
                                    "saltComposition": "Paracetamol",
                                    "packSize": "15 Tabs",
                                    "hsnCode": "3004",
                                    "gstRatePercent": 12.0,
                                    "offerId": $o1,
                                    "supplierId": $supplierId,
                                    "supplierName": "Cipla Wholesale Hub",
                                    "unitPricePtr": 100.0,
                                    "mrp": 140.0,
                                    "moq": 5,
                                    "availableQuantity": 200,
                                    "quantity": 10,
                                    "effectiveUnitPrice": 100.0,
                                    "lineTotalWithGst": 1120.0
                                  }
                                ]
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
            }

            val networkModule = NetworkModule(
                tokenProvider = dataStoreManager,
                initialBaseUrl = "https://api.pharmaos.example/",
                extraInterceptors = listOf(slowInitialSyncInterceptor)
            )
            dataStoreManager.setAuthExecutionMode(AuthExecutionMode.PRODUCTION_BACKEND_HTTP)
            prodRepo = PharmaRepositoryImpl(
                db = db,
                dataStoreManager = dataStoreManager,
                authRemoteDataSource = AuthRemoteDataSource(networkModule),
                pharmaRemoteDataSource = PharmaRemoteDataSource(networkModule),
                networkModule = networkModule
            )
        }

        val viewModel = BasketViewModel(prodRepo)
        composeTestRule.setContent {
            PharmaOSTheme {
                SelectedProductsOrderSummaryScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onProceedToCheckout = {},
                    onExploreCatalogue = {}
                )
            }
        }

        // Wait for local basket to appear even while initial refresh is still in-flight
        waitForUiCondition {
            viewModel.uiState.value.basket?.items?.firstOrNull()?.quantity == 10
        }

        // Tap Quick Add +10 immediately while slow /basket/saved refresh is running
        composeTestRule.onNodeWithTag("quick_add_10_$itemId").performClick()

        // Must update immediately to 20 and stay at 20 even after initial refresh finishes
        waitForUiCondition {
            viewModel.uiState.value.basket?.items?.firstOrNull()?.quantity == 20 &&
                !viewModel.uiState.value.isRefreshing
        }

        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("20")
        composeTestRule.onNodeWithTag("product_subtotal_$itemId").assertTextContains(Formatters.formatInr(2240.0), substring = true)
        composeTestRule.onNodeWithTag("sticky_total_order_value").assertTextContains(Formatters.formatInr(2240.0))
    }

    @Test
    fun basketToSummaryNavHostFlow_updatesImmediatelyAndStaysSyncedOnBackAndReenter() {
        val itemId = runBlocking {
            seedSingleProductBasket(
                unitPrice = 100.0,
                gstRate = 12.0,
                moq = 5,
                availableQty = 500,
                initialQty = 10,
                deliveryCharge = 150.0,
                freeDeliveryThreshold = 5000.0
            )
        }

        val sharedViewModel = BasketViewModel(repository)
        composeTestRule.setContent {
            PharmaOSTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "basket"
                ) {
                    composable("basket") {
                        BasketScreen(
                            viewModel = sharedViewModel,
                            onNavigateBack = {},
                            onNavigateToOptimizer = {},
                            onNavigateToCheckout = {},
                            onNavigateToCatalogue = {},
                            onNavigateToSelectedProductsSummary = {
                                navController.navigate("selected_products_summary")
                            }
                        )
                    }
                    composable("selected_products_summary") {
                        SelectedProductsOrderSummaryScreen(
                            viewModel = sharedViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onProceedToCheckout = {},
                            onExploreCatalogue = {}
                        )
                    }
                }
            }
        }

        // Start on BasketScreen with qty = 10 -> payable 1270.00
        waitForUiCondition { sharedViewModel.uiState.value.basket?.items?.firstOrNull()?.quantity == 10 }
        composeTestRule.onNodeWithTag("basket_qty_$itemId").assertTextContains("10")
        composeTestRule.onNodeWithTag("basket_bottom_payable_amount").assertTextContains(Formatters.formatInr(1270.0))

        // Navigate to Review Quantities & Order Value Summary
        composeTestRule.onNodeWithTag("view_selected_products_summary_link").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selected_products_screen").assertIsDisplayed()

        // Tap Quick Add +100 -> qty 110 -> taxable 11000 (>= 5000 free delivery), GST 1320 -> grandTotal 12320
        val revisionBefore = sharedViewModel.uiState.value.basketRevision
        composeTestRule.onNodeWithTag("quick_add_100_$itemId").performClick()
        waitForUiCondition { sharedViewModel.uiState.value.basket?.items?.firstOrNull()?.quantity == 110 }
        assertTrue(sharedViewModel.uiState.value.basketRevision > revisionBefore)

        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("110")
        composeTestRule.onNodeWithTag("product_subtotal_$itemId").assertTextContains(Formatters.formatInr(12320.0), substring = true)
        composeTestRule.onNodeWithTag("selected_products_header_summary").assertTextContains("1 item(s) • 110 total units")
        composeTestRule.onNodeWithTag("sticky_total_order_value").assertTextContains(Formatters.formatInr(12320.0))

        // Navigate back to BasketScreen -> must immediately reflect qty 110 & 12320.00
        composeTestRule.onNodeWithTag("selected_products_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("basket_qty_$itemId").assertTextContains("110")
        composeTestRule.onNodeWithTag("basket_bottom_payable_amount").assertTextContains(Formatters.formatInr(12320.0))

        // Re-enter Review Quantities & Order Value Summary -> still 110 and can decrease to 109 immediately
        composeTestRule.onNodeWithTag("view_selected_products_summary_link").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("110")
        composeTestRule.onNodeWithTag("quantity_minus_$itemId").performClick()
        waitForUiCondition { sharedViewModel.uiState.value.basket?.items?.firstOrNull()?.quantity == 109 }
        // 109 * 100 = 10900, GST 12% = 1308, grandTotal = 12208
        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("109")
        composeTestRule.onNodeWithTag("sticky_total_order_value").assertTextContains(Formatters.formatInr(12208.0))
    }

    @Test
    fun schemeFreeUnitsAndMultiSupplierTotals_recalculateImmediatelyOnQuickAdd() {
        var itemId = 0L
        runBlocking {
            val buyerId = insertUser("Rohan", "Apex Medical", "buyer.scheme@test.com", UserRole.STOCKIST)
            val supplierId = insertUser("Vikram", "Cipla Scheme Hub", "sup.scheme@test.com", UserRole.PHARMA_COMPANY)
            dataStoreManager.setLoggedInUserId(buyerId)
            db.analyticsDao().insertOrUpdateSupplierSettings(
                SupplierSettingsEntity(
                    supplierId = supplierId,
                    deliveryCharge = 200.0,
                    freeDeliveryThreshold = 5000.0
                )
            )
            val p1 = insertProduct("Augmentin 625 Duo", "Augmentin", gstRate = 12.0, createdBy = supplierId)
            val o1 = insertOffer(
                productId = p1,
                supplierId = supplierId,
                unitPricePtr = 100.0,
                moq = 5,
                availableQty = 500,
                schemeType = SchemeType.BUY_X_GET_Y_FREE,
                buyQty = 10,
                freeQty = 1
            )
            assertTrue(repository.postApiBasketItems(p1, o1, 10).isSuccess)
            itemId = repository.getApiBasket().items.first().itemId
        }

        val viewModel = BasketViewModel(repository)
        composeTestRule.setContent {
            PharmaOSTheme {
                SelectedProductsOrderSummaryScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onProceedToCheckout = {},
                    onExploreCatalogue = {}
                )
            }
        }

        waitForUiCondition { viewModel.uiState.value.basket?.items?.firstOrNull()?.quantity == 10 }
        assertEquals(1, viewModel.uiState.value.basket?.items?.firstOrNull()?.pricing?.freeQuantity)

        // Tap Quick Add +50 -> qty 60 => 6 free units, taxable 6000 (>= 5000 free delivery!), GST 720 -> total 6720
        composeTestRule.onNodeWithTag("quick_add_50_$itemId").performClick()
        waitForUiCondition {
            val item = viewModel.uiState.value.basket?.items?.firstOrNull()
            item?.quantity == 60 && item.pricing.freeQuantity == 6
        }

        composeTestRule.onNodeWithTag("quantity_display_$itemId").assertTextContains("60")
        composeTestRule.onNodeWithTag("product_subtotal_$itemId").assertTextContains(Formatters.formatInr(6720.0), substring = true)
        composeTestRule.onNodeWithTag("sticky_total_order_value").assertTextContains(Formatters.formatInr(6720.0))
    }
}

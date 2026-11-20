package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.example.ui.screens.orders.CheckoutScreen
import com.example.ui.screens.orders.OrdersViewModel
import com.example.ui.screens.orders.PaymentScreen
import com.example.ui.theme.PharmaOSTheme
import com.example.util.Formatters
import com.example.util.PasswordHasher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import org.junit.Assert.assertNotNull
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
class CheckoutOrderReviewTest {

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

    private fun waitForUiCondition(
        timeoutMs: Long = 6000,
        advanceClockMs: Long = 0L,
        predicate: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            if (advanceClockMs > 0L) {
                composeTestRule.mainClock.advanceTimeBy(advanceClockMs)
            }
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
        email: String,
        fullName: String,
        businessName: String,
        role: UserRole,
        gstin: String,
        address: String
    ): Long {
        val hash = PasswordHasher.hashPassword("Password@123")
        return db.userDao().insertUser(
            UserEntity(
                fullName = fullName,
                businessName = businessName,
                email = email,
                phone = "9876543210",
                gstin = gstin,
                address = address,
                passwordHash = hash.hashHex,
                passwordSalt = hash.saltHex,
                role = role.name,
                status = AccountStatus.ACTIVE.name
            )
        )
    }

    private data class SeededCheckoutFixture(
        val buyerId: Long,
        val supplier1Id: Long,
        val supplier2Id: Long,
        val offer1Id: Long,
        val offer2Id: Long,
        val product1Name: String,
        val product2Name: String
    )

    private suspend fun seedMultiSupplierBasket(): SeededCheckoutFixture {
        val buyerId = insertUser(
            email = "buyer.checkout@pharmaos.com",
            fullName = "Rohan Sharma",
            businessName = "Apex Care Pharmacy Pvt Ltd",
            role = UserRole.STOCKIST,
            gstin = "27AABCA1234F1Z5",
            address = "Plot 44, Andheri Pharma Hub, Mumbai 400053"
        )
        val supplier1Id = insertUser(
            email = "alkem.dist@pharmaos.com",
            fullName = "Vikram Desai",
            businessName = "Alkem Lab Private Limited",
            role = UserRole.PHARMA_COMPANY,
            gstin = "27AAACA8888B1Z2",
            address = "MIDC Taloja, Navi Mumbai"
        )
        val supplier2Id = insertUser(
            email = "cipla.dist@pharmaos.com",
            fullName = "Meera Nair",
            businessName = "Cipla Medico Logistics",
            role = UserRole.MANUFACTURER,
            gstin = "27AAACC7777C1Z8",
            address = "Vikhroli East, Mumbai"
        )

        db.analyticsDao().insertOrUpdateSupplierSettings(
            SupplierSettingsEntity(
                supplierId = supplier1Id,
                deliveryCharge = 0.0,
                freeDeliveryThreshold = 500.0
            )
        )
        db.analyticsDao().insertOrUpdateSupplierSettings(
            SupplierSettingsEntity(
                supplierId = supplier2Id,
                deliveryCharge = 120.0,
                freeDeliveryThreshold = 5000.0
            )
        )

        val product1Id = db.productDao().insertProduct(
            ProductEntity(
                name = "Pan-D Capsule SR",
                brand = "Alkem",
                saltComposition = "Pantoprazole 40mg + Domperidone 30mg",
                strength = "40mg/30mg",
                dosageForm = DosageForm.CAPSULE.name,
                therapeuticCategory = "Gastroenterology",
                manufacturerName = "Alkem Laboratories",
                packSize = "15 Capsules",
                hsnCode = "30049099",
                gstRatePercent = 12.0,
                storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                isRx = true,
                createdBy = supplier1Id
            )
        )
        val product2Id = db.productDao().insertProduct(
            ProductEntity(
                name = "Azee 500 Tablet",
                brand = "Cipla",
                saltComposition = "Azithromycin 500mg",
                strength = "500mg",
                dosageForm = DosageForm.TABLET.name,
                therapeuticCategory = "Antibiotics",
                manufacturerName = "Cipla Limited",
                packSize = "5 Tablets",
                hsnCode = "30042064",
                gstRatePercent = 12.0,
                storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                isRx = true,
                createdBy = supplier2Id
            )
        )

        val offer1Id = db.offerDao().insertOffer(
            SupplierOfferEntity(
                productId = product1Id,
                supplierId = supplier1Id,
                unitPricePtr = 120.0,
                mrp = 180.0,
                moq = 2,
                availableQuantity = 100,
                schemeType = SchemeType.BUY_X_GET_Y_FREE.name,
                schemeBuyQty = 5,
                schemeFreeQty = 1,
                validTillEpochMillis = futureExpiry
            )
        )
        val offer2Id = db.offerDao().insertOffer(
            SupplierOfferEntity(
                productId = product2Id,
                supplierId = supplier2Id,
                unitPricePtr = 90.0,
                mrp = 125.0,
                moq = 1,
                availableQuantity = 80,
                schemeType = SchemeType.NONE.name,
                validTillEpochMillis = futureExpiry
            )
        )

        dataStoreManager.setLoggedInUserId(buyerId)

        assertTrue(repository.postApiBasketItems(product1Id, offer1Id, 6).isSuccess)
        assertTrue(repository.postApiBasketItems(product2Id, offer2Id, 4).isSuccess)

        return SeededCheckoutFixture(
            buyerId = buyerId,
            supplier1Id = supplier1Id,
            supplier2Id = supplier2Id,
            offer1Id = offer1Id,
            offer2Id = offer2Id,
            product1Name = "Pan-D Capsule SR",
            product2Name = "Azee 500 Tablet"
        )
    }

    @Test
    fun checkoutScreen_removesTopBarAndDuplicateInlinePaymentButtons_andDisplaysCompleteOrderReview() {
        val fixture = runBlocking { seedMultiSupplierBasket() }
        val expectedBasket = runBlocking { repository.getApiBasket() }
        assertEquals(2, expectedBasket.items.size)
        assertEquals(2, expectedBasket.supplierSubtotals.size)

        val viewModel = OrdersViewModel(repository)

        composeTestRule.setContent {
            PharmaOSTheme {
                CheckoutScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onProceedToPayment = { _, _ -> }
                )
            }
        }

        waitForUiCondition {
            viewModel.uiState.value.basketForCheckout?.items?.size == 2 &&
                viewModel.uiState.value.currentUser?.id == fixture.buyerId
        }

        // 1 & 2: Top bar Payment button and duplicate inline Payment button MUST NOT exist
        assertEquals(
            "Top bar payment button must be removed",
            0,
            composeTestRule.onAllNodesWithTag("top_bar_submit_order_button").fetchSemanticsNodes().size
        )
        assertEquals(
            "Duplicate inline payment button must be removed",
            0,
            composeTestRule.onAllNodesWithTag("inline_confirm_order_placement_button").fetchSemanticsNodes().size
        )

        // Single clear primary CTA in bottom bar
        composeTestRule.onNodeWithTag("confirm_order_placement_button")
            .assertIsDisplayed()
            .assertIsEnabled()

        // Final order value visible in sticky bottom bar
        composeTestRule.onNodeWithTag("checkout_final_order_value")
            .assertIsDisplayed()
            .assertTextContains(Formatters.formatInr(expectedBasket.grandTotal), substring = true)

        // Billing details visible
        composeTestRule.onNodeWithTag("checkout_billing_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Apex Care Pharmacy Pvt Ltd", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("27AABCA1234F1Z5", substring = true).assertIsDisplayed()

        // Delivery address prefilled with user's registered address
        composeTestRule.onNodeWithTag("checkout_address_input")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Plot 44, Andheri Pharma Hub, Mumbai 400053", substring = true)

        // Selected products, quantities, and supplier allocation cards visible
        composeTestRule.onNodeWithTag("checkout_supplier_card_${fixture.supplier1Id}")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Alkem Lab Private Limited", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(fixture.product1Name, substring = true).assertIsDisplayed()

        val item1 = expectedBasket.items.first { it.productName == fixture.product1Name }
        composeTestRule.onNodeWithTag("checkout_item_qty_${item1.itemId}")
            .assertTextContains("Qty: 6", substring = true)

        composeTestRule.onNodeWithTag("checkout_supplier_card_${fixture.supplier2Id}")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Cipla Medico Logistics", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(fixture.product2Name, substring = true).assertIsDisplayed()

        val item2 = expectedBasket.items.first { it.productName == fixture.product2Name }
        composeTestRule.onNodeWithTag("checkout_item_qty_${item2.itemId}")
            .assertTextContains("Qty: 4", substring = true)

        // Consolidated final order value summary charges visible
        composeTestRule.onNodeWithTag("checkout_final_summary_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("checkout_summary_subtotal")
            .assertTextContains(Formatters.formatInr(expectedBasket.subtotal), substring = true)
        composeTestRule.onNodeWithTag("checkout_summary_gst")
            .assertTextContains(Formatters.formatInr(expectedBasket.totalGst), substring = true)
        composeTestRule.onNodeWithTag("checkout_summary_delivery")
            .assertTextContains(Formatters.formatInr(expectedBasket.totalDeliveryCharges), substring = true)
        composeTestRule.onNodeWithTag("checkout_summary_grand_total")
            .assertTextContains(Formatters.formatInr(expectedBasket.grandTotal), substring = true)
    }

    @Test
    fun checkoutToPaymentFlow_preservesPaymentIntegrationAndPreventsDuplicateOrderSubmission() {
        runBlocking { seedMultiSupplierBasket() }
        val viewModel = OrdersViewModel(repository)

        var currentStep by mutableStateOf("CHECKOUT")
        var forwardedAddress by mutableStateOf("")
        var forwardedNotes by mutableStateOf("")
        val successInvocations = AtomicInteger(0)
        var confirmedOrderIds by mutableStateOf<List<Long>>(emptyList())

        composeTestRule.setContent {
            PharmaOSTheme {
                when (currentStep) {
                    "CHECKOUT" -> CheckoutScreen(
                        viewModel = viewModel,
                        onNavigateBack = {},
                        onProceedToPayment = { addr, notes ->
                            forwardedAddress = addr
                            forwardedNotes = notes
                            currentStep = "PAYMENT"
                        }
                    )
                    "PAYMENT" -> PaymentScreen(
                        viewModel = viewModel,
                        deliveryAddress = forwardedAddress,
                        orderNotes = forwardedNotes,
                        onNavigateBack = { currentStep = "CHECKOUT" },
                        onPaymentSuccess = { ids, _, _ ->
                            successInvocations.incrementAndGet()
                            confirmedOrderIds = ids
                            currentStep = "CONFIRMED"
                        }
                    )
                }
            }
        }

        waitForUiCondition {
            viewModel.uiState.value.basketForCheckout?.items?.size == 2
        }

        // Edit delivery address and dispatch notes on Checkout Screen
        composeTestRule.onNodeWithTag("checkout_address_input").performScrollTo()
        composeTestRule.onNodeWithTag("checkout_address_input").performTextClearance()
        composeTestRule.onNodeWithTag("checkout_address_input").performTextInput("Warehouse 9, Bhiwandi Pharma Park")

        composeTestRule.onNodeWithTag("checkout_notes_input").performScrollTo()
        composeTestRule.onNodeWithTag("checkout_notes_input").performTextInput("Deliver before 4 PM")

        // Tap single Checkout primary CTA -> transitions cleanly to Payment stage
        composeTestRule.onNodeWithTag("confirm_order_placement_button").performClick()
        waitForUiCondition { currentStep == "PAYMENT" }

        assertEquals("Warehouse 9, Bhiwandi Pharma Park", forwardedAddress)
        assertEquals("Deliver before 4 PM", forwardedNotes)

        // PaymentScreen retains existing payment options and primary authorization CTA
        composeTestRule.onNodeWithText("Pharma Wholesale Credit Line", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("pay_and_place_order_button")
            .assertIsDisplayed()
            .assertIsEnabled()

        // Tap Authorize Order twice rapidly to verify duplicate submission protection
        composeTestRule.onNodeWithTag("pay_and_place_order_button").performClick()
        composeTestRule.onNodeWithTag("pay_and_place_order_button").performClick()

        waitForUiCondition(timeoutMs = 8000, advanceClockMs = 200L) { currentStep == "CONFIRMED" }

        assertEquals("Payment success callback must fire exactly once", 1, successInvocations.get())
        assertEquals("Two supplier POs expected (one per supplier, no duplicates)", 2, confirmedOrderIds.size)

        runBlocking {
            val placedOrders = db.orderDao().getAllOrdersFlow().first()
                .filter { it.deliveryAddress == "Warehouse 9, Bhiwandi Pharma Park" }
            assertEquals("Exactly 2 supplier orders persisted (no duplicate orders)", 2, placedOrders.size)
            assertTrue(placedOrders.all { it.notes == "Deliver before 4 PM" })
            assertEquals(confirmedOrderIds.toSet(), placedOrders.map { it.id }.toSet())

            val basketAfter = repository.getApiBasket()
            assertTrue("Basket must be cleared after confirmed placement", basketAfter.items.isEmpty())
        }
    }

    @Test
    fun concurrentOrdersViewModelPlaceOrders_createsOnlySingleSetOfOrders() = runBlocking {
        seedMultiSupplierBasket()
        val viewModel = OrdersViewModel(repository)

        val successCount = AtomicInteger(0)
        val results = listOf(
            async {
                viewModel.placeOrders(
                    deliveryAddress = "Dock 1, Mumbai",
                    notes = "Urgent",
                    onSuccess = { successCount.incrementAndGet() }
                )
            },
            async {
                viewModel.placeOrders(
                    deliveryAddress = "Dock 1, Mumbai",
                    notes = "Urgent",
                    onSuccess = { successCount.incrementAndGet() }
                )
            }
        )
        results.awaitAll()

        // Allow viewModelScope coroutine to finish
        val deadline = System.currentTimeMillis() + 5000
        while (viewModel.uiState.value.isPlacingOrder ||
            db.orderDao().getAllOrdersFlow().first().none { it.deliveryAddress == "Dock 1, Mumbai" }
        ) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > deadline) break
            Thread.sleep(20)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val placedOrders = db.orderDao().getAllOrdersFlow().first()
            .filter { it.deliveryAddress == "Dock 1, Mumbai" }
        assertEquals("Concurrent submissions must not create duplicate orders", 2, placedOrders.size)
        assertEquals("Only one submission callback should succeed", 1, successCount.get())
    }

    @Test
    fun productionBackendMode_orderCreationSucceedsOnlyAfterServerConfirmation() = runBlocking {
        val fixture = seedMultiSupplierBasket()

        var serverMode = "FAIL_500"
        val postOrderHits = AtomicInteger(0)

        val testInterceptor = Interceptor { chain ->
            val req = chain.request()
            if (req.method == "POST" && req.url.encodedPath.endsWith("/api/v1/orders")) {
                postOrderHits.incrementAndGet()
                val (code, bodyJson) = when (serverMode) {
                    "FAIL_500" -> 500 to """{"success":false,"error":"Order service temporarily unavailable"}"""
                    "EMPTY_200" -> 200 to """{"success":true,"data":[]}"""
                    else -> 200 to """
                        {
                          "success": true,
                          "data": [
                            {
                              "id": 777,
                              "orderNumber": "PO-SRV-777",
                              "buyerId": ${fixture.buyerId},
                              "supplierId": ${fixture.supplier1Id},
                              "status": "PLACED",
                              "subtotal": 720.0,
                              "gstAmount": 86.4,
                              "deliveryCharge": 0.0,
                              "totalAmount": 806.4,
                              "deliveryAddress": "Plot 44, Andheri Pharma Hub, Mumbai 400053",
                              "notes": "Verified backend order",
                              "createdAtEpochMillis": ${System.currentTimeMillis()}
                            }
                          ]
                        }
                    """.trimIndent()
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Server Error")
                    .body(bodyJson.toResponseBody("application/json".toMediaType()))
                    .build()
            } else {
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"success":true,"data":[]}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
        }

        val networkModule = NetworkModule(
            tokenProvider = dataStoreManager,
            initialBaseUrl = "https://api.pharmaos.test/",
            extraInterceptors = listOf(testInterceptor)
        )
        dataStoreManager.setAuthExecutionMode(AuthExecutionMode.PRODUCTION_BACKEND_HTTP)
        val remoteRepo = PharmaRepositoryImpl(
            db = db,
            dataStoreManager = dataStoreManager,
            authRemoteDataSource = AuthRemoteDataSource(networkModule),
            pharmaRemoteDataSource = PharmaRemoteDataSource(networkModule),
            networkModule = networkModule
        )

        // 1. Server returns HTTP 500 -> MUST fail, basket MUST remain intact
        serverMode = "FAIL_500"
        val failedResult = remoteRepo.postApiOrders("Plot 44, Andheri Pharma Hub, Mumbai 400053", "Test")
        assertTrue("Order placement must fail when backend returns 500", failedResult.isFailure)
        assertEquals(
            "Basket items must NOT be cleared on backend failure",
            2,
            remoteRepo.getApiBasket().items.size
        )

        // 2. Server returns 200 with empty order list -> MUST NOT claim success
        serverMode = "EMPTY_200"
        val emptyConfirmResult = remoteRepo.postApiOrders("Plot 44, Andheri Pharma Hub, Mumbai 400053", "Test")
        assertTrue("Order placement must fail if backend confirms zero orders", emptyConfirmResult.isFailure)
        assertEquals(
            "Basket items must NOT be cleared without backend order confirmation",
            2,
            remoteRepo.getApiBasket().items.size
        )

        // 3. Server returns HTTP 200 with confirmed OrderDto -> succeeds, caches order, clears basket
        serverMode = "SUCCESS_200"
        val confirmedResult = remoteRepo.postApiOrders("Plot 44, Andheri Pharma Hub, Mumbai 400053", "Verified backend order")
        assertTrue("Order placement must succeed after backend confirmation", confirmedResult.isSuccess)
        assertEquals(listOf(777L), confirmedResult.getOrNull())

        val cachedOrder = db.orderDao().getOrderById(777L)
        assertNotNull("Backend-confirmed order must be cached locally", cachedOrder)
        assertEquals("PO-SRV-777", cachedOrder?.orderNumber)
        assertTrue("Basket must be cleared after backend confirmation", remoteRepo.getApiBasket().items.isEmpty())
        assertFalse("Backend endpoint must have been called for all 3 attempts", postOrderHits.get() < 3)
    }
}

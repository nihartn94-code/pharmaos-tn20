package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.local.entity.BatchEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ProductInventoryEntity
import com.example.data.local.entity.RecallEntity
import com.example.data.local.entity.SupplierOfferEntity
import com.example.data.local.entity.SupplierPerformanceEntity
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
import com.example.domain.usecase.ProductQuotationUseCase
import com.example.ui.screens.catalogue.CatalogueUiState
import com.example.ui.screens.catalogue.CatalogueViewModel
import com.example.ui.screens.catalogue.MedicineDetailsContent
import com.example.ui.screens.catalogue.MedicineDetailsScreen
import com.example.ui.theme.PharmaOSTheme
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
class ProductDetailsQuotationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var repository: PharmaRepositoryImpl

    private val futureExpiry = System.currentTimeMillis() + 180L * 24 * 3600 * 1000
    private val pastExpiry = System.currentTimeMillis() - 10L * 24 * 3600 * 1000

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
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            composeTestRule.waitForIdle()
            if (predicate()) {
                composeTestRule.waitForIdle()
                return
            }
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out after $timeoutMs ms waiting for condition")
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
        id: Long = 0L,
        name: String,
        brand: String,
        salt: String = "Amoxicillin + Clavulanic Acid",
        strength: String = "625mg",
        manufacturer: String = "Cipla Ltd",
        gstRate: Double = 12.0,
        isActive: Boolean = true,
        createdBy: Long = 1L
    ): Long {
        return db.productDao().insertProduct(
            ProductEntity(
                id = id,
                name = name,
                brand = brand,
                saltComposition = salt,
                strength = strength,
                dosageForm = DosageForm.TABLET.name,
                therapeuticCategory = "Antibiotics",
                manufacturerName = manufacturer,
                packSize = "Strip of 10 Tabs",
                hsnCode = "30041010",
                gstRatePercent = gstRate,
                storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                isRx = true,
                isActive = isActive,
                createdBy = createdBy
            )
        )
    }

    @Test
    fun addToBasket_updatesCountImmediately_preventsDuplicates_andValidatesMoqAndStock() {
        var buyerId = 0L
        var productId = 0L
        var offerId = 0L

        runBlocking {
            buyerId = insertUser("Rohan Buyer", "Apex Stockists", "buyer@test.com", UserRole.STOCKIST)
            val supplierId = insertUser("Vikram Supplier", "MedLink Wholesale", "sup@test.com", UserRole.MANUFACTURER)
            dataStoreManager.setLoggedInUserId(buyerId)

            productId = insertProduct(
                name = "Augmentin 625 Duo",
                brand = "Augmentin",
                createdBy = supplierId
            )
            offerId = db.offerDao().insertOffer(
                SupplierOfferEntity(
                    productId = productId,
                    supplierId = supplierId,
                    unitPricePtr = 120.0,
                    mrp = 180.0,
                    moq = 5,
                    availableQuantity = 12,
                    schemeType = SchemeType.NONE.name,
                    validTillEpochMillis = futureExpiry,
                    isActive = true
                )
            )
            val noOfferProductId = insertProduct(
                name = "NoQuote Tab",
                brand = "NoQuote",
                salt = "UniqueSalt",
                strength = "100mg",
                createdBy = supplierId
            )

            val belowMoqRes = repository.postApiBasketItems(productId, offerId, 2)
            assertTrue("Quantity below MOQ must fail", belowMoqRes.isFailure)
            assertTrue(belowMoqRes.exceptionOrNull()?.message?.contains("MOQ", ignoreCase = true) == true)

            val noOfferRes = repository.postApiBasketItems(noOfferProductId, 0L, 5)
            assertTrue("Missing offer must fail without creating fake offer", noOfferRes.isFailure)
            assertTrue(db.offerDao().getAllOffersForProduct(noOfferProductId).isEmpty())
        }

        val viewModel = CatalogueViewModel(repository)

        composeTestRule.setContent {
            PharmaOSTheme {
                MedicineDetailsScreen(
                    productId = productId,
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onProductClick = {},
                    onAddOfferClick = {}
                )
            }
        }
        waitForUiCondition { viewModel.uiState.value.supplierQuotations.isNotEmpty() }

        composeTestRule.onNodeWithTag("details_basket_badge").assertDoesNotExist()

        composeTestRule.onNodeWithTag("medicine_details_list")
            .performScrollToNode(hasTestTag("offer_add_to_basket_button_$offerId"))
        composeTestRule.onNodeWithTag("offer_add_to_basket_button_$offerId")
            .assertIsEnabled()
            .performClick()

        waitForUiCondition { viewModel.uiState.value.cartItemsCount == 5 }

        composeTestRule.onNodeWithTag("details_basket_badge", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("details_basket_button")
            .assertIsDisplayed()
            .assertTextContains("5")

        runBlocking {
            val activeBasket = db.basketDao().getActiveBasketForBuyer(buyerId)
            assertNotNull(activeBasket)
            val basketRows = db.basketDao().getBasketItems(activeBasket!!.id)
            assertEquals(1, basketRows.size)
            assertEquals(5, basketRows[0].quantity)
        }

        // Second click merges into same BasketItemEntity row without duplicate entries
        composeTestRule.onNodeWithTag("offer_add_to_basket_button_$offerId").performClick()
        waitForUiCondition { viewModel.uiState.value.cartItemsCount == 10 }

        composeTestRule.onNodeWithTag("details_basket_button").assertTextContains("10")
        runBlocking {
            val activeBasket = db.basketDao().getActiveBasketForBuyer(buyerId)!!
            val basketRows = db.basketDao().getBasketItems(activeBasket.id)
            assertEquals("Must not create duplicate basket rows for the same product", 1, basketRows.size)
            assertEquals(10, basketRows[0].quantity)
        }

        // Third click tries 10 + 5 = 15 > 12 stock -> rejected cleanly with error message
        composeTestRule.onNodeWithTag("offer_add_to_basket_button_$offerId").performClick()
        composeTestRule.waitForIdle()

        runBlocking {
            val activeBasket = db.basketDao().getActiveBasketForBuyer(buyerId)!!
            assertEquals(10, db.basketDao().getBasketItems(activeBasket.id).single().quantity)
        }
        assertEquals(10, viewModel.uiState.value.cartItemsCount)
    }

    @Test
    fun multipleSuppliers_sortsByLowestEffectiveUnitCost_andDisplaysSummary() {
        var productId = 0L
        var offerAId = 0L
        var offerBId = 0L

        runBlocking {
            val buyerId = insertUser("Buyer", "Buyer Co", "b@co.com", UserRole.STOCKIST)
            val supA = insertUser("Sup A", "LowPtr Freight Pharma", "a@sup.com", UserRole.MANUFACTURER)
            val supB = insertUser("Sup B", "BonusScheme Direct", "b@sup.com", UserRole.MANUFACTURER)
            dataStoreManager.setLoggedInUserId(buyerId)

            productId = insertProduct(
                name = "Azithral 500",
                brand = "Azithral",
                salt = "Azithromycin",
                strength = "500mg",
                gstRate = 12.0,
                createdBy = supA
            )

            // Supplier A: lower raw PTR (90.0), ₹200 delivery charge (<5000) -> 1208/10 = ₹120.80/unit
            offerAId = db.offerDao().insertOffer(
                SupplierOfferEntity(
                    productId = productId,
                    supplierId = supA,
                    unitPricePtr = 90.0,
                    mrp = 150.0,
                    moq = 10,
                    availableQuantity = 50,
                    schemeType = SchemeType.NONE.name,
                    validTillEpochMillis = futureExpiry,
                    isActive = true
                )
            )
            db.analyticsDao().insertOrUpdateSupplierSettings(
                SupplierSettingsEntity(
                    supplierId = supA,
                    deliveryCharge = 200.0,
                    freeDeliveryThreshold = 5000.0,
                    averageLeadTimeDays = 2.0
                )
            )

            // Supplier B: higher raw PTR (95.0), Buy 10 Get 2 Free + free delivery -> 1064/12 = ₹88.67/unit
            offerBId = db.offerDao().insertOffer(
                SupplierOfferEntity(
                    productId = productId,
                    supplierId = supB,
                    unitPricePtr = 95.0,
                    mrp = 150.0,
                    moq = 10,
                    availableQuantity = 60,
                    schemeType = SchemeType.BUY_X_GET_Y_FREE.name,
                    schemeBuyQty = 10,
                    schemeFreeQty = 2,
                    validTillEpochMillis = futureExpiry,
                    isActive = true
                )
            )
            db.analyticsDao().insertOrUpdateSupplierSettings(
                SupplierSettingsEntity(
                    supplierId = supB,
                    deliveryCharge = 0.0,
                    freeDeliveryThreshold = 0.0,
                    averageLeadTimeDays = 1.5
                )
            )

            val (summary, sortedQuotations) = repository.getApiProductQuotationsFlow(productId).first()

            assertEquals(2, sortedQuotations.size)
            assertEquals(offerBId, sortedQuotations[0].offer.offerId)
            assertEquals(offerAId, sortedQuotations[1].offer.offerId)
            assertEquals(88.6667, sortedQuotations[0].effectiveUnitCost!!, 0.01)
            assertEquals(120.80, sortedQuotations[1].effectiveUnitCost!!, 0.01)

            assertEquals(offerBId, summary.bestOfferId)
            assertEquals(90.0, summary.lowestUnitPricePtr!!, 0.01)
            assertEquals(88.6667, summary.lowestEffectiveUnitCost!!, 0.01)
            assertEquals(2, summary.availableSuppliersCount)
            assertEquals(110, summary.totalQuotedAvailableStock)
        }

        val viewModel = CatalogueViewModel(repository)
        composeTestRule.setContent {
            PharmaOSTheme {
                MedicineDetailsScreen(
                    productId = productId,
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onProductClick = {},
                    onAddOfferClick = {}
                )
            }
        }
        waitForUiCondition { viewModel.uiState.value.supplierQuotations.size == 2 }

        composeTestRule.onNodeWithTag("summary_lowest_unit_price").assertTextContains("₹90.00")
        composeTestRule.onNodeWithTag("summary_suppliers_count").assertTextContains("2 of 2")
        composeTestRule.onNodeWithTag("quoted_stock_value").assertTextContains("110 units")
        composeTestRule.onNodeWithTag("medicine_details_list")
            .performScrollToNode(hasTestTag("supplier_offer_card_$offerBId"))
        composeTestRule.onNodeWithText("Lowest Eff. Cost").assertIsDisplayed()
    }

    @Test
    fun differentMoqs_unavailableOfferExclusion_andQuotedVsVerifiedStock() {
        var productId = 0L
        var unavailableOfferId = 0L

        runBlocking {
            val sup1 = insertUser("Sup 1", "Verified Hub", "s1@co.com", UserRole.MANUFACTURER)
            val sup2 = insertUser("Sup 2", "Standard Distributors", "s2@co.com", UserRole.PHARMA_COMPANY)
            val sup3 = insertUser("Sup 3", "Expired Quotes Co", "s3@co.com", UserRole.MANUFACTURER)

            productId = insertProduct(
                name = "Pan-D Capsule",
                brand = "Pan-D",
                salt = "Pantoprazole + Domperidone",
                strength = "40mg + 30mg",
                createdBy = sup1
            )

            db.offerDao().insertOffer(
                SupplierOfferEntity(
                    productId = productId,
                    supplierId = sup1,
                    unitPricePtr = 70.0,
                    mrp = 110.0,
                    moq = 5,
                    availableQuantity = 80,
                    schemeType = SchemeType.NONE.name,
                    validTillEpochMillis = futureExpiry,
                    isActive = true
                )
            )
            db.offerDao().insertOffer(
                SupplierOfferEntity(
                    productId = productId,
                    supplierId = sup2,
                    unitPricePtr = 75.0,
                    mrp = 110.0,
                    moq = 15,
                    availableQuantity = 40,
                    schemeType = SchemeType.NONE.name,
                    validTillEpochMillis = futureExpiry,
                    isActive = true
                )
            )
            unavailableOfferId = db.offerDao().insertOffer(
                SupplierOfferEntity(
                    productId = productId,
                    supplierId = sup3,
                    unitPricePtr = 50.0,
                    mrp = 110.0,
                    moq = 20,
                    availableQuantity = 4,
                    schemeType = SchemeType.NONE.name,
                    validTillEpochMillis = futureExpiry,
                    isActive = true
                )
            )

            db.batchDao().insertBatch(
                BatchEntity(
                    productId = productId,
                    supplierId = sup1,
                    batchNumber = "B-VALID-01",
                    mfgDateEpochMillis = pastExpiry,
                    expiryDateEpochMillis = futureExpiry,
                    initialQuantity = 40,
                    remainingQuantity = 30,
                    storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                    mrp = 110.0,
                    ptr = 70.0
                )
            )
            db.batchDao().insertBatch(
                BatchEntity(
                    productId = productId,
                    supplierId = sup1,
                    batchNumber = "B-EXPIRED-02",
                    mfgDateEpochMillis = pastExpiry - 1000L,
                    expiryDateEpochMillis = pastExpiry,
                    initialQuantity = 50,
                    remainingQuantity = 50,
                    storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                    mrp = 110.0,
                    ptr = 70.0
                )
            )
            db.batchDao().insertBatch(
                BatchEntity(
                    productId = productId,
                    supplierId = sup2,
                    batchNumber = "B-RECALLED-03",
                    mfgDateEpochMillis = pastExpiry,
                    expiryDateEpochMillis = futureExpiry,
                    initialQuantity = 25,
                    remainingQuantity = 25,
                    storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                    mrp = 110.0,
                    ptr = 75.0
                )
            )
            db.recallDao().insertRecall(
                RecallEntity(
                    productId = productId,
                    productName = "Pan-D Capsule",
                    batchNumbersCsv = "B-RECALLED-03",
                    reason = "Quality check hold",
                    severity = "HIGH",
                    createdBySupplierId = sup2,
                    isActive = true
                )
            )
            db.productInventoryDao().insertInventory(
                ProductInventoryEntity(
                    productId = productId,
                    productName = "Pan-D Capsule",
                    sku = "SKU-PAND-TEST",
                    currentStock = 12,
                    reorderThreshold = 5,
                    isActive = true
                )
            )

            val (summary, quotations) = repository.getApiProductQuotationsFlow(productId).first()

            assertEquals(5, summary.minMoq)
            assertEquals(15, summary.maxMoq)
            assertEquals(2, summary.availableSuppliersCount)
            assertEquals(3, summary.totalOffersCount)
            assertEquals(120, summary.totalQuotedAvailableStock)
            assertTrue(summary.hasVerifiedStockRecords)
            assertEquals(42, summary.verifiedAvailableStock)
            assertEquals(70.0, summary.lowestUnitPricePtr!!, 0.01)

            val unavailableItem = quotations.first { it.offer.offerId == unavailableOfferId }
            assertFalse(unavailableItem.isEligibleForPurchase)
            assertTrue(unavailableItem.unavailabilityReason!!.contains("below MOQ"))
        }

        val viewModel = CatalogueViewModel(repository)
        composeTestRule.setContent {
            PharmaOSTheme {
                MedicineDetailsScreen(
                    productId = productId,
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onProductClick = {},
                    onAddOfferClick = {}
                )
            }
        }
        waitForUiCondition { viewModel.uiState.value.supplierQuotations.size == 3 }

        composeTestRule.onNodeWithTag("summary_moq").assertTextContains("5 – 15 units")
        composeTestRule.onNodeWithTag("summary_suppliers_count").assertTextContains("2 of 3")
        composeTestRule.onNodeWithTag("quoted_stock_value").assertTextContains("120 units")
        composeTestRule.onNodeWithTag("verified_stock_value").assertTextContains("42 units")

        composeTestRule.onNodeWithTag("medicine_details_list")
            .performScrollToNode(hasTestTag("supplier_offer_card_$unavailableOfferId"))
        composeTestRule.onNodeWithTag("offer_add_to_basket_button_$unavailableOfferId")
            .assertIsNotEnabled()
            .assertTextContains("Unavailable")
    }

    @Test
    fun tradeMarginCalculation_usesDocumentedFormula_andFlagsMissingFieldsWithoutFabrication() {
        val useCase = ProductQuotationUseCase()

        val fullBreakdown = useCase.calculateTradeMarginBreakdown(
            unitPricePtr = 100.0,
            mrp = 150.0,
            moq = 10,
            gstRatePercent = 12.0,
            schemeType = SchemeType.PERCENT_DISCOUNT_ABOVE_QTY,
            schemeBuyQty = 0,
            schemeFreeQty = 0,
            schemeDiscountPercent = 10.0,
            schemeMinQtyForDiscount = 10,
            supplierSettings = SupplierSettingsEntity(
                supplierId = 1L,
                deliveryCharge = 100.0,
                freeDeliveryThreshold = 5000.0,
                averageLeadTimeDays = 2.0
            )
        )
        assertEquals(110.80, fullBreakdown.effectiveUnitCost!!, 0.001)
        assertEquals(39.20, fullBreakdown.marginAmountPerUnit!!, 0.001)
        assertEquals(26.1333, fullBreakdown.marginPercent!!, 0.01)
        assertTrue(fullBreakdown.missingFields.isEmpty())

        var productId = 0L
        var offerId = 0L
        runBlocking {
            val supplierId = insertUser("Minimal Supplier", "NoSettings Pharma", "min@sup.com", UserRole.MANUFACTURER)
            productId = insertProduct(
                name = "MissingInfo Med",
                brand = "MinimalBrand",
                salt = "MinimalSalt",
                strength = "10mg",
                createdBy = supplierId
            )
            offerId = db.offerDao().insertOffer(
                SupplierOfferEntity(
                    productId = productId,
                    supplierId = supplierId,
                    unitPricePtr = 80.0,
                    mrp = 0.0,
                    moq = 5,
                    availableQuantity = 25,
                    schemeType = SchemeType.NONE.name,
                    validTillEpochMillis = futureExpiry,
                    isActive = true
                )
            )
            db.analyticsDao().insertOrUpdatePerformance(
                SupplierPerformanceEntity(
                    supplierId = supplierId,
                    overallScore = 95,
                    completedOrdersCount = 2,
                    fulfilmentRate = 100.0,
                    onTimeRate = 100.0,
                    cancellationRate = 0.0,
                    discrepancyRate = 0.0,
                    returnRate = 0.0
                )
            )

            val (summary, items) = repository.getApiProductQuotationsFlow(productId).first()
            val item = items.single { it.offer.offerId == offerId }

            assertNull("Trust score must be null when < 5 completed orders", item.offer.trustScore)
            assertFalse(item.offer.hasSufficientTrustData)
            assertNull("Margin must not be fabricated when MRP is missing", item.tradeMargin.marginPercent)
            assertTrue(item.tradeMargin.missingFields.contains("MRP"))
            assertTrue(item.tradeMargin.missingFields.contains("Supplier delivery settings"))
            assertFalse(summary.hasVerifiedStockRecords)
            assertNull(summary.verifiedAvailableStock)
        }

        val viewModel = CatalogueViewModel(repository)
        composeTestRule.setContent {
            PharmaOSTheme {
                MedicineDetailsScreen(
                    productId = productId,
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onProductClick = {},
                    onAddOfferClick = {}
                )
            }
        }
        waitForUiCondition { viewModel.uiState.value.supplierQuotations.isNotEmpty() }

        composeTestRule.onNodeWithTag("verified_stock_value")
            .assertTextContains("Not verified", substring = true)
        composeTestRule.onNodeWithTag("medicine_details_list")
            .performScrollToNode(hasTestTag("supplier_offer_card_$offerId"))
        composeTestRule.onNodeWithText("Insufficient data").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delivery fee policy: Missing", substring = true).assertIsDisplayed()
    }

    @Test
    fun alternativeBrands_displayCompleteQuotationFields_andIdentifyMissingInfo() {
        var targetId = 0L
        var altCompleteId = 0L
        var altMissingId = 0L

        runBlocking {
            val supRated = insertUser("Sup Rated", "Apex BioHub", "rated@sup.com", UserRole.MANUFACTURER)
            val supMinimal = insertUser("Sup Minimal", "Plain Traders", "plain@sup.com", UserRole.MANUFACTURER)

            targetId = insertProduct(
                name = "Glycomet 500",
                brand = "Glycomet",
                salt = "Metformin Hydrochloride",
                strength = "500mg",
                manufacturer = "USV Ltd",
                createdBy = supRated
            )
            db.offerDao().insertOffer(
                SupplierOfferEntity(
                    productId = targetId,
                    supplierId = supRated,
                    unitPricePtr = 25.0,
                    mrp = 32.0,
                    moq = 10,
                    availableQuantity = 100,
                    schemeType = SchemeType.NONE.name,
                    validTillEpochMillis = futureExpiry,
                    isActive = true
                )
            )

            altCompleteId = insertProduct(
                name = "Obimet 500",
                brand = "Obimet",
                salt = "Metformin Hydrochloride",
                strength = "500mg",
                manufacturer = "Abbott Healthcare",
                gstRate = 5.0,
                createdBy = supRated
            )
            db.offerDao().insertOffer(
                SupplierOfferEntity(
                    productId = altCompleteId,
                    supplierId = supRated,
                    unitPricePtr = 20.0,
                    mrp = 30.0,
                    moq = 10,
                    availableQuantity = 200,
                    schemeType = SchemeType.NONE.name,
                    validTillEpochMillis = futureExpiry,
                    isActive = true
                )
            )
            db.analyticsDao().insertOrUpdatePerformance(
                SupplierPerformanceEntity(
                    supplierId = supRated,
                    overallScore = 88,
                    completedOrdersCount = 8,
                    fulfilmentRate = 95.0,
                    onTimeRate = 92.0,
                    cancellationRate = 1.0,
                    discrepancyRate = 1.0,
                    returnRate = 1.0
                )
            )
            db.analyticsDao().insertOrUpdateSupplierSettings(
                SupplierSettingsEntity(
                    supplierId = supRated,
                    deliveryCharge = 0.0,
                    freeDeliveryThreshold = 0.0
                )
            )

            altMissingId = insertProduct(
                name = "Metform Generic",
                brand = "Metform",
                salt = "Metformin Hydrochloride",
                strength = "500mg",
                manufacturer = "",
                createdBy = supMinimal
            )

            insertProduct(
                name = "Paracetamol 500",
                brand = "Calpol",
                salt = "Paracetamol",
                strength = "500mg",
                createdBy = supRated
            )

            val alts = repository.getApiAlternativeBrandQuotationsFlow(targetId).first()
            assertEquals(2, alts.size)

            val completeAlt = alts.first { it.product.id == altCompleteId }
            assertEquals("Abbott Healthcare", completeAlt.manufacturerName)
            assertEquals(20.0, completeAlt.unitPricePtr!!, 0.01)
            assertEquals(10, completeAlt.moq)
            assertEquals(88, completeAlt.supplierRating)
            assertTrue(completeAlt.hasSufficientRatingData)
            assertEquals(30.0, completeAlt.tradeMargin.marginPercent!!, 0.01)

            val missingAlt = alts.first { it.product.id == altMissingId }
            assertEquals("Manufacturer unavailable", missingAlt.manufacturerName)
            assertNull(missingAlt.unitPricePtr)
            assertNull(missingAlt.moq)
            assertNull(missingAlt.supplierRating)
            assertNull(missingAlt.tradeMargin.marginPercent)
            assertFalse(missingAlt.hasEligibleOffer)
            assertTrue(missingAlt.missingFields.contains("Manufacturer"))
            assertTrue(missingAlt.missingFields.contains("Active supplier quote"))
        }

        var clickedAltId: Long? = null
        val viewModel = CatalogueViewModel(repository)
        composeTestRule.setContent {
            PharmaOSTheme {
                MedicineDetailsScreen(
                    productId = targetId,
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onProductClick = { clickedAltId = it },
                    onAddOfferClick = {}
                )
            }
        }
        waitForUiCondition { viewModel.uiState.value.alternativeBrandQuotations.size == 2 }

        composeTestRule.onNodeWithTag("medicine_details_list")
            .performScrollToNode(hasTestTag("alternative_brand_card_$altCompleteId"))
        composeTestRule.onNodeWithTag("alternative_brand_card_$altCompleteId").assertIsDisplayed()
        composeTestRule.onNodeWithText("Manufacturer: Abbott Healthcare").assertIsDisplayed()

        composeTestRule.onNodeWithTag("medicine_details_list")
            .performScrollToNode(hasTestTag("alternative_brand_card_$altMissingId"))
        composeTestRule.onNodeWithTag("alternative_brand_card_$altMissingId").assertIsDisplayed()
        composeTestRule.onNodeWithText("No active supplier quotations").assertIsDisplayed()

        composeTestRule.onNodeWithTag("medicine_details_list")
            .performScrollToNode(hasTestTag("alternative_brand_view_$altCompleteId"))
        composeTestRule.onNodeWithTag("alternative_brand_view_$altCompleteId").performClick()
        assertEquals(altCompleteId, clickedAltId)
    }

    @Test
    fun apiIntegration_andLoadingEmptyErrorStates_workWithoutFabricatingData() {
        composeTestRule.setContent {
            PharmaOSTheme {
                MedicineDetailsContent(
                    productId = 999L,
                    uiState = CatalogueUiState(isDetailsLoading = true, selectedProduct = null),
                    onNavigateBack = {},
                    onRetryLoad = {},
                    onProductClick = {},
                    onAddOfferClick = {},
                    onAddOfferToBasket = { _, _ -> },
                    onClearMessages = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Loading medicine details & supplier quotations...").assertIsDisplayed()

        runBlocking {
            val buyerId = insertUser("API Buyer", "API Pharmacy", "apibuyer@test.com", UserRole.STOCKIST)
            dataStoreManager.setLoggedInUserId(buyerId)
            dataStoreManager.setAuthTokens("jwt-api-token", null)
            dataStoreManager.setAuthExecutionMode(AuthExecutionMode.PRODUCTION_BACKEND_HTTP)

            var forceServerError = false
            val apiRequests = mutableListOf<String>()
            val mockInterceptor = Interceptor { chain ->
                val req = chain.request()
                val path = req.url.encodedPath
                apiRequests.add("${req.method} $path")
                if (forceServerError) {
                    return@Interceptor jsonResponse(req, 500, """{"status":500,"message":"Backend quotation service unavailable"}""")
                }
                when {
                    req.method == "GET" && path == "/api/v1/products/701" -> jsonResponse(
                        req,
                        200,
                        """{"success":true,"data":{"id":701,"name":"Remote Dolo 650","brand":"Dolo","saltComposition":"Paracetamol","strength":"650mg","dosageForm":"TABLET","therapeuticCategory":"Analgesic","manufacturerName":"Micro Labs","packSize":"15 Tabs","hsnCode":"3004","gstRatePercent":12.0,"storageCondition":"ROOM_TEMPERATURE","isRx":false,"isActive":true,"createdBy":801,"createdAtEpochMillis":1700000000000}}"""
                    )
                    req.method == "GET" && path == "/api/v1/products/701/offers" -> jsonResponse(
                        req,
                        200,
                        """{"success":true,"data":[{"id":901,"productId":701,"supplierId":801,"supplierName":"Suresh","supplierBusinessName":"Micro Direct Hub","unitPricePtr":30.0,"mrp":45.0,"moq":10,"availableQuantity":250,"schemeType":"NONE","schemeBuyQty":0,"schemeFreeQty":0,"schemeDiscountPercent":0.0,"schemeMinQtyForDiscount":0,"validTillEpochMillis":$futureExpiry,"isActive":true,"lastUpdatedEpochMillis":1700000000000}]}"""
                    )
                    req.method == "GET" && path == "/api/v1/products/701/alternatives" -> jsonResponse(
                        req,
                        200,
                        """{"success":true,"data":[{"id":702,"name":"Calpol 650","brand":"Calpol","saltComposition":"Paracetamol","strength":"650mg","dosageForm":"TABLET","therapeuticCategory":"Analgesic","manufacturerName":"GSK","packSize":"15 Tabs","hsnCode":"3004","gstRatePercent":12.0,"storageCondition":"ROOM_TEMPERATURE","isRx":false,"isActive":true,"createdBy":801,"createdAtEpochMillis":1700000000000}]}"""
                    )
                    req.method == "GET" && path == "/api/v1/products/702/offers" -> jsonResponse(
                        req,
                        200,
                        """{"success":true,"data":[]}"""
                    )
                    req.method == "GET" && path == "/api/v1/suppliers/801/settings" -> jsonResponse(
                        req,
                        200,
                        """{"success":true,"data":{"supplierId":801,"deliveryCharge":50.0,"freeDeliveryThreshold":1000.0,"averageLeadTimeDays":2.0}}"""
                    )
                    req.method == "GET" && path == "/api/v1/suppliers/801/trust-score" -> jsonResponse(
                        req,
                        200,
                        """{"success":true,"data":{"supplierId":801,"supplierName":"Micro Direct Hub","overallScore":92,"hasSufficientData":true,"completedOrdersCount":14,"fulfilmentRate":96.0,"onTimeDeliveryRate":95.0,"cancellationRate":1.0,"discrepancyRate":0.5,"returnRate":1.0,"lastCalculatedEpochMillis":1700000000000}}"""
                    )
                    req.method == "GET" && path == "/api/v1/inventory/batches" -> jsonResponse(
                        req,
                        200,
                        """{"success":true,"data":[]}"""
                    )
                    req.method == "GET" && path == "/api/v1/inventory" -> jsonResponse(
                        req,
                        200,
                        """{"success":true,"data":[]}"""
                    )
                    req.method == "POST" && path == "/api/v1/basket/items" -> jsonResponse(
                        req,
                        200,
                        """{"success":true,"data":{"basketId":55,"items":[{"itemId":1001,"productId":701,"productName":"Remote Dolo 650","brand":"Dolo","saltComposition":"Paracetamol","packSize":"15 Tabs","hsnCode":"3004","gstRatePercent":12.0,"offerId":901,"supplierId":801,"supplierName":"Micro Direct Hub","unitPricePtr":30.0,"mrp":45.0,"moq":10,"availableQuantity":250,"quantity":10,"effectiveUnitPrice":30.0,"lineTotalWithGst":336.0,"schemeDescription":"Standard"}],"subtotal":300.0,"totalGst":36.0,"totalDeliveryCharges":50.0,"grandTotal":386.0,"totalSavings":0.0}}"""
                    )
                    else -> jsonResponse(req, 404, """{"status":404,"message":"Not found"}""")
                }
            }

            val module = NetworkModule(
                tokenProvider = object : TokenProvider {
                    override fun getAccessToken(): String = "jwt-api-token"
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

            forceServerError = true
            val errResult = apiRepo.refreshProductDetailsFromBackend(701L)
            assertTrue(errResult.isFailure)
            assertTrue(errResult.exceptionOrNull()?.message?.contains("Backend quotation service unavailable") == true)
            assertNull(db.productDao().getProductById(701L))

            forceServerError = false
            val okResult = apiRepo.refreshProductDetailsFromBackend(701L)
            assertTrue(okResult.isSuccess)
            assertTrue(apiRequests.contains("GET /api/v1/products/701"))
            assertTrue(apiRequests.contains("GET /api/v1/products/701/offers"))
            assertTrue(apiRequests.contains("GET /api/v1/products/701/alternatives"))
            assertTrue(apiRequests.contains("GET /api/v1/suppliers/801/settings"))
            assertTrue(apiRequests.contains("GET /api/v1/suppliers/801/trust-score"))

            val (summary, quotes) = apiRepo.getApiProductQuotationsFlow(701L).first()
            assertEquals(1, quotes.size)
            assertEquals("Micro Direct Hub", quotes[0].offer.supplierBusinessName)
            assertEquals(92, quotes[0].offer.trustScore)
            assertEquals(50.0, quotes[0].deliveryCharge!!, 0.01)
            assertEquals(30.0, summary.lowestUnitPricePtr!!, 0.01)

            val alts = apiRepo.getApiAlternativeBrandQuotationsFlow(701L).first()
            assertEquals(1, alts.size)
            assertEquals("Calpol 650", alts[0].product.name)
            assertFalse("Calpol has empty offers from API so must not fabricate quotes", alts[0].hasEligibleOffer)

            val basketAddRes = apiRepo.postApiBasketItems(701L, 901L, 10)
            assertTrue(basketAddRes.isSuccess)
            assertTrue(apiRequests.contains("POST /api/v1/basket/items"))
            val syncedBasket = apiRepo.getApiBasketFlow().first()
            assertEquals(1, syncedBasket.items.size)
            assertEquals(10, syncedBasket.items[0].quantity)
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

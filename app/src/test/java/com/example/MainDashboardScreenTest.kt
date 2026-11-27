package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.local.entity.BatchEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.OrderEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.RecallEntity
import com.example.data.local.entity.SupplierOfferEntity
import com.example.data.local.entity.UserEntity
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.repository.PharmaRepositoryImpl
import com.example.domain.model.AccountStatus
import com.example.domain.model.DosageForm
import com.example.domain.model.NotificationType
import com.example.domain.model.OrderStatus
import com.example.domain.model.RecallSeverity
import com.example.domain.model.SchemeType
import com.example.domain.model.StorageCondition
import com.example.domain.model.UserRole
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.dashboard.DashboardViewModel
import com.example.ui.theme.PharmaOSTheme
import com.example.util.PasswordHasher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class MainDashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var repository: PharmaRepositoryImpl

    private val now = System.currentTimeMillis()

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
                    prefsFileName = "test_main_dashboard_tokens_${System.nanoTime()}"
                )
            )
            dataStoreManager.clearAuthSession()
            dataStoreManager.setOnboardingCompleted(true)
            dataStoreManager.setAuthExecutionMode(AuthExecutionMode.DEVELOPMENT_LOCAL_MODE)
            repository = PharmaRepositoryImpl(db = db, dataStoreManager = dataStoreManager)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            dataStoreManager.clearAuthSession()
            db.close()
        }
    }

    private fun waitForUiCondition(timeoutMs: Long = 5000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) break
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out after ${timeoutMs}ms waiting for UI condition")
            }
            Thread.sleep(25)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
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
                phone = "9820001122",
                gstin = "27AABCP1234F1Z5",
                address = "Industrial Estate, Pune",
                passwordHash = hash.hashHex,
                passwordSalt = hash.saltHex,
                role = role.name,
                status = AccountStatus.ACTIVE.name
            )
        )
    }

    @Test
    fun manufacturerDashboard_displaysCurrentInventory_activeTradeAlerts_andPendingOrdersSummary() {
        var openedInventory = false
        var openedAlerts = false
        var openedOrders = false

        val (mfgId, notifId, orderPlacedId) = runBlocking {
            val mfg = insertUser("Dr. Vikram Plant", "Bharat Bio-Pharma", "vikram@bharatbio.in", UserRole.MANUFACTURER)
            val buyer = insertUser("Rohan Stockist", "Apex Distributors", "rohan@apex.in", UserRole.STOCKIST)

            val prodId = db.productDao().insertProduct(
                ProductEntity(
                    name = "Cefixime 200mg DT",
                    brand = "BharatCef",
                    saltComposition = "Cefixime Trihydrate 200mg",
                    strength = "200mg",
                    dosageForm = DosageForm.TABLET.name,
                    therapeuticCategory = "Antibiotics",
                    manufacturerName = "Bharat Bio-Pharma",
                    packSize = "10 Tabs",
                    hsnCode = "30049099",
                    gstRatePercent = 12.0,
                    storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                    isRx = true,
                    isActive = true,
                    createdBy = mfg
                )
            )

            // Seed two live batches: one healthy (120 units), one critical low (8 units)
            db.batchDao().insertBatch(
                BatchEntity(
                    id = 101L,
                    productId = prodId,
                    supplierId = mfg,
                    batchNumber = "BB-CEF-101",
                    mfgDateEpochMillis = now - 30L * 86400000L,
                    expiryDateEpochMillis = now + 300L * 86400000L,
                    initialQuantity = 200,
                    remainingQuantity = 120,
                    storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                    mrp = 150.0,
                    ptr = 100.0
                )
            )
            db.batchDao().insertBatch(
                BatchEntity(
                    id = 102L,
                    productId = prodId,
                    supplierId = mfg,
                    batchNumber = "BB-CEF-102",
                    mfgDateEpochMillis = now - 60L * 86400000L,
                    expiryDateEpochMillis = now + 180L * 86400000L,
                    initialQuantity = 100,
                    remainingQuantity = 8,
                    storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                    mrp = 150.0,
                    ptr = 100.0
                )
            )

            // Seed an active Class I Recall + an unread order notification
            db.recallDao().insertRecall(
                RecallEntity(
                    productId = prodId,
                    productName = "Cefixime 200mg DT",
                    batchNumbersCsv = "BB-OLD-99",
                    reason = "Label print verification hold",
                    severity = RecallSeverity.CRITICAL_CLASS_1.name,
                    createdBySupplierId = mfg,
                    isActive = true
                )
            )
            val nId = db.notificationDao().insertNotification(
                NotificationEntity(
                    recipientUserId = mfg,
                    type = NotificationType.NEW_ORDER_RECEIVED.name,
                    title = "New Wholesale PO #ORD-26-1001",
                    message = "Apex Distributors placed a priority order worth ₹24,500",
                    isRead = false
                )
            )

            // Seed orders: 1 PLACED, 1 PACKED (processing), 1 DISPATCHED (in transit), 1 DELIVERED (excluded)
            val o1 = db.orderDao().insertOrder(
                OrderEntity(
                    orderNumber = "ORD-26-1001",
                    buyerId = buyer,
                    supplierId = mfg,
                    status = OrderStatus.PLACED.name,
                    subtotal = 20000.0,
                    gstAmount = 2400.0,
                    deliveryCharge = 100.0,
                    totalAmount = 22500.0,
                    deliveryAddress = "Pune"
                )
            )
            db.orderDao().insertOrder(
                OrderEntity(
                    orderNumber = "ORD-26-1002",
                    buyerId = buyer,
                    supplierId = mfg,
                    status = OrderStatus.PACKED.name,
                    subtotal = 15000.0,
                    gstAmount = 1800.0,
                    deliveryCharge = 0.0,
                    totalAmount = 16800.0,
                    deliveryAddress = "Mumbai"
                )
            )
            db.orderDao().insertOrder(
                OrderEntity(
                    orderNumber = "ORD-26-1003",
                    buyerId = buyer,
                    supplierId = mfg,
                    status = OrderStatus.DISPATCHED.name,
                    subtotal = 10000.0,
                    gstAmount = 1200.0,
                    deliveryCharge = 0.0,
                    totalAmount = 11200.0,
                    deliveryAddress = "Nashik"
                )
            )
            db.orderDao().insertOrder(
                OrderEntity(
                    orderNumber = "ORD-26-0999",
                    buyerId = buyer,
                    supplierId = mfg,
                    status = OrderStatus.DELIVERED.name,
                    subtotal = 50000.0,
                    gstAmount = 6000.0,
                    deliveryCharge = 0.0,
                    totalAmount = 56000.0,
                    deliveryAddress = "Nagpur"
                )
            )

            dataStoreManager.setAuthTokens("mfg-token", null)
            dataStoreManager.setLoggedInUserId(mfg)
            Triple(mfg, nId, o1)
        }

        val viewModel = DashboardViewModel(repository)

        composeTestRule.setContent {
            PharmaOSTheme {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToCatalogue = {},
                    onNavigateToBasket = {},
                    onNavigateToOrders = { openedOrders = true },
                    onNavigateToBatches = {},
                    onNavigateToShortages = { openedInventory = true },
                    onNavigateToLedger = {},
                    onNavigateToOcr = {},
                    onNavigateToOffers = {},
                    onNavigateToTrustScore = {},
                    onNavigateToNotifications = { openedAlerts = true },
                    onNavigateToSettings = {}
                )
            }
        }

        waitForUiCondition {
            viewModel.uiState.value.currentUser?.id == mfgId &&
                viewModel.uiState.value.inventorySummary.totalStockUnits == 128 &&
                viewModel.uiState.value.pendingOrdersSummary.totalPendingCount == 3 &&
                viewModel.uiState.value.activeTradeAlerts.isNotEmpty()
        }

        // 1. Verify Operations Pulse Card & Current Inventory Section
        composeTestRule.onNodeWithTag("dashboard_operations_pulse_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_current_inventory_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_inventory_total_units").assertTextContains("128")
        composeTestRule.onNodeWithTag("dashboard_inventory_sku_count").assertTextContains("1")
        composeTestRule.onNodeWithTag("dashboard_inventory_row_batch_102").performScrollTo().assertIsDisplayed()

        // 2. Verify Active Trade Alerts + Filter Chips + Mark Read
        composeTestRule.onNodeWithTag("dashboard_trade_alerts_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_alert_filter_critical").performScrollTo().performClick()
        waitForUiCondition {
            viewModel.uiState.value.selectedAlertFilter?.name == "CRITICAL"
        }
        composeTestRule.onNodeWithTag("dashboard_alert_filter_info").performScrollTo().performClick()
        waitForUiCondition {
            viewModel.uiState.value.selectedAlertFilter?.name == "INFO"
        }
        composeTestRule.onNodeWithTag("dashboard_trade_alert_notif_$notifId").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_mark_alert_read_notif_$notifId").performScrollTo().performClick()
        waitForUiCondition {
            viewModel.uiState.value.activeTradeAlerts.none { it.notificationId == notifId }
        }

        // 3. Verify Pending Orders Summary (3 pending, excludes DELIVERED order)
        composeTestRule.onNodeWithTag("dashboard_pending_orders_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_pending_orders_count").assertTextContains("3 Orders")
        composeTestRule.onNodeWithTag("dashboard_pending_orders_value").assertTextContains("50,500.00", substring = true)
        composeTestRule.onNodeWithTag("dashboard_pending_stage_placed").assertTextContains("1", substring = true)
        composeTestRule.onNodeWithTag("dashboard_pending_stage_processing").assertTextContains("1", substring = true)
        composeTestRule.onNodeWithTag("dashboard_pending_stage_dispatched").assertTextContains("1", substring = true)
        composeTestRule.onNodeWithTag("dashboard_pending_order_$orderPlacedId").performScrollTo().assertIsDisplayed()

        // 4. Verify navigation CTAs
        composeTestRule.onNodeWithTag("dashboard_open_inventory_button").performScrollTo().performClick()
        assertTrue(openedInventory)
        composeTestRule.onNodeWithTag("dashboard_view_all_alerts_button").performScrollTo().performClick()
        assertTrue(openedAlerts)
        composeTestRule.onNodeWithTag("dashboard_manage_orders_button").performScrollTo().performClick()
        assertTrue(openedOrders)
    }

    @Test
    fun stockistDashboard_showsBuyerSupplyAvailability_emptyPendingState_andHidesManufacturerInventory() {
        val stockistId = runBlocking {
            val stockist = insertUser("Meera Retailer", "Meera Medicals", "meera@medicals.in", UserRole.STOCKIST)
            val mfg = insertUser("Plant Lead", "SecretMfg", "lead@secretmfg.in", UserRole.MANUFACTURER)

            val prodId = db.productDao().insertProduct(
                ProductEntity(
                    name = "Paracetamol 500mg",
                    brand = "Calpol",
                    saltComposition = "Paracetamol 500mg",
                    strength = "500mg",
                    dosageForm = DosageForm.TABLET.name,
                    therapeuticCategory = "Analgesics",
                    manufacturerName = "SecretMfg",
                    packSize = "15 Tabs",
                    hsnCode = "30049099",
                    gstRatePercent = 12.0,
                    storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                    isRx = false,
                    isActive = true,
                    createdBy = mfg
                )
            )
            db.offerDao().insertOffer(
                SupplierOfferEntity(
                    productId = prodId,
                    supplierId = mfg,
                    unitPricePtr = 22.0,
                    mrp = 32.0,
                    moq = 10,
                    availableQuantity = 500,
                    schemeType = SchemeType.NONE.name,
                    validTillEpochMillis = now + 90L * 86400000L,
                    isActive = true
                )
            )
            // Confidential manufacturer batch that must NOT leak to stockist
            db.batchDao().insertBatch(
                BatchEntity(
                    productId = prodId,
                    supplierId = mfg,
                    batchNumber = "SECRET-PLANT-BATCH-88",
                    expiryDateEpochMillis = now + 180L * 86400000L,
                    initialQuantity = 500,
                    remainingQuantity = 5,
                    storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                    mrp = 32.0,
                    ptr = 22.0
                )
            )

            dataStoreManager.setAuthTokens("stockist-token", null)
            dataStoreManager.setLoggedInUserId(stockist)
            stockist
        }

        val viewModel = DashboardViewModel(repository)

        composeTestRule.setContent {
            PharmaOSTheme {
                DashboardScreen(
                    viewModel = viewModel,
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

        waitForUiCondition {
            viewModel.uiState.value.currentUser?.id == stockistId &&
                viewModel.uiState.value.catalogueSupplySummary.activeProductsCount == 1
        }

        // Stockist sees buyer-scoped supply availability, never internal manufacturer batches
        composeTestRule.onNodeWithTag("dashboard_supply_availability_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_supply_sku_count").assertTextContains("1")
        composeTestRule.onNodeWithTag("dashboard_supply_offers_count").assertTextContains("1")
        assertTrue(composeTestRule.onAllNodesWithText("SECRET-PLANT-BATCH-88", substring = true).fetchSemanticsNodes().isEmpty())

        // Clean empty states for Trade Alerts and Pending Orders
        composeTestRule.onNodeWithTag("dashboard_trade_alerts_empty").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_pending_orders_empty").performScrollTo().assertIsDisplayed()
        assertEquals(0, viewModel.uiState.value.pendingOrdersSummary.totalPendingCount)
    }
}

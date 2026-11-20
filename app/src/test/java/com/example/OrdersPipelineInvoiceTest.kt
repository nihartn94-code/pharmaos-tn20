package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.local.entity.OrderEntity
import com.example.data.local.entity.OrderItemEntity
import com.example.data.local.entity.OrderStatusHistoryEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SupplierOfferEntity
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
import com.example.domain.model.OrderStatus
import com.example.domain.model.SchemeType
import com.example.domain.model.StorageCondition
import com.example.domain.model.UserRole
import com.example.ui.screens.orders.MyOrdersScreen
import com.example.ui.screens.orders.OrderDetailsScreen
import com.example.ui.screens.orders.OrdersViewModel
import com.example.ui.theme.PharmaOSTheme
import com.example.util.PasswordHasher
import com.example.util.TaxInvoicePdfGenerator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class OrdersPipelineInvoiceTest {

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
            repository = PharmaRepositoryImpl(db = db, dataStoreManager = dataStoreManager)
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
        role: UserRole,
        gstin: String = "27ABCDE1234F1Z5",
        address: String = "Plot 14, MIDC Andheri East, Mumbai, MH"
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

    private suspend fun insertProduct(
        name: String = "Dolo 650",
        brand: String = "Micro Labs",
        hsnCode: String = "30049099",
        gstRate: Double = 12.0,
        createdBy: Long
    ): Long {
        return db.productDao().insertProduct(
            ProductEntity(
                name = name,
                brand = brand,
                saltComposition = "Paracetamol",
                strength = "650mg",
                dosageForm = DosageForm.TABLET.name,
                therapeuticCategory = "Analgesic",
                manufacturerName = brand,
                packSize = "15 Tabs",
                hsnCode = hsnCode,
                gstRatePercent = gstRate,
                storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                isRx = false,
                isActive = true,
                createdBy = createdBy
            )
        )
    }

    private suspend fun insertOffer(
        productId: Long,
        supplierId: Long,
        ptr: Double = 100.0,
        moq: Int = 5,
        availableQty: Int = 500
    ): Long {
        return db.offerDao().insertOffer(
            SupplierOfferEntity(
                productId = productId,
                supplierId = supplierId,
                unitPricePtr = ptr,
                mrp = ptr * 1.35,
                moq = moq,
                availableQuantity = availableQty,
                schemeType = SchemeType.NONE.name,
                validTillEpochMillis = futureExpiry,
                isActive = true
            )
        )
    }

    private suspend fun insertOrderWithItem(
        orderNumber: String,
        buyerId: Long,
        supplierId: Long,
        productId: Long,
        offerId: Long,
        status: OrderStatus,
        qty: Int = 10,
        unitPtr: Double = 100.0,
        gstRate: Double = 12.0,
        hsnCode: String = "30049099",
        invoiceNumber: String? = null
    ): Long {
        val subtotal = unitPtr * qty
        val gstAmt = subtotal * gstRate / 100.0
        val delivery = 50.0
        val total = subtotal + gstAmt + delivery
        val orderId = db.orderDao().insertOrder(
            OrderEntity(
                orderNumber = orderNumber,
                buyerId = buyerId,
                supplierId = supplierId,
                status = status.name,
                subtotal = subtotal,
                gstAmount = gstAmt,
                deliveryCharge = delivery,
                totalAmount = total,
                deliveryAddress = "Shop 12, Pharma Market, Pune",
                notes = "Handle with care",
                isEwayBillGenerated = status == OrderStatus.DISPATCHED || status == OrderStatus.DELIVERED,
                ewayBillNumber = if (status == OrderStatus.DISPATCHED || status == OrderStatus.DELIVERED) "EWB-88412" else null,
                vehicleNumber = if (status == OrderStatus.DISPATCHED || status == OrderStatus.DELIVERED) "MH-12-PQ-4455" else null,
                transporterName = if (status == OrderStatus.DISPATCHED || status == OrderStatus.DELIVERED) "SafeExpress Pharma" else null,
                invoiceNumber = invoiceNumber,
                invoiceIssuedAtEpochMillis = if (invoiceNumber != null) System.currentTimeMillis() else null
            )
        )
        db.orderDao().insertOrderItems(
            listOf(
                OrderItemEntity(
                    orderId = orderId,
                    productId = productId,
                    offerId = offerId,
                    productName = "Dolo 650",
                    brand = "Micro Labs",
                    packSize = "15 Tabs",
                    hsnCode = hsnCode,
                    gstRatePercent = gstRate,
                    orderedQuantity = qty,
                    freeQuantity = 0,
                    fulfilledQuantity = if (status == OrderStatus.DELIVERED) qty else 0,
                    unitPricePtr = unitPtr,
                    effectivePrice = unitPtr,
                    batchNumber = "BCH-2026-01"
                )
            )
        )
        db.orderDao().insertStatusHistory(
            OrderStatusHistoryEntity(
                orderId = orderId,
                status = status.name,
                note = "Initial test status",
                changedByUserId = buyerId
            )
        )
        return orderId
    }

    @Test
    fun stockistAccount_ordersScreen_showsScopedOrders_navigatesToBasketAndDetails_andRestrictsPipeline() {
        var stockistId = 0L
        var order1Id = 0L
        var order2Id = 0L
        var otherBuyerOrderId = 0L

        runBlocking {
            stockistId = insertUser("Rohan Mehta", "Mehta Medicals", "stockist@test.com", UserRole.STOCKIST)
            val otherStockistId = insertUser("Other Buyer", "City Chemists", "other@test.com", UserRole.STOCKIST)
            val mfgId = insertUser("Cipla Ops", "Cipla Pharma Mfg", "mfg@test.com", UserRole.MANUFACTURER)

            val productId = insertProduct(createdBy = mfgId)
            val offerId = insertOffer(productId, mfgId)

            dataStoreManager.setLoggedInUserId(stockistId)
            repository.postApiBasketItems(productId, offerId, 5).getOrThrow()

            order1Id = insertOrderWithItem("PO-STK-101", stockistId, mfgId, productId, offerId, OrderStatus.DISPATCHED, invoiceNumber = "INV-101")
            order2Id = insertOrderWithItem("PO-STK-102", stockistId, mfgId, productId, offerId, OrderStatus.DELIVERED, invoiceNumber = "INV-102")
            otherBuyerOrderId = insertOrderWithItem("PO-OTHER-999", otherStockistId, mfgId, productId, offerId, OrderStatus.PLACED)
        }

        val viewModel = OrdersViewModel(repository)
        var basketClickedCount = 0
        var clickedOrderId: Long? = null

        composeTestRule.setContent {
            PharmaOSTheme {
                MyOrdersScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onOrderClick = { clickedOrderId = it },
                    onNavigateToBasket = { basketClickedCount++ }
                )
            }
        }

        waitForUiCondition {
            viewModel.uiState.value.transactions.size == 2 &&
                viewModel.uiState.value.basketItemsCount == 1
        }

        // 1. Live Pipeline toggle & view must be completely hidden from Stockist account
        composeTestRule.onNodeWithTag("tab_pipeline_mode").assertDoesNotExist()
        composeTestRule.onNodeWithTag("order_pipeline_screen").assertDoesNotExist()
        assertFalse(viewModel.uiState.value.isLivePipelineAuthorized)

        // 2. Scoped orders displayed with supplier name and status; unrelated buyer order excluded
        composeTestRule.onNodeWithTag("order_transaction_card_$order1Id").assertIsDisplayed()
        composeTestRule.onNodeWithText("PO-STK-101").assertIsDisplayed()
        composeTestRule.onNodeWithTag("order_history_list").performScrollToNode(hasTestTag("order_transaction_card_$order2Id"))
        composeTestRule.onNodeWithTag("order_transaction_card_$order2Id").assertIsDisplayed()
        composeTestRule.onNodeWithTag("order_transaction_card_$otherBuyerOrderId").assertDoesNotExist()

        // 3. Basket access from TopBar and Header works with live count badge
        composeTestRule.onNodeWithTag("orders_basket_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("orders_basket_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, basketClickedCount)

        composeTestRule.onNodeWithTag("order_history_list").performScrollToNode(hasTestTag("orders_header_basket_button"))
        composeTestRule.onNodeWithTag("orders_header_basket_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, basketClickedCount)

        // 4. Order details navigation works
        composeTestRule.onNodeWithTag("order_history_list").performScrollToNode(hasTestTag("order_details_button_$order1Id"))
        composeTestRule.onNodeWithTag("order_details_button_$order1Id").performClick()
        composeTestRule.waitForIdle()
        assertEquals(order1Id, clickedOrderId)

        // 5. ViewModel + Repository enforce role restriction for Stockist
        viewModel.setActiveViewMode("PIPELINE")
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        assertEquals("HISTORY", viewModel.uiState.value.activeViewMode)
        composeTestRule.onNodeWithTag("order_pipeline_screen").assertDoesNotExist()

        runBlocking {
            val pipelineFlowResult = repository.getApiLivePipelineOrdersFlow().first()
            assertTrue(pipelineFlowResult.isEmpty())

            val pipelineSyncResult = repository.syncLivePipelineFromBackend()
            assertTrue(pipelineSyncResult.isFailure)
            assertTrue(pipelineSyncResult.exceptionOrNull() is SecurityException)

            val dispatchAttempt = repository.patchApiOrderStatus(
                orderId = order1Id,
                newStatus = OrderStatus.DELIVERED.name,
                note = "Unauthorized attempt"
            )
            assertTrue(dispatchAttempt.isFailure)
            assertTrue(dispatchAttempt.exceptionOrNull() is SecurityException)
        }
    }

    @Test
    fun manufacturerAccount_canAccessLivePipeline_andProgressOrderStatus() {
        var mfgId = 0L
        var placedOrderId = 0L
        var otherMfgOrderId = 0L

        runBlocking {
            mfgId = insertUser("Vikram Desai", "Sun Pharma Hub", "sun@test.com", UserRole.MANUFACTURER)
            val otherMfgId = insertUser("Other Mfg", "Dr Reddy", "drl@test.com", UserRole.PHARMA_COMPANY)
            val buyerId = insertUser("Apex Stockist", "Apex Medico", "apex@test.com", UserRole.STOCKIST)
            val productId = insertProduct(createdBy = mfgId)
            val offerId = insertOffer(productId, mfgId)

            dataStoreManager.setLoggedInUserId(mfgId)
            placedOrderId = insertOrderWithItem("PO-MFG-201", buyerId, mfgId, productId, offerId, OrderStatus.PLACED)
            otherMfgOrderId = insertOrderWithItem("PO-MFG-OTHER", buyerId, otherMfgId, productId, offerId, OrderStatus.PACKED)
        }

        val myOrdersVm = OrdersViewModel(repository)
        val detailsVm = OrdersViewModel(repository)
        var showOrderDetails by mutableStateOf(false)

        composeTestRule.setContent {
            PharmaOSTheme {
                if (!showOrderDetails) {
                    MyOrdersScreen(
                        viewModel = myOrdersVm,
                        onNavigateBack = {},
                        onOrderClick = {},
                        onNavigateToBasket = {}
                    )
                } else {
                    OrderDetailsScreen(
                        orderId = placedOrderId,
                        viewModel = detailsVm,
                        onNavigateBack = {},
                        onNavigateToBasket = {}
                    )
                }
            }
        }

        waitForUiCondition {
            myOrdersVm.uiState.value.isLivePipelineAuthorized &&
                myOrdersVm.uiState.value.pipelineItems.size == 1
        }

        // Live Pipeline tab is visible and switches to pipeline screen with manufacturer's own orders
        composeTestRule.onNodeWithTag("tab_pipeline_mode").assertIsDisplayed().performClick()
        waitForUiCondition { myOrdersVm.uiState.value.activeViewMode == "PIPELINE" }

        composeTestRule.onNodeWithTag("order_pipeline_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("order_pipeline_screen").performScrollToNode(hasText("PO-MFG-201", substring = true))
        composeTestRule.onNodeWithText("PO-MFG-201", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("PO-MFG-OTHER", substring = true).assertDoesNotExist()
        assertEquals(otherMfgOrderId != placedOrderId, true)

        // Now verify OrderDetailsScreen status progression updates Room + issues persisted Invoice No on Dispatch
        showOrderDetails = true

        waitForUiCondition { detailsVm.uiState.value.selectedOrderDetails != null }
        composeTestRule.onNodeWithTag("supplier_accept_button").assertIsDisplayed().performClick()
        waitForUiCondition { detailsVm.uiState.value.selectedOrderDetails?.order?.status == OrderStatus.ACCEPTED.name }

        composeTestRule.onNodeWithTag("supplier_pack_button").assertIsDisplayed().performClick()
        waitForUiCondition { detailsVm.uiState.value.selectedOrderDetails?.order?.status == OrderStatus.PACKED.name }

        composeTestRule.onNodeWithTag("supplier_dispatch_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Confirm Dispatch").performClick()
        waitForUiCondition { detailsVm.uiState.value.selectedOrderDetails?.order?.status == OrderStatus.DISPATCHED.name }

        composeTestRule.onNodeWithTag("supplier_deliver_button").assertIsDisplayed().performClick()
        waitForUiCondition { detailsVm.uiState.value.selectedOrderDetails?.order?.status == OrderStatus.DELIVERED.name }

        runBlocking {
            val updatedOrder = db.orderDao().getOrderById(placedOrderId)!!
            assertEquals(OrderStatus.DELIVERED.name, updatedOrder.status)
            assertNotNull("Persisted invoice number must be recorded after dispatch/delivery", updatedOrder.invoiceNumber)
            val history = db.orderDao().getStatusHistoryForOrder(placedOrderId)
            assertTrue(history.any { it.status == OrderStatus.ACCEPTED.name })
            assertTrue(history.any { it.status == OrderStatus.PACKED.name })
            assertTrue(history.any { it.status == OrderStatus.DISPATCHED.name })
            assertTrue(history.any { it.status == OrderStatus.DELIVERED.name })
        }
    }

    @Test
    fun taxInvoice_eligibility_zeroFabrication_andGstCalculation() {
        var placedOrderId = 0L
        var missingFieldsOrderId = 0L
        var intraStateOrderId = 0L
        var interStateOrderId = 0L

        runBlocking {
            val mfgValidId = insertUser("Mfg MH", "Cipla Hub", "mh@test.com", UserRole.MANUFACTURER, gstin = "27AABCC1234D1Z1")
            val buyerMhId = insertUser("Buyer MH", "Mumbai Pharmacy", "bmh@test.com", UserRole.STOCKIST, gstin = "27AAACM9876E1Z9")
            val buyerKaId = insertUser("Buyer KA", "Bengaluru Meds", "bka@test.com", UserRole.STOCKIST, gstin = "29AAACK1111F1Z2")

            val mfgMissingId = insertUser("No GST Seller", "Local Supplier", "nogst@test.com", UserRole.MANUFACTURER, gstin = "")
            val buyerMissingId = insertUser("No GST Buyer", "Local Clinic", "nobuy@test.com", UserRole.STOCKIST, gstin = "")

            dataStoreManager.setLoggedInUserId(buyerMhId)

            val prodId = insertProduct(createdBy = mfgValidId)
            val offerId = insertOffer(prodId, mfgValidId)

            placedOrderId = insertOrderWithItem("PO-PLACED", buyerMhId, mfgValidId, prodId, offerId, OrderStatus.PLACED, invoiceNumber = null)
            missingFieldsOrderId = insertOrderWithItem("PO-MISSING", buyerMissingId, mfgMissingId, prodId, offerId, OrderStatus.DELIVERED, invoiceNumber = null)
            intraStateOrderId = insertOrderWithItem("PO-INTRA", buyerMhId, mfgValidId, prodId, offerId, OrderStatus.DELIVERED, invoiceNumber = "INV-2026-INTRA")
            interStateOrderId = insertOrderWithItem("PO-INTER", buyerKaId, mfgValidId, prodId, offerId, OrderStatus.DELIVERED, invoiceNumber = "INV-2026-INTER")

            // 1. Ineligible PLACED order
            val placedInvoice = repository.getOrderTaxInvoice(placedOrderId).getOrThrow()
            assertFalse(placedInvoice.isEligible)
            assertNull(placedInvoice.invoiceNumber)

            // 2. Delivered order with missing GSTINs and missing invoiceNumber: ZERO fabrication
            val missingInvoice = repository.getOrderTaxInvoice(missingFieldsOrderId).getOrThrow()
            assertTrue(missingInvoice.isEligible)
            assertNull("Invoice number must NOT be fabricated when null", missingInvoice.invoiceNumber)
            assertNull("Seller GSTIN must NOT be fabricated when missing", missingInvoice.seller.gstin)
            assertNull("Buyer GSTIN must NOT be fabricated when missing", missingInvoice.buyer.gstin)
            assertNull("Tax split cannot be invented without state GSTINs", missingInvoice.gstTaxType)
            assertTrue(missingInvoice.missingFields.any { it.contains("Invoice Number") })
            assertTrue(missingInvoice.missingFields.any { it.contains("Seller GSTIN") })
            assertTrue(missingInvoice.missingFields.any { it.contains("Buyer GSTIN") })

            // 3. Intra-state (27 vs 27): CGST + SGST
            val intra = repository.getOrderTaxInvoice(intraStateOrderId).getOrThrow()
            assertTrue(intra.isEligible)
            assertEquals("INV-2026-INTRA", intra.invoiceNumber)
            assertEquals("CGST + SGST", intra.gstTaxType)
            assertEquals("State Code 27", intra.placeOfSupply)
            assertEquals(120.0, intra.totalGstAmount!!, 0.01)
            assertEquals(60.0, intra.cgstTotal!!, 0.01)
            assertEquals(60.0, intra.sgstTotal!!, 0.01)
            assertNull(intra.igstTotal)

            // 4. Inter-state (27 vs 29): IGST
            val inter = repository.getOrderTaxInvoice(interStateOrderId).getOrThrow()
            assertEquals("IGST", inter.gstTaxType)
            assertEquals("State Code 29", inter.placeOfSupply)
            assertEquals(120.0, inter.igstTotal!!, 0.01)
            assertNull(inter.cgstTotal)
            assertNull(inter.sgstTotal)
        }

        // Verify UI renders explicit "Not Issued" & "Not Registered / Unavailable" and warning on missing fields
        val vm = OrdersViewModel(repository)
        var activeOrderId by mutableStateOf(missingFieldsOrderId)
        composeTestRule.setContent {
            PharmaOSTheme {
                OrderDetailsScreen(
                    orderId = activeOrderId,
                    viewModel = vm,
                    onNavigateBack = {},
                    onNavigateToBasket = {}
                )
            }
        }
        waitForUiCondition { vm.uiState.value.taxInvoice?.orderId == missingFieldsOrderId }
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("tax_invoice_missing_fields_warning"))
        composeTestRule.onNodeWithTag("tax_invoice_number_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("Invoice No: Not Issued").assertIsDisplayed()
        composeTestRule.onNodeWithText("Seller GSTIN: Not Registered / Unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithText("Buyer GSTIN: Not Registered / Unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tax_invoice_missing_fields_warning").assertIsDisplayed()

        // Verify UI hides Download/Share buttons and shows ineligible notice for PLACED order
        activeOrderId = placedOrderId
        waitForUiCondition { vm.uiState.value.taxInvoice?.orderId == placedOrderId }
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("tax_invoice_ineligible_notice"))
        composeTestRule.onNodeWithTag("tax_invoice_ineligible_notice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("download_tax_invoice_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("share_tax_invoice_button").assertDoesNotExist()
    }

    @Test
    fun taxInvoicePdf_downloadAndShare_createsValidPdfAndZeroPermissionFileProviderIntent() {
        // Verify zero dangerous storage permissions declared in manifest
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val perms = packageInfo.requestedPermissions?.toList().orEmpty()
        assertFalse(perms.contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertFalse(perms.contains("android.permission.WRITE_EXTERNAL_STORAGE"))

        var eligibleOrderId = 0L
        runBlocking {
            val mfgId = insertUser("Cipla Seller", "Cipla Pharma Mfg", "s@test.com", UserRole.MANUFACTURER, gstin = "27AABCC1234D1Z1")
            val buyerId = insertUser("Apex Buyer", "Apex Pharmacy", "b@test.com", UserRole.STOCKIST, gstin = "27AAACA5555B1Z3")
            dataStoreManager.setLoggedInUserId(buyerId)

            val prodId = insertProduct(createdBy = mfgId)
            val offerId = insertOffer(prodId, mfgId)
            eligibleOrderId = insertOrderWithItem("PO-PDF-301", buyerId, mfgId, prodId, offerId, OrderStatus.DELIVERED, invoiceNumber = "INV-PDF-301")
        }

        val viewModel = OrdersViewModel(repository)
        composeTestRule.setContent {
            PharmaOSTheme {
                OrderDetailsScreen(
                    orderId = eligibleOrderId,
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onNavigateToBasket = {}
                )
            }
        }

        waitForUiCondition { viewModel.uiState.value.taxInvoice?.isEligible == true }
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("download_tax_invoice_button"))
        composeTestRule.onNodeWithTag("download_tax_invoice_button").assertIsDisplayed().performClick()

        waitForUiCondition {
            val state = viewModel.uiState.value
            if (state.errorMessage != null) {
                throw AssertionError("PDF download failed with error: ${state.errorMessage}")
            }
            state.lastSavedInvoicePath != null
        }

        val invoice = viewModel.uiState.value.taxInvoice!!
        val expectedFile = File(File(context.filesDir, "invoices"), TaxInvoicePdfGenerator.sanitizeFileName(invoice))
        assertTrue("Generated internal PDF file must exist", expectedFile.exists())
        val bytes = expectedFile.readBytes()
        assertTrue("PDF size should be > 100 bytes", bytes.size > 100)
        assertEquals("%PDF-", String(bytes, 0, 5, Charsets.US_ASCII))

        // Verify share intent uses FileProvider URI and ACTION_SEND
        var capturedChooserIntent: Intent? = null
        viewModel.shareTaxInvoicePdf(context, eligibleOrderId) { intent ->
            capturedChooserIntent = intent
        }
        waitForUiCondition { capturedChooserIntent != null }

        assertEquals(Intent.ACTION_CHOOSER, capturedChooserIntent!!.action)
        @Suppress("DEPRECATION")
        val targetIntent = capturedChooserIntent!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        assertEquals(Intent.ACTION_SEND, targetIntent!!.action)
        assertEquals("application/pdf", targetIntent.type)
        assertTrue((targetIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        @Suppress("DEPRECATION")
        val streamUri = targetIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertNotNull(streamUri)
        assertEquals("${context.packageName}.fileprovider", streamUri!!.authority)
    }

    @Test
    fun backendHttpMode_syncOrders_backendInvoice_roleEnforcement_andDuplicateOrderPrevention() {
        runBlocking {
            val stockistId = insertUser("Stockist Api", "MedPlus", "stkapi@test.com", UserRole.STOCKIST)
            val mfgId = insertUser("Mfg Api", "Sun Mfg", "mfgapi@test.com", UserRole.MANUFACTURER)
            val prodId = insertProduct(createdBy = mfgId)
            val offerId = insertOffer(prodId, mfgId)

            var activeRole = UserRole.STOCKIST
            var postOrdersCount = AtomicInteger(0)
            val seenIdempotencyKeys = mutableListOf<String?>()

            val mockInterceptor = Interceptor { chain ->
                val req = chain.request()
                val path = req.url.encodedPath
                when {
                    req.method == "GET" && path == "/api/v1/orders/pipeline" -> {
                        if (activeRole == UserRole.STOCKIST) {
                            jsonResponse(req, 403, """{"status":403,"message":"Forbidden: Live Pipeline requires MANUFACTURER or PHARMA_COMPANY role"}""")
                        } else {
                            jsonResponse(
                                req,
                                200,
                                """{"success":true,"data":[{"id":8801,"orderNumber":"PO-API-8801","buyerId":$stockistId,"supplierId":$mfgId,"status":"DISPATCHED","subtotal":1000.0,"gstAmount":120.0,"deliveryCharge":50.0,"totalAmount":1170.0,"deliveryAddress":"Warehouse 4","notes":"","isEwayBillGenerated":true,"ewayBillNumber":"EWB-API-1","vehicleNumber":"MH-01-AA-1111","transporterName":"BlueDart","invoiceNumber":"INV-API-8801","invoiceIssuedAtEpochMillis":1730000000000,"createdAtEpochMillis":1730000000000}]}"""
                            )
                        }
                    }
                    req.method == "GET" && path == "/api/v1/orders" -> {
                        jsonResponse(
                            req,
                            200,
                            """{"success":true,"data":[{"id":8801,"orderNumber":"PO-API-8801","buyerId":$stockistId,"supplierId":$mfgId,"status":"DISPATCHED","subtotal":1000.0,"gstAmount":120.0,"deliveryCharge":50.0,"totalAmount":1170.0,"deliveryAddress":"Warehouse 4","notes":"","isEwayBillGenerated":true,"ewayBillNumber":"EWB-API-1","vehicleNumber":"MH-01-AA-1111","transporterName":"BlueDart","invoiceNumber":"INV-API-8801","invoiceIssuedAtEpochMillis":1730000000000,"createdAtEpochMillis":1730000000000}]}"""
                        )
                    }
                    req.method == "GET" && path == "/api/v1/orders/8801/invoice" -> {
                        jsonResponse(
                            req,
                            200,
                            """{"success":true,"data":{"orderId":8801,"orderNumber":"PO-API-8801","orderStatus":"DISPATCHED","orderDateEpochMillis":1730000000000,"invoiceNumber":"INV-BACKEND-991","invoiceDateEpochMillis":1730000500000,"isEligible":true,"seller":{"legalName":"Sun Pharma Industries Ltd","gstin":"24AAACS1234A1Z5","address":"Vadodara, GJ"},"buyer":{"legalName":"MedPlus Health Services","gstin":"36AAACM2222B1Z8","shippingAddress":"Hyderabad, TS"},"placeOfSupply":"36-Telangana","gstTaxType":"IGST","items":[{"productName":"Dolo 650","brand":"Micro Labs","packSize":"15 Tabs","hsnCode":"30049099","batchNumber":"B-99","quantity":10,"freeQuantity":0,"unitPricePtr":100.0,"effectiveUnitPrice":100.0,"taxableValue":1000.0,"gstRatePercent":12.0,"gstAmount":120.0,"igstAmount":120.0,"lineTotal":1120.0}],"subtotalTaxable":1000.0,"igstTotal":120.0,"totalGstAmount":120.0,"deliveryCharge":50.0,"grandTotal":1170.0,"missingFields":[]}}"""
                        )
                    }
                    req.method == "GET" && path == "/api/v1/orders/8801/invoice/pdf" -> {
                        val fakePdf = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".toByteArray()
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(fakePdf.toResponseBody("application/pdf".toMediaType()))
                            .build()
                    }
                    req.method == "POST" && path == "/api/v1/basket/items" -> {
                        jsonResponse(
                            req,
                            200,
                            """{"success":true,"data":{"basketId":901,"items":[{"itemId":9001,"productId":$prodId,"productName":"Dolo 650","brand":"Dolo","saltComposition":"Paracetamol","packSize":"15 Tabs","hsnCode":"30049099","gstRatePercent":12.0,"offerId":$offerId,"supplierId":$mfgId,"supplierName":"Sun Mfg","unitPricePtr":100.0,"mrp":150.0,"moq":2,"availableQuantity":100,"quantity":5,"effectiveUnitPrice":100.0,"lineTotalWithGst":560.0,"schemeDescription":"Standard"}],"subtotal":500.0,"totalGst":60.0,"totalDeliveryCharges":50.0,"grandTotal":610.0,"totalSavings":0.0}}"""
                        )
                    }
                    req.method == "POST" && path == "/api/v1/orders" -> {
                        val count = postOrdersCount.incrementAndGet()
                        val bodyBuffer = okio.Buffer()
                        req.body?.writeTo(bodyBuffer)
                        val bodyText = bodyBuffer.readUtf8()
                        seenIdempotencyKeys.add(bodyText)
                        jsonResponse(
                            req,
                            201,
                            """{"success":true,"data":[{"id":8802,"orderNumber":"PO-API-8802-$count","buyerId":$stockistId,"supplierId":$mfgId,"status":"PLACED","subtotal":500.0,"gstAmount":60.0,"deliveryCharge":50.0,"totalAmount":610.0,"deliveryAddress":"Shop 12","notes":"Order note","createdAtEpochMillis":1730001000000}]}"""
                        )
                    }
                    else -> jsonResponse(req, 404, """{"status":404,"message":"Not found"}""")
                }
            }

            val module = NetworkModule(
                tokenProvider = object : TokenProvider {
                    override fun getAccessToken(): String = "jwt-prod-token"
                },
                initialBaseUrl = ApiConfig.EMULATOR_DEFAULT_BASE_URL,
                extraInterceptors = listOf(mockInterceptor)
            )
            val prodRepo = PharmaRepositoryImpl(
                db = db,
                dataStoreManager = dataStoreManager,
                authRemoteDataSource = AuthRemoteDataSource(module),
                pharmaRemoteDataSource = PharmaRemoteDataSource(module),
                networkModule = module
            )

            dataStoreManager.setAuthExecutionMode(AuthExecutionMode.PRODUCTION_BACKEND_HTTP)
            dataStoreManager.setAuthTokens("jwt-prod-token", null)
            dataStoreManager.setLoggedInUserId(stockistId)

            // 1. ensureDefaultOrdersSeeded MUST NOT insert fake local orders in production mode
            prodRepo.ensureDefaultOrdersSeeded()
            assertEquals(0, db.orderDao().getAllOrders().size)

            // 2. Stockist live pipeline call rejected locally & backed by 403 contract
            activeRole = UserRole.STOCKIST
            val stkPipelineRes = prodRepo.syncLivePipelineFromBackend()
            assertTrue(stkPipelineRes.isFailure)

            // 3. Switch to Manufacturer and sync orders & live pipeline twice -> deduplicated cleanly
            activeRole = UserRole.MANUFACTURER
            dataStoreManager.setLoggedInUserId(mfgId)
            assertTrue(prodRepo.syncOrdersFromBackend().isSuccess)
            assertTrue(prodRepo.syncLivePipelineFromBackend().isSuccess)
            assertEquals(1, db.orderDao().getAllOrders().size)
            assertEquals("INV-API-8801", db.orderDao().getOrderById(8801L)?.invoiceNumber)

            // 4. Authoritative backend tax invoice + PDF download
            val backendInvoice = prodRepo.getOrderTaxInvoice(8801L).getOrThrow()
            assertTrue(backendInvoice.isBackendAuthoritative)
            assertEquals("INV-BACKEND-991", backendInvoice.invoiceNumber)
            assertEquals("IGST", backendInvoice.gstTaxType)
            assertEquals("24AAACS1234A1Z5", backendInvoice.seller.gstin)

            val pdfBytes = prodRepo.downloadBackendTaxInvoicePdf(8801L).getOrThrow()
            assertTrue(String(pdfBytes, Charsets.US_ASCII).startsWith("%PDF-"))

            // 5. Duplicate order prevention under concurrent checkout submissions
            dataStoreManager.setLoggedInUserId(stockistId)
            prodRepo.postApiBasketItems(prodId, offerId, 5).getOrThrow()

            val firstCall = async { prodRepo.postApiOrders("Shop 12", "Order note") }
            val secondCall = async { prodRepo.postApiOrders("Shop 12", "Order note") }
            val results = awaitAll(firstCall, secondCall)

            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, results.count { it.isFailure })
            assertEquals("Only one HTTP POST /orders should be fired for identical concurrent submissions", 1, postOrdersCount.get())
            assertTrue("Request body must carry deterministic idempotencyKey", seenIdempotencyKeys.first()?.contains("idempotencyKey") == true)
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

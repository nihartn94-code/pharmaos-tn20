package com.example

import com.example.data.local.entity.OrderEntity
import com.example.data.local.entity.OrderItemEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ProofOfDeliveryEntity
import com.example.data.local.entity.ReturnEntity
import com.example.data.local.entity.StockRecordEntity
import com.example.data.local.entity.SupplierOfferEntity
import com.example.data.local.entity.SupplierSettingsEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.AccountStatus
import com.example.domain.model.OrderStatus
import com.example.domain.model.ReturnReason
import com.example.domain.model.ReturnStatus
import com.example.domain.model.SchemeType
import com.example.domain.model.ShortageRiskLevel
import com.example.domain.model.UserRole
import com.example.domain.repository.BasketItemDetail
import com.example.domain.usecase.CalculateTrustScoreUseCase
import com.example.domain.usecase.EvaluateShortageRiskUseCase
import com.example.data.local.dao.ProductDao
import com.example.data.repository.ProductRepository
import com.example.domain.usecase.OptimizeBasketUseCase
import com.example.domain.usecase.ParsePurchaseListUseCase
import com.example.util.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PharmaOSTests {

    // 1. Password Hasher Test
    @Test
    fun testPasswordHashingAndVerification() {
        val rawPassword = "StrongSecretPass@123"
        val hashResult = PasswordHasher.hashPassword(rawPassword)

        assertNotNull(hashResult.hashHex)
        assertNotNull(hashResult.saltHex)
        assertEquals(32, hashResult.saltHex.length) // 16 bytes = 32 hex chars
        assertEquals(64, hashResult.hashHex.length) // 256 bits = 64 hex chars

        // Verification with correct password
        assertTrue(PasswordHasher.verifyPassword(rawPassword, hashResult.hashHex, hashResult.saltHex))

        // Verification with wrong password
        assertFalse(PasswordHasher.verifyPassword("WrongPassword", hashResult.hashHex, hashResult.saltHex))
    }

    // 2. Scheme Pricing Tests
    @Test
    fun testBuyXGetYFreeSchemeCalculation() {
        val optimizer = OptimizeBasketUseCase()
        // Unit price 100, quantity 25, Buy 10 Get 2 Free
        // 25 / 10 = 2 batches -> 2 * 2 = 4 free units
        // Regular qty: 25, Total received: 29
        // Gross: 2500, Discount: 0, Net: 2500
        val pricing = optimizer.calculateSchemePrice(
            unitPrice = 100.0,
            quantity = 25,
            schemeType = SchemeType.BUY_X_GET_Y_FREE,
            buyQty = 10,
            freeQty = 2,
            discountPercent = 0.0,
            minQtyForDiscount = 0,
            gstRatePercent = 12.0
        )

        assertEquals(25, pricing.regularQuantity)
        assertEquals(4, pricing.freeQuantity)
        assertEquals(29, pricing.totalReceivedQuantity)
        assertEquals(2500.0, pricing.grossAmount, 0.01)
        assertEquals(2500.0, pricing.netAmount, 0.01)
        // Effective unit price = 2500 / 29 = 86.2068...
        assertEquals(86.21, pricing.effectiveUnitPrice, 0.05)
    }

    @Test
    fun testPercentDiscountAboveQtyCalculation() {
        val optimizer = OptimizeBasketUseCase()
        // Unit price 200, quantity 50, 10% discount above 30 units
        // Gross: 10,000, Discount: 1,000, Net: 9,000
        val pricing = optimizer.calculateSchemePrice(
            unitPrice = 200.0,
            quantity = 50,
            schemeType = SchemeType.PERCENT_DISCOUNT_ABOVE_QTY,
            buyQty = 0,
            freeQty = 0,
            discountPercent = 10.0,
            minQtyForDiscount = 30,
            gstRatePercent = 12.0
        )

        assertEquals(50, pricing.regularQuantity)
        assertEquals(0, pricing.freeQuantity)
        assertEquals(10000.0, pricing.grossAmount, 0.01)
        assertEquals(1000.0, pricing.discountAmount, 0.01)
        assertEquals(9000.0, pricing.netAmount, 0.01)
        assertEquals(180.0, pricing.effectiveUnitPrice, 0.01)
        assertEquals(1080.0, pricing.gstAmount, 0.01)
        assertEquals(10080.0, pricing.totalWithGst, 0.01)
    }

    // 3. Best-Buy Basket Optimizer Test
    @Test
    fun testBestBuyOptimizerExactSearchAndConsolidation() {
        val optimizer = OptimizeBasketUseCase()

        val item1 = BasketItemDetail(
            itemId = 1,
            productId = 101,
            productName = "Paracetamol 650mg",
            brand = "Dolo",
            saltComposition = "Paracetamol",
            packSize = "10x10",
            hsnCode = "3004",
            gstRatePercent = 12.0,
            offerId = 201,
            supplierId = 1,
            supplierName = "Supplier A",
            unitPricePtr = 150.0,
            mrp = 200.0,
            moq = 10,
            availableQuantity = 100,
            quantity = 20,
            pricing = optimizer.calculateSchemePrice(150.0, 20, SchemeType.NONE, 0, 0, 0.0, 0, 12.0),
            schemeDescription = "Standard",
            trustScore = 90,
            hasSufficientTrustData = true
        )

        // Offers for product 101: Supplier A @ 150, Supplier B @ 120 (cheaper!)
        val offerA = SupplierOfferEntity(
            id = 201,
            productId = 101,
            supplierId = 1,
            unitPricePtr = 150.0,
            mrp = 200.0,
            moq = 10,
            availableQuantity = 100,
            schemeType = "NONE",
            validTillEpochMillis = System.currentTimeMillis() + 100000
        )
        val offerB = SupplierOfferEntity(
            id = 202,
            productId = 101,
            supplierId = 2,
            unitPricePtr = 120.0,
            mrp = 200.0,
            moq = 10,
            availableQuantity = 100,
            schemeType = "NONE",
            validTillEpochMillis = System.currentTimeMillis() + 100000
        )

        val suppliersMap = mapOf(
            1L to UserEntity(id = 1, fullName = "Supplier A", businessName = "Pharma A", email = "a@test.com", phone = "1234567890", gstin = null, address = "Addr", passwordHash = "", passwordSalt = "", role = "MANUFACTURER", status = "ACTIVE"),
            2L to UserEntity(id = 2, fullName = "Supplier B", businessName = "Pharma B", email = "b@test.com", phone = "1234567890", gstin = null, address = "Addr", passwordHash = "", passwordSalt = "", role = "MANUFACTURER", status = "ACTIVE")
        )

        val settingsMap = mapOf(
            1L to SupplierSettingsEntity(supplierId = 1, deliveryCharge = 0.0, freeDeliveryThreshold = 0.0),
            2L to SupplierSettingsEntity(supplierId = 2, deliveryCharge = 0.0, freeDeliveryThreshold = 0.0)
        )

        val result = optimizer.optimize(
            currentItems = listOf(item1),
            availableOffersByProduct = mapOf(101L to listOf(offerA, offerB)),
            suppliersById = suppliersMap,
            settingsBySupplier = settingsMap,
            supplierTrustScores = mapOf(1L to 90, 2L to 85)
        )

        assertTrue(result.isExact)
        assertEquals(2L, result.lines[0].suggestedSupplierId)
        assertEquals(120.0, result.lines[0].suggestedUnitPrice, 0.01)
        assertTrue(result.savingsRupees > 0)
    }

    // 4. Pharma Trust Score Use Case Test
    @Test
    fun testTrustScoreCalculationWeights() {
        val calculator = CalculateTrustScoreUseCase()
        val now = System.currentTimeMillis()

        // Test with fewer than 5 completed orders -> Insufficient data
        val fourOrders = (1..4).map {
            OrderEntity(
                id = it.toLong(),
                orderNumber = "ORD-$it",
                buyerId = 10,
                supplierId = 1,
                status = OrderStatus.DELIVERED.name,
                subtotal = 1000.0,
                gstAmount = 120.0,
                deliveryCharge = 0.0,
                totalAmount = 1120.0,
                deliveryAddress = "Clinic",
                deliveredEpochMillis = now,
                expectedDeliveryEpochMillis = now + 100000
            )
        }
        val items = fourOrders.map {
            OrderItemEntity(
                id = it.id,
                orderId = it.id,
                productId = 1,
                offerId = 1,
                productName = "Med",
                brand = "Brand",
                packSize = "10s",
                hsnCode = "3004",
                gstRatePercent = 12.0,
                orderedQuantity = 50,
                fulfilledQuantity = 50,
                unitPricePtr = 20.0,
                effectivePrice = 20.0
            )
        }

        val resInsufficient = calculator.calculate(1, "Supplier 1", fourOrders, items, emptyList(), emptyList())
        assertFalse(resInsufficient.hasSufficientData)

        // Test with 5 completed orders, 100% on time, 100% fulfilled, 0 cancellations, 0 discrepancies, 0 returns
        val fiveOrders = (1..5).map {
            OrderEntity(
                id = it.toLong(),
                orderNumber = "ORD-$it",
                buyerId = 10,
                supplierId = 1,
                status = OrderStatus.DELIVERED.name,
                subtotal = 1000.0,
                gstAmount = 120.0,
                deliveryCharge = 0.0,
                totalAmount = 1120.0,
                deliveryAddress = "Clinic",
                deliveredEpochMillis = now,
                expectedDeliveryEpochMillis = now + 100000
            )
        }
        val fiveItems = fiveOrders.map {
            OrderItemEntity(
                id = it.id,
                orderId = it.id,
                productId = 1,
                offerId = 1,
                productName = "Med",
                brand = "Brand",
                packSize = "10s",
                hsnCode = "3004",
                gstRatePercent = 12.0,
                orderedQuantity = 50,
                fulfilledQuantity = 50,
                unitPricePtr = 20.0,
                effectivePrice = 20.0
            )
        }

        val resPerfect = calculator.calculate(1, "Supplier 1", fiveOrders, fiveItems, emptyList(), emptyList())
        assertTrue(resPerfect.hasSufficientData)
        assertEquals(100, resPerfect.overallScore)
        assertEquals(1.0, resPerfect.fulfilmentRate, 0.001)
        assertEquals(1.0, resPerfect.onTimeDeliveryRate, 0.001)
    }

    // 5. Shortage Risk Evaluation Test
    @Test
    fun testShortageRiskRuleEvaluation() {
        val evaluator = EvaluateShortageRiskUseCase()
        val product = ProductEntity(
            id = 1,
            name = "Amoxicillin 500mg",
            brand = "Mox",
            saltComposition = "Amoxicillin",
            strength = "500 mg",
            dosageForm = "CAPSULE",
            therapeuticCategory = "Antibiotic",
            manufacturerName = "Pharma Corp",
            packSize = "10x10",
            hsnCode = "3004",
            gstRatePercent = 12.0,
            storageCondition = "ROOM_TEMPERATURE",
            isRx = true,
            createdBy = 1
        )

        val now = System.currentTimeMillis()

        // Test High Risk triggering R1 (>=2 stockouts in 30 days), R3 (<2 suppliers in stock), R4 (lead time > 7d)
        val snapshots = listOf(
            StockRecordEntity(id = 1, productId = 1, supplierId = 1, offerId = 1, availableQuantity = 0, recordedAtEpochMillis = now - 1000, reason = ""),
            StockRecordEntity(id = 2, productId = 1, supplierId = 1, offerId = 1, availableQuantity = 0, recordedAtEpochMillis = now - 50000, reason = "")
        )

        val offers = listOf(
            SupplierOfferEntity(id = 1, productId = 1, supplierId = 1, unitPricePtr = 100.0, mrp = 150.0, moq = 10, availableQuantity = 0, schemeType = "NONE", validTillEpochMillis = now + 100000)
        )

        val settings = mapOf(
            1L to SupplierSettingsEntity(supplierId = 1, averageLeadTimeDays = 9.0)
        )

        val result = evaluator.evaluate(product, offers, snapshots, settings)

        // Should trigger R1 (stockouts), R3 (fewer than 2 suppliers with stock), R4 (lead time 9.0 > 7.0)
        assertTrue(result.triggeredReasons.size >= 3)
        assertEquals(ShortageRiskLevel.HIGH, result.riskLevel)
    }

    // 6. OCR Purchase-List Parser Test
    @Test
    fun testOcrLineParsingAndFuzzyMatch() {
        val parser = ParsePurchaseListUseCase()

        // Test parsing various formats
        val item1 = parser.parseLine("Paracetamol 650 x 10")
        assertEquals("Paracetamol", item1.medicineName)
        assertEquals(10, item1.quantity)

        val item2 = parser.parseLine("Azithral 500 - 5 strips")
        assertEquals("Azithral", item2.medicineName)
        assertEquals(5, item2.quantity)

        val item3 = parser.parseLine("Augmentin 625mg 20 boxes")
        assertEquals("Augmentin", item3.medicineName)
        assertEquals(20, item3.quantity)

        // Test catalogue matching
        val catalogue = listOf(
            ProductEntity(id = 10, name = "Paracetamol 650mg", brand = "Dolo 650", saltComposition = "Paracetamol", strength = "650 mg", dosageForm = "TABLET", therapeuticCategory = "Analgesic", manufacturerName = "Micro", packSize = "15s", hsnCode = "3004", gstRatePercent = 12.0, storageCondition = "ROOM_TEMPERATURE", isRx = false, createdBy = 1),
            ProductEntity(id = 20, name = "Azithromycin 500mg", brand = "Azithral", saltComposition = "Azithromycin", strength = "500 mg", dosageForm = "TABLET", therapeuticCategory = "Antibiotic", manufacturerName = "Alembic", packSize = "5s", hsnCode = "3004", gstRatePercent = 12.0, storageCondition = "ROOM_TEMPERATURE", isRx = true, createdBy = 1)
        )

        parser.matchWithCatalogue(item1, catalogue)
        assertEquals(10L, item1.matchedProductId)
        assertTrue(item1.matchConfidence > 0.5)

        parser.matchWithCatalogue(item2, catalogue)
        assertEquals(20L, item2.matchedProductId)
        assertTrue(item2.matchConfidence > 0.7)
    }

    // 7. Inventory Depletion Threshold Monitoring Test
    @Test
    fun testInventoryDepletionMonitoringThreshold() {
        data class TestStockItem(val name: String, val remainingQty: Int)

        val inventory = listOf(
            TestStockItem("Augmentin 625 Duo", 45),
            TestStockItem("Azithromycin 500mg", 12),
            TestStockItem("Pan-D Capsule", 8),
            TestStockItem("Metformin 500mg", 0)
        )

        val userDefinedThreshold = 10

        val depletedItems = inventory.filter { it.remainingQty <= userDefinedThreshold }

        // With threshold 10:
        // Pan-D (8) and Metformin (0) should be flagged
        assertEquals(2, depletedItems.size)
        assertTrue(depletedItems.any { it.name == "Pan-D Capsule" && it.remainingQty == 8 })
        assertTrue(depletedItems.any { it.name == "Metformin 500mg" && it.remainingQty == 0 })
        assertFalse(depletedItems.any { it.name == "Augmentin 625 Duo" })

        // When user increases threshold to 15:
        val higherThreshold = 15
        val higherDepletedItems = inventory.filter { it.remainingQty <= higherThreshold }
        assertEquals(3, higherDepletedItems.size)
        assertTrue(higherDepletedItems.any { it.name == "Azithromycin 500mg" })
    }

    // 12. Pharmaceutical Product Inventory Entity & Reorder Threshold Tests
    @Test
    fun testProductInventoryEntityFieldsAndReorderThreshold() {
        val inventoryItem = com.example.data.local.entity.ProductInventoryEntity(
            productName = "Augmentin 625 Duo",
            sku = "SKU-AUG-625-10T",
            currentStock = 14,
            reorderThreshold = 25,
            saltComposition = "Amoxicillin (500mg) + Clavulanic Acid (125mg)",
            dosageForm = "Tablet",
            packSize = "Box of 10 Strips x 10 Tabs",
            unitPricePtr = 142.50,
            mrp = 198.00,
            storageCondition = "ROOM_TEMPERATURE",
            supplierName = "Cipla Wholesale Direct Hub"
        )

        // Verify mandatory fields
        assertEquals("Augmentin 625 Duo", inventoryItem.productName)
        assertEquals("SKU-AUG-625-10T", inventoryItem.sku)
        assertEquals(14, inventoryItem.currentStock)
        assertEquals(25, inventoryItem.reorderThreshold)

        // Verify low stock calculation (14 <= 25)
        assertTrue(inventoryItem.isLowStock)
        assertFalse(inventoryItem.isOutOfStock)

        // Replenishment needed: (25 * 2) - 14 = 36 units
        assertEquals(36, inventoryItem.replenishmentQuantityNeeded)

        // Valuation: 14 * 142.50 = 1995.0
        assertEquals(1995.0, inventoryItem.totalInventoryValuation, 0.01)
    }

    @Test
    fun testProductInventoryOutOfStockAndHealthyLevels() {
        val outOfStockItem = com.example.data.local.entity.ProductInventoryEntity(
            productName = "Azithromycin 500mg",
            sku = "SKU-AZTH-500-5T",
            currentStock = 0,
            reorderThreshold = 20
        )
        assertTrue(outOfStockItem.isOutOfStock)
        assertTrue(outOfStockItem.isLowStock)
        assertEquals(40, outOfStockItem.replenishmentQuantityNeeded)

        val healthyItem = com.example.data.local.entity.ProductInventoryEntity(
            productName = "Paracetamol 650mg",
            sku = "SKU-DOLO-650-15T",
            currentStock = 150,
            reorderThreshold = 40
        )
        assertFalse(healthyItem.isLowStock)
        assertFalse(healthyItem.isOutOfStock)
        assertEquals(0, healthyItem.replenishmentQuantityNeeded)
    }

    // 13. ProductRepository Bridging Room DAO with ViewModel Tests
    @Test
    fun testProductRepositoryInsertFetchUpdateDelete() = runBlocking {
        val fakeDao = FakeProductDao()
        val repository = ProductRepository(
            productDao = fakeDao,
            ioDispatcher = Dispatchers.Unconfined
        )

        // 1. Initial State
        assertEquals(0, repository.getAllProducts().size)
        assertEquals(0, repository.getActiveProductCount())

        // 2. Insert Single Product
        val prod1 = ProductEntity(
            name = "Augmentin 625 Duo",
            brand = "Augmentin",
            saltComposition = "Amoxicillin + Clavulanic Acid",
            strength = "625mg",
            dosageForm = "Tablet",
            therapeuticCategory = "Antibiotics",
            manufacturerName = "GSK",
            packSize = "Box of 10",
            hsnCode = "30041010",
            gstRatePercent = 12.0,
            storageCondition = "ROOM_TEMPERATURE",
            isRx = true,
            createdBy = 1L
        )
        val id1 = repository.insertProduct(prod1)
        assertTrue(id1 > 0)
        assertEquals(1, repository.getAllProducts().size)
        assertEquals(1, repository.getActiveProductCount())

        // 3. Fetch by ID
        val fetched1 = repository.getProductById(id1)
        assertNotNull(fetched1)
        assertEquals("Augmentin 625 Duo", fetched1?.name)

        // 4. Reactive Flow Fetch
        val flowList = repository.getAllProductsFlow().first()
        assertEquals(1, flowList.size)
        assertEquals("Augmentin 625 Duo", flowList[0].name)

        // 5. Insert Multiple Products
        val prod2 = ProductEntity(
            name = "Pan-D",
            brand = "Pan-D",
            saltComposition = "Pantoprazole + Domperidone",
            strength = "40mg + 30mg",
            dosageForm = "Capsule",
            therapeuticCategory = "Gastrointestinal",
            manufacturerName = "Alkem",
            packSize = "Strip of 15",
            hsnCode = "30049099",
            gstRatePercent = 12.0,
            storageCondition = "ROOM_TEMPERATURE",
            isRx = false,
            createdBy = 1L
        )
        val ids = repository.insertProducts(listOf(prod2))
        assertEquals(1, ids.size)
        assertEquals(2, repository.getAllProducts().size)

        // 6. Search Flow
        val searchResults = repository.searchProductsFlow("Augmentin").first()
        assertEquals(1, searchResults.size)
        assertEquals("Augmentin 625 Duo", searchResults[0].name)

        // 7. Update Product
        val updatedProd1 = fetched1!!.copy(name = "Augmentin 625 Duo Forte")
        repository.updateProduct(updatedProd1)
        val afterUpdate = repository.getProductById(id1)
        assertEquals("Augmentin 625 Duo Forte", afterUpdate?.name)

        // 8. Update Active Status
        repository.setProductActiveStatus(id1, false)
        assertFalse(repository.getProductById(id1)!!.isActive)
        val activeFlow = repository.getAllActiveProductsFlow().first()
        assertEquals(1, activeFlow.size)
        assertEquals("Pan-D", activeFlow[0].name)

        // 9. Delete Product by Entity
        repository.deleteProduct(activeFlow[0])
        assertEquals(1, repository.getAllProducts().size)

        // 10. Delete Product by ID
        repository.deleteProductById(id1)
        assertEquals(0, repository.getAllProducts().size)
        assertNull(repository.getProductById(id1))
    }

    // 14. Manufacturer Testing User Instance Tests
    @Test
    fun testManufacturerTestingUserInstance() {
        val testPassword = "Password@123"
        val hash = PasswordHasher.hashPassword(testPassword)

        val mfgUser = UserEntity(
            id = 101L,
            fullName = "Dr. Vikram Malhotra (VP Manufacturing)",
            businessName = "Bharat Bio-Pharma Formulations Pvt. Ltd.",
            email = "manufacturer@pharmaos.com",
            phone = "9876543220",
            gstin = "27AAACB1234F1Z9",
            address = "Plant 1, Plot 88, MIDC Tarapur Industrial Area, Boisar, Palghar, Maharashtra 401506",
            passwordHash = hash.hashHex,
            passwordSalt = hash.saltHex,
            role = UserRole.MANUFACTURER.name,
            status = AccountStatus.ACTIVE.name
        )

        // Verify manufacturer profile attributes
        assertEquals("Dr. Vikram Malhotra (VP Manufacturing)", mfgUser.fullName)
        assertEquals("Bharat Bio-Pharma Formulations Pvt. Ltd.", mfgUser.businessName)
        assertEquals("manufacturer@pharmaos.com", mfgUser.email)
        assertEquals("9876543220", mfgUser.phone)
        assertEquals("27AAACB1234F1Z9", mfgUser.gstin)
        assertEquals(UserRole.MANUFACTURER.name, mfgUser.role)
        assertEquals(AccountStatus.ACTIVE.name, mfgUser.status)

        // Verify password hash verification
        assertTrue(PasswordHasher.verifyPassword(testPassword, mfgUser.passwordHash, mfgUser.passwordSalt))
        assertFalse(PasswordHasher.verifyPassword("WrongPassword", mfgUser.passwordHash, mfgUser.passwordSalt))

        // Verify Manufacturer Role Supplier Capabilities
        val role = UserRole.valueOf(mfgUser.role)
        assertEquals("Manufacturer", role.displayName)
        assertTrue(role.isSupplier)
        assertFalse(role.isBuyer)
    }

    // 15. Product List Screen & Add Product Dialog via ProductRepository Tests
    @Test
    fun testProductListScreenAndAddProductDialogViaRepository() = runBlocking {
        val fakeProductDao = FakeProductDao()
        val fakeInventoryDao = FakeProductInventoryDao()
        val repository = ProductRepository(
            productDao = fakeProductDao,
            inventoryDao = fakeInventoryDao,
            ioDispatcher = Dispatchers.Unconfined
        )

        // 1. Initial State should be empty
        assertEquals(0, repository.getProductInventoryFlow().first().size)

        // 2. Add product via Add Product dialog parameters: name, sku, current stock, reorder threshold
        val productId = repository.addProduct(
            productName = "Augmentin 625 Duo",
            sku = "SKU-AUG-625-10T",
            currentStock = 14,
            reorderThreshold = 25,
            dosageForm = "Tablet",
            packSize = "Box of 10 Strips x 10 Tabs",
            saltComposition = "Amoxicillin (500mg) + Clavulanic Acid (125mg)"
        )
        assertTrue(productId > 0)

        // 3. Verify product is listed in inventory flow
        val inventoryList = repository.getProductInventoryFlow().first()
        assertEquals(1, inventoryList.size)
        val item = inventoryList[0]
        assertEquals("Augmentin 625 Duo", item.productName)
        assertEquals("SKU-AUG-625-10T", item.sku)
        assertEquals(14, item.currentStock)
        assertEquals(25, item.reorderThreshold)

        // 4. Verify low stock depletion threshold
        assertTrue(item.currentStock <= item.reorderThreshold)

        // 5. Add second healthy stock product
        repository.addProduct(
            productName = "Paracetamol 650mg",
            sku = "SKU-PARA-650-15T",
            currentStock = 100,
            reorderThreshold = 30,
            dosageForm = "Tablet"
        )
        assertEquals(2, repository.getProductInventoryFlow().first().size)

        // 6. Test stock adjustment
        repository.adjustStockBySku("SKU-AUG-625-10T", delta = +20)
        val updatedAug = repository.getProductInventoryFlow().first().first { it.sku == "SKU-AUG-625-10T" }
        assertEquals(34, updatedAug.currentStock)
        assertFalse(updatedAug.currentStock <= updatedAug.reorderThreshold)

        // 7. Test ViewModel search and filter logic
        val viewModel = com.example.ui.screens.products.ProductListViewModel(repository)
        viewModel.onSearchQueryChange("Augmentin")
        val searchResults = viewModel.uiState.value.filteredProducts
        assertEquals(1, searchResults.size)
        assertEquals("SKU-AUG-625-10T", searchResults[0].sku)

        // 8. Test ViewModel addProduct validation and execution
        viewModel.addProduct(
            productName = "Pan-D Capsule",
            sku = "SKU-PAND-15C",
            currentStock = 12,
            reorderThreshold = 20
        )
        assertEquals(3, repository.getProductInventoryFlow().first().size)
    }
}

// In-memory fake DAO for testing ProductRepository
private class FakeProductDao : ProductDao {
    val items = mutableListOf<ProductEntity>()
    var nextId = 1L

    override suspend fun insertProduct(product: ProductEntity): Long {
        val id = if (product.id > 0) product.id else nextId++
        val saved = product.copy(id = id)
        items.removeAll { it.id == id }
        items.add(saved)
        return id
    }

    override suspend fun insertProducts(products: List<ProductEntity>): List<Long> {
        return products.map { insertProduct(it) }
    }

    override suspend fun updateProduct(product: ProductEntity) {
        val idx = items.indexOfFirst { it.id == product.id }
        if (idx >= 0) items[idx] = product
    }

    override suspend fun deleteProduct(product: ProductEntity) {
        items.removeAll { it.id == product.id }
    }

    override suspend fun deleteProductById(productId: Long) {
        items.removeAll { it.id == productId }
    }

    override suspend fun deleteAllProducts() {
        items.clear()
    }

    override suspend fun getProductById(productId: Long): ProductEntity? {
        return items.firstOrNull { it.id == productId }
    }

    override fun getProductByIdFlow(productId: Long): Flow<ProductEntity?> {
        return flowOf(items.firstOrNull { it.id == productId })
    }

    override fun getAllActiveProductsFlow(): Flow<List<ProductEntity>> {
        return flowOf(items.filter { it.isActive })
    }

    override fun getAllProductsFlow(): Flow<List<ProductEntity>> {
        return flowOf(items.toList())
    }

    override suspend fun getAllProducts(): List<ProductEntity> {
        return items.toList()
    }

    override suspend fun getActiveProductCount(): Int {
        return items.count { it.isActive }
    }

    override fun getAlternativeBrandsFlow(
        saltComposition: String,
        strength: String,
        currentProductId: Long
    ): Flow<List<ProductEntity>> {
        return flowOf(items.filter {
            it.isActive && it.saltComposition == saltComposition && it.strength == strength && it.id != currentProductId
        })
    }

    override suspend fun setProductActiveStatus(productId: Long, isActive: Boolean) {
        val idx = items.indexOfFirst { it.id == productId }
        if (idx >= 0) {
            items[idx] = items[idx].copy(isActive = isActive)
        }
    }

    override fun searchProductsFlow(query: String): Flow<List<ProductEntity>> {
        val q = query.lowercase()
        return flowOf(items.filter {
            it.isActive && (it.name.lowercase().contains(q) || it.brand.lowercase().contains(q))
        })
    }
}

private class FakeProductInventoryDao : com.example.data.local.dao.ProductInventoryDao {
    val items = mutableListOf<com.example.data.local.entity.ProductInventoryEntity>()
    var nextId = 1L

    override suspend fun insertInventory(inventory: com.example.data.local.entity.ProductInventoryEntity): Long {
        val id = if (inventory.id > 0) inventory.id else nextId++
        val saved = inventory.copy(id = id)
        items.removeAll { it.sku == inventory.sku || it.id == id }
        items.add(saved)
        return id
    }

    override suspend fun insertAll(items: List<com.example.data.local.entity.ProductInventoryEntity>): List<Long> {
        return items.map { insertInventory(it) }
    }

    override suspend fun updateInventory(inventory: com.example.data.local.entity.ProductInventoryEntity) {
        val idx = items.indexOfFirst { it.id == inventory.id || it.sku == inventory.sku }
        if (idx >= 0) items[idx] = inventory
    }

    override suspend fun deleteInventory(inventory: com.example.data.local.entity.ProductInventoryEntity) {
        items.removeAll { it.id == inventory.id || it.sku == inventory.sku }
    }

    override fun getAllInventoryFlow(): Flow<List<com.example.data.local.entity.ProductInventoryEntity>> {
        return flowOf(items.toList())
    }

    override fun getAllInventoryIncludingInactiveFlow(): Flow<List<com.example.data.local.entity.ProductInventoryEntity>> {
        return flowOf(items.toList())
    }

    override fun getLowStockInventoryFlow(): Flow<List<com.example.data.local.entity.ProductInventoryEntity>> {
        return flowOf(items.filter { it.currentStock <= it.reorderThreshold })
    }

    override fun getOutOfStockInventoryFlow(): Flow<List<com.example.data.local.entity.ProductInventoryEntity>> {
        return flowOf(items.filter { it.currentStock <= 0 })
    }

    override fun getInventoryByIdFlow(id: Long): Flow<com.example.data.local.entity.ProductInventoryEntity?> {
        return flowOf(items.firstOrNull { it.id == id })
    }

    override fun getInventoryBySkuFlow(sku: String): Flow<com.example.data.local.entity.ProductInventoryEntity?> {
        return flowOf(items.firstOrNull { it.sku == sku })
    }

    override fun searchInventoryFlow(query: String): Flow<List<com.example.data.local.entity.ProductInventoryEntity>> {
        val q = query.lowercase()
        return flowOf(items.filter { it.productName.lowercase().contains(q) || it.sku.lowercase().contains(q) })
    }

    override suspend fun getAllInventory(): List<com.example.data.local.entity.ProductInventoryEntity> = items.toList()
    override suspend fun getInventoryById(id: Long): com.example.data.local.entity.ProductInventoryEntity? = items.firstOrNull { it.id == id }
    override suspend fun getInventoryBySku(sku: String): com.example.data.local.entity.ProductInventoryEntity? = items.firstOrNull { it.sku == sku }
    override suspend fun getLowStockItems(): List<com.example.data.local.entity.ProductInventoryEntity> = items.filter { it.currentStock <= it.reorderThreshold }
    override suspend fun getItemsBelowThreshold(threshold: Int): List<com.example.data.local.entity.ProductInventoryEntity> = items.filter { it.currentStock <= threshold }
    override suspend fun getLowStockCount(): Int = items.count { it.currentStock <= it.reorderThreshold }
    override suspend fun getOutOfStockCount(): Int = items.count { it.currentStock <= 0 }
    override suspend fun getTotalStockUnits(): Int = items.sumOf { it.currentStock }
    override suspend fun getTotalSkuCount(): Int = items.size

    override suspend fun updateCurrentStock(id: Long, newStock: Int, timestamp: Long) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) items[idx] = items[idx].copy(currentStock = newStock)
    }

    override suspend fun updateCurrentStockBySku(sku: String, newStock: Int, timestamp: Long) {
        val idx = items.indexOfFirst { it.sku == sku }
        if (idx >= 0) items[idx] = items[idx].copy(currentStock = newStock)
    }

    override suspend fun updateReorderThreshold(id: Long, newThreshold: Int) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) items[idx] = items[idx].copy(reorderThreshold = newThreshold)
    }

    override suspend fun updateReorderThresholdBySku(sku: String, newThreshold: Int) {
        val idx = items.indexOfFirst { it.sku == sku }
        if (idx >= 0) items[idx] = items[idx].copy(reorderThreshold = newThreshold)
    }

    override suspend fun addStock(sku: String, quantityToAdd: Int, timestamp: Long) {
        val idx = items.indexOfFirst { it.sku == sku }
        if (idx >= 0) items[idx] = items[idx].copy(currentStock = items[idx].currentStock + quantityToAdd)
    }

    override suspend fun deductStock(sku: String, quantityToDeduct: Int, timestamp: Long) {
        val idx = items.indexOfFirst { it.sku == sku }
        if (idx >= 0) items[idx] = items[idx].copy(currentStock = maxOf(0, items[idx].currentStock - quantityToDeduct))
    }

    override suspend fun deleteById(id: Long) {
        items.removeAll { it.id == id }
    }

    override suspend fun deleteBySku(sku: String) {
        items.removeAll { it.sku == sku }
    }

    override suspend fun deleteAll() {
        items.clear()
    }
}


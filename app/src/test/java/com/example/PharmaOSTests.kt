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
import com.example.domain.model.OrderStatus
import com.example.domain.model.ReturnReason
import com.example.domain.model.ReturnStatus
import com.example.domain.model.SchemeType
import com.example.domain.model.ShortageRiskLevel
import com.example.domain.repository.BasketItemDetail
import com.example.domain.usecase.CalculateTrustScoreUseCase
import com.example.domain.usecase.EvaluateShortageRiskUseCase
import com.example.domain.usecase.OptimizeBasketUseCase
import com.example.domain.usecase.ParsePurchaseListUseCase
import com.example.util.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}

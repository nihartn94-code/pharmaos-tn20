package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.AppDatabaseMigrations
import com.example.data.local.entity.MedicineSubmissionEntity
import com.example.data.local.entity.OfferBannerEntity
import com.example.data.local.entity.OrganizationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Deterministic Room 5 -> 6 migration verification test.
 *
 * Builds an actual SQLite database at user_version = 5 containing real v5 rows
 * across users, products, orders, baskets, and addresses, then opens it with
 * Room's AppDatabase + AppDatabaseMigrations.ALL_MIGRATIONS (WITHOUT
 * fallbackToDestructiveMigration) so Room executes MIGRATION_5_6 and performs
 * full v6 schema validation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomMigration5To6Test {

    private lateinit var context: Context
    private val dbName = "pharmaos_migration_5_6_test.db"
    private var migratedDb: AppDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        migratedDb?.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration5To6PreservesAllExistingRowsAndCreatesValidV6Schema() = runBlocking {
        // 1. Create an exact v5 SQLite database on disk and populate legacy rows
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            createExactV5Schema(sqlite)
            seedV5LegacyRows(sqlite)
            sqlite.version = 5
        } finally {
            sqlite.close()
        }

        // 2. Open via Room WITHOUT fallbackToDestructiveMigration so MIGRATION_5_6 runs
        //    and Room strictly validates every column, type, nullability, and index of v6.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabaseMigrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        migratedDb = db

        // 3. Verify pre-existing v5 user rows are preserved with safe v6 column defaults
        val preservedUser = db.userDao().getUserByEmail("legacy.stockist@pharmaos.com")
        assertNotNull("Legacy v5 user row must be preserved across 5->6 migration", preservedUser)
        assertEquals("Legacy Stockist", preservedUser!!.fullName)
        assertEquals("Apex Pharmacy", preservedUser.businessName)
        assertEquals("STOCKIST", preservedUser.role)
        assertEquals(0L, preservedUser.organizationId)
        assertEquals("", preservedUser.permissionsCsv)

        // 4. Verify pre-existing v5 address rows are preserved
        val addresses = db.userAddressDao().getAddressesForUserFlow(preservedUser.id).first()
        assertEquals(1, addresses.size)
        assertEquals("Mumbai", addresses.first().city)

        // 5. Verify pre-existing v5 product rows are preserved with APPROVED/ACTIVE defaults
        val products = db.productDao().getAllProductsFlow().first()
        assertEquals(1, products.size)
        val preservedProduct = products.first()
        assertEquals("CardioShield 40", preservedProduct.name)
        assertEquals("30049099", preservedProduct.hsnCode)
        assertEquals("APPROVED", preservedProduct.approvalStatus)
        assertEquals("ACTIVE", preservedProduct.catalogueStatus)

        // 6. Verify pre-existing v5 order row is preserved
        val orders = db.orderDao().getOrdersForBuyerFlow(preservedUser.id).first()
        assertEquals(1, orders.size)
        assertEquals("ORD-V5-001", orders.first().orderNumber)
        assertEquals(2450.0, orders.first().totalAmount, 0.01)

        // 7. Verify newly created v6 tables accept inserts and queries cleanly
        val workspaceDao = db.workspaceManagementDao()
        val orgId = workspaceDao.upsertOrganization(
            OrganizationEntity(
                name = "Bharat Bio-Pharma",
                type = "PHARMACEUTICAL_ORGANIZATION",
                gstin = "27AABCB1234F1Z9",
                drugLicenseNumber = "MH-MZ4-123456",
                address = "Andheri East, Mumbai",
                isActive = true,
                createdAtEpochMillis = 1_700_000_000_000L
            )
        )
        assertTrue(orgId > 0L)

        val submissionId = workspaceDao.insertSubmission(
            MedicineSubmissionEntity(
                organizationId = orgId,
                organizationName = "Bharat Bio-Pharma",
                submittedByUserId = preservedUser.id,
                submittedByName = preservedUser.fullName,
                submittedByRole = "MANUFACTURER",
                medicineName = "TelmiCore 40",
                brandName = "TelmiCore",
                genericName = "Telmisartan",
                saltComposition = "Telmisartan 40mg",
                dosageForm = "Tablet",
                strength = "40 mg",
                routeOfAdministration = "Oral",
                packSize = "Strip of 10",
                packagingType = "Blister Strip",
                therapeuticCategory = "Cardiology",
                storageCondition = "ROOM_TEMPERATURE",
                isRx = true,
                mrp = 140.0,
                wholesaleRatePtr = 98.0,
                hsnCode = "30049099",
                gstRatePercent = 12.0,
                skuCode = "TELMI-40-10T",
                description = "Antihypertensive tablet",
                approvalStatus = "PENDING_APPROVAL",
                catalogueStatus = "INACTIVE"
            )
        )
        assertTrue(submissionId > 0L)

        val bannerId = workspaceDao.insertBanner(
            OfferBannerEntity(
                organizationId = orgId,
                organizationName = "Bharat Bio-Pharma",
                title = "Cardiology Launch Scheme",
                subtitle = "10+2 Strips Free",
                offerType = "FLAT_SCHEME",
                discountOrOfferSummary = "10+2 Free",
                startDateEpochMillis = 1_700_000_000_000L,
                endDateEpochMillis = 1_900_000_000_000L,
                priorityOrder = 1,
                status = "ACTIVE",
                targetAudience = "STOCKISTS",
                ctaText = "Explore Offer",
                ctaDestinationRoute = "explore",
                createdByUserId = preservedUser.id
            )
        )
        assertTrue(bannerId > 0L)
        assertEquals(1, workspaceDao.getBannersByOrganizationFlow(orgId).first().size)
    }

    private fun seedV5LegacyRows(db: SQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO `users` (`id`, `fullName`, `businessName`, `email`, `phone`, `gstin`, `address`,
                `passwordHash`, `passwordSalt`, `role`, `status`, `createdAtEpochMillis`,
                `emailVerified`, `phoneVerified`, `profileBio`)
            VALUES (101, 'Legacy Stockist', 'Apex Pharmacy', 'legacy.stockist@pharmaos.com',
                '9876543210', '27ABCDE1234F1Z5', 'Lower Parel, Mumbai',
                'hash_v5', 'salt_v5', 'STOCKIST', 'ACTIVE', 1700000000000, 1, 1, 'Legacy v5 account')
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `user_addresses` (`id`, `userId`, `addressName`, `contactPerson`, `phone`,
                `addressLine1`, `addressLine2`, `city`, `state`, `postalCode`, `country`, `isDefault`, `updatedAtEpochMillis`)
            VALUES (1, 101, 'Main Warehouse', 'Legacy Stockist', '9876543210',
                'Plot 42 MIDC', 'Phase 2', 'Mumbai', 'Maharashtra', '400093', 'India', 1, 1700000000000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `products` (`id`, `name`, `brand`, `saltComposition`, `strength`, `dosageForm`,
                `therapeuticCategory`, `manufacturerName`, `packSize`, `hsnCode`, `gstRatePercent`,
                `storageCondition`, `isRx`, `isActive`, `createdBy`, `createdAtEpochMillis`)
            VALUES (501, 'CardioShield 40', 'CardioShield', 'Atorvastatin 40mg', '40 mg', 'Tablet',
                'Cardiology', 'Bharat Bio-Pharma', 'Strip of 10', '30049099', 12.0,
                'ROOM_TEMPERATURE', 1, 1, 201, 1700000000000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `orders` (`id`, `orderNumber`, `buyerId`, `supplierId`, `status`, `subtotal`,
                `gstAmount`, `deliveryCharge`, `totalAmount`, `deliveryAddress`, `notes`,
                `isEwayBillGenerated`, `createdAtEpochMillis`)
            VALUES (901, 'ORD-V5-001', 101, 201, 'CONFIRMED', 2187.5, 262.5, 0.0, 2450.0,
                'Plot 42 MIDC, Mumbai', 'Priority dispatch', 0, 1700000000000)
            """.trimIndent()
        )
    }

    private fun createExactV5Schema(db: SQLiteDatabase) {
        // 1. users (v5 schema — before org/RBAC columns)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `users` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `fullName` TEXT NOT NULL,
                `businessName` TEXT NOT NULL,
                `email` TEXT NOT NULL,
                `phone` TEXT NOT NULL,
                `gstin` TEXT,
                `address` TEXT NOT NULL,
                `passwordHash` TEXT NOT NULL,
                `passwordSalt` TEXT NOT NULL,
                `role` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `emailVerified` INTEGER NOT NULL,
                `phoneVerified` INTEGER NOT NULL,
                `profileBio` TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_email` ON `users` (`email`)")

        // 2. user_addresses
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_addresses` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `userId` INTEGER NOT NULL,
                `addressName` TEXT NOT NULL,
                `contactPerson` TEXT NOT NULL,
                `phone` TEXT NOT NULL,
                `addressLine1` TEXT NOT NULL,
                `addressLine2` TEXT NOT NULL,
                `city` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `postalCode` TEXT NOT NULL,
                `country` TEXT NOT NULL,
                `isDefault` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_addresses_userId` ON `user_addresses` (`userId`)")

        // 3. products (v5 schema — before governance columns)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `products` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `brand` TEXT NOT NULL,
                `saltComposition` TEXT NOT NULL,
                `strength` TEXT NOT NULL,
                `dosageForm` TEXT NOT NULL,
                `therapeuticCategory` TEXT NOT NULL,
                `manufacturerName` TEXT NOT NULL,
                `packSize` TEXT NOT NULL,
                `hsnCode` TEXT NOT NULL,
                `gstRatePercent` REAL NOT NULL,
                `storageCondition` TEXT NOT NULL,
                `isRx` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                `createdBy` INTEGER NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_name` ON `products` (`name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_brand` ON `products` (`brand`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_saltComposition` ON `products` (`saltComposition`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_therapeuticCategory` ON `products` (`therapeuticCategory`)")

        // 4. product_inventory
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `product_inventory` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `productName` TEXT NOT NULL,
                `sku` TEXT NOT NULL,
                `currentStock` INTEGER NOT NULL,
                `reorderThreshold` INTEGER NOT NULL,
                `productId` INTEGER,
                `saltComposition` TEXT NOT NULL,
                `dosageForm` TEXT NOT NULL,
                `packSize` TEXT NOT NULL,
                `batchNumber` TEXT NOT NULL,
                `expiryDateEpochMillis` INTEGER,
                `unitPricePtr` REAL NOT NULL,
                `mrp` REAL NOT NULL,
                `storageCondition` TEXT NOT NULL,
                `locationRack` TEXT NOT NULL,
                `supplierName` TEXT NOT NULL,
                `isActive` INTEGER NOT NULL,
                `lastAuditedEpochMillis` INTEGER NOT NULL,
                `lastRestockedEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_product_inventory_sku` ON `product_inventory` (`sku`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_inventory_productName` ON `product_inventory` (`productName`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_inventory_currentStock` ON `product_inventory` (`currentStock`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_inventory_reorderThreshold` ON `product_inventory` (`reorderThreshold`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_inventory_productId` ON `product_inventory` (`productId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_inventory_storageCondition` ON `product_inventory` (`storageCondition`)")

        // 5. supplier_offers
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `supplier_offers` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `productId` INTEGER NOT NULL,
                `supplierId` INTEGER NOT NULL,
                `unitPricePtr` REAL NOT NULL,
                `mrp` REAL NOT NULL,
                `moq` INTEGER NOT NULL,
                `availableQuantity` INTEGER NOT NULL,
                `schemeType` TEXT NOT NULL,
                `schemeBuyQty` INTEGER NOT NULL,
                `schemeFreeQty` INTEGER NOT NULL,
                `schemeDiscountPercent` REAL NOT NULL,
                `schemeMinQtyForDiscount` INTEGER NOT NULL,
                `validTillEpochMillis` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                `lastUpdatedEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_offers_productId` ON `supplier_offers` (`productId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_offers_supplierId` ON `supplier_offers` (`supplierId`)")

        // 6. baskets
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `baskets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `buyerId` INTEGER NOT NULL,
                `name` TEXT,
                `isSaved` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_baskets_buyerId` ON `baskets` (`buyerId`)")

        // 7. basket_items
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `basket_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `basketId` INTEGER NOT NULL,
                `productId` INTEGER NOT NULL,
                `offerId` INTEGER NOT NULL,
                `supplierId` INTEGER NOT NULL,
                `quantity` INTEGER NOT NULL,
                `addedAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_basket_items_basketId` ON `basket_items` (`basketId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_basket_items_productId` ON `basket_items` (`productId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_basket_items_offerId` ON `basket_items` (`offerId`)")

        // 8. orders
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `orders` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `orderNumber` TEXT NOT NULL,
                `buyerId` INTEGER NOT NULL,
                `supplierId` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `subtotal` REAL NOT NULL,
                `gstAmount` REAL NOT NULL,
                `deliveryCharge` REAL NOT NULL,
                `totalAmount` REAL NOT NULL,
                `deliveryAddress` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `isEwayBillGenerated` INTEGER NOT NULL,
                `ewayBillNumber` TEXT,
                `vehicleNumber` TEXT,
                `transporterName` TEXT,
                `expectedDeliveryEpochMillis` INTEGER,
                `dispatchEpochMillis` INTEGER,
                `deliveredEpochMillis` INTEGER,
                `invoiceNumber` TEXT,
                `invoiceIssuedAtEpochMillis` INTEGER,
                `createdAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_buyerId` ON `orders` (`buyerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_supplierId` ON `orders` (`supplierId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_orders_orderNumber` ON `orders` (`orderNumber`)")

        // 9. order_items
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `order_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `orderId` INTEGER NOT NULL,
                `productId` INTEGER NOT NULL,
                `offerId` INTEGER NOT NULL,
                `productName` TEXT NOT NULL,
                `brand` TEXT NOT NULL,
                `packSize` TEXT NOT NULL,
                `hsnCode` TEXT NOT NULL,
                `gstRatePercent` REAL NOT NULL,
                `orderedQuantity` INTEGER NOT NULL,
                `freeQuantity` INTEGER NOT NULL,
                `fulfilledQuantity` INTEGER NOT NULL,
                `unitPricePtr` REAL NOT NULL,
                `effectivePrice` REAL NOT NULL,
                `batchNumber` TEXT,
                `expiryDateEpochMillis` INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_items_orderId` ON `order_items` (`orderId`)")

        // 10. order_status_history
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `order_status_history` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `orderId` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `note` TEXT NOT NULL,
                `changedAtEpochMillis` INTEGER NOT NULL,
                `changedByUserId` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_status_history_orderId` ON `order_status_history` (`orderId`)")

        // 11. batches
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `batches` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `productId` INTEGER NOT NULL,
                `supplierId` INTEGER NOT NULL,
                `batchNumber` TEXT NOT NULL,
                `mfgDateEpochMillis` INTEGER,
                `expiryDateEpochMillis` INTEGER NOT NULL,
                `initialQuantity` INTEGER NOT NULL,
                `remainingQuantity` INTEGER NOT NULL,
                `storageCondition` TEXT NOT NULL,
                `mrp` REAL NOT NULL,
                `ptr` REAL NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_batches_productId` ON `batches` (`productId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_batches_supplierId` ON `batches` (`supplierId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_batches_batchNumber` ON `batches` (`batchNumber`)")

        // 12. stock_records
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `stock_records` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `productId` INTEGER NOT NULL,
                `supplierId` INTEGER NOT NULL,
                `offerId` INTEGER NOT NULL,
                `availableQuantity` INTEGER NOT NULL,
                `recordedAtEpochMillis` INTEGER NOT NULL,
                `reason` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_records_productId` ON `stock_records` (`productId`)")

        // 13. returns
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `returns` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `orderId` INTEGER NOT NULL,
                `orderItemId` INTEGER NOT NULL,
                `buyerId` INTEGER NOT NULL,
                `supplierId` INTEGER NOT NULL,
                `productId` INTEGER NOT NULL,
                `batchNumber` TEXT NOT NULL,
                `quantity` INTEGER NOT NULL,
                `reason` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `requestedAtEpochMillis` INTEGER NOT NULL,
                `decisionAtEpochMillis` INTEGER,
                `decisionNotes` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_returns_orderId` ON `returns` (`orderId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_returns_buyerId` ON `returns` (`buyerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_returns_supplierId` ON `returns` (`supplierId`)")

        // 14. credit_notes
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `credit_notes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `creditNoteNumber` TEXT NOT NULL,
                `returnId` INTEGER NOT NULL,
                `orderId` INTEGER NOT NULL,
                `buyerId` INTEGER NOT NULL,
                `supplierId` INTEGER NOT NULL,
                `amount` REAL NOT NULL,
                `gstAmount` REAL NOT NULL,
                `totalAmount` REAL NOT NULL,
                `issuedAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_credit_notes_creditNoteNumber` ON `credit_notes` (`creditNoteNumber`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_credit_notes_returnId` ON `credit_notes` (`returnId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_credit_notes_buyerId` ON `credit_notes` (`buyerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_credit_notes_supplierId` ON `credit_notes` (`supplierId`)")

        // 15. credit_ledger
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `credit_ledger` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `buyerId` INTEGER NOT NULL,
                `supplierId` INTEGER NOT NULL,
                `entryType` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `balanceAfter` REAL NOT NULL,
                `referenceType` TEXT NOT NULL,
                `referenceId` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `dateEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_credit_ledger_buyerId` ON `credit_ledger` (`buyerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_credit_ledger_supplierId` ON `credit_ledger` (`supplierId`)")

        // 16. notifications
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `notifications` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipientUserId` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `message` TEXT NOT NULL,
                `referenceId` TEXT,
                `isRead` INTEGER NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_recipientUserId` ON `notifications` (`recipientUserId`)")

        // 17. supplier_performance
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `supplier_performance` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `supplierId` INTEGER NOT NULL,
                `overallScore` INTEGER NOT NULL,
                `completedOrdersCount` INTEGER NOT NULL,
                `fulfilmentRate` REAL NOT NULL,
                `onTimeRate` REAL NOT NULL,
                `cancellationRate` REAL NOT NULL,
                `discrepancyRate` REAL NOT NULL,
                `returnRate` REAL NOT NULL,
                `calculatedAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_supplier_performance_supplierId` ON `supplier_performance` (`supplierId`)")

        // 18. recalls
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recalls` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `productId` INTEGER NOT NULL,
                `productName` TEXT NOT NULL,
                `batchNumbersCsv` TEXT NOT NULL,
                `reason` TEXT NOT NULL,
                `severity` TEXT NOT NULL,
                `createdBySupplierId` INTEGER NOT NULL,
                `recallDateEpochMillis` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recalls_productId` ON `recalls` (`productId`)")

        // 19. shortage_risks
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shortage_risks` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `productId` INTEGER NOT NULL,
                `riskLevel` TEXT NOT NULL,
                `triggeredReasonsCsv` TEXT NOT NULL,
                `totalAvailableQuantity` INTEGER NOT NULL,
                `availableSuppliersCount` INTEGER NOT NULL,
                `avgLeadTimeDays` REAL NOT NULL,
                `evaluatedAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_shortage_risks_productId` ON `shortage_risks` (`productId`)")

        // 20. proof_of_delivery
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `proof_of_delivery` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `orderId` INTEGER NOT NULL,
                `receivedDateEpochMillis` INTEGER NOT NULL,
                `receivedByName` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `photoUri` TEXT,
                `shortQuantity` INTEGER NOT NULL,
                `damagedQuantity` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_proof_of_delivery_orderId` ON `proof_of_delivery` (`orderId`)")

        // 21. supplier_settings
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `supplier_settings` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `supplierId` INTEGER NOT NULL,
                `deliveryCharge` REAL NOT NULL,
                `freeDeliveryThreshold` REAL NOT NULL,
                `averageLeadTimeDays` REAL NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_supplier_settings_supplierId` ON `supplier_settings` (`supplierId`)")

        // 22. compliance_verifications
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `compliance_verifications` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `userId` INTEGER NOT NULL,
                `gstin` TEXT NOT NULL,
                `isGstinVerified` INTEGER NOT NULL,
                `drugLicenseNumber` TEXT NOT NULL,
                `drugLicenseIssueDate` TEXT NOT NULL,
                `drugLicenseExpiryDate` TEXT NOT NULL,
                `drugLicenseDocUri` TEXT,
                `whoGmpCertificateNumber` TEXT,
                `whoGmpIssueDate` TEXT,
                `whoGmpExpiryDate` TEXT,
                `whoGmpDocUri` TEXT,
                `isWhoGmpApplicable` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `verifiedAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_compliance_verifications_userId` ON `compliance_verifications` (`userId`)")
    }
}

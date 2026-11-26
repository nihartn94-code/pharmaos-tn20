package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Deterministic, non-destructive Room migrations for PharmaOS.
 *
 * Historical note:
 * Versions 1..4 schema snapshots were not checked into this repository before v5.
 * Version 5 -> 6 is implemented deterministically below, preserving all existing
 * users, addresses, products, supplier offers, baskets, orders, inventory, batches,
 * ledger entries, returns, recalls, notifications, and compliance records.
 */
object AppDatabaseMigrations {

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Extend existing `users` table with organization & RBAC scoping columns
            addColumnIfMissing(db, "users", "organizationId", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "users", "organizationType", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "users", "organizationName", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "users", "assignedRolesCsv", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "users", "permissionsCsv", "TEXT NOT NULL DEFAULT ''")

            // 2. Extend existing `products` table with Company governance & approval lifecycle columns
            addColumnIfMissing(db, "products", "organizationId", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "products", "approvalStatus", "TEXT NOT NULL DEFAULT 'APPROVED'")
            addColumnIfMissing(db, "products", "catalogueStatus", "TEXT NOT NULL DEFAULT 'ACTIVE'")
            addColumnIfMissing(db, "products", "genericName", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "products", "routeOfAdministration", "TEXT NOT NULL DEFAULT 'Oral'")
            addColumnIfMissing(db, "products", "packagingType", "TEXT NOT NULL DEFAULT 'Blister Strip'")
            addColumnIfMissing(db, "products", "description", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "products", "imageUrl", "TEXT")
            addColumnIfMissing(db, "products", "skuCode", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "products", "mrp", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "products", "regulatoryIdentifier", "TEXT")

            // 3. Create new v6 workspace governance & promotional tables
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `organizations` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `gstin` TEXT,
                    `drugLicenseNumber` TEXT,
                    `address` TEXT NOT NULL,
                    `isActive` INTEGER NOT NULL,
                    `createdAtEpochMillis` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_organizations_type` ON `organizations` (`type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_organizations_name` ON `organizations` (`name`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `organization_members` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `organizationId` INTEGER NOT NULL,
                    `userId` INTEGER NOT NULL,
                    `fullName` TEXT NOT NULL,
                    `email` TEXT NOT NULL,
                    `rolesCsv` TEXT NOT NULL,
                    `permissionsCsv` TEXT NOT NULL,
                    `isActive` INTEGER NOT NULL,
                    `joinedAtEpochMillis` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_organization_members_organizationId` ON `organization_members` (`organizationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_organization_members_userId` ON `organization_members` (`userId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_organization_members_organizationId_userId` ON `organization_members` (`organizationId`, `userId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `medicine_submissions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `organizationId` INTEGER NOT NULL,
                    `organizationName` TEXT NOT NULL,
                    `submittedByUserId` INTEGER NOT NULL,
                    `submittedByName` TEXT NOT NULL,
                    `submittedByRole` TEXT NOT NULL,
                    `medicineName` TEXT NOT NULL,
                    `brandName` TEXT NOT NULL,
                    `genericName` TEXT NOT NULL,
                    `saltComposition` TEXT NOT NULL,
                    `dosageForm` TEXT NOT NULL,
                    `strength` TEXT NOT NULL,
                    `routeOfAdministration` TEXT NOT NULL,
                    `packSize` TEXT NOT NULL,
                    `packagingType` TEXT NOT NULL,
                    `therapeuticCategory` TEXT NOT NULL,
                    `storageCondition` TEXT NOT NULL,
                    `isRx` INTEGER NOT NULL,
                    `mrp` REAL NOT NULL,
                    `wholesaleRatePtr` REAL NOT NULL,
                    `hsnCode` TEXT NOT NULL,
                    `gstRatePercent` REAL NOT NULL,
                    `skuCode` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `imageUrl` TEXT,
                    `regulatoryIdentifier` TEXT,
                    `supportingDocUri` TEXT,
                    `approvalStatus` TEXT NOT NULL,
                    `catalogueStatus` TEXT NOT NULL,
                    `approvedProductId` INTEGER,
                    `reviewedByUserId` INTEGER,
                    `reviewedByName` TEXT,
                    `rejectionReason` TEXT,
                    `reviewNotes` TEXT,
                    `submittedAtEpochMillis` INTEGER NOT NULL,
                    `reviewedAtEpochMillis` INTEGER,
                    `updatedAtEpochMillis` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_submissions_organizationId` ON `medicine_submissions` (`organizationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_submissions_approvalStatus` ON `medicine_submissions` (`approvalStatus`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_submissions_submittedByUserId` ON `medicine_submissions` (`submittedByUserId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `approval_audit_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `submissionId` INTEGER NOT NULL,
                    `organizationId` INTEGER NOT NULL,
                    `action` TEXT NOT NULL,
                    `fromStatus` TEXT NOT NULL,
                    `toStatus` TEXT NOT NULL,
                    `actorUserId` INTEGER NOT NULL,
                    `actorName` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `timestampEpochMillis` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_approval_audit_logs_submissionId` ON `approval_audit_logs` (`submissionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_approval_audit_logs_organizationId` ON `approval_audit_logs` (`organizationId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `master_data_entries` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `organizationId` INTEGER NOT NULL,
                    `type` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `normalizedName` TEXT NOT NULL,
                    `code` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `isOrganizationScoped` INTEGER NOT NULL,
                    `isActive` INTEGER NOT NULL,
                    `createdAtEpochMillis` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_master_data_entries_type` ON `master_data_entries` (`type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_master_data_entries_organizationId` ON `master_data_entries` (`organizationId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_master_data_entries_type_normalizedName_organizationId` ON `master_data_entries` (`type`, `normalizedName`, `organizationId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `offer_banners` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `organizationId` INTEGER NOT NULL,
                    `organizationName` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `subtitle` TEXT NOT NULL,
                    `imageUrl` TEXT,
                    `offerType` TEXT NOT NULL,
                    `discountOrOfferSummary` TEXT NOT NULL,
                    `linkedProductId` INTEGER,
                    `linkedProductName` TEXT,
                    `linkedCategory` TEXT,
                    `linkedCampaignId` INTEGER,
                    `startDateEpochMillis` INTEGER NOT NULL,
                    `endDateEpochMillis` INTEGER NOT NULL,
                    `priorityOrder` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `targetAudience` TEXT NOT NULL,
                    `ctaText` TEXT NOT NULL,
                    `ctaDestinationRoute` TEXT NOT NULL,
                    `createdByUserId` INTEGER NOT NULL,
                    `createdAtEpochMillis` INTEGER NOT NULL,
                    `updatedAtEpochMillis` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_offer_banners_organizationId` ON `offer_banners` (`organizationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_offer_banners_status` ON `offer_banners` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_offer_banners_priorityOrder` ON `offer_banners` (`priorityOrder`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `promotional_campaigns` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `organizationId` INTEGER NOT NULL,
                    `organizationName` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `schemeSummary` TEXT NOT NULL,
                    `discountPercent` REAL NOT NULL,
                    `minOrderQuantity` INTEGER NOT NULL,
                    `linkedProductIdsCsv` TEXT NOT NULL,
                    `linkedProductNamesSummary` TEXT NOT NULL,
                    `termsAndConditions` TEXT NOT NULL,
                    `startDateEpochMillis` INTEGER NOT NULL,
                    `endDateEpochMillis` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `redemptionsCount` INTEGER NOT NULL,
                    `createdByUserId` INTEGER NOT NULL,
                    `createdAtEpochMillis` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_promotional_campaigns_organizationId` ON `promotional_campaigns` (`organizationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_promotional_campaigns_status` ON `promotional_campaigns` (`status`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `medicine_announcements` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `organizationId` INTEGER NOT NULL,
                    `organizationName` TEXT NOT NULL,
                    `linkedProductId` INTEGER NOT NULL,
                    `linkedProductName` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `imageUrl` TEXT,
                    `status` TEXT NOT NULL,
                    `createdByUserId` INTEGER NOT NULL,
                    `publishedAtEpochMillis` INTEGER,
                    `createdAtEpochMillis` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_announcements_organizationId` ON `medicine_announcements` (`organizationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_announcements_linkedProductId` ON `medicine_announcements` (`linkedProductId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_announcements_status` ON `medicine_announcements` (`status`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `stockist_purchased_inventory` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `stockistOrganizationId` INTEGER NOT NULL,
                    `stockistUserId` INTEGER NOT NULL,
                    `productId` INTEGER NOT NULL,
                    `productName` TEXT NOT NULL,
                    `brand` TEXT NOT NULL,
                    `saltComposition` TEXT NOT NULL,
                    `batchNumber` TEXT NOT NULL,
                    `expiryDateEpochMillis` INTEGER NOT NULL,
                    `purchasedQuantity` INTEGER NOT NULL,
                    `remainingQuantity` INTEGER NOT NULL,
                    `reorderLevel` INTEGER NOT NULL,
                    `purchaseUnitPrice` REAL NOT NULL,
                    `sourceOrderNumber` TEXT,
                    `updatedAtEpochMillis` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_stockist_purchased_inventory_stockistOrganizationId` ON `stockist_purchased_inventory` (`stockistOrganizationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_stockist_purchased_inventory_stockistUserId` ON `stockist_purchased_inventory` (`stockistUserId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_stockist_purchased_inventory_productId` ON `stockist_purchased_inventory` (`productId`)")
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_5_6)

    private fun addColumnIfMissing(
        db: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
        columnDefinition: String
    ) {
        val existingColumns = mutableSetOf<String>()
        db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIdx >= 0) {
                    existingColumns.add(cursor.getString(nameIdx))
                }
            }
        }
        if (columnName !in existingColumns) {
            db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnDefinition")
        }
    }
}

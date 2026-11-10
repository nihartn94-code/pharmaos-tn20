package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room database entity representing pharmaceutical product inventory levels.
 *
 * Tracks individual pharmaceutical SKUs, current on-hand stock counts,
 * and critical reorder thresholds along with medicine details and storage parameters.
 *
 * Mandatory fields:
 * - [productName]: Brand or generic name of the pharmaceutical medicine
 * - [sku]: Stock Keeping Unit code uniquely identifying the medicine/pack
 * - [currentStock]: Number of units currently available in local pharmacy inventory
 * - [reorderThreshold]: Minimum stock level below which reorder alerts are triggered
 */
@Entity(
    tableName = "product_inventory",
    indices = [
        Index(value = ["sku"], unique = true),
        Index(value = ["productName"]),
        Index(value = ["currentStock"]),
        Index(value = ["reorderThreshold"]),
        Index(value = ["productId"]),
        Index(value = ["storageCondition"])
    ]
)
data class ProductInventoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Product name (brand or generic title) */
    val productName: String,

    /** Unique Stock Keeping Unit code (e.g. "SKU-AUG-625-10T") */
    val sku: String,

    /** Current on-hand stock quantity in units/packs */
    val currentStock: Int,

    /** Reorder threshold level triggering replenishment alerts */
    val reorderThreshold: Int,

    /** Foreign reference to base product catalogue ID if linked */
    val productId: Long? = null,

    /** Active pharmaceutical ingredient or salt formula */
    val saltComposition: String = "",

    /** Pharmaceutical dosage form (Tablet, Syrup, Injection, Capsule, etc.) */
    val dosageForm: String = "Tablet",

    /** Packaging format description (e.g. "Box of 10 Strips x 10 Tabs") */
    val packSize: String = "Strip of 10",

    /** Active stock batch number */
    val batchNumber: String = "",

    /** Expiry timestamp in epoch milliseconds */
    val expiryDateEpochMillis: Long? = null,

    /** Wholesale Price To Retailer (PTR) in INR */
    val unitPricePtr: Double = 0.0,

    /** Maximum Retail Price (MRP) in INR */
    val mrp: Double = 0.0,

    /** Temperature & handling specification (e.g. ROOM_TEMPERATURE, COLD_CHAIN_2_TO_8) */
    val storageCondition: String = "ROOM_TEMPERATURE",

    /** Pharmacy physical bin/shelf location identifier (e.g. "RACK-B-04") */
    val locationRack: String = "A-1",

    /** Primary verified distributor or manufacturer name */
    val supplierName: String = "",

    /** Indicates whether item is active for dispensing and procurement */
    val isActive: Boolean = true,

    /** Timestamp of last inventory count or audit in epoch milliseconds */
    val lastAuditedEpochMillis: Long = System.currentTimeMillis(),

    /** Timestamp of last replenishment stock-in in epoch milliseconds */
    val lastRestockedEpochMillis: Long = System.currentTimeMillis()
) {
    /**
     * True if current stock is at or below the reorder threshold.
     */
    val isLowStock: Boolean
        get() = currentStock <= reorderThreshold

    /**
     * True if completely depleted of stock.
     */
    val isOutOfStock: Boolean
        get() = currentStock <= 0

    /**
     * Number of units required to bring stock back to twice the safe threshold.
     */
    val replenishmentQuantityNeeded: Int
        get() = if (currentStock < reorderThreshold) {
            (reorderThreshold * 2) - currentStock
        } else {
            0
        }

    /**
     * Total valuation of on-hand inventory at PTR cost.
     */
    val totalInventoryValuation: Double
        get() = currentStock * unitPricePtr
}

/**
 * Typealias for seamless interoperability if referenced as [InventoryEntity].
 */
typealias InventoryEntity = ProductInventoryEntity

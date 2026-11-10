package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ProductInventoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object (DAO) for managing pharmaceutical product inventory levels.
 *
 * Provides reactive [Flow] streams and suspend operations to insert, query, update,
 * audit stock levels, and monitor low-stock depletion thresholds.
 */
@Dao
interface ProductInventoryDao {

    // =========================================================================
    // INSERTS & WRITES
    // =========================================================================

    /**
     * Inserts or replaces a pharmaceutical product inventory record.
     * @return Generated primary key ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventory(inventory: ProductInventoryEntity): Long

    /**
     * Inserts a batch of pharmaceutical product inventory records.
     * @return List of generated primary key IDs
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProductInventoryEntity>): List<Long>

    /**
     * Updates an existing inventory record.
     */
    @Update
    suspend fun updateInventory(inventory: ProductInventoryEntity)

    /**
     * Deletes a specific inventory record.
     */
    @Delete
    suspend fun deleteInventory(inventory: ProductInventoryEntity)

    // =========================================================================
    // REACTIVE STREAMS (FLOW) FOR COMPOSE UI & SERVICES
    // =========================================================================

    /**
     * Emits the complete list of active pharmaceutical inventory sorted by product name.
     */
    @Query("SELECT * FROM product_inventory WHERE isActive = 1 ORDER BY productName ASC")
    fun getAllInventoryFlow(): Flow<List<ProductInventoryEntity>>

    /**
     * Emits all inventory items regardless of active status.
     */
    @Query("SELECT * FROM product_inventory ORDER BY productName ASC")
    fun getAllInventoryIncludingInactiveFlow(): Flow<List<ProductInventoryEntity>>

    /**
     * Emits inventory items whose current stock is at or below their reorder threshold.
     * Sorted in ascending order of stock (most depleted first).
     */
    @Query("""
        SELECT * FROM product_inventory 
        WHERE isActive = 1 AND currentStock <= reorderThreshold 
        ORDER BY currentStock ASC
    """)
    fun getLowStockInventoryFlow(): Flow<List<ProductInventoryEntity>>

    /**
     * Emits inventory items that are completely out of stock (currentStock <= 0).
     */
    @Query("SELECT * FROM product_inventory WHERE isActive = 1 AND currentStock <= 0 ORDER BY productName ASC")
    fun getOutOfStockInventoryFlow(): Flow<List<ProductInventoryEntity>>

    /**
     * Observes a specific inventory item by its database primary key.
     */
    @Query("SELECT * FROM product_inventory WHERE id = :id LIMIT 1")
    fun getInventoryByIdFlow(id: Long): Flow<ProductInventoryEntity?>

    /**
     * Observes a specific inventory item by its unique SKU code.
     */
    @Query("SELECT * FROM product_inventory WHERE sku = :sku LIMIT 1")
    fun getInventoryBySkuFlow(sku: String): Flow<ProductInventoryEntity?>

    /**
     * Searches inventory by product name, SKU, salt composition, or brand.
     */
    @Query("""
        SELECT * FROM product_inventory 
        WHERE isActive = 1 AND (
            productName LIKE '%' || :query || '%' OR 
            sku LIKE '%' || :query || '%' OR 
            saltComposition LIKE '%' || :query || '%' OR 
            supplierName LIKE '%' || :query || '%'
        )
        ORDER BY productName ASC
    """)
    fun searchInventoryFlow(query: String): Flow<List<ProductInventoryEntity>>

    // =========================================================================
    // ONE-OFF SUSPEND QUERIES (FOR WORKERS, AUDITS & REPOSITORIES)
    // =========================================================================

    /**
     * Fetches all inventory records synchronously.
     */
    @Query("SELECT * FROM product_inventory WHERE isActive = 1 ORDER BY productName ASC")
    suspend fun getAllInventory(): List<ProductInventoryEntity>

    /**
     * Retrieves an inventory record by its primary key ID.
     */
    @Query("SELECT * FROM product_inventory WHERE id = :id LIMIT 1")
    suspend fun getInventoryById(id: Long): ProductInventoryEntity?

    /**
     * Retrieves an inventory record by its unique SKU code.
     */
    @Query("SELECT * FROM product_inventory WHERE sku = :sku LIMIT 1")
    suspend fun getInventoryBySku(sku: String): ProductInventoryEntity?

    /**
     * Retrieves all items currently at or below their respective reorder threshold.
     */
    @Query("""
        SELECT * FROM product_inventory 
        WHERE isActive = 1 AND currentStock <= reorderThreshold 
        ORDER BY currentStock ASC
    """)
    suspend fun getLowStockItems(): List<ProductInventoryEntity>

    /**
     * Retrieves items whose stock is at or below an arbitrary threshold parameter.
     */
    @Query("""
        SELECT * FROM product_inventory 
        WHERE isActive = 1 AND currentStock <= :threshold 
        ORDER BY currentStock ASC
    """)
    suspend fun getItemsBelowThreshold(threshold: Int): List<ProductInventoryEntity>

    /**
     * Counts the total number of low stock items currently requiring replenishment.
     */
    @Query("SELECT COUNT(*) FROM product_inventory WHERE isActive = 1 AND currentStock <= reorderThreshold")
    suspend fun getLowStockCount(): Int

    /**
     * Counts the total number of items completely out of stock.
     */
    @Query("SELECT COUNT(*) FROM product_inventory WHERE isActive = 1 AND currentStock <= 0")
    suspend fun getOutOfStockCount(): Int

    /**
     * Sums all physical units across all pharmaceutical inventory lines.
     */
    @Query("SELECT COALESCE(SUM(currentStock), 0) FROM product_inventory WHERE isActive = 1")
    suspend fun getTotalStockUnits(): Int

    /**
     * Counts the total number of distinct active SKUs managed in inventory.
     */
    @Query("SELECT COUNT(*) FROM product_inventory WHERE isActive = 1")
    suspend fun getTotalSkuCount(): Int

    // =========================================================================
    // DIRECT IN-PLACE MODIFICATIONS & STOCK MOVEMENTS
    // =========================================================================

    /**
     * Updates the current stock count for an item by its ID, recording the audit timestamp.
     */
    @Query("""
        UPDATE product_inventory 
        SET currentStock = :newStock, lastAuditedEpochMillis = :timestamp 
        WHERE id = :id
    """)
    suspend fun updateCurrentStock(id: Long, newStock: Int, timestamp: Long = System.currentTimeMillis())

    /**
     * Updates the current stock count for an item by its SKU, recording the audit timestamp.
     */
    @Query("""
        UPDATE product_inventory 
        SET currentStock = :newStock, lastAuditedEpochMillis = :timestamp 
        WHERE sku = :sku
    """)
    suspend fun updateCurrentStockBySku(sku: String, newStock: Int, timestamp: Long = System.currentTimeMillis())

    /**
     * Adjusts the reorder threshold for an item by ID.
     */
    @Query("UPDATE product_inventory SET reorderThreshold = :newThreshold WHERE id = :id")
    suspend fun updateReorderThreshold(id: Long, newThreshold: Int)

    /**
     * Adjusts the reorder threshold for an item by SKU.
     */
    @Query("UPDATE product_inventory SET reorderThreshold = :newThreshold WHERE sku = :sku")
    suspend fun updateReorderThresholdBySku(sku: String, newThreshold: Int)

    /**
     * Increments on-hand stock for replenishment (stock-in) by SKU.
     */
    @Query("""
        UPDATE product_inventory 
        SET currentStock = currentStock + :quantityToAdd, 
            lastRestockedEpochMillis = :timestamp,
            lastAuditedEpochMillis = :timestamp
        WHERE sku = :sku
    """)
    suspend fun addStock(sku: String, quantityToAdd: Int, timestamp: Long = System.currentTimeMillis())

    /**
     * Deducts on-hand stock for dispensing or order fulfillment by SKU, preventing negative counts.
     */
    @Query("""
        UPDATE product_inventory 
        SET currentStock = MAX(0, currentStock - :quantityToDeduct), 
            lastAuditedEpochMillis = :timestamp 
        WHERE sku = :sku
    """)
    suspend fun deductStock(sku: String, quantityToDeduct: Int, timestamp: Long = System.currentTimeMillis())

    // =========================================================================
    // DELETIONS & CLEARING
    // =========================================================================

    /**
     * Deletes an inventory record by its ID.
     */
    @Query("DELETE FROM product_inventory WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Deletes an inventory record by its SKU.
     */
    @Query("DELETE FROM product_inventory WHERE sku = :sku")
    suspend fun deleteBySku(sku: String)

    /**
     * Clears all inventory records from the table.
     */
    @Query("DELETE FROM product_inventory")
    suspend fun deleteAll()
}

/**
 * Typealias for seamless interoperability if referenced as [InventoryDao].
 */
typealias InventoryDao = ProductInventoryDao

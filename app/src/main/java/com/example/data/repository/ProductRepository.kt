package com.example.data.repository

import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.ProductInventoryDao
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ProductInventoryEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Repository class that bridges the Room Database DAO ([ProductDao], [ProductInventoryDao])
 * with ViewModels across the application.
 *
 * Implements the Repository Pattern recommended by Android Architecture guidelines:
 * - Abstracts Room data sources from UI components and ViewModels.
 * - Dispatches all operations safely to [Dispatchers.IO].
 * - Exposes reactive [Flow] streams for real-time UI updates with StateFlow/collectAsStateWithLifecycle.
 * - Provides suspend functions for inserting, updating, deleting, and fetching products.
 */
class ProductRepository(
    private val productDao: ProductDao,
    private val inventoryDao: ProductInventoryDao? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    // =========================================================================
    // REACTIVE FLOW STREAMS (OBSERVABLE BY VIEWMODELS)
    // =========================================================================

    /**
     * Hot reactive stream of all products in the database sorted by name.
     */
    val allProducts: Flow<List<ProductEntity>> =
        productDao.getAllProductsFlow().flowOn(ioDispatcher)

    /**
     * Hot reactive stream of currently active pharmaceutical products.
     */
    val activeProducts: Flow<List<ProductEntity>> =
        productDao.getAllActiveProductsFlow().flowOn(ioDispatcher)

    /**
     * Fetches all products as a reactive [Flow].
     */
    fun getAllProductsFlow(): Flow<List<ProductEntity>> {
        return productDao.getAllProductsFlow().flowOn(ioDispatcher)
    }

    /**
     * Fetches all active products as a reactive [Flow].
     */
    fun getAllActiveProductsFlow(): Flow<List<ProductEntity>> {
        return productDao.getAllActiveProductsFlow().flowOn(ioDispatcher)
    }

    /**
     * Observes a specific product by its ID as a reactive [Flow].
     */
    fun getProductByIdFlow(productId: Long): Flow<ProductEntity?> {
        return productDao.getProductByIdFlow(productId).flowOn(ioDispatcher)
    }

    /**
     * Searches products reactively by name, brand, salt composition, or category.
     */
    fun searchProductsFlow(query: String): Flow<List<ProductEntity>> {
        val trimmed = query.trim()
        return if (trimmed.isBlank()) {
            productDao.getAllActiveProductsFlow().flowOn(ioDispatcher)
        } else {
            productDao.searchProductsFlow(trimmed).flowOn(ioDispatcher)
        }
    }

    /**
     * Emits alternative pharmaceutical brands sharing identical salt composition and strength.
     */
    fun getAlternativeBrandsFlow(
        saltComposition: String,
        strength: String,
        currentProductId: Long
    ): Flow<List<ProductEntity>> {
        return productDao.getAlternativeBrandsFlow(
            saltComposition = saltComposition,
            strength = strength,
            currentProductId = currentProductId
        ).flowOn(ioDispatcher)
    }

    // =========================================================================
    // ONE-OFF FETCHING FUNCTIONS (SUSPEND)
    // =========================================================================

    /**
     * Fetches a single product by primary key ID, or null if not found.
     */
    suspend fun getProductById(productId: Long): ProductEntity? = withContext(ioDispatcher) {
        productDao.getProductById(productId)
    }

    /**
     * Fetches all products synchronously from the database.
     */
    suspend fun getAllProducts(): List<ProductEntity> = withContext(ioDispatcher) {
        productDao.getAllProducts()
    }

    /**
     * Returns the count of active pharmaceutical products.
     */
    suspend fun getActiveProductCount(): Int = withContext(ioDispatcher) {
        productDao.getActiveProductCount()
    }

    // =========================================================================
    // INSERTION FUNCTIONS
    // =========================================================================

    /**
     * Inserts a single product into the database, returning the generated row ID.
     */
    suspend fun insertProduct(product: ProductEntity): Long = withContext(ioDispatcher) {
        productDao.insertProduct(product)
    }

    /**
     * Inserts a list of products in a single database transaction.
     */
    suspend fun insertProducts(products: List<ProductEntity>): List<Long> = withContext(ioDispatcher) {
        productDao.insertProducts(products)
    }

    // =========================================================================
    // UPDATING FUNCTIONS
    // =========================================================================

    /**
     * Updates an existing product entity in the database.
     */
    suspend fun updateProduct(product: ProductEntity): Unit = withContext(ioDispatcher) {
        productDao.updateProduct(product)
    }

    /**
     * Toggles or updates the active status flag of a product by ID.
     */
    suspend fun setProductActiveStatus(productId: Long, isActive: Boolean): Unit = withContext(ioDispatcher) {
        productDao.setProductActiveStatus(productId, isActive)
    }

    // =========================================================================
    // DELETING FUNCTIONS
    // =========================================================================

    /**
     * Deletes a specific product entity from the database.
     */
    suspend fun deleteProduct(product: ProductEntity): Unit = withContext(ioDispatcher) {
        productDao.deleteProduct(product)
    }

    /**
     * Deletes a product by its primary key ID.
     */
    suspend fun deleteProductById(productId: Long): Unit = withContext(ioDispatcher) {
        productDao.deleteProductById(productId)
    }

    /**
     * Clears all products from the database table.
     */
    suspend fun deleteAllProducts(): Unit = withContext(ioDispatcher) {
        productDao.deleteAllProducts()
    }

    // =========================================================================
    // INVENTORY LEVEL EXTENSIONS (BRIDGING PRODUCT INVENTORY DAO)
    // =========================================================================

    /**
     * Reactive stream of inventory levels if [ProductInventoryDao] is configured.
     */
    fun getProductInventoryFlow(): Flow<List<ProductInventoryEntity>> {
        return inventoryDao?.getAllInventoryFlow()?.flowOn(ioDispatcher) ?: emptyFlow()
    }

    /**
     * Reactive stream of items below their reorder threshold.
     */
    fun getLowStockInventoryFlow(): Flow<List<ProductInventoryEntity>> {
        return inventoryDao?.getLowStockInventoryFlow()?.flowOn(ioDispatcher) ?: emptyFlow()
    }

    /**
     * Searches inventory by query string (product name, SKU, or salt composition).
     */
    fun searchInventoryFlow(query: String): Flow<List<ProductInventoryEntity>> {
        val trimmed = query.trim()
        return if (trimmed.isBlank()) {
            inventoryDao?.getAllInventoryFlow()?.flowOn(ioDispatcher) ?: emptyFlow()
        } else {
            inventoryDao?.searchInventoryFlow(trimmed)?.flowOn(ioDispatcher) ?: emptyFlow()
        }
    }

    /**
     * Inserts a product inventory record with SKU and reorder threshold.
     */
    suspend fun insertProductInventory(inventory: ProductInventoryEntity): Long = withContext(ioDispatcher) {
        inventoryDao?.insertInventory(inventory) ?: -1L
    }

    /**
     * Convenience method to insert a new product into both catalogue and inventory tracking.
     */
    suspend fun addProduct(
        productName: String,
        sku: String,
        currentStock: Int,
        reorderThreshold: Int,
        dosageForm: String = "Tablet",
        packSize: String = "Strip of 10",
        saltComposition: String = "",
        unitPricePtr: Double = 0.0,
        mrp: Double = 0.0,
        storageCondition: String = "ROOM_TEMPERATURE",
        createdBy: Long = 1L
    ): Long = withContext(ioDispatcher) {
        val cleanName = productName.trim()
        val cleanSku = sku.trim().uppercase()

        // 1. Insert into ProductEntity for general catalogue access
        val productEntity = ProductEntity(
            name = cleanName,
            brand = cleanName.substringBefore(" ").trim(),
            saltComposition = saltComposition.ifBlank { cleanName },
            strength = "Standard",
            dosageForm = dosageForm,
            therapeuticCategory = "General Medicine",
            manufacturerName = "PharmaOS Formulation",
            packSize = packSize,
            hsnCode = "3004",
            gstRatePercent = 12.0,
            storageCondition = storageCondition,
            isRx = false,
            createdBy = createdBy
        )
        val generatedProductId = try {
            productDao.insertProduct(productEntity)
        } catch (_: Exception) {
            0L
        }

        // 2. Insert into ProductInventoryEntity for SKU and reorder threshold tracking
        val inventoryEntity = ProductInventoryEntity(
            productName = cleanName,
            sku = cleanSku,
            currentStock = currentStock,
            reorderThreshold = reorderThreshold,
            productId = if (generatedProductId > 0) generatedProductId else null,
            saltComposition = saltComposition.ifBlank { cleanName },
            dosageForm = dosageForm,
            packSize = packSize,
            unitPricePtr = unitPricePtr,
            mrp = mrp,
            storageCondition = storageCondition
        )
        inventoryDao?.insertInventory(inventoryEntity) ?: generatedProductId
    }

    /**
     * Updates the current stock count for a given SKU.
     */
    suspend fun updateCurrentStockBySku(sku: String, newStock: Int): Unit = withContext(ioDispatcher) {
        inventoryDao?.updateCurrentStockBySku(sku, newStock)
    }

    /**
     * Increments or decrements stock count for a given SKU by delta.
     */
    suspend fun adjustStockBySku(sku: String, delta: Int): Unit = withContext(ioDispatcher) {
        if (delta > 0) {
            inventoryDao?.addStock(sku, delta)
        } else if (delta < 0) {
            inventoryDao?.deductStock(sku, -delta)
        }
    }

    /**
     * Updates the reorder alert threshold for a given SKU.
     */
    suspend fun updateReorderThresholdBySku(sku: String, newThreshold: Int): Unit = withContext(ioDispatcher) {
        inventoryDao?.updateReorderThresholdBySku(sku, newThreshold)
    }

    /**
     * Deletes an inventory record by SKU.
     */
    suspend fun deleteInventoryBySku(sku: String): Unit = withContext(ioDispatcher) {
        inventoryDao?.deleteBySku(sku)
    }

    /**
     * Deletes an inventory record by primary key ID.
     */
    suspend fun deleteInventoryById(id: Long): Unit = withContext(ioDispatcher) {
        inventoryDao?.deleteById(id)
    }
}

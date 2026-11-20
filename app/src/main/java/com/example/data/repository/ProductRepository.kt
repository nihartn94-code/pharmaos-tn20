package com.example.data.repository

import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.ProductInventoryDao
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ProductInventoryEntity
import com.example.data.remote.dto.ProductDto
import com.example.data.remote.dto.ProductInventoryDto
import com.example.data.remote.mapper.toDto
import com.example.data.remote.mapper.toEntity
import com.example.data.remote.source.PharmaRemoteDataSource
import com.example.data.remote.util.NetworkResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Repository class that bridges the Room Database DAO ([ProductDao], [ProductInventoryDao])
 * and [PharmaRemoteDataSource] (Spring Boot REST API) with ViewModels across the application.
 *
 * Implements the Repository Pattern recommended by Android Architecture guidelines:
 * - Abstracts Room data sources & remote HTTP endpoints from UI components and ViewModels.
 * - Dispatches all operations safely to [Dispatchers.IO].
 * - Exposes reactive [Flow] streams backed by Room for real-time UI updates.
 * - When remote sync is active, write operations execute HTTP requests first and fail explicitly
 *   without silent fallback to local-only success if the backend request fails.
 */
class ProductRepository(
    private val productDao: ProductDao,
    private val inventoryDao: ProductInventoryDao? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val remoteDataSource: PharmaRemoteDataSource? = null,
    private val isRemoteSyncEnabled: () -> Boolean = { false }
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
    // REMOTE SYNCHRONIZATION (SPRING BOOT REST API -> ROOM CACHE)
    // =========================================================================

    /**
     * Pulls the product catalogue from the Spring Boot REST API and updates Room cache on HTTP 2xx.
     */
    suspend fun syncProductsFromBackend(): Result<Int> = withContext(ioDispatcher) {
        val remote = remoteDataSource
            ?: return@withContext Result.failure(IllegalStateException("Remote data source is not attached."))
        when (val res = remote.fetchProducts()) {
            is NetworkResult.Success -> {
                val entities = res.data.map { it.toEntity() }
                if (entities.isNotEmpty()) {
                    productDao.insertProducts(entities)
                }
                Result.success(entities.size)
            }
            else -> Result.failure(res.toException())
        }
    }

    /**
     * Pulls SKU inventory records from the Spring Boot REST API and updates Room cache on HTTP 2xx.
     */
    suspend fun syncInventoryFromBackend(): Result<Int> = withContext(ioDispatcher) {
        val remote = remoteDataSource
            ?: return@withContext Result.failure(IllegalStateException("Remote data source is not attached."))
        when (val res = remote.fetchInventory()) {
            is NetworkResult.Success -> {
                val entities = res.data.map { it.toEntity() }
                if (entities.isNotEmpty()) {
                    inventoryDao?.insertAll(entities)
                }
                Result.success(entities.size)
            }
            else -> Result.failure(res.toException())
        }
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
     * Inserts a single product into backend (when remote sync enabled) + Room cache,
     * returning the generated row ID. Throws on remote write failure instead of silent fallback.
     */
    suspend fun insertProduct(product: ProductEntity): Long = withContext(ioDispatcher) {
        if (isRemoteSyncEnabled() && remoteDataSource != null) {
            when (val res = remoteDataSource.createProduct(product.toDto())) {
                is NetworkResult.Success -> {
                    val savedEntity = res.data.toEntity()
                    return@withContext productDao.insertProduct(savedEntity)
                }
                else -> throw res.toException()
            }
        }
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
     * Updates an existing product entity via REST API (when remote enabled) and Room cache.
     */
    suspend fun updateProduct(product: ProductEntity): Unit = withContext(ioDispatcher) {
        if (isRemoteSyncEnabled() && remoteDataSource != null) {
            when (val res = remoteDataSource.updateProduct(product.id, product.toDto())) {
                is NetworkResult.Success -> {
                    productDao.updateProduct(res.data.toEntity())
                    return@withContext
                }
                else -> throw res.toException()
            }
        }
        productDao.updateProduct(product)
    }

    /**
     * Toggles or updates the active status flag of a product by ID.
     */
    suspend fun setProductActiveStatus(productId: Long, isActive: Boolean): Unit = withContext(ioDispatcher) {
        if (isRemoteSyncEnabled() && remoteDataSource != null) {
            when (val res = remoteDataSource.updateProductStatus(productId, isActive)) {
                is NetworkResult.Success -> {
                    productDao.setProductActiveStatus(productId, isActive)
                    return@withContext
                }
                else -> throw res.toException()
            }
        }
        productDao.setProductActiveStatus(productId, isActive)
    }

    // =========================================================================
    // DELETING FUNCTIONS
    // =========================================================================

    /**
     * Deletes a specific product entity from the backend (when remote enabled) and Room cache.
     */
    suspend fun deleteProduct(product: ProductEntity): Unit = withContext(ioDispatcher) {
        if (isRemoteSyncEnabled() && remoteDataSource != null) {
            when (val res = remoteDataSource.deleteProduct(product.id)) {
                is NetworkResult.Success -> {
                    productDao.deleteProduct(product)
                    return@withContext
                }
                else -> throw res.toException()
            }
        }
        productDao.deleteProduct(product)
    }

    /**
     * Deletes a product by its primary key ID.
     */
    suspend fun deleteProductById(productId: Long): Unit = withContext(ioDispatcher) {
        if (isRemoteSyncEnabled() && remoteDataSource != null) {
            when (val res = remoteDataSource.deleteProduct(productId)) {
                is NetworkResult.Success -> {
                    productDao.deleteProductById(productId)
                    return@withContext
                }
                else -> throw res.toException()
            }
        }
        productDao.deleteProductById(productId)
    }

    /**
     * Clears all products from the local Room cache table.
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
        if (isRemoteSyncEnabled() && remoteDataSource != null) {
            when (val res = remoteDataSource.upsertInventoryItem(inventory.toDto())) {
                is NetworkResult.Success -> {
                    return@withContext inventoryDao?.insertInventory(res.data.toEntity()) ?: res.data.id
                }
                else -> throw res.toException()
            }
        }
        inventoryDao?.insertInventory(inventory) ?: -1L
    }

    /**
     * Convenience method to insert a new product into both catalogue and inventory tracking.
     * In Backend HTTP mode, sends remote POST/PUT calls and fails explicitly on HTTP errors.
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

        if (isRemoteSyncEnabled() && remoteDataSource != null) {
            val remoteProductRes = remoteDataSource.createProduct(
                ProductDto(
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
            )
            val savedProduct = when (remoteProductRes) {
                is NetworkResult.Success -> {
                    val savedId = productDao.insertProduct(remoteProductRes.data.toEntity())
                    remoteProductRes.data.toEntity().copy(id = savedId)
                }
                else -> throw remoteProductRes.toException()
            }

            val remoteInventoryRes = remoteDataSource.upsertInventoryItem(
                ProductInventoryDto(
                    productName = cleanName,
                    sku = cleanSku,
                    currentStock = currentStock,
                    reorderThreshold = reorderThreshold,
                    productId = savedProduct.id,
                    saltComposition = saltComposition.ifBlank { cleanName },
                    dosageForm = dosageForm,
                    packSize = packSize,
                    unitPricePtr = unitPricePtr,
                    mrp = mrp,
                    storageCondition = storageCondition
                )
            )
            return@withContext when (remoteInventoryRes) {
                is NetworkResult.Success -> {
                    inventoryDao?.insertInventory(remoteInventoryRes.data.toEntity()) ?: savedProduct.id
                }
                else -> throw remoteInventoryRes.toException()
            }
        }

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
        if (isRemoteSyncEnabled() && remoteDataSource != null) {
            when (val res = remoteDataSource.adjustInventoryStock(sku = sku, newStock = newStock, reason = "Direct Stock Update")) {
                is NetworkResult.Success -> {
                    inventoryDao?.insertInventory(res.data.toEntity())
                    return@withContext
                }
                else -> throw res.toException()
            }
        }
        inventoryDao?.updateCurrentStockBySku(sku, newStock)
    }

    /**
     * Increments or decrements stock count for a given SKU by delta.
     */
    suspend fun adjustStockBySku(sku: String, delta: Int): Unit = withContext(ioDispatcher) {
        if (isRemoteSyncEnabled() && remoteDataSource != null) {
            when (val res = remoteDataSource.adjustInventoryStock(sku = sku, delta = delta, reason = "Quick Adjustment")) {
                is NetworkResult.Success -> {
                    inventoryDao?.insertInventory(res.data.toEntity())
                    return@withContext
                }
                else -> throw res.toException()
            }
        }
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
        if (isRemoteSyncEnabled() && remoteDataSource != null) {
            when (val res = remoteDataSource.updateReorderThreshold(sku, newThreshold)) {
                is NetworkResult.Success -> {
                    inventoryDao?.insertInventory(res.data.toEntity())
                    return@withContext
                }
                else -> throw res.toException()
            }
        }
        inventoryDao?.updateReorderThresholdBySku(sku, newThreshold)
    }

    /**
     * Deletes an inventory record by SKU.
     */
    suspend fun deleteInventoryBySku(sku: String): Unit = withContext(ioDispatcher) {
        if (isRemoteSyncEnabled() && remoteDataSource != null) {
            when (val res = remoteDataSource.deleteInventoryBySku(sku)) {
                is NetworkResult.Success -> {
                    inventoryDao?.deleteBySku(sku)
                    return@withContext
                }
                else -> throw res.toException()
            }
        }
        inventoryDao?.deleteBySku(sku)
    }

    /**
     * Deletes an inventory record by primary key ID.
     */
    suspend fun deleteInventoryById(id: Long): Unit = withContext(ioDispatcher) {
        inventoryDao?.deleteById(id)
    }
}

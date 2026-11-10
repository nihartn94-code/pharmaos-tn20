package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>): List<Long>

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :productId")
    suspend fun deleteProductById(productId: Long)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getProductById(productId: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    fun getProductByIdFlow(productId: Long): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProducts(): List<ProductEntity>

    @Query("SELECT COUNT(*) FROM products WHERE isActive = 1")
    suspend fun getActiveProductCount(): Int

    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 
        AND saltComposition = :saltComposition 
        AND strength = :strength 
        AND id != :currentProductId
        ORDER BY name ASC
    """)
    fun getAlternativeBrandsFlow(saltComposition: String, strength: String, currentProductId: Long): Flow<List<ProductEntity>>

    @Query("UPDATE products SET isActive = :isActive WHERE id = :productId")
    suspend fun setProductActiveStatus(productId: Long, isActive: Boolean)

    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 AND (
            name LIKE '%' || :query || '%' OR 
            brand LIKE '%' || :query || '%' OR 
            saltComposition LIKE '%' || :query || '%' OR 
            therapeuticCategory LIKE '%' || :query || '%'
        ) 
        ORDER BY name ASC
    """)
    fun searchProductsFlow(query: String): Flow<List<ProductEntity>>
}

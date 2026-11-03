package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.SupplierOfferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffer(offer: SupplierOfferEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffers(offers: List<SupplierOfferEntity>): List<Long>

    @Update
    suspend fun updateOffer(offer: SupplierOfferEntity)

    @Query("SELECT * FROM supplier_offers WHERE id = :offerId LIMIT 1")
    suspend fun getOfferById(offerId: Long): SupplierOfferEntity?

    @Query("SELECT * FROM supplier_offers WHERE productId = :productId AND isActive = 1 ORDER BY unitPricePtr ASC")
    fun getActiveOffersForProductFlow(productId: Long): Flow<List<SupplierOfferEntity>>

    @Query("SELECT * FROM supplier_offers WHERE productId = :productId AND isActive = 1 ORDER BY unitPricePtr ASC")
    suspend fun getActiveOffersForProduct(productId: Long): List<SupplierOfferEntity>

    @Query("SELECT * FROM supplier_offers WHERE supplierId = :supplierId ORDER BY lastUpdatedEpochMillis DESC")
    fun getOffersBySupplierFlow(supplierId: Long): Flow<List<SupplierOfferEntity>>

    @Query("SELECT * FROM supplier_offers WHERE supplierId = :supplierId ORDER BY lastUpdatedEpochMillis DESC")
    suspend fun getOffersBySupplier(supplierId: Long): List<SupplierOfferEntity>

    @Query("SELECT * FROM supplier_offers WHERE isActive = 1")
    suspend fun getAllActiveOffers(): List<SupplierOfferEntity>

    @Query("SELECT * FROM supplier_offers WHERE isActive = 1")
    fun getAllActiveOffersFlow(): Flow<List<SupplierOfferEntity>>

    @Query("UPDATE supplier_offers SET availableQuantity = :newQuantity, lastUpdatedEpochMillis = :updatedTime WHERE id = :offerId")
    suspend fun updateOfferQuantity(offerId: Long, newQuantity: Int, updatedTime: Long = System.currentTimeMillis())

    @Query("UPDATE supplier_offers SET isActive = :isActive WHERE id = :offerId")
    suspend fun setOfferActiveStatus(offerId: Long, isActive: Boolean)

    @Query("SELECT COUNT(*) FROM supplier_offers WHERE supplierId = :supplierId AND isActive = 1")
    suspend fun getActiveOfferCountForSupplier(supplierId: Long): Int
}

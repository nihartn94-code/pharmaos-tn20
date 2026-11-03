package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.BatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: BatchEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatches(batches: List<BatchEntity>): List<Long>

    @Update
    suspend fun updateBatch(batch: BatchEntity)

    @Query("SELECT * FROM batches WHERE id = :batchId LIMIT 1")
    suspend fun getBatchById(batchId: Long): BatchEntity?

    @Query("SELECT * FROM batches ORDER BY expiryDateEpochMillis ASC")
    fun getAllBatchesFlow(): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches ORDER BY expiryDateEpochMillis ASC")
    suspend fun getAllBatches(): List<BatchEntity>

    @Query("SELECT * FROM batches WHERE supplierId = :supplierId ORDER BY expiryDateEpochMillis ASC")
    fun getBatchesForSupplierFlow(supplierId: Long): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches WHERE productId = :productId ORDER BY expiryDateEpochMillis ASC")
    fun getBatchesForProductFlow(productId: Long): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches WHERE productId = :productId")
    suspend fun getBatchesForProduct(productId: Long): List<BatchEntity>

    @Query("SELECT * FROM batches WHERE batchNumber = :batchNumber LIMIT 1")
    suspend fun getBatchByNumber(batchNumber: String): BatchEntity?

    @Query("SELECT * FROM batches WHERE batchNumber IN (:batchNumbers)")
    suspend fun getBatchesByNumbers(batchNumbers: List<String>): List<BatchEntity>

    @Query("SELECT COUNT(*) FROM batches WHERE expiryDateEpochMillis <= :thresholdEpochMillis AND remainingQuantity > 0")
    suspend fun getNearExpiryBatchCount(thresholdEpochMillis: Long): Int

    @Query("UPDATE batches SET remainingQuantity = :newQuantity WHERE id = :batchId")
    suspend fun updateBatchQuantity(batchId: Long, newQuantity: Int)
}

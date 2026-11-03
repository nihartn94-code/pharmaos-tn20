package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.RecallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecallDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecall(recall: RecallEntity): Long

    @Update
    suspend fun updateRecall(recall: RecallEntity)

    @Query("SELECT * FROM recalls WHERE id = :recallId LIMIT 1")
    suspend fun getRecallById(recallId: Long): RecallEntity?

    @Query("SELECT * FROM recalls WHERE isActive = 1 ORDER BY recallDateEpochMillis DESC")
    fun getActiveRecallsFlow(): Flow<List<RecallEntity>>

    @Query("SELECT * FROM recalls ORDER BY recallDateEpochMillis DESC")
    fun getAllRecallsFlow(): Flow<List<RecallEntity>>

    @Query("SELECT * FROM recalls WHERE productId = :productId AND isActive = 1")
    suspend fun getActiveRecallsForProduct(productId: Long): List<RecallEntity>

    @Query("SELECT COUNT(*) FROM recalls WHERE isActive = 1")
    suspend fun getActiveRecallCount(): Int
}

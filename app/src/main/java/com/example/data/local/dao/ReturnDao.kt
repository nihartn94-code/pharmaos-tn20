package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CreditNoteEntity
import com.example.data.local.entity.ReturnEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReturnDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReturn(returnEntity: ReturnEntity): Long

    @Update
    suspend fun updateReturn(returnEntity: ReturnEntity)

    @Query("SELECT * FROM returns WHERE id = :returnId LIMIT 1")
    suspend fun getReturnById(returnId: Long): ReturnEntity?

    @Query("SELECT * FROM returns WHERE buyerId = :buyerId ORDER BY requestedAtEpochMillis DESC")
    fun getReturnsForBuyerFlow(buyerId: Long): Flow<List<ReturnEntity>>

    @Query("SELECT * FROM returns WHERE supplierId = :supplierId ORDER BY requestedAtEpochMillis DESC")
    fun getReturnsForSupplierFlow(supplierId: Long): Flow<List<ReturnEntity>>

    @Query("SELECT * FROM returns ORDER BY requestedAtEpochMillis DESC")
    fun getAllReturnsFlow(): Flow<List<ReturnEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCreditNote(creditNote: CreditNoteEntity): Long

    @Query("SELECT * FROM credit_notes WHERE returnId = :returnId LIMIT 1")
    suspend fun getCreditNoteByReturnId(returnId: Long): CreditNoteEntity?

    @Query("SELECT * FROM credit_notes WHERE buyerId = :buyerId ORDER BY issuedAtEpochMillis DESC")
    fun getCreditNotesForBuyerFlow(buyerId: Long): Flow<List<CreditNoteEntity>>

    @Query("SELECT * FROM credit_notes WHERE supplierId = :supplierId ORDER BY issuedAtEpochMillis DESC")
    fun getCreditNotesForSupplierFlow(supplierId: Long): Flow<List<CreditNoteEntity>>

    @Query("SELECT * FROM credit_notes ORDER BY issuedAtEpochMillis DESC")
    fun getAllCreditNotesFlow(): Flow<List<CreditNoteEntity>>
}

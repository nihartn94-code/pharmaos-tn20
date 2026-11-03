package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.CreditLedgerEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: CreditLedgerEntryEntity): Long

    @Query("SELECT * FROM credit_ledger WHERE buyerId = :buyerId ORDER BY dateEpochMillis DESC")
    fun getLedgerEntriesForBuyerFlow(buyerId: Long): Flow<List<CreditLedgerEntryEntity>>

    @Query("SELECT * FROM credit_ledger WHERE supplierId = :supplierId ORDER BY dateEpochMillis DESC")
    fun getLedgerEntriesForSupplierFlow(supplierId: Long): Flow<List<CreditLedgerEntryEntity>>

    @Query("SELECT * FROM credit_ledger WHERE buyerId = :buyerId AND supplierId = :supplierId ORDER BY dateEpochMillis DESC")
    fun getLedgerBetweenPartiesFlow(buyerId: Long, supplierId: Long): Flow<List<CreditLedgerEntryEntity>>

    @Query("SELECT * FROM credit_ledger WHERE buyerId = :buyerId AND supplierId = :supplierId ORDER BY dateEpochMillis DESC")
    suspend fun getLedgerBetweenParties(buyerId: Long, supplierId: Long): List<CreditLedgerEntryEntity>

    @Query("SELECT * FROM credit_ledger ORDER BY dateEpochMillis DESC")
    fun getAllLedgerEntriesFlow(): Flow<List<CreditLedgerEntryEntity>>

    @Query("SELECT * FROM credit_ledger ORDER BY dateEpochMillis DESC")
    suspend fun getAllLedgerEntries(): List<CreditLedgerEntryEntity>

    @Query("SELECT balanceAfter FROM credit_ledger WHERE buyerId = :buyerId AND supplierId = :supplierId ORDER BY dateEpochMillis DESC, id DESC LIMIT 1")
    suspend fun getLatestBalanceBetweenParties(buyerId: Long, supplierId: Long): Double?

    @Query("SELECT SUM(CASE WHEN entryType = 'DEBIT' THEN amount ELSE -amount END) FROM credit_ledger WHERE buyerId = :buyerId")
    suspend fun getTotalOutstandingBalanceForBuyer(buyerId: Long): Double?
}

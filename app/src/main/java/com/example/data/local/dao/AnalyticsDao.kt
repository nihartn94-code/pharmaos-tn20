package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ProofOfDeliveryEntity
import com.example.data.local.entity.ShortageRiskEntity
import com.example.data.local.entity.StockRecordEntity
import com.example.data.local.entity.SupplierPerformanceEntity
import com.example.data.local.entity.SupplierSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {
    // Stock snapshots
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockRecord(record: StockRecordEntity): Long

    @Query("SELECT * FROM stock_records WHERE productId = :productId ORDER BY recordedAtEpochMillis DESC LIMIT :limit")
    suspend fun getRecentStockRecords(productId: Long, limit: Int = 10): List<StockRecordEntity>

    @Query("SELECT COUNT(*) FROM stock_records WHERE productId = :productId AND availableQuantity = 0 AND recordedAtEpochMillis >= :sinceEpochMillis")
    suspend fun getStockoutCountInWindow(productId: Long, sinceEpochMillis: Long): Int

    // Supplier Performance (Trust Score)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePerformance(record: SupplierPerformanceEntity): Long

    @Query("SELECT * FROM supplier_performance WHERE supplierId = :supplierId LIMIT 1")
    suspend fun getSupplierPerformance(supplierId: Long): SupplierPerformanceEntity?

    @Query("SELECT * FROM supplier_performance WHERE supplierId = :supplierId LIMIT 1")
    fun getSupplierPerformanceFlow(supplierId: Long): Flow<SupplierPerformanceEntity?>

    @Query("SELECT * FROM supplier_performance ORDER BY overallScore DESC")
    fun getAllSupplierPerformancesFlow(): Flow<List<SupplierPerformanceEntity>>

    // Shortage Risks
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateShortageRisk(risk: ShortageRiskEntity): Long

    @Query("SELECT * FROM shortage_risks WHERE productId = :productId LIMIT 1")
    suspend fun getShortageRiskForProduct(productId: Long): ShortageRiskEntity?

    @Query("SELECT * FROM shortage_risks WHERE riskLevel != 'NONE' ORDER BY evaluatedAtEpochMillis DESC")
    fun getActiveShortageAlertsFlow(): Flow<List<ShortageRiskEntity>>

    @Query("SELECT * FROM shortage_risks ORDER BY evaluatedAtEpochMillis DESC")
    fun getAllShortageRisksFlow(): Flow<List<ShortageRiskEntity>>

    @Query("SELECT COUNT(*) FROM shortage_risks WHERE riskLevel IN ('MEDIUM', 'HIGH')")
    suspend fun getHighRiskShortageCount(): Int

    // Proof of Delivery
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProofOfDelivery(pod: ProofOfDeliveryEntity): Long

    @Query("SELECT * FROM proof_of_delivery WHERE orderId = :orderId LIMIT 1")
    suspend fun getProofOfDelivery(orderId: Long): ProofOfDeliveryEntity?

    @Query("SELECT * FROM proof_of_delivery WHERE orderId = :orderId LIMIT 1")
    fun getProofOfDeliveryFlow(orderId: Long): Flow<ProofOfDeliveryEntity?>

    // Supplier Settings
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSupplierSettings(settings: SupplierSettingsEntity): Long

    @Query("SELECT * FROM supplier_settings WHERE supplierId = :supplierId LIMIT 1")
    suspend fun getSupplierSettings(supplierId: Long): SupplierSettingsEntity?

    @Query("SELECT * FROM supplier_settings WHERE supplierId = :supplierId LIMIT 1")
    fun getSupplierSettingsFlow(supplierId: Long): Flow<SupplierSettingsEntity?>
}

package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ComplianceVerificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ComplianceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCompliance(compliance: ComplianceVerificationEntity): Long

    @Query("SELECT * FROM compliance_verifications WHERE userId = :userId LIMIT 1")
    suspend fun getComplianceByUserId(userId: Long): ComplianceVerificationEntity?

    @Query("SELECT * FROM compliance_verifications WHERE userId = :userId LIMIT 1")
    fun getComplianceByUserIdFlow(userId: Long): Flow<ComplianceVerificationEntity?>
}

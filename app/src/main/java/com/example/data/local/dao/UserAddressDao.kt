package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.UserAddressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserAddressDao {

    @Query("SELECT * FROM user_addresses WHERE userId = :userId ORDER BY isDefault DESC, updatedAtEpochMillis DESC, id ASC")
    fun getAddressesForUserFlow(userId: Long): Flow<List<UserAddressEntity>>

    @Query("SELECT * FROM user_addresses WHERE userId = :userId ORDER BY isDefault DESC, updatedAtEpochMillis DESC, id ASC")
    suspend fun getAddressesForUser(userId: Long): List<UserAddressEntity>

    @Query("SELECT * FROM user_addresses WHERE id = :addressId LIMIT 1")
    suspend fun getAddressById(addressId: Long): UserAddressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddress(address: UserAddressEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(addresses: List<UserAddressEntity>)

    @Update
    suspend fun updateAddress(address: UserAddressEntity)

    @Query("DELETE FROM user_addresses WHERE id = :addressId")
    suspend fun deleteAddressById(addressId: Long)

    @Query("DELETE FROM user_addresses WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)

    @Query("UPDATE user_addresses SET isDefault = 0 WHERE userId = :userId")
    suspend fun clearDefaultForUser(userId: Long)

    @Query("UPDATE user_addresses SET isDefault = 1, updatedAtEpochMillis = :updatedAt WHERE id = :addressId")
    suspend fun markAsDefault(addressId: Long, updatedAt: Long = System.currentTimeMillis())
}

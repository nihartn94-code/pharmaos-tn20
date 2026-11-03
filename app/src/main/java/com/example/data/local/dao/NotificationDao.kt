package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>): List<Long>

    @Query("SELECT * FROM notifications WHERE recipientUserId = :userId ORDER BY createdAtEpochMillis DESC")
    fun getNotificationsForUserFlow(userId: Long): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE recipientUserId = :userId AND isRead = 0")
    fun getUnreadNotificationCountFlow(userId: Long): Flow<Int>

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :notificationId")
    suspend fun markAsRead(notificationId: Long)

    @Query("UPDATE notifications SET isRead = 1 WHERE recipientUserId = :userId")
    suspend fun markAllAsRead(userId: Long)

    @Query("DELETE FROM notifications WHERE recipientUserId = :userId")
    suspend fun clearAllForUser(userId: Long)
}

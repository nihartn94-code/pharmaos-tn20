package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.OrderEntity
import com.example.data.local.entity.OrderItemEntity
import com.example.data.local.entity.OrderStatusHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItemEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatusHistory(history: OrderStatusHistoryEntity): Long

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Update
    suspend fun updateOrderItem(item: OrderItemEntity)

    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun getOrderById(orderId: Long): OrderEntity?

    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    fun getOrderByIdFlow(orderId: Long): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE buyerId = :buyerId ORDER BY createdAtEpochMillis DESC")
    fun getOrdersForBuyerFlow(buyerId: Long): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE buyerId = :buyerId ORDER BY createdAtEpochMillis DESC")
    suspend fun getOrdersForBuyer(buyerId: Long): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE supplierId = :supplierId ORDER BY createdAtEpochMillis DESC")
    fun getOrdersForSupplierFlow(supplierId: Long): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE supplierId = :supplierId ORDER BY createdAtEpochMillis DESC")
    suspend fun getOrdersForSupplier(supplierId: Long): List<OrderEntity>

    @Query("SELECT * FROM orders ORDER BY createdAtEpochMillis DESC")
    fun getAllOrdersFlow(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders ORDER BY createdAtEpochMillis DESC")
    suspend fun getAllOrders(): List<OrderEntity>

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    fun getOrderItemsFlow(orderId: Long): Flow<List<OrderItemEntity>>

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun getOrderItems(orderId: Long): List<OrderItemEntity>

    @Query("SELECT * FROM order_items WHERE id = :orderItemId LIMIT 1")
    suspend fun getOrderItemById(orderItemId: Long): OrderItemEntity?

    @Query("SELECT * FROM order_status_history WHERE orderId = :orderId ORDER BY changedAtEpochMillis ASC")
    fun getOrderStatusHistoryFlow(orderId: Long): Flow<List<OrderStatusHistoryEntity>>

    @Query("SELECT * FROM order_status_history WHERE orderId = :orderId ORDER BY changedAtEpochMillis ASC")
    suspend fun getOrderStatusHistory(orderId: Long): List<OrderStatusHistoryEntity>

    @Query("SELECT COUNT(*) FROM orders")
    suspend fun getTotalOrderCount(): Int

    @Query("SELECT COUNT(*) FROM orders WHERE buyerId = :buyerId AND status NOT IN ('DELIVERED', 'CANCELLED')")
    suspend fun getOpenOrderCountForBuyer(buyerId: Long): Int

    @Query("SELECT COUNT(*) FROM orders WHERE supplierId = :supplierId AND status IN ('PLACED', 'ACCEPTED', 'PACKED', 'DISPATCHED')")
    suspend fun getPendingOrderCountForSupplier(supplierId: Long): Int
}

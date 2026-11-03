package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.BasketEntity
import com.example.data.local.entity.BasketItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BasketDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBasket(basket: BasketEntity): Long

    @Update
    suspend fun updateBasket(basket: BasketEntity)

    @Query("SELECT * FROM baskets WHERE buyerId = :buyerId AND isSaved = 0 LIMIT 1")
    suspend fun getActiveBasketForBuyer(buyerId: Long): BasketEntity?

    @Query("SELECT * FROM baskets WHERE buyerId = :buyerId AND isSaved = 1 ORDER BY updatedAtEpochMillis DESC")
    fun getSavedBasketsFlow(buyerId: Long): Flow<List<BasketEntity>>

    @Query("SELECT * FROM baskets WHERE id = :basketId LIMIT 1")
    suspend fun getBasketById(basketId: Long): BasketEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBasketItem(item: BasketItemEntity): Long

    @Query("SELECT * FROM basket_items WHERE basketId = :basketId ORDER BY id ASC")
    fun getBasketItemsFlow(basketId: Long): Flow<List<BasketItemEntity>>

    @Query("SELECT * FROM basket_items WHERE basketId = :basketId ORDER BY id ASC")
    suspend fun getBasketItems(basketId: Long): List<BasketItemEntity>

    @Query("SELECT * FROM basket_items WHERE basketId = :basketId AND productId = :productId LIMIT 1")
    suspend fun getBasketItemByProduct(basketId: Long, productId: Long): BasketItemEntity?

    @Query("UPDATE basket_items SET quantity = :quantity, offerId = :offerId, supplierId = :supplierId WHERE id = :itemId")
    suspend fun updateBasketItem(itemId: Long, quantity: Int, offerId: Long, supplierId: Long)

    @Query("UPDATE basket_items SET quantity = :quantity WHERE id = :itemId")
    suspend fun updateBasketItemQuantity(itemId: Long, quantity: Int)

    @Query("DELETE FROM basket_items WHERE id = :itemId")
    suspend fun deleteBasketItem(itemId: Long)

    @Query("DELETE FROM basket_items WHERE basketId = :basketId")
    suspend fun clearBasket(basketId: Long)

    @Query("DELETE FROM baskets WHERE id = :basketId")
    suspend fun deleteBasket(basketId: Long)
}

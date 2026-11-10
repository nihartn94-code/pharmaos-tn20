package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.AnalyticsDao
import com.example.data.local.dao.BasketDao
import com.example.data.local.dao.BatchDao
import com.example.data.local.dao.LedgerDao
import com.example.data.local.dao.NotificationDao
import com.example.data.local.dao.OfferDao
import com.example.data.local.dao.OrderDao
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.ProductInventoryDao
import com.example.data.local.dao.RecallDao
import com.example.data.local.dao.ReturnDao
import com.example.data.local.dao.UserDao
import com.example.data.local.entity.BasketEntity
import com.example.data.local.entity.BasketItemEntity
import com.example.data.local.entity.BatchEntity
import com.example.data.local.entity.CreditLedgerEntryEntity
import com.example.data.local.entity.CreditNoteEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.OrderEntity
import com.example.data.local.entity.OrderItemEntity
import com.example.data.local.entity.OrderStatusHistoryEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ProductInventoryEntity
import com.example.data.local.entity.ProofOfDeliveryEntity
import com.example.data.local.entity.RecallEntity
import com.example.data.local.entity.ReturnEntity
import com.example.data.local.entity.ShortageRiskEntity
import com.example.data.local.entity.StockRecordEntity
import com.example.data.local.entity.SupplierOfferEntity
import com.example.data.local.entity.SupplierPerformanceEntity
import com.example.data.local.entity.SupplierSettingsEntity
import com.example.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        ProductEntity::class,
        ProductInventoryEntity::class,
        SupplierOfferEntity::class,
        BasketEntity::class,
        BasketItemEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        OrderStatusHistoryEntity::class,
        BatchEntity::class,
        StockRecordEntity::class,
        ReturnEntity::class,
        CreditNoteEntity::class,
        CreditLedgerEntryEntity::class,
        NotificationEntity::class,
        SupplierPerformanceEntity::class,
        RecallEntity::class,
        ShortageRiskEntity::class,
        ProofOfDeliveryEntity::class,
        SupplierSettingsEntity::class,
        com.example.data.local.entity.ComplianceVerificationEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun productDao(): ProductDao
    abstract fun productInventoryDao(): ProductInventoryDao
    fun inventoryDao(): ProductInventoryDao = productInventoryDao()
    abstract fun offerDao(): OfferDao
    abstract fun basketDao(): BasketDao
    abstract fun orderDao(): OrderDao
    abstract fun batchDao(): BatchDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun returnDao(): ReturnDao
    abstract fun recallDao(): RecallDao
    abstract fun notificationDao(): NotificationDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun complianceDao(): com.example.data.local.dao.ComplianceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pharmaos_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

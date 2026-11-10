package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.repository.PharmaRepositoryImpl
import com.example.domain.repository.PharmaRepository
import com.example.domain.usecase.CalculateTrustScoreUseCase
import com.example.domain.usecase.EvaluateShortageRiskUseCase
import com.example.domain.usecase.OptimizeBasketUseCase
import com.example.domain.usecase.ParsePurchaseListUseCase
import com.example.service.InventoryMonitorManager
import com.example.service.InventoryMonitoringService
import com.example.util.NotificationHelper
import com.example.worker.ExpiryAndShortageWorker
import com.example.worker.InventoryDepletionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PharmaApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var dataStoreManager: DataStoreManager
        private set

    lateinit var repository: PharmaRepository
        private set

    lateinit var productRepository: com.example.data.repository.ProductRepository
        private set

    lateinit var inventoryMonitorManager: InventoryMonitorManager
        private set

    val optimizeBasketUseCase = OptimizeBasketUseCase()
    val calculateTrustScoreUseCase = CalculateTrustScoreUseCase()
    val evaluateShortageRiskUseCase = EvaluateShortageRiskUseCase()
    val parsePurchaseListUseCase = ParsePurchaseListUseCase()

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        dataStoreManager = DataStoreManager(this)
        repository = PharmaRepositoryImpl(
            db = database,
            dataStoreManager = dataStoreManager,
            optimizeBasketUseCase = optimizeBasketUseCase,
            calculateTrustScoreUseCase = calculateTrustScoreUseCase,
            evaluateShortageRiskUseCase = evaluateShortageRiskUseCase
        )
        productRepository = com.example.data.repository.ProductRepository(
            productDao = database.productDao(),
            inventoryDao = database.productInventoryDao()
        )

        inventoryMonitorManager = InventoryMonitorManager(
            context = this,
            repository = repository,
            db = database,
            dataStoreManager = dataStoreManager
        )

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                repository.ensureDefaultManufacturerUser()
            } catch (_: Exception) {
                // Ignore in test runners
            }
        }

        try {
            NotificationHelper.createNotificationChannel(this)
            inventoryMonitorManager.startMonitoring()
            InventoryMonitoringService.startService(this)
            com.example.service.NotificationManagementService.start(this)
            ExpiryAndShortageWorker.scheduleDaily(this)
            InventoryDepletionWorker.schedulePeriodic(this, intervalHours = 1L)
        } catch (_: Exception) {
            // Silently skip in environments without notification or WorkManager support (such as unit test runners)
        }
    }
}

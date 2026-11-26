package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.remote.source.AuthRemoteDataSource
import com.example.data.remote.source.PharmaRemoteDataSource
import com.example.data.repository.PharmaRepositoryImpl
import com.example.di.NetworkModule
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PharmaApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var dataStoreManager: DataStoreManager
        private set

    lateinit var networkModule: NetworkModule
        private set

    lateinit var authRemoteDataSource: AuthRemoteDataSource
        private set

    lateinit var pharmaRemoteDataSource: PharmaRemoteDataSource
        private set

    lateinit var workspaceRemoteDataSource: com.example.data.remote.source.WorkspaceRemoteDataSource
        private set

    lateinit var repository: PharmaRepository
        private set

    lateinit var workspaceRepository: com.example.domain.repository.WorkspaceRepository
        private set

    lateinit var productRepository: com.example.data.repository.ProductRepository
        private set

    lateinit var inventoryMonitorManager: InventoryMonitorManager
        private set

    @Volatile
    private var isRemoteSyncActive: Boolean = true

    val optimizeBasketUseCase = OptimizeBasketUseCase()
    val calculateTrustScoreUseCase = CalculateTrustScoreUseCase()
    val evaluateShortageRiskUseCase = EvaluateShortageRiskUseCase()
    val parsePurchaseListUseCase = ParsePurchaseListUseCase()

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        dataStoreManager = DataStoreManager(this)
        networkModule = NetworkModule(tokenProvider = dataStoreManager)
        authRemoteDataSource = AuthRemoteDataSource(networkModule)
        pharmaRemoteDataSource = PharmaRemoteDataSource(networkModule)
        workspaceRemoteDataSource = com.example.data.remote.source.WorkspaceRemoteDataSource(networkModule)

        repository = PharmaRepositoryImpl(
            db = database,
            dataStoreManager = dataStoreManager,
            optimizeBasketUseCase = optimizeBasketUseCase,
            calculateTrustScoreUseCase = calculateTrustScoreUseCase,
            evaluateShortageRiskUseCase = evaluateShortageRiskUseCase,
            authRemoteDataSource = authRemoteDataSource,
            pharmaRemoteDataSource = pharmaRemoteDataSource,
            networkModule = networkModule
        )
        workspaceRepository = com.example.data.repository.WorkspaceRepositoryImpl(
            db = database,
            dataStoreManager = dataStoreManager,
            pharmaRepository = repository,
            remoteDataSource = workspaceRemoteDataSource
        )
        productRepository = com.example.data.repository.ProductRepository(
            productDao = database.productDao(),
            inventoryDao = database.productInventoryDao(),
            remoteDataSource = pharmaRemoteDataSource,
            isRemoteSyncEnabled = { isRemoteSyncActive }
        )

        inventoryMonitorManager = InventoryMonitorManager(
            context = this,
            repository = repository,
            db = database,
            dataStoreManager = dataStoreManager
        )

        val appIoScope = CoroutineScope(Dispatchers.IO)

        appIoScope.launch {
            try {
                dataStoreManager.backendBaseUrlFlow.collect { savedUrl ->
                    networkModule.updateBaseUrl(savedUrl)
                }
            } catch (_: Exception) {
            }
        }

        appIoScope.launch {
            try {
                dataStoreManager.accessTokenFlow.collect {
                    // Populates DataStoreManager.cachedAccessToken for AuthInterceptor
                }
            } catch (_: Exception) {
            }
        }

        appIoScope.launch {
            try {
                dataStoreManager.authExecutionModeFlow.collect { mode ->
                    isRemoteSyncActive = mode == AuthExecutionMode.PRODUCTION_BACKEND_HTTP
                    if (mode == AuthExecutionMode.DEVELOPMENT_LOCAL_MODE) {
                        repository.ensureDefaultManufacturerUser()
                        workspaceRepository.ensureWorkspaceSeededForLocalDev()
                    }
                }
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

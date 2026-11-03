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
import com.example.util.NotificationHelper
import com.example.worker.ExpiryAndShortageWorker

class PharmaApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var dataStoreManager: DataStoreManager
        private set

    lateinit var repository: PharmaRepository
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

        try {
            NotificationHelper.createNotificationChannel(this)
            ExpiryAndShortageWorker.scheduleDaily(this)
        } catch (_: Exception) {
            // Silently skip in environments without notification or WorkManager support (such as unit test runners)
        }
    }
}

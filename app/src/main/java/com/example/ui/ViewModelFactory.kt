package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.PharmaApplication
import com.example.ui.screens.auth.AuthViewModel
import com.example.ui.screens.basket.BasketViewModel
import com.example.ui.screens.catalogue.CatalogueViewModel
import com.example.ui.screens.dashboard.DashboardViewModel
import com.example.ui.screens.inventory.InventoryViewModel
import com.example.ui.screens.ledger.LedgerViewModel
import com.example.ui.screens.ocr.OcrViewModel
import com.example.ui.screens.offers.OffersViewModel
import com.example.ui.screens.optimizer.OptimizerViewModel
import com.example.ui.screens.orders.OrdersViewModel
import com.example.ui.screens.returns.ReturnsViewModel
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.shortage.ShortageViewModel
import com.example.ui.screens.trust.TrustScoreViewModel

class PharmaViewModelFactory(
    private val app: PharmaApplication
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repo = app.repository
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                AuthViewModel(repo) as T
            }
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                DashboardViewModel(repo) as T
            }
            modelClass.isAssignableFrom(CatalogueViewModel::class.java) -> {
                CatalogueViewModel(repo) as T
            }
            modelClass.isAssignableFrom(OffersViewModel::class.java) -> {
                OffersViewModel(repo) as T
            }
            modelClass.isAssignableFrom(BasketViewModel::class.java) -> {
                BasketViewModel(repo) as T
            }
            modelClass.isAssignableFrom(OptimizerViewModel::class.java) -> {
                OptimizerViewModel(repo) as T
            }
            modelClass.isAssignableFrom(OcrViewModel::class.java) -> {
                OcrViewModel(repo, app.parsePurchaseListUseCase) as T
            }
            modelClass.isAssignableFrom(OrdersViewModel::class.java) -> {
                OrdersViewModel(repo) as T
            }
            modelClass.isAssignableFrom(InventoryViewModel::class.java) -> {
                InventoryViewModel(repo, app.inventoryMonitorManager) as T
            }
            modelClass.isAssignableFrom(ReturnsViewModel::class.java) -> {
                ReturnsViewModel(repo) as T
            }
            modelClass.isAssignableFrom(LedgerViewModel::class.java) -> {
                LedgerViewModel(repo) as T
            }
            modelClass.isAssignableFrom(TrustScoreViewModel::class.java) -> {
                TrustScoreViewModel(repo) as T
            }
            modelClass.isAssignableFrom(ShortageViewModel::class.java) -> {
                ShortageViewModel(repo) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(repo, app.dataStoreManager) as T
            }
            modelClass.isAssignableFrom(com.example.ui.screens.compliance.ComplianceViewModel::class.java) -> {
                com.example.ui.screens.compliance.ComplianceViewModel(repo) as T
            }
            modelClass.isAssignableFrom(com.example.ui.screens.search.SearchViewModel::class.java) -> {
                com.example.ui.screens.search.SearchViewModel(repo) as T
            }
            modelClass.isAssignableFrom(com.example.ui.screens.products.ProductListViewModel::class.java) -> {
                com.example.ui.screens.products.ProductListViewModel(app.productRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

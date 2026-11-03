package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.PharmaApplication
import com.example.ui.PharmaViewModelFactory
import com.example.ui.screens.auth.AuthViewModel
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.RegisterScreen
import com.example.ui.screens.basket.BasketScreen
import com.example.ui.screens.basket.BasketViewModel
import com.example.ui.screens.catalogue.AddEditProductScreen
import com.example.ui.screens.catalogue.CatalogueViewModel
import com.example.ui.screens.catalogue.MedicineCatalogueScreen
import com.example.ui.screens.catalogue.MedicineDetailsScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.dashboard.DashboardViewModel
import com.example.ui.screens.inventory.BatchExpiryScreen
import com.example.ui.screens.inventory.InventoryDashboardScreen
import com.example.ui.screens.inventory.InventoryViewModel
import com.example.ui.screens.inventory.RecallScreen
import com.example.ui.screens.ledger.CreditLedgerScreen
import com.example.ui.screens.ledger.LedgerViewModel
import com.example.ui.screens.notifications.NotificationsScreen
import com.example.ui.screens.ocr.OcrReviewScreen
import com.example.ui.screens.ocr.OcrScannerScreen
import com.example.ui.screens.ocr.OcrViewModel
import com.example.ui.screens.offers.AddEditOfferScreen
import com.example.ui.screens.offers.OffersViewModel
import com.example.ui.screens.offers.SupplierOffersScreen
import com.example.ui.screens.optimizer.BestBuyOptimizerScreen
import com.example.ui.screens.optimizer.OptimizerViewModel
import com.example.ui.screens.orders.CheckoutScreen
import com.example.ui.screens.orders.MyOrdersScreen
import com.example.ui.screens.orders.OrderConfirmationScreen
import com.example.ui.screens.orders.OrderDetailsScreen
import com.example.ui.screens.orders.OrdersViewModel
import com.example.ui.screens.returns.ReturnsCreditNotesScreen
import com.example.ui.screens.returns.ReturnsViewModel
import com.example.ui.screens.settings.AdminUserManagementScreen
import com.example.ui.screens.settings.CsvImportExportScreen
import com.example.ui.screens.settings.ProfileSettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.settings.SupplierSettingsScreen
import com.example.ui.screens.shortage.ShortageAlertsScreen
import com.example.ui.screens.shortage.ShortageViewModel
import com.example.ui.screens.search.SearchMoleculesScreen
import com.example.ui.screens.search.SearchViewModel
import com.example.ui.screens.trust.PharmaTrustScoreScreen
import com.example.ui.screens.trust.SupplierComparisonScreen
import com.example.ui.screens.trust.TrustScoreViewModel

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val app = context.applicationContext as PharmaApplication
    val factory = remember { PharmaViewModelFactory(app) }
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.SPLASH
    ) {
        // Splash
        composable(NavRoutes.SPLASH) {
            val authViewModel: AuthViewModel = viewModel(factory = factory)
            LaunchedEffect(Unit) {
                val current = app.repository.getApiUsersMe()
                if (current != null) {
                    navController.navigate(NavRoutes.DASHBOARD) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                } else {
                    navController.navigate(NavRoutes.LOGIN) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // Auth
        composable(NavRoutes.LOGIN) {
            val authViewModel: AuthViewModel = viewModel(factory = factory)
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = { navController.navigate(NavRoutes.REGISTER) },
                onLoginSuccess = {
                    navController.navigate(NavRoutes.DASHBOARD) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.REGISTER) {
            val authViewModel: AuthViewModel = viewModel(factory = factory)
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onRegisterSuccess = { role ->
                    if (role == com.example.domain.model.UserRole.ADMIN) {
                        navController.navigate(NavRoutes.DASHBOARD) {
                            popUpTo(NavRoutes.REGISTER) { inclusive = true }
                        }
                    } else {
                        navController.navigate(NavRoutes.COMPLIANCE_VERIFICATION) {
                            popUpTo(NavRoutes.REGISTER) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(NavRoutes.COMPLIANCE_VERIFICATION) {
            val complianceViewModel: com.example.ui.screens.compliance.ComplianceViewModel = viewModel(factory = factory)
            com.example.ui.screens.compliance.ComplianceVerificationScreen(
                viewModel = complianceViewModel,
                onNavigateBack = {
                    navController.navigate(NavRoutes.DASHBOARD) {
                        popUpTo(NavRoutes.COMPLIANCE_VERIFICATION) { inclusive = true }
                    }
                },
                onVerificationComplete = {
                    navController.navigate(NavRoutes.DASHBOARD) {
                        popUpTo(NavRoutes.COMPLIANCE_VERIFICATION) { inclusive = true }
                    }
                }
            )
        }

        // Dashboard
        composable(NavRoutes.DASHBOARD) {
            val dashboardViewModel: DashboardViewModel = viewModel(factory = factory)
            DashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToCatalogue = { navController.navigate(NavRoutes.MEDICINE_CATALOGUE) },
                onNavigateToBasket = { navController.navigate(NavRoutes.BASKET) },
                onNavigateToOrders = { navController.navigate(NavRoutes.MY_ORDERS) },
                onNavigateToBatches = { navController.navigate(NavRoutes.INVENTORY_DASHBOARD) },
                onNavigateToShortages = { navController.navigate(NavRoutes.SHORTAGE_ALERTS) },
                onNavigateToLedger = { navController.navigate(NavRoutes.CREDIT_LEDGER) },
                onNavigateToOcr = { navController.navigate(NavRoutes.OCR_SCANNER) },
                onNavigateToOffers = { navController.navigate(NavRoutes.SUPPLIER_OFFERS) },
                onNavigateToTrustScore = { navController.navigate(NavRoutes.PHARMA_TRUST_SCORE) },
                onNavigateToNotifications = { navController.navigate(NavRoutes.NOTIFICATIONS) },
                onNavigateToSettings = { navController.navigate(NavRoutes.PROFILE_SETTINGS) },
                onNavigateToReturns = { navController.navigate(NavRoutes.RETURNS_CREDIT_NOTES) },
                onNavigateToOptimizer = { navController.navigate(NavRoutes.BEST_BUY_OPTIMIZER) },
                onNavigateToCompliance = { navController.navigate(NavRoutes.COMPLIANCE_VERIFICATION) },
                onNavigateToSearch = { navController.navigate(NavRoutes.SEARCH_MOLECULES) },
                onLoggedOut = {
                    navController.navigate(NavRoutes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Catalogue
        composable(NavRoutes.MEDICINE_CATALOGUE) {
            val catalogueViewModel: CatalogueViewModel = viewModel(factory = factory)
            MedicineCatalogueScreen(
                viewModel = catalogueViewModel,
                onNavigateBack = { navController.popBackStack() },
                onProductClick = { id -> navController.navigate(NavRoutes.medicineDetails(id)) },
                onAddProductClick = { navController.navigate(NavRoutes.ADD_EDIT_PRODUCT) },
                onNavigateToBasket = { navController.navigate(NavRoutes.BASKET) },
                onNavigateToDashboard = {
                    navController.navigate(NavRoutes.DASHBOARD) {
                        popUpTo(NavRoutes.DASHBOARD) { inclusive = true }
                    }
                },
                onNavigateToOrders = { navController.navigate(NavRoutes.MY_ORDERS) },
                onNavigateToProfile = { navController.navigate(NavRoutes.PROFILE_SETTINGS) },
                onNavigateToOcr = { navController.navigate(NavRoutes.OCR_SCANNER) },
                onNavigateToSearch = { navController.navigate(NavRoutes.SEARCH_MOLECULES) }
            )
        }

        // Search Molecules Engine (image_5fbf2d0a.png reference)
        composable(NavRoutes.SEARCH_MOLECULES) {
            val searchViewModel: SearchViewModel = viewModel(factory = factory)
            SearchMoleculesScreen(
                viewModel = searchViewModel,
                onNavigateBack = { navController.popBackStack() },
                onProductSelected = { productId ->
                    navController.navigate(NavRoutes.medicineDetails(productId))
                },
                onSearchQuerySubmitted = { query ->
                    navController.navigate(NavRoutes.MEDICINE_CATALOGUE)
                }
            )
        }

        composable(NavRoutes.ADD_EDIT_PRODUCT) {
            val catalogueViewModel: CatalogueViewModel = viewModel(factory = factory)
            AddEditProductScreen(
                viewModel = catalogueViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.MEDICINE_DETAILS,
            arguments = listOf(navArgument("productId") { type = NavType.LongType })
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
            val catalogueViewModel: CatalogueViewModel = viewModel(factory = factory)
            MedicineDetailsScreen(
                productId = productId,
                viewModel = catalogueViewModel,
                onNavigateBack = { navController.popBackStack() },
                onProductClick = { id -> navController.navigate(NavRoutes.medicineDetails(id)) },
                onAddOfferClick = { id -> navController.navigate(NavRoutes.addEditOffer(id)) }
            )
        }

        // Supplier Offers
        composable(NavRoutes.SUPPLIER_OFFERS) {
            val offersViewModel: OffersViewModel = viewModel(factory = factory)
            SupplierOffersScreen(
                viewModel = offersViewModel,
                onNavigateBack = { navController.popBackStack() },
                onAddOfferClick = { prodId -> navController.navigate(NavRoutes.addEditOffer(prodId)) }
            )
        }

        composable(
            route = NavRoutes.ADD_EDIT_OFFER,
            arguments = listOf(navArgument("productId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val prodIdStr = backStackEntry.arguments?.getString("productId")
            val prodId = prodIdStr?.toLongOrNull()
            val offersViewModel: OffersViewModel = viewModel(factory = factory)
            AddEditOfferScreen(
                initialProductId = prodId,
                viewModel = offersViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Basket & Optimizer
        composable(NavRoutes.BASKET) {
            val basketViewModel: BasketViewModel = viewModel(factory = factory)
            BasketScreen(
                viewModel = basketViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToOptimizer = { navController.navigate(NavRoutes.BEST_BUY_OPTIMIZER) },
                onNavigateToCheckout = { navController.navigate(NavRoutes.CHECKOUT) },
                onNavigateToCatalogue = { navController.navigate(NavRoutes.MEDICINE_CATALOGUE) }
            )
        }

        composable(NavRoutes.BEST_BUY_OPTIMIZER) {
            val optimizerViewModel: OptimizerViewModel = viewModel(factory = factory)
            BestBuyOptimizerScreen(
                viewModel = optimizerViewModel,
                onNavigateBack = { navController.popBackStack() },
                onApplied = { navController.popBackStack() }
            )
        }

        // OCR Scanner & Review
        composable(NavRoutes.OCR_SCANNER) {
            val ocrViewModel: OcrViewModel = viewModel(factory = factory)
            OcrScannerScreen(
                viewModel = ocrViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReview = { navController.navigate(NavRoutes.OCR_REVIEW) }
            )
        }

        composable(NavRoutes.OCR_REVIEW) {
            val ocrViewModel: OcrViewModel = viewModel(factory = factory)
            OcrReviewScreen(
                viewModel = ocrViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBasket = {
                    navController.navigate(NavRoutes.BASKET) {
                        popUpTo(NavRoutes.OCR_SCANNER) { inclusive = true }
                    }
                }
            )
        }

        // Orders & Checkout
        composable(NavRoutes.CHECKOUT) {
            val ordersViewModel: OrdersViewModel = viewModel(factory = factory)
            CheckoutScreen(
                viewModel = ordersViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOrderPlaced = { ids ->
                    navController.navigate(NavRoutes.orderConfirmation(ids)) {
                        popUpTo(NavRoutes.BASKET) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = NavRoutes.ORDER_CONFIRMATION,
            arguments = listOf(navArgument("orderIds") { type = NavType.StringType })
        ) { backStackEntry ->
            val idsStr = backStackEntry.arguments?.getString("orderIds") ?: ""
            val ids = idsStr.split(",").mapNotNull { it.toLongOrNull() }
            OrderConfirmationScreen(
                orderIds = ids,
                onNavigateToOrders = {
                    navController.navigate(NavRoutes.MY_ORDERS) {
                        popUpTo(NavRoutes.DASHBOARD)
                    }
                },
                onNavigateToDashboard = {
                    navController.navigate(NavRoutes.DASHBOARD) {
                        popUpTo(NavRoutes.DASHBOARD) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.MY_ORDERS) {
            val ordersViewModel: OrdersViewModel = viewModel(factory = factory)
            MyOrdersScreen(
                viewModel = ordersViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOrderClick = { id -> navController.navigate(NavRoutes.orderDetails(id)) },
                onNavigateToDashboard = {
                    navController.navigate(NavRoutes.DASHBOARD) {
                        popUpTo(NavRoutes.DASHBOARD) { inclusive = true }
                    }
                },
                onNavigateToCatalogue = { navController.navigate(NavRoutes.MEDICINE_CATALOGUE) },
                onNavigateToOcr = { navController.navigate(NavRoutes.OCR_SCANNER) },
                onNavigateToProfile = { navController.navigate(NavRoutes.PROFILE_SETTINGS) }
            )
        }

        composable(
            route = NavRoutes.ORDER_DETAILS,
            arguments = listOf(navArgument("orderId") { type = NavType.LongType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getLong("orderId") ?: 0L
            val ordersViewModel: OrdersViewModel = viewModel(factory = factory)
            OrderDetailsScreen(
                orderId = orderId,
                viewModel = ordersViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBasket = { navController.navigate(NavRoutes.BASKET) }
            )
        }

        // Real-Time Inventory & Stock Dashboard
        composable(NavRoutes.INVENTORY_DASHBOARD) {
            val inventoryViewModel: InventoryViewModel = viewModel(factory = factory)
            InventoryDashboardScreen(
                viewModel = inventoryViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRecalls = { navController.navigate(NavRoutes.RECALLS) },
                onNavigateToBasket = { navController.navigate(NavRoutes.BASKET) }
            )
        }

        // Inventory Batches & Recalls
        composable(NavRoutes.BATCH_EXPIRY) {
            val inventoryViewModel: InventoryViewModel = viewModel(factory = factory)
            BatchExpiryScreen(
                viewModel = inventoryViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRecalls = { navController.navigate(NavRoutes.RECALLS) },
                onAddBatchClick = { navController.navigate(NavRoutes.INVENTORY_DASHBOARD) }
            )
        }

        composable(NavRoutes.RECALLS) {
            val inventoryViewModel: InventoryViewModel = viewModel(factory = factory)
            RecallScreen(
                viewModel = inventoryViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Returns & Credit Notes
        composable(NavRoutes.RETURNS_CREDIT_NOTES) {
            val returnsViewModel: ReturnsViewModel = viewModel(factory = factory)
            ReturnsCreditNotesScreen(
                viewModel = returnsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Credit Ledger
        composable(NavRoutes.CREDIT_LEDGER) {
            val ledgerViewModel: LedgerViewModel = viewModel(factory = factory)
            CreditLedgerScreen(
                viewModel = ledgerViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Pharma Trust Score
        composable(NavRoutes.PHARMA_TRUST_SCORE) {
            val trustScoreViewModel: TrustScoreViewModel = viewModel(factory = factory)
            PharmaTrustScoreScreen(
                viewModel = trustScoreViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCompare = { navController.navigate(NavRoutes.SUPPLIER_COMPARISON) }
            )
        }

        composable(NavRoutes.SUPPLIER_COMPARISON) {
            val trustScoreViewModel: TrustScoreViewModel = viewModel(factory = factory)
            SupplierComparisonScreen(
                viewModel = trustScoreViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Shortage Alerts
        composable(NavRoutes.SHORTAGE_ALERTS) {
            val shortageViewModel: ShortageViewModel = viewModel(factory = factory)
            ShortageAlertsScreen(
                viewModel = shortageViewModel,
                onNavigateBack = { navController.popBackStack() },
                onProductClick = { id -> navController.navigate(NavRoutes.medicineDetails(id)) }
            )
        }

        // Settings & Profile
        composable(NavRoutes.PROFILE_SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
            ProfileSettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSupplierSettings = { navController.navigate(NavRoutes.SUPPLIER_SETTINGS) },
                onNavigateToCsv = { navController.navigate(NavRoutes.CSV_IMPORT_EXPORT) },
                onNavigateToAdminUsers = { navController.navigate(NavRoutes.ADMIN_USER_MANAGEMENT) },
                onLoggedOut = {
                    navController.navigate(NavRoutes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.SUPPLIER_SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
            SupplierSettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.CSV_IMPORT_EXPORT) {
            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
            CsvImportExportScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.ADMIN_USER_MANAGEMENT) {
            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
            AdminUserManagementScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Notifications
        composable(NavRoutes.NOTIFICATIONS) {
            NotificationsScreen(
                repository = app.repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

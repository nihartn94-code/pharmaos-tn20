package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.PharmaApplication
import com.example.domain.repository.LaunchDestination
import com.example.ui.PharmaViewModelFactory
import com.example.ui.screens.auth.AuthViewModel
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.RegisterScreen
import com.example.ui.screens.auth.WelcomeScreen
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
import com.example.ui.screens.orders.PaymentScreen
import com.example.ui.screens.orders.SelectedProductsOrderSummaryScreen
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

import androidx.compose.runtime.rememberCoroutineScope
import com.example.domain.model.WorkspaceType
import com.example.ui.screens.company.CompanyAddMedicineScreen
import com.example.ui.screens.company.CompanyApprovalsScreen
import com.example.ui.screens.company.CompanyCatalogueScreen
import com.example.ui.screens.company.CompanyDashboardScreen
import com.example.ui.screens.company.CompanyMasterDataScreen
import com.example.ui.screens.company.CompanyWorkspaceViewModel
import com.example.ui.screens.manufacturer.ManufacturerBannersScreen
import com.example.ui.screens.manufacturer.ManufacturerDashboardScreen
import com.example.ui.screens.manufacturer.ManufacturerPromotionsScreen
import com.example.ui.screens.manufacturer.ManufacturerUpdatesScreen
import com.example.ui.screens.manufacturer.ManufacturerWorkspaceViewModel
import com.example.ui.screens.stockist.StockistInventoryViewModel
import com.example.ui.screens.stockist.StockistPurchasedInventoryScreen
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val app = context.applicationContext as PharmaApplication
    val factory = remember { PharmaViewModelFactory(app) }
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    suspend fun resolveAuthorizedRootRoute(): String {
        val user = app.repository.getApiUsersMeFlow().firstOrNull()
        if (user == null) return NavRoutes.LOGIN
        val savedWorkspace = app.workspaceRepository.getActiveWorkspaceFlow().firstOrNull()
        val effectiveWorkspace = if (savedWorkspace != null && user.authorizedWorkspaces().contains(savedWorkspace)) {
            savedWorkspace
        } else {
            user.defaultWorkspace()
        }
        return NavRoutes.rootRouteForWorkspace(effectiveWorkspace)
    }

    fun navigateToAuthorizedRoot() {
        coroutineScope.launch {
            val target = resolveAuthorizedRootRoute()
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    fun performFullLogout() {
        coroutineScope.launch {
            runCatching {
                val previousUser = app.repository.getApiUsersMeFlow().firstOrNull()
                app.workspaceRepository.clearSensitiveLocalSessionCaches(previousUser = previousUser)
            }
            navController.navigate(NavRoutes.LOGIN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Global HTTP 401 session expiration handler (only 401/refresh failure triggers logout)
    LaunchedEffect(Unit) {
        app.networkModule.sessionExpiredEvents.collect {
            runCatching {
                val previousUser = app.repository.getApiUsersMeFlow().firstOrNull()
                app.repository.logout()
                app.workspaceRepository.clearSensitiveLocalSessionCaches(previousUser = previousUser)
            }
            navController.navigate(NavRoutes.LOGIN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Global HTTP 403 permission-denied handler — NEVER logs out or clears tokens
    LaunchedEffect(Unit) {
        app.networkModule.permissionDeniedEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Foreground resume lifecycle sync: refresh banner clock + re-validate /api/v1/auth/context + active workspace
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    app.workspaceRepository.refreshBannerClock()
                    val currentUser = app.repository.getApiUsersMeFlow().firstOrNull()
                    if (currentUser != null) {
                        runCatching { app.workspaceRepository.syncActiveWorkspace() }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    NavHost(
        navController = navController,
        startDestination = NavRoutes.SPLASH
    ) {
        // Splash
        composable(NavRoutes.SPLASH) {
            LaunchedEffect(Unit) {
                val targetRoute = when (app.repository.resolveLaunchDestination()) {
                    LaunchDestination.DASHBOARD -> {
                        runCatching { app.workspaceRepository.syncActiveWorkspace() }
                        resolveAuthorizedRootRoute()
                    }
                    LaunchDestination.COMPLIANCE_VERIFICATION -> NavRoutes.COMPLIANCE_VERIFICATION
                    LaunchDestination.LOGIN -> NavRoutes.LOGIN
                    LaunchDestination.WELCOME -> NavRoutes.WELCOME
                }
                navController.navigate(targetRoute) {
                    popUpTo(NavRoutes.SPLASH) { inclusive = true }
                }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // First-time Welcome Screen (shown only before initial account setup is completed)
        composable(NavRoutes.WELCOME) {
            WelcomeScreen(
                onGetStarted = {
                    navController.navigate(NavRoutes.REGISTER) {
                        launchSingleTop = true
                    }
                },
                onSignIn = {
                    navController.navigate(NavRoutes.LOGIN) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // Auth
        composable(NavRoutes.LOGIN) {
            val authViewModel: AuthViewModel = viewModel(factory = factory)
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = {
                    navController.navigate(NavRoutes.REGISTER) {
                        launchSingleTop = true
                    }
                },
                onLoginSuccess = {
                    coroutineScope.launch {
                        runCatching { app.workspaceRepository.syncActiveWorkspace() }
                    }
                    navigateToAuthorizedRoot()
                }
            )
        }

        composable(NavRoutes.REGISTER) {
            val authViewModel: AuthViewModel = viewModel(factory = factory)
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = {
                    navController.navigate(NavRoutes.LOGIN) {
                        launchSingleTop = true
                    }
                },
                onRegisterSuccess = { role ->
                    if (role == com.example.domain.model.UserRole.ADMIN) {
                        navigateToAuthorizedRoot()
                    } else {
                        navController.navigate(NavRoutes.COMPLIANCE_VERIFICATION) {
                            popUpTo(0) { inclusive = true }
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
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navigateToAuthorizedRoot()
                    }
                },
                onVerificationComplete = {
                    navigateToAuthorizedRoot()
                }
            )
        }

        // Stockist Dashboard
        composable(NavRoutes.DASHBOARD) {
            val dashboardViewModel: DashboardViewModel = viewModel(factory = factory)
            DashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToCatalogue = {
                    navController.navigate(NavRoutes.medicineCatalogue(focus = "products")) {
                        launchSingleTop = true
                    }
                },
                onNavigateToManufacturerCatalogue = {
                    navController.navigate(NavRoutes.medicineCatalogue(focus = "manufacturers")) {
                        launchSingleTop = true
                    }
                },
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
                onNavigateToStockistPurchasedInventory = {
                    navController.navigate(NavRoutes.STOCKIST_PURCHASED_INVENTORY) {
                        launchSingleTop = true
                    }
                },
                onOpenOfferBanner = { banner ->
                    val linkedProductId = banner.linkedProductId
                    if (linkedProductId != null && linkedProductId > 0L) {
                        navController.navigate(NavRoutes.medicineDetails(linkedProductId))
                    } else {
                        navController.navigate(NavRoutes.medicineCatalogue(focus = "products")) {
                            launchSingleTop = true
                        }
                    }
                },
                onLoggedOut = { performFullLogout() }
            )
        }

        // Stockist Purchased Inventory (Strictly isolated from Manufacturer Real-Time Inventory)
        composable(NavRoutes.STOCKIST_PURCHASED_INVENTORY) {
            val stockistInventoryViewModel: StockistInventoryViewModel = viewModel(factory = factory)
            StockistPurchasedInventoryScreen(
                viewModel = stockistInventoryViewModel,
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navigateToAuthorizedRoot()
                    }
                }
            )
        }

        // =========================================================================
        // MANUFACTURER WORKSPACE (Independent Navigation Architecture)
        // =========================================================================
        composable(NavRoutes.MANUFACTURER_DASHBOARD) {
            val manufacturerViewModel: ManufacturerWorkspaceViewModel = viewModel(factory = factory)
            ManufacturerDashboardScreen(
                viewModel = manufacturerViewModel,
                onNavigateBanners = {
                    navController.navigate(NavRoutes.MANUFACTURER_BANNERS) { launchSingleTop = true }
                },
                onNavigatePromotions = {
                    navController.navigate(NavRoutes.MANUFACTURER_PROMOTIONS) { launchSingleTop = true }
                },
                onNavigateUpdates = {
                    navController.navigate(NavRoutes.MANUFACTURER_UPDATES) { launchSingleTop = true }
                },
                onNavigateProfile = { navController.navigate(NavRoutes.PROFILE_SETTINGS) },
                onNavigateNotifications = { navController.navigate(NavRoutes.NOTIFICATIONS) },
                onNavigateRealTimeInventory = { navController.navigate(NavRoutes.INVENTORY_DASHBOARD) },
                onNavigateBatches = { navController.navigate(NavRoutes.BATCH_EXPIRY) },
                onNavigateRecalls = { navController.navigate(NavRoutes.RECALLS) },
                onNavigateOrders = { navController.navigate(NavRoutes.MY_ORDERS) },
                onSwitchToCompanyWorkspace = {
                    coroutineScope.launch {
                        app.workspaceRepository.switchActiveWorkspace(WorkspaceType.COMPANY)
                        navController.navigate(NavRoutes.COMPANY_DASHBOARD) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onReturnToAuthorizedWorkspace = { navigateToAuthorizedRoot() }
            )
        }

        composable(NavRoutes.MANUFACTURER_BANNERS) {
            val manufacturerViewModel: ManufacturerWorkspaceViewModel = viewModel(factory = factory)
            ManufacturerBannersScreen(
                viewModel = manufacturerViewModel,
                onNavigateHome = {
                    navController.navigate(NavRoutes.MANUFACTURER_DASHBOARD) {
                        popUpTo(NavRoutes.MANUFACTURER_DASHBOARD) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigatePromotions = {
                    navController.navigate(NavRoutes.MANUFACTURER_PROMOTIONS) { launchSingleTop = true }
                },
                onNavigateUpdates = {
                    navController.navigate(NavRoutes.MANUFACTURER_UPDATES) { launchSingleTop = true }
                },
                onNavigateProfile = { navController.navigate(NavRoutes.PROFILE_SETTINGS) },
                onReturnToAuthorizedWorkspace = { navigateToAuthorizedRoot() }
            )
        }

        composable(NavRoutes.MANUFACTURER_PROMOTIONS) {
            val manufacturerViewModel: ManufacturerWorkspaceViewModel = viewModel(factory = factory)
            ManufacturerPromotionsScreen(
                viewModel = manufacturerViewModel,
                onNavigateHome = {
                    navController.navigate(NavRoutes.MANUFACTURER_DASHBOARD) {
                        popUpTo(NavRoutes.MANUFACTURER_DASHBOARD) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateProducts = {
                    navController.navigate(NavRoutes.MANUFACTURER_BANNERS) { launchSingleTop = true }
                },
                onNavigateUpdates = {
                    navController.navigate(NavRoutes.MANUFACTURER_UPDATES) { launchSingleTop = true }
                },
                onNavigateProfile = { navController.navigate(NavRoutes.PROFILE_SETTINGS) },
                onReturnToAuthorizedWorkspace = { navigateToAuthorizedRoot() }
            )
        }

        composable(NavRoutes.MANUFACTURER_UPDATES) {
            val manufacturerViewModel: ManufacturerWorkspaceViewModel = viewModel(factory = factory)
            ManufacturerUpdatesScreen(
                viewModel = manufacturerViewModel,
                onNavigateHome = {
                    navController.navigate(NavRoutes.MANUFACTURER_DASHBOARD) {
                        popUpTo(NavRoutes.MANUFACTURER_DASHBOARD) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateProducts = {
                    navController.navigate(NavRoutes.MANUFACTURER_BANNERS) { launchSingleTop = true }
                },
                onNavigatePromotions = {
                    navController.navigate(NavRoutes.MANUFACTURER_PROMOTIONS) { launchSingleTop = true }
                },
                onNavigateProfile = { navController.navigate(NavRoutes.PROFILE_SETTINGS) },
                onReturnToAuthorizedWorkspace = { navigateToAuthorizedRoot() }
            )
        }

        // =========================================================================
        // COMPANY WORKSPACE (Independent Navigation Architecture)
        // =========================================================================
        composable(NavRoutes.COMPANY_DASHBOARD) {
            val companyViewModel: CompanyWorkspaceViewModel = viewModel(factory = factory)
            CompanyDashboardScreen(
                viewModel = companyViewModel,
                onNavigateCatalogue = {
                    navController.navigate(NavRoutes.COMPANY_CATALOGUE) { launchSingleTop = true }
                },
                onNavigateAddMedicine = {
                    navController.navigate(NavRoutes.COMPANY_ADD_MEDICINE) { launchSingleTop = true }
                },
                onNavigateApprovals = {
                    navController.navigate(NavRoutes.COMPANY_APPROVALS) { launchSingleTop = true }
                },
                onNavigateManagement = {
                    navController.navigate(NavRoutes.COMPANY_MASTER_DATA) { launchSingleTop = true }
                },
                onNavigateProfile = { navController.navigate(NavRoutes.PROFILE_SETTINGS) },
                onNavigateNotifications = { navController.navigate(NavRoutes.NOTIFICATIONS) },
                onSwitchToManufacturerWorkspace = {
                    coroutineScope.launch {
                        app.workspaceRepository.switchActiveWorkspace(WorkspaceType.MANUFACTURER)
                        navController.navigate(NavRoutes.MANUFACTURER_DASHBOARD) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onReturnToAuthorizedWorkspace = { navigateToAuthorizedRoot() }
            )
        }

        composable(NavRoutes.COMPANY_CATALOGUE) {
            val companyViewModel: CompanyWorkspaceViewModel = viewModel(factory = factory)
            CompanyCatalogueScreen(
                viewModel = companyViewModel,
                onNavigateHome = {
                    navController.navigate(NavRoutes.COMPANY_DASHBOARD) {
                        popUpTo(NavRoutes.COMPANY_DASHBOARD) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateAddMedicine = {
                    navController.navigate(NavRoutes.COMPANY_ADD_MEDICINE) { launchSingleTop = true }
                },
                onNavigateApprovals = {
                    navController.navigate(NavRoutes.COMPANY_APPROVALS) { launchSingleTop = true }
                },
                onNavigateManagement = {
                    navController.navigate(NavRoutes.COMPANY_MASTER_DATA) { launchSingleTop = true }
                },
                onNavigateProfile = { navController.navigate(NavRoutes.PROFILE_SETTINGS) },
                onReturnToAuthorizedWorkspace = { navigateToAuthorizedRoot() }
            )
        }

        composable(NavRoutes.COMPANY_ADD_MEDICINE) {
            val companyViewModel: CompanyWorkspaceViewModel = viewModel(factory = factory)
            CompanyAddMedicineScreen(
                viewModel = companyViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSubmittedSuccess = {
                    navController.navigate(NavRoutes.COMPANY_APPROVALS) {
                        popUpTo(NavRoutes.COMPANY_ADD_MEDICINE) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(NavRoutes.COMPANY_APPROVALS) {
            val companyViewModel: CompanyWorkspaceViewModel = viewModel(factory = factory)
            CompanyApprovalsScreen(
                viewModel = companyViewModel,
                onNavigateHome = {
                    navController.navigate(NavRoutes.COMPANY_DASHBOARD) {
                        popUpTo(NavRoutes.COMPANY_DASHBOARD) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateCatalogue = {
                    navController.navigate(NavRoutes.COMPANY_CATALOGUE) { launchSingleTop = true }
                },
                onNavigateManagement = {
                    navController.navigate(NavRoutes.COMPANY_MASTER_DATA) { launchSingleTop = true }
                },
                onNavigateProfile = { navController.navigate(NavRoutes.PROFILE_SETTINGS) },
                onReturnToAuthorizedWorkspace = { navigateToAuthorizedRoot() }
            )
        }

        composable(NavRoutes.COMPANY_MASTER_DATA) {
            val companyViewModel: CompanyWorkspaceViewModel = viewModel(factory = factory)
            CompanyMasterDataScreen(
                viewModel = companyViewModel,
                onNavigateHome = {
                    navController.navigate(NavRoutes.COMPANY_DASHBOARD) {
                        popUpTo(NavRoutes.COMPANY_DASHBOARD) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateCatalogue = {
                    navController.navigate(NavRoutes.COMPANY_CATALOGUE) { launchSingleTop = true }
                },
                onNavigateApprovals = {
                    navController.navigate(NavRoutes.COMPANY_APPROVALS) { launchSingleTop = true }
                },
                onNavigateProfile = { navController.navigate(NavRoutes.PROFILE_SETTINGS) },
                onReturnToAuthorizedWorkspace = { navigateToAuthorizedRoot() }
            )
        }

        // Unified Product & Manufacturer Exploration Catalogue
        composable(
            route = NavRoutes.MEDICINE_CATALOGUE_ROUTE,
            arguments = listOf(
                navArgument("focus") {
                    type = NavType.StringType
                    defaultValue = "products"
                },
                navArgument("manufacturer") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("query") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val rawFocus = backStackEntry.arguments?.getString("focus").orEmpty()
            val rawManufacturer = backStackEntry.arguments?.getString("manufacturer").orEmpty()
            val rawQuery = backStackEntry.arguments?.getString("query").orEmpty()
            val decodedFocus = runCatching { java.net.URLDecoder.decode(rawFocus, "UTF-8") }.getOrDefault(rawFocus)
            val decodedManufacturer = runCatching { java.net.URLDecoder.decode(rawManufacturer, "UTF-8") }.getOrDefault(rawManufacturer)
            val decodedQuery = runCatching { java.net.URLDecoder.decode(rawQuery, "UTF-8") }.getOrDefault(rawQuery)

            val catalogueViewModel: CatalogueViewModel = viewModel(factory = factory)
            MedicineCatalogueScreen(
                viewModel = catalogueViewModel,
                initialFocus = decodedFocus.ifBlank { "products" },
                initialManufacturer = decodedManufacturer,
                initialQuery = decodedQuery,
                onNavigateBack = { navController.popBackStack() },
                onProductClick = { id -> navController.navigate(NavRoutes.medicineDetails(id)) },
                onAddProductClick = {
                    if (catalogueViewModel.uiState.value.canManageCatalogue) {
                        navController.navigate(NavRoutes.PRODUCT_INVENTORY_LIST)
                    }
                },
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
                    navController.navigate(NavRoutes.medicineCatalogue(focus = "products", query = query)) {
                        launchSingleTop = true
                    }
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
                onAddOfferClick = { id -> navController.navigate(NavRoutes.addEditOffer(id)) },
                onNavigateToBasket = { navController.navigate(NavRoutes.BASKET) }
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
                onNavigateToCatalogue = { navController.navigate(NavRoutes.MEDICINE_CATALOGUE) },
                onNavigateToSelectedProductsSummary = { navController.navigate(NavRoutes.SELECTED_PRODUCTS_SUMMARY) }
            )
        }

        composable(NavRoutes.SELECTED_PRODUCTS_SUMMARY) { backStackEntry ->
            val basketOwner = remember(backStackEntry) {
                try {
                    navController.getBackStackEntry(NavRoutes.BASKET)
                } catch (_: IllegalArgumentException) {
                    backStackEntry
                }
            }
            val basketViewModel: BasketViewModel = viewModel(
                viewModelStoreOwner = basketOwner,
                factory = factory
            )
            SelectedProductsOrderSummaryScreen(
                viewModel = basketViewModel,
                onNavigateBack = { navController.popBackStack() },
                onProceedToCheckout = { navController.navigate(NavRoutes.CHECKOUT) },
                onExploreCatalogue = { navController.navigate(NavRoutes.MEDICINE_CATALOGUE) }
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

        // Orders & Checkout & Payment
        composable(NavRoutes.CHECKOUT) {
            val ordersViewModel: OrdersViewModel = viewModel(factory = factory)
            CheckoutScreen(
                viewModel = ordersViewModel,
                onNavigateBack = { navController.popBackStack() },
                onProceedToPayment = { addr, notes ->
                    navController.navigate(NavRoutes.payment(addr, notes)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = NavRoutes.PAYMENT,
            arguments = listOf(
                navArgument("address") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("notes") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val address = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("address") ?: "", "UTF-8")
            val notes = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("notes") ?: "", "UTF-8")
            val checkoutEntry = remember(backStackEntry) {
                try {
                    navController.getBackStackEntry(NavRoutes.CHECKOUT)
                } catch (_: IllegalArgumentException) {
                    backStackEntry
                }
            }
            val ordersViewModel: OrdersViewModel = viewModel(
                viewModelStoreOwner = checkoutEntry,
                factory = factory
            )
            PaymentScreen(
                viewModel = ordersViewModel,
                deliveryAddress = address,
                orderNotes = notes,
                onNavigateBack = { navController.popBackStack() },
                onPaymentSuccess = { orderIds, paymentMethod, txnId ->
                    if (orderIds.isNotEmpty()) {
                        navController.navigate(NavRoutes.orderConfirmation(orderIds, paymentMethod, txnId)) {
                            popUpTo(NavRoutes.BASKET) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(
            route = NavRoutes.ORDER_CONFIRMATION,
            arguments = listOf(
                navArgument("orderIds") { type = NavType.StringType },
                navArgument("paymentMethod") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("txnId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val idsStr = backStackEntry.arguments?.getString("orderIds") ?: ""
            val rawMethod = backStackEntry.arguments?.getString("paymentMethod")
            val rawTxn = backStackEntry.arguments?.getString("txnId")
            val paymentMethod = if (!rawMethod.isNullOrBlank()) java.net.URLDecoder.decode(rawMethod, "UTF-8") else null
            val txnId = if (!rawTxn.isNullOrBlank()) java.net.URLDecoder.decode(rawTxn, "UTF-8") else null
            val ids = idsStr.split(",").mapNotNull { it.toLongOrNull() }
            OrderConfirmationScreen(
                orderIds = ids,
                paymentMethod = paymentMethod,
                transactionId = txnId,
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
                onNavigateToBasket = { navController.navigate(NavRoutes.BASKET) },
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
                onNavigateToBasket = { navController.navigate(NavRoutes.BASKET) },
                onNavigateToProducts = { navController.navigate(NavRoutes.PRODUCT_INVENTORY_LIST) }
            )
        }

        // Product Inventory List (Room Database using ProductRepository)
        composable(NavRoutes.PRODUCT_INVENTORY_LIST) {
            val productViewModel: com.example.ui.screens.products.ProductListViewModel = viewModel(factory = factory)
            com.example.ui.screens.products.ProductListScreen(
                viewModel = productViewModel,
                onNavigateBack = { navController.popBackStack() }
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
                onLoggedOut = { performFullLogout() }
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

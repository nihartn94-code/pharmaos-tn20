package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SupplierOfferEntity
import com.example.data.local.entity.UserEntity
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.repository.PharmaRepositoryImpl
import com.example.domain.model.AccountStatus
import com.example.domain.model.DosageForm
import com.example.domain.model.SchemeType
import com.example.domain.model.StorageCondition
import com.example.domain.model.UserRole
import com.example.ui.navigation.NavRoutes
import com.example.ui.screens.catalogue.CatalogueViewModel
import com.example.ui.screens.catalogue.ExploreMode
import com.example.ui.screens.catalogue.MedicineCatalogueScreen
import com.example.ui.screens.catalogue.MedicineDetailsScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.dashboard.DashboardViewModel
import com.example.ui.theme.PharmaOSTheme
import com.example.util.PasswordHasher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class ManufacturerCatalogueExplorationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var repository: PharmaRepositoryImpl

    private var supplierId: Long = 0L
    private var sunD3Id: Long = 0L
    private var sunPantocidId: Long = 0L
    private var ciplaParaId: Long = 0L
    private var aristoMonocefId: Long = 0L

    @Before
    fun setup() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            dataStoreManager = DataStoreManager(
                context = context,
                secureTokenStorage = com.example.data.local.SecureTokenStorage.createForUnitTest(
                    context,
                    prefsFileName = "test_mfr_catalogue_tokens_${System.nanoTime()}"
                )
            )
            dataStoreManager.clearAuthSession()
            dataStoreManager.setOnboardingCompleted(true)
            dataStoreManager.setAuthExecutionMode(AuthExecutionMode.DEVELOPMENT_LOCAL_MODE)
            repository = PharmaRepositoryImpl(db = db, dataStoreManager = dataStoreManager)

            val now = System.currentTimeMillis()
            val supplierHash = PasswordHasher.hashPassword("Pass123!")
            supplierId = db.userDao().insertUser(
                UserEntity(
                    fullName = "Apex Wholesale",
                    businessName = "Apex Pharma Distributors",
                    email = "supplier@apexpharma.test",
                    phone = "9800001111",
                    gstin = "27AABCU9603R1ZM",
                    address = "Mumbai, MH",
                    passwordHash = supplierHash.hashHex,
                    passwordSalt = supplierHash.saltHex,
                    role = UserRole.PHARMA_COMPANY.name,
                    status = AccountStatus.ACTIVE.name
                )
            )

            sunD3Id = db.productDao().insertProduct(
                ProductEntity(
                    name = "Sun Vitamin D3 60K",
                    brand = "Sun Vitamin D3",
                    saltComposition = "Cholecalciferol Softgel IP",
                    strength = "60,000 IU",
                    dosageForm = DosageForm.CAPSULE.name,
                    therapeuticCategory = "Vitamins & Nutrition",
                    manufacturerName = "Sun Pharma",
                    packSize = "Pack of 4 Softgels",
                    hsnCode = "30045020",
                    gstRatePercent = 12.0,
                    storageCondition = StorageCondition.COOL_8_25.name,
                    isRx = false,
                    isActive = true,
                    createdBy = supplierId
                )
            )
            sunPantocidId = db.productDao().insertProduct(
                ProductEntity(
                    name = "Pantocid 40 Tablet",
                    brand = "Pantocid 40",
                    saltComposition = "Pantoprazole Sodium IP",
                    strength = "40mg",
                    dosageForm = DosageForm.TABLET.name,
                    therapeuticCategory = "Gastrointestinal",
                    manufacturerName = "Sun Pharma",
                    packSize = "Strip of 15 Tabs",
                    hsnCode = "30049099",
                    gstRatePercent = 12.0,
                    storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                    isRx = true,
                    isActive = true,
                    createdBy = supplierId
                )
            )
            ciplaParaId = db.productDao().insertProduct(
                ProductEntity(
                    name = "Paracip 500 Tablet",
                    brand = "Paracip 500",
                    saltComposition = "Paracetamol IP",
                    strength = "500mg",
                    dosageForm = DosageForm.TABLET.name,
                    therapeuticCategory = "Analgesics",
                    manufacturerName = "Cipla",
                    packSize = "Strip of 15 Tabs",
                    hsnCode = "30049060",
                    gstRatePercent = 12.0,
                    storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                    isRx = false,
                    isActive = true,
                    createdBy = supplierId
                )
            )
            aristoMonocefId = db.productDao().insertProduct(
                ProductEntity(
                    name = "Monocef 1g Injection",
                    brand = "Monocef 1g",
                    saltComposition = "Ceftriaxone Sodium IP 1000mg",
                    strength = "1g",
                    dosageForm = DosageForm.INJECTION.name,
                    therapeuticCategory = "Injectables",
                    manufacturerName = "Aristo Pharma",
                    packSize = "Vial with WFI",
                    hsnCode = "30042090",
                    gstRatePercent = 12.0,
                    storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                    isRx = true,
                    isActive = true,
                    createdBy = supplierId
                )
            )

            val validTill = now + 180L * 86400000L
            listOf(
                Triple(sunD3Id, 118.0, 140.0),
                Triple(sunPantocidId, 96.0, 125.0),
                Triple(ciplaParaId, 38.0, 48.0),
                Triple(aristoMonocefId, 62.0, 78.0)
            ).forEach { (pid, ptr, mrp) ->
                db.offerDao().insertOffer(
                    SupplierOfferEntity(
                        productId = pid,
                        supplierId = supplierId,
                        unitPricePtr = ptr,
                        mrp = mrp,
                        moq = 1,
                        availableQuantity = 500,
                        schemeType = SchemeType.NONE.name,
                        validTillEpochMillis = validTill,
                        isActive = true
                    )
                )
            }
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            dataStoreManager.clearAuthSession()
            dataStoreManager.setAuthExecutionMode(AuthExecutionMode.DEVELOPMENT_LOCAL_MODE)
            db.close()
        }
    }

    private fun testVmFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return when {
                modelClass.isAssignableFrom(DashboardViewModel::class.java) -> DashboardViewModel(repository) as T
                modelClass.isAssignableFrom(CatalogueViewModel::class.java) -> CatalogueViewModel(repository) as T
                else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
            }
        }
    }

    private fun seedAndSignInUser(email: String, fullName: String, role: UserRole): Long = runBlocking {
        val hash = PasswordHasher.hashPassword("Test1234!")
        val id = db.userDao().insertUser(
            UserEntity(
                fullName = fullName,
                businessName = "$fullName Pharma",
                email = email,
                phone = "9811112222",
                gstin = "27AABCU9603R1ZN",
                address = "Mumbai, MH",
                passwordHash = hash.hashHex,
                passwordSalt = hash.saltHex,
                role = role.name,
                status = AccountStatus.ACTIVE.name
            )
        )
        dataStoreManager.setAuthTokens("test_token_$id", null)
        dataStoreManager.setLoggedInUserId(id)
        id
    }

    private fun waitForUiCondition(timeoutMs: Long = 5000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.waitForIdle()
            if (predicate()) {
                composeTestRule.waitForIdle()
                return
            }
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out after ${timeoutMs}ms waiting for UI condition")
            }
            Thread.sleep(25)
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun sidePanelManufacturerCatalog_navigatesToUnifiedExplore_drillsIntoManufacturer_opensProductDetails_andPreservesStateOnBack() {
        seedAndSignInUser("stockist@explore.test", "Aarav Stockist", UserRole.STOCKIST)
        val factory = testVmFactory()
        var basketNavigated = false

        composeTestRule.setContent {
            PharmaOSTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = NavRoutes.DASHBOARD
                ) {
                    composable(NavRoutes.DASHBOARD) {
                        val dashboardVm: DashboardViewModel = viewModel(factory = factory)
                        DashboardScreen(
                            viewModel = dashboardVm,
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
                            onNavigateToBasket = { basketNavigated = true },
                            onNavigateToOrders = {},
                            onNavigateToBatches = {},
                            onNavigateToShortages = {},
                            onNavigateToLedger = {},
                            onNavigateToOcr = {},
                            onNavigateToOffers = {},
                            onNavigateToTrustScore = {},
                            onNavigateToNotifications = {},
                            onNavigateToSettings = {}
                        )
                    }
                    composable(
                        route = NavRoutes.MEDICINE_CATALOGUE_ROUTE,
                        arguments = listOf(
                            navArgument("focus") { type = NavType.StringType; defaultValue = "products" },
                            navArgument("manufacturer") { type = NavType.StringType; defaultValue = "" },
                            navArgument("query") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { entry ->
                        val focus = entry.arguments?.getString("focus") ?: "products"
                        val manufacturer = entry.arguments?.getString("manufacturer").orEmpty()
                        val query = entry.arguments?.getString("query").orEmpty()
                        val catalogueVm: CatalogueViewModel = viewModel(factory = factory)
                        MedicineCatalogueScreen(
                            viewModel = catalogueVm,
                            initialFocus = focus,
                            initialManufacturer = manufacturer,
                            initialQuery = query,
                            onNavigateBack = { navController.popBackStack() },
                            onProductClick = { id -> navController.navigate(NavRoutes.medicineDetails(id)) },
                            onAddProductClick = {},
                            onNavigateToBasket = { basketNavigated = true }
                        )
                    }
                    composable(
                        route = NavRoutes.MEDICINE_DETAILS,
                        arguments = listOf(navArgument("productId") { type = NavType.LongType })
                    ) { entry ->
                        val productId = entry.arguments?.getLong("productId") ?: 0L
                        val detailsVm: CatalogueViewModel = viewModel(factory = factory)
                        MedicineDetailsScreen(
                            productId = productId,
                            viewModel = detailsVm,
                            onNavigateBack = { navController.popBackStack() },
                            onProductClick = { id -> navController.navigate(NavRoutes.medicineDetails(id)) },
                            onAddOfferClick = {},
                            onNavigateToBasket = { basketNavigated = true }
                        )
                    }
                }
            }
        }

        // 1. Open side panel on Dashboard and click Manufacturer Catalog
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("dashboard_hamburger_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("dashboard_hamburger_button").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("drawer_item_catalog").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("drawer_item_catalog").performClick()

        // 2. Verify Explore opens in Manufacturers mode displaying real manufacturers
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("manufacturer_directory_list").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("manufacturer_card_sun_pharma").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("catalogue_topbar_title").assertTextContains("Manufacturer Catalog")
        composeTestRule.onNodeWithTag("manufacturer_card_sun_pharma").assertIsDisplayed()
        composeTestRule.onNodeWithTag("manufacturer_card_cipla").assertIsDisplayed()
        composeTestRule.onNodeWithTag("manufacturer_card_aristo_pharma").assertIsDisplayed()

        // Stockist must NOT see manufacturer inventory management action
        assertTrue(
            composeTestRule.onAllNodesWithTag("catalogue_manage_inventory_button").fetchSemanticsNodes().isEmpty()
        )

        // 3. Select Sun Pharma -> drills into Sun Pharma products only
        composeTestRule.onNodeWithTag("manufacturer_card_sun_pharma").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("selected_manufacturer_banner").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("product_card_$sunPantocidId").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("selected_manufacturer_title").assertTextContains("Sun Pharma")
        composeTestRule.onNodeWithTag("product_card_$sunD3Id").assertIsDisplayed()
        composeTestRule.onNodeWithTag("product_card_$sunPantocidId").assertIsDisplayed()
        // Cipla & Aristo products must NOT be in Sun Pharma listing
        assertTrue(composeTestRule.onAllNodesWithTag("product_card_$ciplaParaId").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithTag("product_card_$aristoMonocefId").fetchSemanticsNodes().isEmpty())

        // 4. Add Sun Pharma product to basket directly from manufacturer product list
        composeTestRule.onNodeWithTag("card_add_pill_$sunPantocidId").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("catalogue_basket_badge").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("floating_order_bar").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("catalogue_basket_badge").assertTextContains("10")

        // 5. Select product -> opens existing MedicineDetailsScreen
        composeTestRule.onNodeWithTag("product_card_$sunPantocidId").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("medicine_details_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("Pantocid 40 Tablet", substring = true).onFirst().assertIsDisplayed()

        // 6. Navigate Back from MedicineDetailsScreen -> returns to Explore with Sun Pharma still selected!
        composeTestRule.onNodeWithTag("top_bar_back_button").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("selected_manufacturer_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("selected_manufacturer_title").assertTextContains("Sun Pharma")
        composeTestRule.onNodeWithTag("product_card_$sunPantocidId").assertIsDisplayed()

        // 7. Navigate Back from Sun Pharma drilldown -> returns to Manufacturer Directory
        composeTestRule.onNodeWithTag("catalogue_back_button").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("manufacturer_directory_list").fetchSemanticsNodes().isNotEmpty()
        }

        // 8. Navigate Back from Manufacturer Directory -> returns to Dashboard
        composeTestRule.onNodeWithTag("catalogue_back_button").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("dashboard_hamburger_button").fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(basketNavigated)
    }

    @Test
    fun catalogueViewModel_supportsSearchingAndFilteringByManufacturerSaltStrengthAndDosageForm() {
        seedAndSignInUser("stockist2@explore.test", "Meera Stockist", UserRole.STOCKIST)
        val viewModel = CatalogueViewModel(repository)

        waitForUiCondition {
            !viewModel.uiState.value.isLoading && viewModel.uiState.value.allProducts.size >= 4
        }

        // Apply manufacturer focus navigation
        viewModel.applyInitialNavigation(focus = "manufacturers")
        assertEquals(ExploreMode.MANUFACTURERS, viewModel.uiState.value.exploreMode)
        assertTrue(viewModel.uiState.value.manufacturers.any { it.name == "Sun Pharma" && it.productCount == 2 })
        assertTrue(viewModel.uiState.value.manufacturers.any { it.name == "Cipla" && it.productCount == 1 })
        assertTrue(viewModel.uiState.value.manufacturers.any { it.name == "Aristo Pharma" && it.productCount == 1 })

        // Search by salt composition filters both products and manufacturers
        viewModel.onSearchQueryChanged("Ceftriaxone")
        assertEquals(1, viewModel.uiState.value.filteredProducts.size)
        assertEquals(aristoMonocefId, viewModel.uiState.value.filteredProducts.first().id)
        assertEquals(listOf("Aristo Pharma"), viewModel.uiState.value.filteredManufacturers.map { it.name })

        // Search by strength filters accurately
        viewModel.onSearchQueryChanged("60,000 IU")
        assertEquals(1, viewModel.uiState.value.filteredProducts.size)
        assertEquals(sunD3Id, viewModel.uiState.value.filteredProducts.first().id)
        assertEquals(listOf("Sun Pharma"), viewModel.uiState.value.filteredManufacturers.map { it.name })

        // Clear search, select manufacturer + strength + dosage form combined
        viewModel.clearFilters()
        viewModel.selectManufacturer("Sun Pharma")
        assertEquals(2, viewModel.uiState.value.filteredProducts.size)

        viewModel.selectStrength("40mg")
        assertEquals(1, viewModel.uiState.value.filteredProducts.size)
        assertEquals(sunPantocidId, viewModel.uiState.value.filteredProducts.first().id)

        // Extended filters: dosage form INJECTION with salt Ceftriaxone
        viewModel.setFilters(
            dosageForm = DosageForm.INJECTION,
            category = null,
            storage = null,
            rxOnly = null,
            inStockOnly = false,
            sortOption = viewModel.uiState.value.sortBy,
            manufacturer = null,
            strength = "1g",
            saltFilter = "Ceftriaxone"
        )
        assertEquals(1, viewModel.uiState.value.filteredProducts.size)
        assertEquals(aristoMonocefId, viewModel.uiState.value.filteredProducts.first().id)

        // Calling applyInitialNavigation again with same route key (e.g. popping back from details) must NOT reset state
        viewModel.applyInitialNavigation(focus = "manufacturers")
        assertEquals(DosageForm.INJECTION, viewModel.uiState.value.selectedDosageForm)
        assertEquals("1g", viewModel.uiState.value.selectedStrength)
        assertEquals("Ceftriaxone", viewModel.uiState.value.selectedSaltFilter)

        // Clear filters resets cleanly
        viewModel.clearFilters()
        assertNull(viewModel.uiState.value.selectedManufacturer)
        assertNull(viewModel.uiState.value.selectedStrength)
        assertEquals("", viewModel.uiState.value.selectedSaltFilter)
        assertNull(viewModel.uiState.value.selectedDosageForm)
        assertEquals(4, viewModel.uiState.value.filteredProducts.size)
    }

    @Test
    fun roleBasedAccess_manufacturerSeesManageInventoryAction_whileStockistCannot() {
        seedAndSignInUser("mfg@sunpharma.test", "Sun Pharma Ops", UserRole.MANUFACTURER)
        val viewModel = CatalogueViewModel(repository)
        var manageInventoryClicked = false

        composeTestRule.setContent {
            PharmaOSTheme {
                MedicineCatalogueScreen(
                    viewModel = viewModel,
                    initialFocus = "manufacturers",
                    onNavigateBack = {},
                    onProductClick = {},
                    onAddProductClick = { manageInventoryClicked = true }
                )
            }
        }

        waitForUiCondition {
            viewModel.uiState.value.canManageCatalogue &&
                composeTestRule.onAllNodesWithTag("catalogue_manage_inventory_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("catalogue_manage_inventory_button").assertIsDisplayed().performClick()
        assertTrue(manageInventoryClicked)
    }

    @Test
    fun exploreScreen_searchAndStrengthFilterChips_updateProductListingLive() {
        seedAndSignInUser("stockist3@explore.test", "Neha Pharmacy", UserRole.STOCKIST)
        val viewModel = CatalogueViewModel(repository)

        composeTestRule.setContent {
            PharmaOSTheme {
                MedicineCatalogueScreen(
                    viewModel = viewModel,
                    initialFocus = "products",
                    onNavigateBack = {},
                    onProductClick = {},
                    onAddProductClick = {}
                )
            }
        }

        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("product_card_$sunPantocidId").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("product_card_$ciplaParaId").fetchSemanticsNodes().isNotEmpty()
        }

        // Filter by Strength chip (500mg -> Cipla Paracip 500)
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("strength_chip_500mg").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("strength_chip_500mg").performClick()
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("product_card_$ciplaParaId").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("product_card_$sunPantocidId").fetchSemanticsNodes().isEmpty()
        }

        // Toggle strength chip off and search by salt composition
        composeTestRule.onNodeWithTag("strength_chip_500mg").performClick()
        composeTestRule.onNodeWithTag("catalogue_search_input").performTextInput("Pantoprazole")
        waitForUiCondition {
            composeTestRule.onAllNodesWithTag("product_card_$sunPantocidId").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("product_card_$ciplaParaId").fetchSemanticsNodes().isEmpty()
        }
    }
}

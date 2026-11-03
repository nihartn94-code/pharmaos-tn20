package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
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
import com.example.data.local.entity.ProofOfDeliveryEntity
import com.example.data.local.entity.RecallEntity
import com.example.data.local.entity.ReturnEntity
import com.example.data.local.entity.ShortageRiskEntity
import com.example.data.local.entity.StockRecordEntity
import com.example.data.local.entity.SupplierOfferEntity
import com.example.data.local.entity.SupplierPerformanceEntity
import com.example.data.local.entity.SupplierSettingsEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.AccountStatus
import com.example.domain.model.AuthUser
import com.example.domain.model.DosageForm
import com.example.domain.model.ExpiryRiskStatus
import com.example.domain.model.InventoryDashboardSummary
import com.example.domain.model.InventoryStockItem
import com.example.domain.model.NotificationType
import com.example.domain.model.OfferWithSupplier
import com.example.domain.model.OptimizationResult
import com.example.domain.model.StockLevelStatus
import com.example.domain.model.OrderStatus
import com.example.domain.model.ProductDetail
import com.example.domain.model.ReturnReason
import com.example.domain.model.ReturnStatus
import com.example.domain.model.SchemeType
import com.example.domain.model.ShortageEvaluationResult
import com.example.domain.model.StorageCondition
import com.example.domain.model.TrustScoreBreakdown
import com.example.domain.model.UserRole
import com.example.domain.repository.BasketItemDetail
import com.example.domain.repository.BasketLoadReport
import com.example.domain.repository.BasketWithDetails
import com.example.domain.repository.BatchWithProduct
import com.example.domain.repository.DashboardKpis
import com.example.domain.repository.NamedBasketSummary
import com.example.domain.repository.OrderDetailsWithHistory
import com.example.domain.repository.PharmaRepository
import com.example.domain.repository.RecallWithAffectedStock
import com.example.domain.repository.ReturnWithDetails
import com.example.domain.repository.SupplierSubtotal
import com.example.domain.usecase.CalculateTrustScoreUseCase
import com.example.domain.usecase.EvaluateShortageRiskUseCase
import com.example.domain.usecase.OptimizeBasketUseCase
import com.example.util.Formatters
import com.example.util.PasswordHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class PharmaRepositoryImpl(
    private val db: AppDatabase,
    private val dataStoreManager: DataStoreManager,
    private val optimizeBasketUseCase: OptimizeBasketUseCase = OptimizeBasketUseCase(),
    private val calculateTrustScoreUseCase: CalculateTrustScoreUseCase = CalculateTrustScoreUseCase(),
    private val evaluateShortageRiskUseCase: EvaluateShortageRiskUseCase = EvaluateShortageRiskUseCase()
) : PharmaRepository {

    private val userDao = db.userDao()
    private val productDao = db.productDao()
    private val offerDao = db.offerDao()
    private val basketDao = db.basketDao()
    private val orderDao = db.orderDao()
    private val batchDao = db.batchDao()
    private val ledgerDao = db.ledgerDao()
    private val returnDao = db.returnDao()
    private val recallDao = db.recallDao()
    private val notificationDao = db.notificationDao()
    private val analyticsDao = db.analyticsDao()
    private val complianceDao = db.complianceDao()

    override suspend fun postApiAuthLogin(
        email: String,
        password: String,
        expectedRole: UserRole?
    ): Result<AuthUser> {
        val trimmed = email.trim()
        val user = userDao.getUserByIdentifier(trimmed)
            ?: userDao.getUserByEmail(trimmed.lowercase())
            ?: return Result.failure(IllegalArgumentException("No account registered with '$trimmed'."))

        if (expectedRole != null && user.role != expectedRole.name) {
            val actualRoleName = try { UserRole.valueOf(user.role).displayName } catch (_: Exception) { user.role }
            return Result.failure(IllegalArgumentException("This account is registered as '$actualRoleName'. Please switch to the $actualRoleName login page."))
        }

        if (user.status == AccountStatus.SUSPENDED.name) {
            return Result.failure(IllegalStateException("This account is currently suspended. Please contact administrator."))
        }

        val isValid = PasswordHasher.verifyPassword(password, user.passwordHash, user.passwordSalt)
        if (!isValid) {
            return Result.failure(IllegalArgumentException("Incorrect password entered."))
        }

        dataStoreManager.setLoggedInUserId(user.id)
        val role = try { UserRole.valueOf(user.role) } catch (_: Exception) { UserRole.STOCKIST }
        val status = try { AccountStatus.valueOf(user.status) } catch (_: Exception) { AccountStatus.ACTIVE }

        return Result.success(
            AuthUser(
                id = user.id,
                fullName = user.fullName,
                businessName = user.businessName,
                email = user.email,
                phone = user.phone,
                gstin = user.gstin,
                address = user.address,
                role = role,
                status = status
            )
        )
    }

    override suspend fun postApiAuthGoogle(
        email: String,
        displayName: String,
        role: UserRole
    ): Result<AuthUser> {
        val trimmedEmail = email.trim().lowercase()
        val existing = userDao.getUserByEmail(trimmedEmail)
        if (existing != null) {
            if (existing.status == AccountStatus.SUSPENDED.name) {
                return Result.failure(IllegalStateException("This account is currently suspended. Please contact administrator."))
            }
            dataStoreManager.setLoggedInUserId(existing.id)
            val existingRole = try { UserRole.valueOf(existing.role) } catch (_: Exception) { UserRole.STOCKIST }
            val status = try { AccountStatus.valueOf(existing.status) } catch (_: Exception) { AccountStatus.ACTIVE }
            return Result.success(
                AuthUser(
                    id = existing.id,
                    fullName = existing.fullName,
                    businessName = existing.businessName,
                    email = existing.email,
                    phone = existing.phone,
                    gstin = existing.gstin,
                    address = existing.address,
                    role = existingRole,
                    status = status
                )
            )
        }

        val userCount = userDao.getUserCount()
        // First user ever automatically becomes Master Admin
        val effectiveRole = if (userCount == 0) UserRole.ADMIN else role
        val derivedHandle = trimmedEmail.substringBefore("@").replace(".", " ").replace("_", " ").split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        val safeName = displayName.trim().ifBlank { derivedHandle.ifBlank { "Enterprise Partner" } }
        val enterpriseName = when (effectiveRole) {
            UserRole.ADMIN -> "PharmaOS Central Administration"
            UserRole.MANUFACTURER -> "$safeName Pharma Labs"
            UserRole.PHARMA_COMPANY -> "$safeName Healthcare"
            UserRole.STOCKIST -> "$safeName Pharma Distributors"
        }

        val hashResult = PasswordHasher.hashPassword("GoogleAuth_${System.currentTimeMillis()}")
        val entity = UserEntity(
            fullName = safeName,
            businessName = enterpriseName,
            email = trimmedEmail,
            phone = "9876543210",
            gstin = "27AAAPL1234C1ZV",
            address = "Commercial B2B Premises, Industrial Zone, India",
            passwordHash = hashResult.hashHex,
            passwordSalt = hashResult.saltHex,
            role = effectiveRole.name,
            status = AccountStatus.ACTIVE.name
        )

        val id = userDao.insertUser(entity)
        dataStoreManager.setLoggedInUserId(id)

        if (effectiveRole.isSupplier) {
            analyticsDao.insertOrUpdateSupplierSettings(
                SupplierSettingsEntity(
                    supplierId = id,
                    deliveryCharge = 250.0,
                    freeDeliveryThreshold = 5000.0,
                    averageLeadTimeDays = 3.0
                )
            )
        }

        return Result.success(
            AuthUser(
                id = id,
                fullName = entity.fullName,
                businessName = entity.businessName,
                email = entity.email,
                phone = entity.phone,
                gstin = entity.gstin,
                address = entity.address,
                role = effectiveRole,
                status = AccountStatus.ACTIVE
            )
        )
    }

    override suspend fun postApiAuthRegister(
        fullName: String,
        businessName: String,
        email: String,
        phone: String,
        gstin: String?,
        address: String,
        password: String,
        role: UserRole
    ): Result<AuthUser> {
        val existing = userDao.getUserByEmail(email.trim().lowercase())
        if (existing != null) {
            return Result.failure(IllegalArgumentException("An account with email $email already exists."))
        }

        val userCount = userDao.getUserCount()
        // Rule: The first account ever registered automatically becomes Administrator
        val effectiveRole = if (userCount == 0) UserRole.ADMIN else role

        val hashResult = PasswordHasher.hashPassword(password)
        val entity = UserEntity(
            fullName = fullName.trim(),
            businessName = businessName.trim(),
            email = email.trim().lowercase(),
            phone = phone.trim(),
            gstin = gstin?.trim()?.ifBlank { null },
            address = address.trim(),
            passwordHash = hashResult.hashHex,
            passwordSalt = hashResult.saltHex,
            role = effectiveRole.name,
            status = AccountStatus.ACTIVE.name
        )

        val id = userDao.insertUser(entity)
        dataStoreManager.setLoggedInUserId(id)

        // Seed default supplier settings if supplier
        if (effectiveRole.isSupplier) {
            analyticsDao.insertOrUpdateSupplierSettings(
                SupplierSettingsEntity(
                    supplierId = id,
                    deliveryCharge = 250.0,
                    freeDeliveryThreshold = 5000.0,
                    averageLeadTimeDays = 3.0
                )
            )
        }

        return Result.success(
            AuthUser(
                id = id,
                fullName = entity.fullName,
                businessName = entity.businessName,
                email = entity.email,
                phone = entity.phone,
                gstin = entity.gstin,
                address = entity.address,
                role = effectiveRole,
                status = AccountStatus.ACTIVE
            )
        )
    }

    override fun getApiUsersMeFlow(): Flow<AuthUser?> {
        return dataStoreManager.loggedInUserIdFlow.map { userId ->
            if (userId == null) null
            else {
                val user = userDao.getUserById(userId) ?: return@map null
                val role = try { UserRole.valueOf(user.role) } catch (_: Exception) { UserRole.STOCKIST }
                val status = try { AccountStatus.valueOf(user.status) } catch (_: Exception) { AccountStatus.ACTIVE }
                AuthUser(
                    id = user.id,
                    fullName = user.fullName,
                    businessName = user.businessName,
                    email = user.email,
                    phone = user.phone,
                    gstin = user.gstin,
                    address = user.address,
                    role = role,
                    status = status
                )
            }
        }
    }

    override suspend fun getApiUsersMe(): AuthUser? {
        val userId = dataStoreManager.loggedInUserIdFlow.firstOrNull()
        val user = if (userId != null) {
            userDao.getUserById(userId)
        } else null

        val effectiveUser = user ?: ensureDefaultStockistUser()
        val role = try { UserRole.valueOf(effectiveUser.role) } catch (_: Exception) { UserRole.STOCKIST }
        val status = try { AccountStatus.valueOf(effectiveUser.status) } catch (_: Exception) { AccountStatus.ACTIVE }
        return AuthUser(
            id = effectiveUser.id,
            fullName = effectiveUser.fullName,
            businessName = effectiveUser.businessName,
            email = effectiveUser.email,
            phone = effectiveUser.phone,
            gstin = effectiveUser.gstin,
            address = effectiveUser.address,
            role = role,
            status = status
        )
    }

    private suspend fun ensureDefaultSupplierUser(): UserEntity {
        val existing = userDao.getAllUsers().firstOrNull { it.role == UserRole.MANUFACTURER.name || it.role == UserRole.PHARMA_COMPANY.name }
        if (existing != null) return existing
        val hash = PasswordHasher.hashPassword("PharmaPass123")
        val entity = UserEntity(
            fullName = "Alkem Laboratories",
            businessName = "Alkem Lab Private Limited",
            email = "supply@alkem.com",
            phone = "+919876543210",
            gstin = "27AAACA1234A1Z5",
            address = "Alkem House, Lower Parel, Mumbai",
            passwordHash = hash.hashHex,
            passwordSalt = hash.saltHex,
            role = UserRole.MANUFACTURER.name,
            status = AccountStatus.ACTIVE.name
        )
        val id = userDao.insertUser(entity)
        return entity.copy(id = id)
    }

    private suspend fun ensureDefaultStockistUser(): UserEntity {
        val existingStockist = userDao.getAllUsers().firstOrNull { it.role == UserRole.STOCKIST.name }
        if (existingStockist != null) {
            dataStoreManager.setLoggedInUserId(existingStockist.id)
            return existingStockist
        }
        val hash = PasswordHasher.hashPassword("Stockist@123")
        val newUser = UserEntity(
            fullName = "Rohan Sharma",
            businessName = "Apex Pharma Distributors",
            email = "rohan.sharma@apexpharma.com",
            phone = "+919876543210",
            gstin = "27ABCDE1234F1Z5",
            address = "Shop 12, Medical Market, Mumbai, MH",
            passwordHash = hash.hashHex,
            passwordSalt = hash.saltHex,
            role = UserRole.STOCKIST.name,
            status = AccountStatus.ACTIVE.name
        )
        val id = userDao.insertUser(newUser)
        dataStoreManager.setLoggedInUserId(id)
        return newUser.copy(id = id)
    }

    override suspend fun logout() {
        dataStoreManager.setLoggedInUserId(null)
    }

    override fun getApiUsersFlow(): Flow<List<UserEntity>> = userDao.getAllUsersFlow()

    override suspend fun patchApiUserStatus(userId: Long, status: String): Result<Unit> {
        val current = getApiUsersMe()
        if (current?.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("Only Administrator can change account status."))
        }
        userDao.updateUserStatus(userId, status)
        return Result.success(Unit)
    }

    override suspend fun getFirstUserRegistrationCheck(): Boolean {
        return userDao.getUserCount() == 0
    }

    override suspend fun saveComplianceVerification(
        userId: Long,
        gstin: String,
        drugLicenseNumber: String,
        drugLicenseIssueDate: String,
        drugLicenseExpiryDate: String,
        drugLicenseDocUri: String?,
        whoGmpCertificateNumber: String?,
        whoGmpIssueDate: String?,
        whoGmpExpiryDate: String?,
        whoGmpDocUri: String?,
        isWhoGmpApplicable: Boolean
    ): Result<Unit> {
        return try {
            val entity = com.example.data.local.entity.ComplianceVerificationEntity(
                userId = userId,
                gstin = gstin,
                isGstinVerified = true,
                drugLicenseNumber = drugLicenseNumber,
                drugLicenseIssueDate = drugLicenseIssueDate,
                drugLicenseExpiryDate = drugLicenseExpiryDate,
                drugLicenseDocUri = drugLicenseDocUri,
                whoGmpCertificateNumber = whoGmpCertificateNumber,
                whoGmpIssueDate = whoGmpIssueDate,
                whoGmpExpiryDate = whoGmpExpiryDate,
                whoGmpDocUri = whoGmpDocUri,
                isWhoGmpApplicable = isWhoGmpApplicable,
                status = "VERIFIED",
                verifiedAtEpochMillis = System.currentTimeMillis()
            )
            complianceDao.insertOrUpdateCompliance(entity)
            // Also ensure User table has the verified GSTIN
            val user = userDao.getUserById(userId)
            if (user != null) {
                userDao.updateUser(user.copy(gstin = gstin))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getComplianceVerificationFlow(userId: Long): Flow<com.example.data.local.entity.ComplianceVerificationEntity?> {
        return complianceDao.getComplianceByUserIdFlow(userId)
    }

    override suspend fun ensureDefaultCatalogueSeeded() {
        val suppliers = userDao.getAllUsers().filter { it.role == UserRole.MANUFACTURER.name || it.role == UserRole.PHARMA_COMPANY.name }
        val supplierId = if (suppliers.isNotEmpty()) {
            suppliers.first().id
        } else {
            ensureDefaultSupplierUser().id
        }
        ensureDefaultStockistUser()
        val oneYearAhead = System.currentTimeMillis() + 365L * 24 * 3600 * 1000

        if (productDao.getAllProducts().isEmpty()) {

        data class SeedItem(
            val name: String,
            val brand: String,
            val salt: String,
            val strength: String,
            val form: DosageForm,
            val category: String,
            val manufacturer: String,
            val packSize: String,
            val hsn: String,
            val gst: Double,
            val storage: StorageCondition,
            val isRx: Boolean,
            val ptr: Double,
            val mrp: Double,
            val marginOverride: Int
        )

        val referenceItems = listOf(
            SeedItem(
                name = "Amoxicillin 250mg + Clav",
                brand = "Amoxicillin 250mg + Clav",
                salt = "Amox Trihydrate IP 250mg",
                strength = "250mg + 125mg",
                form = DosageForm.CAPSULE,
                category = "Capsules & Softgels",
                manufacturer = "Alkem Lab",
                packSize = "Strip of 10 Caps",
                hsn = "30041010",
                gst = 12.0,
                storage = StorageCondition.ROOM_TEMPERATURE,
                isRx = true,
                ptr = 68.50,
                mrp = 76.00,
                marginOverride = 26
            ),
            SeedItem(
                name = "Vitamin D3 60,000 IU",
                brand = "Vitamin D3 60,000 IU",
                salt = "Cholecalciferol Softgel IP",
                strength = "60,000 IU",
                form = DosageForm.CAPSULE,
                category = "Capsules & Softgels",
                manufacturer = "Sun Pharma",
                packSize = "Pack of 4 Softgels",
                hsn = "30045020",
                gst = 12.0,
                storage = StorageCondition.COOL_8_25,
                isRx = false,
                ptr = 120.00,
                mrp = 130.00,
                marginOverride = 24
            ),
            SeedItem(
                name = "Omez 20mg Capsules",
                brand = "Omez 20mg Capsules",
                salt = "Omeprazole IP Enteric Coated",
                strength = "20mg",
                form = DosageForm.CAPSULE,
                category = "Capsules & Softgels",
                manufacturer = "Dr. Reddy's",
                packSize = "Strip of 15 Caps",
                hsn = "30049099",
                gst = 12.0,
                storage = StorageCondition.ROOM_TEMPERATURE,
                isRx = true,
                ptr = 54.00,
                mrp = 63.50,
                marginOverride = 22
            ),
            SeedItem(
                name = "Razo-D SR Capsules",
                brand = "Razo-D SR Capsules",
                salt = "Rabeprazole Sodium + Domperidone SR",
                strength = "20mg + 30mg",
                form = DosageForm.CAPSULE,
                category = "Capsules & Softgels",
                manufacturer = "Corona Remedies",
                packSize = "Strip of 10",
                hsn = "30049099",
                gst = 12.0,
                storage = StorageCondition.ROOM_TEMPERATURE,
                isRx = true,
                ptr = 162.00,
                mrp = 184.00,
                marginOverride = 20
            ),
            SeedItem(
                name = "Darolac Probiotic Caps",
                brand = "Darolac Probiotic Caps",
                salt = "Multi-strain Pre & Probiotics",
                strength = "1.25B CFU",
                form = DosageForm.CAPSULE,
                category = "Capsules & Softgels",
                manufacturer = "Aristo Pharma",
                packSize = "Strip of 10",
                hsn = "30049099",
                gst = 12.0,
                storage = StorageCondition.COOL_8_25,
                isRx = false,
                ptr = 92.00,
                mrp = 102.00,
                marginOverride = 25
            ),
            SeedItem(
                name = "Paracetamol 500mg",
                brand = "Paracetamol 500mg",
                salt = "Paracetamol IP",
                strength = "500mg",
                form = DosageForm.TABLET,
                category = "Tablets",
                manufacturer = "Cipla",
                packSize = "Strip of 15 Tabs",
                hsn = "30049060",
                gst = 12.0,
                storage = StorageCondition.ROOM_TEMPERATURE,
                isRx = false,
                ptr = 42.00,
                mrp = 48.00,
                marginOverride = 12
            ),
            SeedItem(
                name = "Pantoprazole 40mg",
                brand = "Pantoprazole 40mg",
                salt = "Pantoprazole Sodium IP",
                strength = "40mg",
                form = DosageForm.TABLET,
                category = "Tablets",
                manufacturer = "Alkem",
                packSize = "Strip of 10 Tabs",
                hsn = "30049099",
                gst = 12.0,
                storage = StorageCondition.ROOM_TEMPERATURE,
                isRx = true,
                ptr = 58.00,
                mrp = 68.00,
                marginOverride = 15
            ),
            SeedItem(
                name = "Monocef 1g Injection",
                brand = "Monocef 1g Injection",
                salt = "Ceftriaxone Sodium IP 1000mg",
                strength = "1g",
                form = DosageForm.INJECTION,
                category = "Injectables",
                manufacturer = "Aristo Pharma",
                packSize = "Vial with WFI",
                hsn = "30042090",
                gst = 12.0,
                storage = StorageCondition.COOL_8_25,
                isRx = true,
                ptr = 55.00,
                mrp = 64.00,
                marginOverride = 14
            ),
            SeedItem(
                name = "Ascoril D Plus Syrup",
                brand = "Ascoril D Plus Syrup",
                salt = "Dextromethorphan + Chlorpheniramine + Phenylephrine",
                strength = "100ml",
                form = DosageForm.SYRUP,
                category = "Syrups",
                manufacturer = "Glenmark",
                packSize = "100ml Bottle",
                hsn = "30049032",
                gst = 12.0,
                storage = StorageCondition.ROOM_TEMPERATURE,
                isRx = true,
                ptr = 85.00,
                mrp = 98.00,
                marginOverride = 13
            ),
            SeedItem(
                name = "Metformin 500 mg Tablet",
                brand = "Glycomet 500 mg",
                salt = "Metformin Hydrochloride 500 mg",
                strength = "500 mg",
                form = DosageForm.TABLET,
                category = "Diabetics",
                manufacturer = "USV Private Limited",
                packSize = "Strip of 20 Tabs",
                hsn = "30049099",
                gst = 12.0,
                storage = StorageCondition.ROOM_TEMPERATURE,
                isRx = true,
                ptr = 22.00,
                mrp = 26.50,
                marginOverride = 18
            ),
            SeedItem(
                name = "Metformin 500 mg SR",
                brand = "Glycomet SR 500 mg",
                salt = "Metformin Hydrochloride SR 500 mg",
                strength = "500 mg SR",
                form = DosageForm.TABLET,
                category = "Diabetics",
                manufacturer = "USV Private Limited",
                packSize = "Strip of 20 Tabs",
                hsn = "30049099",
                gst = 12.0,
                storage = StorageCondition.ROOM_TEMPERATURE,
                isRx = true,
                ptr = 28.00,
                mrp = 33.50,
                marginOverride = 17
            ),
            SeedItem(
                name = "Metformin 500 mg + Glimepiride 1 mg",
                brand = "Glimisave M1",
                salt = "Metformin 500 mg + Glimepiride 1 mg",
                strength = "500 mg + 1 mg",
                form = DosageForm.TABLET,
                category = "Diabetics",
                manufacturer = "Eris Lifesciences",
                packSize = "Strip of 15 Tabs",
                hsn = "30049099",
                gst = 12.0,
                storage = StorageCondition.ROOM_TEMPERATURE,
                isRx = true,
                ptr = 74.00,
                mrp = 89.00,
                marginOverride = 17
            ),
            SeedItem(
                name = "Metformin 500 mg + Sitagliptin 50 mg",
                brand = "Janumet 500/50",
                salt = "Metformin 500 mg + Sitagliptin 50 mg",
                strength = "500 mg + 50 mg",
                form = DosageForm.TABLET,
                category = "Diabetics",
                manufacturer = "MSD Pharmaceuticals",
                packSize = "Strip of 14 Tabs",
                hsn = "30049099",
                gst = 12.0,
                storage = StorageCondition.ROOM_TEMPERATURE,
                isRx = true,
                ptr = 165.00,
                mrp = 195.00,
                marginOverride = 15
            ),
            SeedItem(
                name = "Metformin 500 mg Injection",
                brand = "Metformin IV 500 mg",
                salt = "Metformin Sterile Solution 500 mg",
                strength = "500 mg / 10ml",
                form = DosageForm.INJECTION,
                category = "Diabetics",
                manufacturer = "Intas Pharmaceuticals",
                packSize = "Vial",
                hsn = "30049099",
                gst = 12.0,
                storage = StorageCondition.ROOM_TEMPERATURE,
                isRx = true,
                ptr = 45.00,
                mrp = 55.00,
                marginOverride = 18
            )
        )

        val oneYearAhead = System.currentTimeMillis() + 365L * 24 * 3600 * 1000
        referenceItems.forEach { item ->
            val pId = productDao.insertProduct(
                ProductEntity(
                    name = item.name,
                    brand = item.brand,
                    saltComposition = item.salt,
                    strength = item.strength,
                    dosageForm = item.form.name,
                    therapeuticCategory = item.category,
                    manufacturerName = item.manufacturer,
                    packSize = item.packSize,
                    hsnCode = item.hsn,
                    gstRatePercent = item.gst,
                    storageCondition = item.storage.name,
                    isRx = item.isRx,
                    createdBy = supplierId
                )
            )

            offerDao.insertOffer(
                SupplierOfferEntity(
                    productId = pId,
                    supplierId = supplierId,
                    unitPricePtr = item.ptr,
                    mrp = item.mrp,
                    moq = 10,
                    availableQuantity = 5000,
                    schemeType = SchemeType.NONE.name,
                    schemeDiscountPercent = item.marginOverride.toDouble(),
                    validTillEpochMillis = oneYearAhead
                )
            )
        }
    }

    // Ensure every single product has an offer
    val allCurrentProducts = productDao.getAllProducts()
    for (prod in allCurrentProducts) {
        val offers = offerDao.getActiveOffersForProduct(prod.id)
        if (offers.isEmpty()) {
            val ptr = when {
                prod.name.contains("Amox", true) -> 68.50
                prod.name.contains("Vitamin D3", true) -> 120.00
                prod.name.contains("Omez", true) -> 54.00
                prod.name.contains("Razo", true) -> 162.00
                prod.name.contains("Darolac", true) -> 92.00
                prod.name.contains("Paracetamol", true) -> 42.00
                prod.name.contains("Panto", true) -> 58.00
                prod.name.contains("Monocef", true) -> 55.00
                prod.name.contains("Ascoril", true) -> 85.00
                else -> 65.00
            }
            val mrp = (ptr * 1.15).coerceAtLeast(ptr + 5.0)
            offerDao.insertOffer(
                SupplierOfferEntity(
                    productId = prod.id,
                    supplierId = supplierId,
                    unitPricePtr = ptr,
                    mrp = mrp,
                    moq = 1,
                    availableQuantity = 10000,
                    schemeType = SchemeType.NONE.name,
                    schemeDiscountPercent = 20.0,
                    validTillEpochMillis = oneYearAhead
                )
            )
        }
    }
}

    override fun getApiProductsFlow(): Flow<List<ProductDetail>> {
        return combine(
            productDao.getAllActiveProductsFlow(),
            offerDao.getAllActiveOffersFlow()
        ) { products, offers ->
            products.map { p ->
                val prodOffers = offers.filter { it.productId == p.id }
                val bestOffer = prodOffers.minByOrNull { it.unitPricePtr }
                val minPrice = bestOffer?.unitPricePtr
                val minMrp = bestOffer?.mrp
                val margin = if (bestOffer != null) {
                    if (bestOffer.schemeDiscountPercent > 0.0) {
                        bestOffer.schemeDiscountPercent.toInt()
                    } else if (bestOffer.mrp > 0 && bestOffer.unitPricePtr > 0) {
                        (((bestOffer.mrp - bestOffer.unitPricePtr) / bestOffer.mrp) * 100).toInt().coerceAtLeast(1)
                    } else null
                } else null
                mapToProductDetail(p, minPrice, prodOffers.size, minMrp, margin, bestOffer?.id)
            }
        }
    }

    override suspend fun getApiProducts(): List<ProductDetail> {
        val products = productDao.getAllProducts().filter { it.isActive }
        val offers = offerDao.getAllActiveOffers()
        return products.map { p ->
            val prodOffers = offers.filter { it.productId == p.id }
            val bestOffer = prodOffers.minByOrNull { it.unitPricePtr }
            val minPrice = bestOffer?.unitPricePtr
            val minMrp = bestOffer?.mrp
            val margin = if (bestOffer != null) {
                if (bestOffer.schemeDiscountPercent > 0.0) {
                    bestOffer.schemeDiscountPercent.toInt()
                } else if (bestOffer.mrp > 0 && bestOffer.unitPricePtr > 0) {
                    (((bestOffer.mrp - bestOffer.unitPricePtr) / bestOffer.mrp) * 100).toInt().coerceAtLeast(1)
                } else null
            } else null
            mapToProductDetail(p, minPrice, prodOffers.size, minMrp, margin, bestOffer?.id)
        }
    }

    override suspend fun getApiProductById(id: Long): ProductDetail? {
        val p = productDao.getProductById(id) ?: return null
        val offers = offerDao.getActiveOffersForProduct(id)
        val bestOffer = offers.minByOrNull { it.unitPricePtr }
        val minPrice = bestOffer?.unitPricePtr
        val minMrp = bestOffer?.mrp
        val margin = if (bestOffer != null) {
            if (bestOffer.schemeDiscountPercent > 0.0) {
                bestOffer.schemeDiscountPercent.toInt()
            } else if (bestOffer.mrp > 0 && bestOffer.unitPricePtr > 0) {
                (((bestOffer.mrp - bestOffer.unitPricePtr) / bestOffer.mrp) * 100).toInt().coerceAtLeast(1)
            } else null
        } else null
        return mapToProductDetail(p, minPrice, offers.size, minMrp, margin, bestOffer?.id)
    }

    override fun getApiProductByIdFlow(id: Long): Flow<ProductDetail?> {
        return combine(
            productDao.getProductByIdFlow(id),
            offerDao.getAllActiveOffersFlow()
        ) { p, offers ->
            if (p == null) null
            else {
                val prodOffers = offers.filter { it.productId == p.id }
                val bestOffer = prodOffers.minByOrNull { it.unitPricePtr }
                val minPrice = bestOffer?.unitPricePtr
                val minMrp = bestOffer?.mrp
                val margin = if (bestOffer != null) {
                    if (bestOffer.schemeDiscountPercent > 0.0) {
                        bestOffer.schemeDiscountPercent.toInt()
                    } else if (bestOffer.mrp > 0 && bestOffer.unitPricePtr > 0) {
                        (((bestOffer.mrp - bestOffer.unitPricePtr) / bestOffer.mrp) * 100).toInt().coerceAtLeast(1)
                    } else null
                } else null
                mapToProductDetail(p, minPrice, prodOffers.size, minMrp, margin, bestOffer?.id)
            }
        }
    }

    override suspend fun postApiProducts(product: ProductEntity): Result<Long> {
        val current = getApiUsersMe()
        if (current == null || current.role == UserRole.STOCKIST) {
            return Result.failure(SecurityException("Only Suppliers and Admin can create products."))
        }
        val id = productDao.insertProduct(product.copy(createdBy = current.id))
        return Result.success(id)
    }

    override suspend fun putApiProducts(product: ProductEntity): Result<Unit> {
        val current = getApiUsersMe()
        if (current == null || current.role == UserRole.STOCKIST) {
            return Result.failure(SecurityException("Only Suppliers and Admin can edit products."))
        }
        productDao.updateProduct(product)
        return Result.success(Unit)
    }

    override suspend fun patchApiProductDeactivate(id: Long, isActive: Boolean): Result<Unit> {
        val current = getApiUsersMe()
        if (current == null || current.role == UserRole.STOCKIST) {
            return Result.failure(SecurityException("Only Suppliers and Admin can activate/deactivate products."))
        }
        productDao.setProductActiveStatus(id, isActive)
        return Result.success(Unit)
    }

    override fun getApiProductAlternativeBrandsFlow(id: Long): Flow<List<ProductEntity>> = flow {
        val prod = productDao.getProductById(id)
        if (prod == null) {
            emit(emptyList())
        } else {
            productDao.getAlternativeBrandsFlow(prod.saltComposition, prod.strength, id).collect {
                emit(it)
            }
        }
    }

    override fun getApiProductOffersFlow(productId: Long): Flow<List<OfferWithSupplier>> {
        return combine(
            offerDao.getActiveOffersForProductFlow(productId),
            userDao.getAllUsersFlow(),
            analyticsDao.getAllSupplierPerformancesFlow()
        ) { offers, users, perfs ->
            val usersMap = users.associateBy { it.id }
            val perfMap = perfs.associateBy { it.supplierId }
            offers.map { o ->
                val sup = usersMap[o.supplierId]
                val perf = perfMap[o.supplierId]
                val scheme = try { SchemeType.valueOf(o.schemeType) } catch (_: Exception) { SchemeType.NONE }
                OfferWithSupplier(
                    offerId = o.id,
                    productId = o.productId,
                    supplierId = o.supplierId,
                    supplierName = sup?.fullName ?: "Supplier #${o.supplierId}",
                    supplierBusinessName = sup?.businessName ?: "Supplier",
                    unitPricePtr = o.unitPricePtr,
                    mrp = o.mrp,
                    moq = o.moq,
                    availableQuantity = o.availableQuantity,
                    schemeType = scheme,
                    schemeBuyQty = o.schemeBuyQty,
                    schemeFreeQty = o.schemeFreeQty,
                    schemeDiscountPercent = o.schemeDiscountPercent,
                    schemeMinQtyForDiscount = o.schemeMinQtyForDiscount,
                    validTillEpochMillis = o.validTillEpochMillis,
                    isActive = o.isActive,
                    lastUpdatedEpochMillis = o.lastUpdatedEpochMillis,
                    trustScore = if ((perf?.completedOrdersCount ?: 0) >= 5) perf?.overallScore else null,
                    hasSufficientTrustData = (perf?.completedOrdersCount ?: 0) >= 5
                )
            }
        }
    }

    override suspend fun getApiProductOffers(productId: Long): List<OfferWithSupplier> {
        val offers = offerDao.getActiveOffersForProduct(productId)
        val users = userDao.getAllUsers().associateBy { it.id }
        return offers.map { o ->
            val sup = users[o.supplierId]
            val perf = analyticsDao.getSupplierPerformance(o.supplierId)
            val scheme = try { SchemeType.valueOf(o.schemeType) } catch (_: Exception) { SchemeType.NONE }
            OfferWithSupplier(
                offerId = o.id,
                productId = o.productId,
                supplierId = o.supplierId,
                supplierName = sup?.fullName ?: "Supplier #${o.supplierId}",
                supplierBusinessName = sup?.businessName ?: "Supplier",
                unitPricePtr = o.unitPricePtr,
                mrp = o.mrp,
                moq = o.moq,
                availableQuantity = o.availableQuantity,
                schemeType = scheme,
                schemeBuyQty = o.schemeBuyQty,
                schemeFreeQty = o.schemeFreeQty,
                schemeDiscountPercent = o.schemeDiscountPercent,
                schemeMinQtyForDiscount = o.schemeMinQtyForDiscount,
                validTillEpochMillis = o.validTillEpochMillis,
                isActive = o.isActive,
                lastUpdatedEpochMillis = o.lastUpdatedEpochMillis,
                trustScore = if ((perf?.completedOrdersCount ?: 0) >= 5) perf?.overallScore else null,
                hasSufficientTrustData = (perf?.completedOrdersCount ?: 0) >= 5
            )
        }
    }

    override fun getApiSupplierOffersFlow(supplierId: Long): Flow<List<OfferWithSupplier>> {
        return combine(
            offerDao.getOffersBySupplierFlow(supplierId),
            userDao.getAllUsersFlow(),
            analyticsDao.getAllSupplierPerformancesFlow()
        ) { offers, users, perfs ->
            val usersMap = users.associateBy { it.id }
            val perfMap = perfs.associateBy { it.supplierId }
            offers.map { o ->
                val sup = usersMap[o.supplierId]
                val perf = perfMap[o.supplierId]
                val scheme = try { SchemeType.valueOf(o.schemeType) } catch (_: Exception) { SchemeType.NONE }
                OfferWithSupplier(
                    offerId = o.id,
                    productId = o.productId,
                    supplierId = o.supplierId,
                    supplierName = sup?.fullName ?: "Supplier #${o.supplierId}",
                    supplierBusinessName = sup?.businessName ?: "Supplier",
                    unitPricePtr = o.unitPricePtr,
                    mrp = o.mrp,
                    moq = o.moq,
                    availableQuantity = o.availableQuantity,
                    schemeType = scheme,
                    schemeBuyQty = o.schemeBuyQty,
                    schemeFreeQty = o.schemeFreeQty,
                    schemeDiscountPercent = o.schemeDiscountPercent,
                    schemeMinQtyForDiscount = o.schemeMinQtyForDiscount,
                    validTillEpochMillis = o.validTillEpochMillis,
                    isActive = o.isActive,
                    lastUpdatedEpochMillis = o.lastUpdatedEpochMillis,
                    trustScore = if ((perf?.completedOrdersCount ?: 0) >= 5) perf?.overallScore else null,
                    hasSufficientTrustData = (perf?.completedOrdersCount ?: 0) >= 5
                )
            }
        }
    }

    override suspend fun postApiSupplierOffers(offer: SupplierOfferEntity): Result<Long> {
        val current = getApiUsersMe()
        if (current == null || !current.role.isSupplier && current.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("Only Suppliers and Admin can publish offers."))
        }
        val id = offerDao.insertOffer(offer.copy(supplierId = current.id))
        // Record stock snapshot
        analyticsDao.insertStockRecord(
            StockRecordEntity(
                productId = offer.productId,
                supplierId = current.id,
                offerId = id,
                availableQuantity = offer.availableQuantity,
                reason = "Offer created"
            )
        )
        // Evaluate shortage risk
        evaluateProductShortageRiskInternal(offer.productId)
        return Result.success(id)
    }

    override suspend fun putApiSupplierOffers(offer: SupplierOfferEntity): Result<Unit> {
        val current = getApiUsersMe()
        if (current == null || !current.role.isSupplier && current.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("Only Suppliers and Admin can update offers."))
        }
        offerDao.updateOffer(offer.copy(lastUpdatedEpochMillis = System.currentTimeMillis()))
        analyticsDao.insertStockRecord(
            StockRecordEntity(
                productId = offer.productId,
                supplierId = offer.supplierId,
                offerId = offer.id,
                availableQuantity = offer.availableQuantity,
                reason = "Offer updated"
            )
        )
        evaluateProductShortageRiskInternal(offer.productId)
        return Result.success(Unit)
    }

    override suspend fun patchApiSupplierOfferDeactivate(offerId: Long, isActive: Boolean): Result<Unit> {
        offerDao.setOfferActiveStatus(offerId, isActive)
        return Result.success(Unit)
    }

    override fun getApiBasketFlow(): Flow<BasketWithDetails> {
        return combine(
            dataStoreManager.loggedInUserIdFlow,
            productDao.getAllActiveProductsFlow(),
            offerDao.getAllActiveOffersFlow(),
            userDao.getAllUsersFlow()
        ) { buyerId, products, offers, users ->
            val effectiveBuyerId = buyerId ?: users.firstOrNull { it.role == UserRole.STOCKIST.name }?.id ?: users.firstOrNull()?.id
            if (effectiveBuyerId == null) {
                return@combine emptyBasketWithDetails()
            }
            val basket = basketDao.getActiveBasketForBuyer(effectiveBuyerId) ?: return@combine emptyBasketWithDetails()
            val items = basketDao.getBasketItems(basket.id)
            val productsMap = products.associateBy { it.id }
            val offersMap = offers.associateBy { it.id }
            val usersMap = users.associateBy { it.id }

            buildBasketDetails(basket.id, items, productsMap, offersMap, usersMap)
        }
    }

    override suspend fun getApiBasket(): BasketWithDetails {
        val buyer = getApiUsersMe() ?: return emptyBasketWithDetails()
        val basket = basketDao.getActiveBasketForBuyer(buyer.id) ?: return emptyBasketWithDetails()
        val items = basketDao.getBasketItems(basket.id)
        val products = productDao.getAllProducts().associateBy { it.id }
        val offers = offerDao.getAllActiveOffers().associateBy { it.id }
        val users = userDao.getAllUsers().associateBy { it.id }
        return buildBasketDetails(basket.id, items, products, offers, users)
    }

    private suspend fun buildBasketDetails(
        basketId: Long,
        items: List<BasketItemEntity>,
        productsMap: Map<Long, ProductEntity>,
        offersMap: Map<Long, SupplierOfferEntity>,
        usersMap: Map<Long, UserEntity>
    ): BasketWithDetails {
        val details = mutableListOf<BasketItemDetail>()
        val supplierTotals = mutableMapOf<Long, SupplierAccumulator>()

        for (item in items) {
            val prod = productsMap[item.productId] ?: continue
            val offer = offersMap[item.offerId] ?: continue
            val supplier = usersMap[offer.supplierId]
            val supplierName = supplier?.businessName ?: supplier?.fullName ?: "Supplier"

            val scheme = try { SchemeType.valueOf(offer.schemeType) } catch (_: Exception) { SchemeType.NONE }
            val pricing = optimizeBasketUseCase.calculateSchemePrice(
                unitPrice = offer.unitPricePtr,
                quantity = item.quantity,
                schemeType = scheme,
                buyQty = offer.schemeBuyQty,
                freeQty = offer.schemeFreeQty,
                discountPercent = offer.schemeDiscountPercent,
                minQtyForDiscount = offer.schemeMinQtyForDiscount,
                gstRatePercent = prod.gstRatePercent
            )

            val schemeDesc = when (scheme) {
                SchemeType.BUY_X_GET_Y_FREE -> "Buy ${offer.schemeBuyQty} Get ${offer.schemeFreeQty} Free"
                SchemeType.PERCENT_DISCOUNT_ABOVE_QTY -> "${offer.schemeDiscountPercent}% Off above ${offer.schemeMinQtyForDiscount} units"
                SchemeType.NONE -> "Standard"
            }

            val perf = analyticsDao.getSupplierPerformance(offer.supplierId)
            val trustScore = if ((perf?.completedOrdersCount ?: 0) >= 5) perf?.overallScore else null

            details.add(
                BasketItemDetail(
                    itemId = item.id,
                    productId = prod.id,
                    productName = prod.name,
                    brand = prod.brand,
                    saltComposition = prod.saltComposition,
                    packSize = prod.packSize,
                    hsnCode = prod.hsnCode,
                    gstRatePercent = prod.gstRatePercent,
                    offerId = offer.id,
                    supplierId = offer.supplierId,
                    supplierName = supplierName,
                    unitPricePtr = offer.unitPricePtr,
                    mrp = offer.mrp,
                    moq = offer.moq,
                    availableQuantity = offer.availableQuantity,
                    quantity = item.quantity,
                    pricing = pricing,
                    schemeDescription = schemeDesc,
                    trustScore = trustScore,
                    hasSufficientTrustData = (perf?.completedOrdersCount ?: 0) >= 5
                )
            )

            val accum = supplierTotals.getOrPut(offer.supplierId) {
                val settings = analyticsDao.getSupplierSettings(offer.supplierId) ?: SupplierSettingsEntity(supplierId = offer.supplierId)
                SupplierAccumulator(
                    supplierId = offer.supplierId,
                    supplierName = supplierName,
                    freeDeliveryThreshold = settings.freeDeliveryThreshold,
                    deliveryCharge = settings.deliveryCharge
                )
            }
            accum.subtotal += pricing.netAmount
            accum.gstAmount += pricing.gstAmount
        }

        var totalDelivery = 0.0
        val subtotalsMap = mutableMapOf<Long, SupplierSubtotal>()
        for ((supId, accum) in supplierTotals) {
            val isFree = accum.subtotal >= accum.freeDeliveryThreshold
            val charge = if (isFree) 0.0 else accum.deliveryCharge
            totalDelivery += charge
            subtotalsMap[supId] = SupplierSubtotal(
                supplierId = supId,
                supplierName = accum.supplierName,
                subtotal = accum.subtotal,
                gstAmount = accum.gstAmount,
                deliveryCharge = charge,
                freeDeliveryThreshold = accum.freeDeliveryThreshold,
                total = accum.subtotal + accum.gstAmount + charge,
                isFreeDeliveryMet = isFree
            )
        }

        val subtotal = details.sumOf { it.pricing.netAmount }
        val totalGst = details.sumOf { it.pricing.gstAmount }
        val grandTotal = subtotal + totalGst + totalDelivery
        val totalSavings = details.sumOf { it.pricing.savingsAmount }

        return BasketWithDetails(
            basketId = basketId,
            items = details,
            subtotal = subtotal,
            totalGst = totalGst,
            totalDeliveryCharges = totalDelivery,
            grandTotal = grandTotal,
            totalSavings = totalSavings,
            supplierSubtotals = subtotalsMap
        )
    }

    private class SupplierAccumulator(
        val supplierId: Long,
        val supplierName: String,
        val freeDeliveryThreshold: Double,
        val deliveryCharge: Double,
        var subtotal: Double = 0.0,
        var gstAmount: Double = 0.0
    )

    private fun emptyBasketWithDetails() = BasketWithDetails(
        basketId = 0,
        items = emptyList(),
        subtotal = 0.0,
        totalGst = 0.0,
        totalDeliveryCharges = 0.0,
        grandTotal = 0.0,
        totalSavings = 0.0,
        supplierSubtotals = emptyMap()
    )

    override suspend fun postApiBasketItems(productId: Long, offerId: Long, quantity: Int): Result<Unit> {
        val buyerId = getApiUsersMe()?.id ?: ensureDefaultStockistUser().id
        var offer = if (offerId > 0) offerDao.getOfferById(offerId) else null
        if (offer == null) {
            val existing = offerDao.getActiveOffersForProduct(productId).firstOrNull()
            if (existing != null) {
                offer = existing
            } else {
                val supplier = ensureDefaultSupplierUser()
                val newId = offerDao.insertOffer(
                    SupplierOfferEntity(
                        productId = productId,
                        supplierId = supplier.id,
                        unitPricePtr = 68.50,
                        mrp = 76.00,
                        moq = 1,
                        availableQuantity = 10000,
                        schemeType = SchemeType.NONE.name,
                        validTillEpochMillis = System.currentTimeMillis() + 365L * 24 * 3600 * 1000
                    )
                )
                offer = offerDao.getOfferById(newId)
            }
        }
        val safeOffer = offer ?: return Result.failure(IllegalArgumentException("Offer not found."))

        var basket = basketDao.getActiveBasketForBuyer(buyerId)
        if (basket == null) {
            val newId = basketDao.insertBasket(BasketEntity(buyerId = buyerId, isSaved = false))
            basket = BasketEntity(id = newId, buyerId = buyerId, isSaved = false)
        }

        val safeQuantity = quantity.coerceAtLeast(1)
        val existingItem = basketDao.getBasketItemByProduct(basket.id, productId)
        if (existingItem != null) {
            val newQty = existingItem.quantity + safeQuantity
            basketDao.updateBasketItem(existingItem.id, newQty, safeOffer.id, safeOffer.supplierId)
        } else {
            basketDao.insertBasketItem(
                BasketItemEntity(
                    basketId = basket.id,
                    productId = productId,
                    offerId = safeOffer.id,
                    supplierId = safeOffer.supplierId,
                    quantity = safeQuantity
                )
            )
        }
        return Result.success(Unit)
    }

    override suspend fun putApiBasketItemQuantity(itemId: Long, quantity: Int): Result<Unit> {
        if (quantity <= 0) {
            basketDao.deleteBasketItem(itemId)
            return Result.success(Unit)
        }
        basketDao.updateBasketItemQuantity(itemId, quantity)
        return Result.success(Unit)
    }

    override suspend fun deleteApiBasketItem(itemId: Long): Result<Unit> {
        basketDao.deleteBasketItem(itemId)
        return Result.success(Unit)
    }

    override suspend fun deleteApiBasketClear(): Result<Unit> {
        val buyer = getApiUsersMe() ?: return Result.success(Unit)
        val basket = basketDao.getActiveBasketForBuyer(buyer.id) ?: return Result.success(Unit)
        basketDao.clearBasket(basket.id)
        return Result.success(Unit)
    }

    override suspend fun postApiBasketSaveNamed(name: String): Result<Long> {
        val buyer = getApiUsersMe() ?: return Result.failure(IllegalStateException("Please login."))
        val activeBasket = basketDao.getActiveBasketForBuyer(buyer.id) ?: return Result.failure(IllegalStateException("Basket is empty."))
        val items = basketDao.getBasketItems(activeBasket.id)
        if (items.isEmpty()) return Result.failure(IllegalStateException("Cannot save an empty basket."))

        val savedBasketId = basketDao.insertBasket(
            BasketEntity(buyerId = buyer.id, name = name.trim(), isSaved = true)
        )
        for (item in items) {
            basketDao.insertBasketItem(
                item.copy(id = 0, basketId = savedBasketId)
            )
        }
        return Result.success(savedBasketId)
    }

    override fun getApiSavedBasketsFlow(): Flow<List<NamedBasketSummary>> = flow {
        val buyer = getApiUsersMe()
        if (buyer == null) {
            emit(emptyList())
        } else {
            basketDao.getSavedBasketsFlow(buyer.id).collect { baskets ->
                val summaries = baskets.map { b ->
                    val items = basketDao.getBasketItems(b.id)
                    NamedBasketSummary(
                        id = b.id,
                        name = b.name ?: "Saved Basket #${b.id}",
                        itemCount = items.size,
                        updatedAtEpochMillis = b.updatedAtEpochMillis
                    )
                }
                emit(summaries)
            }
        }
    }

    override suspend fun postApiBasketLoadSaved(savedBasketId: Long): Result<BasketLoadReport> {
        val buyer = getApiUsersMe() ?: return Result.failure(IllegalStateException("Please login."))
        val savedItems = basketDao.getBasketItems(savedBasketId)
        if (savedItems.isEmpty()) return Result.failure(IllegalStateException("Saved basket has no items."))

        var activeBasket = basketDao.getActiveBasketForBuyer(buyer.id)
        if (activeBasket == null) {
            val newId = basketDao.insertBasket(BasketEntity(buyerId = buyer.id, isSaved = false))
            activeBasket = BasketEntity(id = newId, buyerId = buyer.id, isSaved = false)
        } else {
            basketDao.clearBasket(activeBasket.id)
        }

        val priceWarnings = mutableListOf<String>()
        val unavailableWarnings = mutableListOf<String>()
        var loadedCount = 0

        for (item in savedItems) {
            val offer = offerDao.getOfferById(item.offerId)
            val prod = productDao.getProductById(item.productId)
            val prodName = prod?.name ?: "Product #${item.productId}"

            if (offer == null || !offer.isActive || offer.availableQuantity <= 0) {
                unavailableWarnings.add("$prodName is currently out of stock or offer was deactivated.")
                continue
            }

            val adjustedQty = Math.min(item.quantity, offer.availableQuantity)
            if (adjustedQty < item.quantity) {
                unavailableWarnings.add("$prodName quantity adjusted from ${item.quantity} to $adjustedQty due to limited availability.")
            }

            basketDao.insertBasketItem(
                BasketItemEntity(
                    basketId = activeBasket.id,
                    productId = item.productId,
                    offerId = offer.id,
                    supplierId = offer.supplierId,
                    quantity = adjustedQty
                )
            )
            loadedCount++
        }

        return Result.success(
            BasketLoadReport(
                itemsLoadedCount = loadedCount,
                priceChangeWarnings = priceWarnings,
                unavailableWarnings = unavailableWarnings
            )
        )
    }

    override suspend fun postApiBasketOptimize(preferReliableSuppliers: Boolean): OptimizationResult {
        val basketDetails = getApiBasket()
        if (basketDetails.items.isEmpty()) {
            return OptimizationResult(
                isExact = true,
                currentTotal = 0.0,
                optimizedTotal = 0.0,
                savingsRupees = 0.0,
                savingsPercent = 0.0,
                lines = emptyList(),
                whySuggestions = listOf("Your basket is empty."),
                assumptions = emptyList()
            )
        }

        val allOffers = offerDao.getAllActiveOffers()
        val offersByProduct = allOffers.groupBy { it.productId }
        val suppliers = userDao.getAllUsers().associateBy { it.id }

        val settingsMap = mutableMapOf<Long, SupplierSettingsEntity>()
        val trustScores = mutableMapOf<Long, Int?>()

        for (sup in suppliers.values.filter { it.role != UserRole.STOCKIST.name }) {
            settingsMap[sup.id] = analyticsDao.getSupplierSettings(sup.id) ?: SupplierSettingsEntity(supplierId = sup.id)
            val perf = analyticsDao.getSupplierPerformance(sup.id)
            trustScores[sup.id] = if ((perf?.completedOrdersCount ?: 0) >= 5) perf?.overallScore else null
        }

        return optimizeBasketUseCase.optimize(
            currentItems = basketDetails.items,
            availableOffersByProduct = offersByProduct,
            suppliersById = suppliers,
            settingsBySupplier = settingsMap,
            supplierTrustScores = trustScores,
            preferReliableSuppliers = preferReliableSuppliers
        )
    }

    override suspend fun postApiBasketApplyOptimization(result: OptimizationResult): Result<Unit> {
        val buyer = getApiUsersMe() ?: return Result.failure(IllegalStateException("Please login."))
        val activeBasket = basketDao.getActiveBasketForBuyer(buyer.id) ?: return Result.failure(IllegalStateException("No active basket."))

        db.withTransaction {
            basketDao.clearBasket(activeBasket.id)
            val offers = offerDao.getAllActiveOffers()
            for (line in result.lines) {
                val candidateOffer = offers.find { it.productId == line.productId && it.supplierId == line.suggestedSupplierId }
                    ?: continue
                basketDao.insertBasketItem(
                    BasketItemEntity(
                        basketId = activeBasket.id,
                        productId = line.productId,
                        offerId = candidateOffer.id,
                        supplierId = candidateOffer.supplierId,
                        quantity = line.suggestedQuantity
                    )
                )
            }
        }
        return Result.success(Unit)
    }

    override suspend fun ensureDefaultOrdersSeeded() {
        if (orderDao.getTotalOrderCount() > 0) return

        ensureDefaultCatalogueSeeded()
        val buyer = ensureDefaultStockistUser()
        val supplier = ensureDefaultSupplierUser()
        val now = System.currentTimeMillis()

        // 1. PO-44820: MedPlus Wholesale Ltd. • Paracetamol 500mg (In Transit, Hub 2 Passed 65%)
        val o1 = OrderEntity(
            orderNumber = "PO-44820",
            buyerId = buyer.id,
            supplierId = supplier.id,
            status = OrderStatus.DISPATCHED.name,
            subtotal = 64200.0,
            gstAmount = 7704.0,
            deliveryCharge = 0.0,
            totalAmount = 71904.0,
            deliveryAddress = "MedPlus Warehouse Hub 2, Sector 18, Gurugram, HR",
            notes = "Cold chain monitored delivery",
            isEwayBillGenerated = true,
            ewayBillNumber = "EWB-293847102938",
            vehicleNumber = "DL-01-AB-4200",
            transporterName = "BlueDart",
            dispatchEpochMillis = now - 18 * 3600 * 1000L,
            expectedDeliveryEpochMillis = now + 14 * 3600 * 1000L,
            createdAtEpochMillis = now - 24 * 3600 * 1000L
        )
        val o1Id = orderDao.insertOrder(o1)
        val p1 = productDao.getAllProducts().firstOrNull { it.name.contains("Paracetamol", true) }
        val p1Id = p1?.id ?: 1L
        orderDao.insertOrderItems(
            listOf(
                OrderItemEntity(
                    orderId = o1Id,
                    productId = p1Id,
                    offerId = 1L,
                    productName = p1?.name ?: "Paracetamol 500mg",
                    brand = "Calpol 500",
                    packSize = "Strip of 15 Tabs",
                    hsnCode = "30049060",
                    gstRatePercent = 12.0,
                    orderedQuantity = 1500,
                    unitPricePtr = 42.80,
                    effectivePrice = 42.80,
                    batchNumber = "PCM-2024-B1"
                )
            )
        )
        orderDao.insertStatusHistory(OrderStatusHistoryEntity(orderId = o1Id, status = OrderStatus.PLACED.name, changedAtEpochMillis = now - 24 * 3600 * 1000L, note = "PO created via automated replenishment", changedByUserId = buyer.id))
        orderDao.insertStatusHistory(OrderStatusHistoryEntity(orderId = o1Id, status = OrderStatus.ACCEPTED.name, changedAtEpochMillis = now - 22 * 3600 * 1000L, note = "Order accepted and scheduled for dispatch", changedByUserId = supplier.id))
        orderDao.insertStatusHistory(OrderStatusHistoryEntity(orderId = o1Id, status = OrderStatus.PACKED.name, changedAtEpochMillis = now - 20 * 3600 * 1000L, note = "Packaging and serialisation verified", changedByUserId = supplier.id))
        orderDao.insertStatusHistory(OrderStatusHistoryEntity(orderId = o1Id, status = OrderStatus.DISPATCHED.name, changedAtEpochMillis = now - 18 * 3600 * 1000L, note = "Dispatched via BlueDart (Hub 2 Passed)", changedByUserId = supplier.id))

        // 2. PO-44815: Fortis Healthcare Chain • Amoxicillin 250mg (QA Check, Batch Serialization 44%)
        val o2 = OrderEntity(
            orderNumber = "PO-44815",
            buyerId = buyer.id,
            supplierId = supplier.id,
            status = OrderStatus.PACKED.name,
            subtotal = 125000.0,
            gstAmount = 15000.0,
            deliveryCharge = 0.0,
            totalAmount = 140000.0,
            deliveryAddress = "Fortis Central Depot, Bannerghatta Road, Bengaluru, KA",
            notes = "COA Verification mandatory upon gate check",
            isEwayBillGenerated = true,
            ewayBillNumber = "EWB-109283746152",
            vehicleNumber = "KA-04-E-1980",
            transporterName = "SafeX Logistics",
            dispatchEpochMillis = null,
            expectedDeliveryEpochMillis = now + 48 * 3600 * 1000L,
            createdAtEpochMillis = now - 36 * 3600 * 1000L
        )
        val o2Id = orderDao.insertOrder(o2)
        val p2 = productDao.getAllProducts().firstOrNull { it.name.contains("Amoxicillin", true) }
        val p2Id = p2?.id ?: 2L
        orderDao.insertOrderItems(
            listOf(
                OrderItemEntity(
                    orderId = o2Id,
                    productId = p2Id,
                    offerId = 2L,
                    productName = p2?.name ?: "Amoxicillin 250mg",
                    brand = "Mox 250",
                    packSize = "Strip of 10 Caps",
                    hsnCode = "30041010",
                    gstRatePercent = 12.0,
                    orderedQuantity = 2000,
                    unitPricePtr = 62.50,
                    effectivePrice = 62.50,
                    batchNumber = "AMX-2024-C9"
                )
            )
        )
        orderDao.insertStatusHistory(OrderStatusHistoryEntity(orderId = o2Id, status = OrderStatus.PLACED.name, changedAtEpochMillis = now - 36 * 3600 * 1000L, note = "PO created for hospital central pharmacy", changedByUserId = buyer.id))
        orderDao.insertStatusHistory(OrderStatusHistoryEntity(orderId = o2Id, status = OrderStatus.ACCEPTED.name, changedAtEpochMillis = now - 30 * 3600 * 1000L, note = "Quality inspection and batch serialization in progress", changedByUserId = supplier.id))
        orderDao.insertStatusHistory(OrderStatusHistoryEntity(orderId = o2Id, status = OrderStatus.PACKED.name, changedAtEpochMillis = now - 12 * 3600 * 1000L, note = "Batch serialized, COA verified", changedByUserId = supplier.id))

        // 3. PO-44809: Zydus Distribution • Cefixime 200mg (Staging, Pallet Staging 18%)
        val o3 = OrderEntity(
            orderNumber = "PO-44809",
            buyerId = buyer.id,
            supplierId = supplier.id,
            status = OrderStatus.ACCEPTED.name,
            subtotal = 758000.0,
            gstAmount = 90960.0,
            deliveryCharge = 0.0,
            totalAmount = 848960.0,
            deliveryAddress = "Zydus Regional Staging Hub, GIDC, Ahmedabad, GJ",
            notes = "Bulk container dispatch - Bay 3 Staging",
            isEwayBillGenerated = false,
            createdAtEpochMillis = now - 48 * 3600 * 1000L
        )
        val o3Id = orderDao.insertOrder(o3)
        val p3 = productDao.getAllProducts().firstOrNull { it.name.contains("Cefixime", true) || it.name.contains("Omez", true) }
        val p3Id = p3?.id ?: 3L
        orderDao.insertOrderItems(
            listOf(
                OrderItemEntity(
                    orderId = o3Id,
                    productId = p3Id,
                    offerId = 3L,
                    productName = p3?.name ?: "Cefixime 200mg",
                    brand = "Taxim-O 200",
                    packSize = "Strip of 10 Tabs",
                    hsnCode = "30042090",
                    gstRatePercent = 12.0,
                    orderedQuantity = 7500,
                    unitPricePtr = 101.0,
                    effectivePrice = 101.0,
                    batchNumber = "CFX-2024-X4"
                )
            )
        )
        orderDao.insertStatusHistory(OrderStatusHistoryEntity(orderId = o3Id, status = OrderStatus.PLACED.name, changedAtEpochMillis = now - 48 * 3600 * 1000L, note = "Commercial PO received", changedByUserId = buyer.id))
        orderDao.insertStatusHistory(OrderStatusHistoryEntity(orderId = o3Id, status = OrderStatus.ACCEPTED.name, changedAtEpochMillis = now - 28 * 3600 * 1000L, note = "Pallet staging at Bay 3 initiated", changedByUserId = supplier.id))
    }

    override suspend fun postApiOrders(deliveryAddress: String, notes: String): Result<List<Long>> {
        val buyer = getApiUsersMe() ?: return Result.failure(IllegalStateException("Please login to place order."))
        val basketDetails = getApiBasket()
        if (basketDetails.items.isEmpty()) {
            return Result.failure(IllegalStateException("Cannot place order with empty basket."))
        }

        val orderIds = mutableListOf<Long>()

        db.withTransaction {
            // Group items per supplier to split into separate orders
            val itemsBySupplier = basketDetails.items.groupBy { it.supplierId }

            for ((supplierId, supItems) in itemsBySupplier) {
                val subtotal = supItems.sumOf { it.pricing.netAmount }
                val gstAmount = supItems.sumOf { it.pricing.gstAmount }
                val supplierSubtotalObj = basketDetails.supplierSubtotals[supplierId]
                val deliveryCharge = supplierSubtotalObj?.deliveryCharge ?: 0.0
                val totalAmount = subtotal + gstAmount + deliveryCharge

                val orderNumber = Formatters.generateOrderNumber()

                val orderEntity = OrderEntity(
                    orderNumber = orderNumber,
                    buyerId = buyer.id,
                    supplierId = supplierId,
                    status = OrderStatus.PLACED.name,
                    subtotal = subtotal,
                    gstAmount = gstAmount,
                    deliveryCharge = deliveryCharge,
                    totalAmount = totalAmount,
                    deliveryAddress = deliveryAddress.trim(),
                    notes = notes.trim(),
                    createdAtEpochMillis = System.currentTimeMillis()
                )

                val orderId = orderDao.insertOrder(orderEntity)
                orderIds.add(orderId)

                // Insert line items & reduce available stock
                val orderItems = supItems.map { item ->
                    // Reduce supplier offer stock
                    val offer = offerDao.getOfferById(item.offerId)
                    if (offer != null) {
                        val newQty = Math.max(0, offer.availableQuantity - item.quantity)
                        offerDao.updateOfferQuantity(offer.id, newQty)
                        analyticsDao.insertStockRecord(
                            StockRecordEntity(
                                productId = item.productId,
                                supplierId = supplierId,
                                offerId = offer.id,
                                availableQuantity = newQty,
                                reason = "Order $orderNumber placed"
                            )
                        )
                    }

                    OrderItemEntity(
                        orderId = orderId,
                        productId = item.productId,
                        offerId = item.offerId,
                        productName = item.productName,
                        brand = item.brand,
                        packSize = item.packSize,
                        hsnCode = item.hsnCode,
                        gstRatePercent = item.gstRatePercent,
                        orderedQuantity = item.quantity,
                        freeQuantity = item.pricing.freeQuantity,
                        fulfilledQuantity = item.quantity,
                        unitPricePtr = item.unitPricePtr,
                        effectivePrice = item.pricing.effectiveUnitPrice
                    )
                }
                orderDao.insertOrderItems(orderItems)

                // Insert status history
                orderDao.insertStatusHistory(
                    OrderStatusHistoryEntity(
                        orderId = orderId,
                        status = OrderStatus.PLACED.name,
                        note = "Order placed by ${buyer.businessName}",
                        changedByUserId = buyer.id
                    )
                )

                // Add Credit Ledger Entry (Debit to the buyer)
                val currentBalance = ledgerDao.getLatestBalanceBetweenParties(buyer.id, supplierId) ?: 0.0
                val newBalance = currentBalance + totalAmount
                ledgerDao.insertEntry(
                    CreditLedgerEntryEntity(
                        buyerId = buyer.id,
                        supplierId = supplierId,
                        entryType = "DEBIT",
                        amount = totalAmount,
                        balanceAfter = newBalance,
                        referenceType = "ORDER",
                        referenceId = orderNumber,
                        description = "Purchase Order $orderNumber"
                    )
                )

                // Notify supplier
                notificationDao.insertNotification(
                    NotificationEntity(
                        recipientUserId = supplierId,
                        type = NotificationType.NEW_ORDER_RECEIVED.name,
                        title = "New Purchase Order $orderNumber",
                        message = "Received new order worth ${Formatters.formatInr(totalAmount)} from ${buyer.businessName}",
                        referenceId = orderId.toString()
                    )
                )
            }

            // Clear active basket
            basketDao.clearBasket(basketDetails.basketId)
        }

        return Result.success(orderIds)
    }

    override fun getApiOrdersFlow(): Flow<List<OrderEntity>> = flow {
        val user = getApiUsersMe()
        if (user == null) {
            emit(emptyList())
        } else if (user.role == UserRole.ADMIN) {
            orderDao.getAllOrdersFlow().collect { emit(it) }
        } else if (user.role.isSupplier) {
            orderDao.getOrdersForSupplierFlow(user.id).collect { emit(it) }
        } else {
            orderDao.getOrdersForBuyerFlow(user.id).collect { emit(it) }
        }
    }

    override suspend fun getApiOrders(): List<OrderEntity> {
        val user = getApiUsersMe() ?: return emptyList()
        return when {
            user.role == UserRole.ADMIN -> orderDao.getAllOrders()
            user.role.isSupplier -> orderDao.getOrdersForSupplier(user.id)
            else -> orderDao.getOrdersForBuyer(user.id)
        }
    }

    override fun getApiSupplierOrdersFlow(supplierId: Long): Flow<List<OrderEntity>> =
        orderDao.getOrdersForSupplierFlow(supplierId)

    override suspend fun getApiOrderById(id: Long): OrderDetailsWithHistory? {
        val order = orderDao.getOrderById(id) ?: return null
        val buyer = userDao.getUserById(order.buyerId)
        val supplier = userDao.getUserById(order.supplierId)
        val items = orderDao.getOrderItems(order.id)
        val history = orderDao.getOrderStatusHistory(order.id)
        val pod = analyticsDao.getProofOfDelivery(order.id)
        return OrderDetailsWithHistory(
            order = order,
            buyer = buyer,
            supplier = supplier,
            items = items,
            history = history,
            pod = pod
        )
    }

    override fun getApiOrderByIdFlow(id: Long): Flow<OrderDetailsWithHistory?> = flow {
        orderDao.getOrderByIdFlow(id).collect { order ->
            if (order == null) emit(null)
            else {
                val buyer = userDao.getUserById(order.buyerId)
                val supplier = userDao.getUserById(order.supplierId)
                val items = orderDao.getOrderItems(order.id)
                val history = orderDao.getOrderStatusHistory(order.id)
                val pod = analyticsDao.getProofOfDelivery(order.id)
                emit(OrderDetailsWithHistory(order, buyer, supplier, items, history, pod))
            }
        }
    }

    override suspend fun patchApiOrderStatus(
        orderId: Long,
        newStatus: String,
        note: String,
        dispatchEpochMillis: Long?,
        expectedDeliveryEpochMillis: Long?,
        ewayBillNumber: String?,
        vehicleNumber: String?,
        transporterName: String?
    ): Result<Unit> {
        val user = getApiUsersMe() ?: return Result.failure(IllegalStateException("Please login."))
        val order = orderDao.getOrderById(orderId) ?: return Result.failure(IllegalArgumentException("Order not found."))

        db.withTransaction {
            val updated = order.copy(
                status = newStatus,
                dispatchEpochMillis = dispatchEpochMillis ?: order.dispatchEpochMillis,
                expectedDeliveryEpochMillis = expectedDeliveryEpochMillis ?: order.expectedDeliveryEpochMillis,
                deliveredEpochMillis = if (newStatus == OrderStatus.DELIVERED.name) System.currentTimeMillis() else order.deliveredEpochMillis,
                isEwayBillGenerated = !ewayBillNumber.isNullOrBlank() || order.isEwayBillGenerated,
                ewayBillNumber = ewayBillNumber ?: order.ewayBillNumber,
                vehicleNumber = vehicleNumber ?: order.vehicleNumber,
                transporterName = transporterName ?: order.transporterName
            )
            orderDao.updateOrder(updated)

            orderDao.insertStatusHistory(
                OrderStatusHistoryEntity(
                    orderId = orderId,
                    status = newStatus,
                    note = note.ifBlank { "Status changed to $newStatus" },
                    changedByUserId = user.id
                )
            )

            // If DELIVERED, auto-create batch records for inventory tracking
            if (newStatus == OrderStatus.DELIVERED.name) {
                val items = orderDao.getOrderItems(orderId)
                for (item in items) {
                    val batchNum = item.batchNumber ?: "BAT-${order.orderNumber.takeLast(4)}"
                    val expDate = item.expiryDateEpochMillis ?: (System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000))
                    val product = productDao.getProductById(item.productId)
                    batchDao.insertBatch(
                        BatchEntity(
                            productId = item.productId,
                            supplierId = order.supplierId,
                            batchNumber = batchNum,
                            mfgDateEpochMillis = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000),
                            expiryDateEpochMillis = expDate,
                            initialQuantity = item.fulfilledQuantity + item.freeQuantity,
                            remainingQuantity = item.fulfilledQuantity + item.freeQuantity,
                            storageCondition = product?.storageCondition ?: StorageCondition.ROOM_TEMPERATURE.name,
                            mrp = item.unitPricePtr * 1.25,
                            ptr = item.unitPricePtr
                        )
                    )
                }

                // Recalculate supplier trust score
                recalculateSupplierTrustScore(order.supplierId)
            }

            // Notify buyer
            notificationDao.insertNotification(
                NotificationEntity(
                    recipientUserId = order.buyerId,
                    type = NotificationType.ORDER_STATUS_CHANGED.name,
                    title = "Order ${order.orderNumber} is now $newStatus",
                    message = note.ifBlank { "Your order has been updated to $newStatus." },
                    referenceId = orderId.toString()
                )
            )
        }

        return Result.success(Unit)
    }

    override suspend fun patchApiOrderItemFulfillment(
        orderItemId: Long,
        fulfilledQty: Int,
        batchNumber: String?,
        expiryDateEpochMillis: Long?
    ): Result<Unit> {
        val item = orderDao.getOrderItemById(orderItemId) ?: return Result.failure(IllegalArgumentException("Item not found."))
        orderDao.updateOrderItem(
            item.copy(
                fulfilledQuantity = fulfilledQty,
                batchNumber = batchNumber?.trim(),
                expiryDateEpochMillis = expiryDateEpochMillis
            )
        )
        return Result.success(Unit)
    }

    override suspend fun postApiOrderProofOfDelivery(
        orderId: Long,
        receivedByName: String,
        notes: String,
        photoUri: String?,
        shortQuantity: Int,
        damagedQuantity: Int
    ): Result<Unit> {
        val order = orderDao.getOrderById(orderId) ?: return Result.failure(IllegalArgumentException("Order not found."))
        analyticsDao.insertProofOfDelivery(
            ProofOfDeliveryEntity(
                orderId = orderId,
                receivedByName = receivedByName.trim(),
                notes = notes.trim(),
                photoUri = photoUri,
                shortQuantity = shortQuantity,
                damagedQuantity = damagedQuantity
            )
        )
        // Mark order as DELIVERED if not already
        patchApiOrderStatus(orderId, OrderStatus.DELIVERED.name, "Proof of delivery recorded by $receivedByName")
        return Result.success(Unit)
    }

    override suspend fun postApiOrderReorder(pastOrderId: Long): Result<Unit> {
        val buyer = getApiUsersMe() ?: return Result.failure(IllegalStateException("Please login."))
        val pastItems = orderDao.getOrderItems(pastOrderId)
        if (pastItems.isEmpty()) return Result.failure(IllegalArgumentException("Order has no line items."))

        var basket = basketDao.getActiveBasketForBuyer(buyer.id)
        if (basket == null) {
            val newId = basketDao.insertBasket(BasketEntity(buyerId = buyer.id, isSaved = false))
            basket = BasketEntity(id = newId, buyerId = buyer.id, isSaved = false)
        }

        for (item in pastItems) {
            // Find active offer
            val offers = offerDao.getActiveOffersForProduct(item.productId)
            val bestOffer = offers.find { it.id == item.offerId } ?: offers.minByOrNull { it.unitPricePtr }
            if (bestOffer != null) {
                postApiBasketItems(item.productId, bestOffer.id, item.orderedQuantity)
            }
        }
        return Result.success(Unit)
    }

    override fun getApiBatchesFlow(): Flow<List<BatchWithProduct>> {
        return combine(
            batchDao.getAllBatchesFlow(),
            productDao.getAllActiveProductsFlow()
        ) { batches, products ->
            val pMap = products.associateBy { it.id }
            batches.map { b ->
                val p = pMap[b.productId]
                BatchWithProduct(
                    batch = b,
                    productName = p?.name ?: "Product #${b.productId}",
                    brand = p?.brand ?: "",
                    saltComposition = p?.saltComposition ?: "",
                    daysUntilExpiry = Formatters.daysUntil(b.expiryDateEpochMillis)
                )
            }
        }
    }

    override suspend fun getApiBatches(): List<BatchWithProduct> {
        val batches = batchDao.getAllBatches()
        val products = productDao.getAllProducts().associateBy { it.id }
        return batches.map { b ->
            val p = products[b.productId]
            BatchWithProduct(
                batch = b,
                productName = p?.name ?: "Product #${b.productId}",
                brand = p?.brand ?: "",
                saltComposition = p?.saltComposition ?: "",
                daysUntilExpiry = Formatters.daysUntil(b.expiryDateEpochMillis)
            )
        }
    }

    override suspend fun postApiBatches(batch: BatchEntity): Result<Long> {
        val id = batchDao.insertBatch(batch)
        return Result.success(id)
    }

    override fun getRealTimeInventoryFlow(): Flow<List<InventoryStockItem>> {
        return combine(
            batchDao.getAllBatchesFlow(),
            productDao.getAllProductsFlow(),
            recallDao.getAllRecallsFlow()
        ) { batches, products, recalls ->
            val pMap = products.associateBy { it.id }
            val activeRecalls = recalls.filter { it.isActive }

            val batchItems = batches.map { b ->
                val p = pMap[b.productId]
                val daysUntilExpiry = Formatters.daysUntil(b.expiryDateEpochMillis)
                val expiryStatus = when {
                    daysUntilExpiry < 0 -> ExpiryRiskStatus.EXPIRED
                    daysUntilExpiry <= 30 -> ExpiryRiskStatus.EXPIRING_CRITICAL
                    daysUntilExpiry <= 90 -> ExpiryRiskStatus.EXPIRING_WARNING
                    else -> ExpiryRiskStatus.HEALTHY
                }

                val reorderThreshold = 25
                val stockStatus = when {
                    b.remainingQuantity <= 0 -> StockLevelStatus.OUT_OF_STOCK
                    b.remainingQuantity <= 10 -> StockLevelStatus.CRITICAL_LOW
                    b.remainingQuantity <= reorderThreshold -> StockLevelStatus.LOW_STOCK
                    else -> StockLevelStatus.HEALTHY
                }

                val matchingRecall = activeRecalls.firstOrNull { r ->
                    r.productId == b.productId && (r.batchNumbersCsv.isBlank() || r.batchNumbersCsv.contains(b.batchNumber))
                }
                val isRecalled = matchingRecall != null

                val flags = mutableListOf<String>()
                if (b.remainingQuantity <= 0) {
                    flags.add("Out of Stock (0 units)")
                } else if (b.remainingQuantity <= 10) {
                    flags.add("Critical Low Stock (${b.remainingQuantity} units left)")
                } else if (b.remainingQuantity <= reorderThreshold) {
                    flags.add("Below Reorder Level (≤$reorderThreshold units)")
                }

                if (daysUntilExpiry < 0) {
                    flags.add("Expired (${-daysUntilExpiry}d ago) - Quarantine")
                } else if (daysUntilExpiry <= 30) {
                    flags.add("Expiring soon ($daysUntilExpiry days left)")
                }

                if (isRecalled) {
                    flags.add("Active Recall: ${matchingRecall?.reason ?: "Regulatory Hold"}")
                }

                val storage = try {
                    StorageCondition.valueOf(b.storageCondition)
                } catch (_: Exception) {
                    try {
                        p?.storageCondition?.let { StorageCondition.valueOf(it) } ?: StorageCondition.ROOM_TEMPERATURE
                    } catch (_: Exception) {
                        StorageCondition.ROOM_TEMPERATURE
                    }
                }

                if (storage == StorageCondition.REFRIGERATED_2_8 || storage == StorageCondition.FROZEN) {
                    flags.add("Cold Chain Monitored")
                }

                val unitPrice = if (b.ptr > 0) b.ptr else (b.mrp * 0.78)
                val totalVal = b.remainingQuantity * unitPrice

                InventoryStockItem(
                    id = b.id,
                    productId = b.productId,
                    productName = p?.name ?: "Product #${b.productId}",
                    brand = p?.brand ?: "Generic",
                    saltComposition = p?.saltComposition ?: "",
                    dosageForm = p?.dosageForm ?: "Tablet",
                    therapeuticCategory = p?.therapeuticCategory ?: "General",
                    batchNumber = b.batchNumber,
                    remainingQuantity = b.remainingQuantity,
                    initialQuantity = b.initialQuantity,
                    reorderThreshold = reorderThreshold,
                    stockStatus = stockStatus,
                    expiryDateEpochMillis = b.expiryDateEpochMillis,
                    daysUntilExpiry = daysUntilExpiry,
                    expiryStatus = expiryStatus,
                    storageCondition = storage,
                    mrp = b.mrp,
                    ptr = unitPrice,
                    totalValuation = totalVal,
                    isRecalled = isRecalled,
                    recallReason = matchingRecall?.reason,
                    criticalFlags = flags
                )
            }

            // Products with 0 batches registered -> Flag as Out of Stock
            val productIdsWithBatches = batches.map { it.productId }.toSet()
            val missingProductItems = products.filter { !productIdsWithBatches.contains(it.id) }.map { p ->
                val storage = try {
                    StorageCondition.valueOf(p.storageCondition)
                } catch (_: Exception) {
                    StorageCondition.ROOM_TEMPERATURE
                }
                InventoryStockItem(
                    id = -p.id,
                    productId = p.id,
                    productName = p.name,
                    brand = p.brand,
                    saltComposition = p.saltComposition,
                    dosageForm = p.dosageForm,
                    therapeuticCategory = p.therapeuticCategory,
                    batchNumber = "NO-LOT",
                    remainingQuantity = 0,
                    initialQuantity = 0,
                    reorderThreshold = 25,
                    stockStatus = StockLevelStatus.OUT_OF_STOCK,
                    expiryDateEpochMillis = System.currentTimeMillis() + 86400000L * 365,
                    daysUntilExpiry = 365,
                    expiryStatus = ExpiryRiskStatus.HEALTHY,
                    storageCondition = storage,
                    mrp = 0.0,
                    ptr = 0.0,
                    totalValuation = 0.0,
                    isRecalled = false,
                    recallReason = null,
                    criticalFlags = listOf("No Stock Recorded / 0 Units Available")
                )
            }

            (batchItems + missingProductItems).sortedWith(
                compareByDescending<InventoryStockItem> { it.isCritical }
                    .thenBy { it.remainingQuantity }
                    .thenBy { it.daysUntilExpiry }
            )
        }
    }

    override fun getInventorySummaryFlow(): Flow<InventoryDashboardSummary> {
        return getRealTimeInventoryFlow().map { items ->
            InventoryDashboardSummary(
                totalStockUnits = items.sumOf { it.remainingQuantity },
                totalInventoryValue = items.sumOf { it.totalValuation },
                totalSkusCount = items.map { it.productId }.distinct().size,
                criticalItemsCount = items.count { it.isCritical },
                outOfStockCount = items.count { it.stockStatus == StockLevelStatus.OUT_OF_STOCK },
                lowStockCount = items.count { it.stockStatus == StockLevelStatus.LOW_STOCK || it.stockStatus == StockLevelStatus.CRITICAL_LOW },
                nearExpiryCount = items.count { it.expiryStatus == ExpiryRiskStatus.EXPIRED || it.expiryStatus == ExpiryRiskStatus.EXPIRING_CRITICAL },
                recalledCount = items.count { it.isRecalled },
                healthyCount = items.count { !it.isCritical }
            )
        }
    }

    override suspend fun adjustBatchStock(batchId: Long, newQuantity: Int, reason: String): Result<Unit> {
        return try {
            val safeQty = newQuantity.coerceAtLeast(0)
            if (batchId > 0) {
                batchDao.updateBatchQuantity(batchId, safeQty)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun quickRestockToBasket(productId: Long, quantity: Int): Result<Unit> {
        return try {
            val offers = offerDao.getActiveOffersForProduct(productId)
            val bestOffer = offers.minByOrNull { it.unitPricePtr }
                ?: return Result.failure(Exception("No active supplier offer available for quick restock"))
            postApiBasketItems(productId = productId, offerId = bestOffer.id, quantity = quantity.coerceAtLeast(bestOffer.moq))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getApiNotificationsFlow(): Flow<List<NotificationEntity>> = flow {
        val user = getApiUsersMe()
        if (user == null) {
            emit(emptyList())
        } else {
            notificationDao.getNotificationsForUserFlow(user.id).collect { emit(it) }
        }
    }

    override fun getApiNotificationsUnreadCountFlow(): Flow<Int> = flow {
        val user = getApiUsersMe()
        if (user == null) {
            emit(0)
        } else {
            notificationDao.getUnreadNotificationCountFlow(user.id).collect { emit(it) }
        }
    }

    override suspend fun patchApiNotificationRead(id: Long): Result<Unit> {
        notificationDao.markAsRead(id)
        return Result.success(Unit)
    }

    override suspend fun patchApiNotificationsReadAll(): Result<Unit> {
        val user = getApiUsersMe() ?: return Result.success(Unit)
        notificationDao.markAllAsRead(user.id)
        return Result.success(Unit)
    }

    override suspend fun getApiSuppliersTrustScore(supplierId: Long): TrustScoreBreakdown {
        val sup = userDao.getUserById(supplierId)
        val name = sup?.businessName ?: sup?.fullName ?: "Supplier"
        val orders = orderDao.getOrdersForSupplier(supplierId)
        val allItems = mutableListOf<OrderItemEntity>()
        val pods = mutableListOf<ProofOfDeliveryEntity>()
        for (ord in orders) {
            allItems.addAll(orderDao.getOrderItems(ord.id))
            analyticsDao.getProofOfDelivery(ord.id)?.let { pods.add(it) }
        }
        val returns = returnDao.getAllReturnsFlow().first().filter { it.supplierId == supplierId }
        val breakdown = calculateTrustScoreUseCase.calculate(supplierId, name, orders, allItems, pods, returns)

        // Persist
        analyticsDao.insertOrUpdatePerformance(
            SupplierPerformanceEntity(
                supplierId = supplierId,
                overallScore = breakdown.overallScore,
                completedOrdersCount = breakdown.completedOrdersCount,
                fulfilmentRate = breakdown.fulfilmentRate,
                onTimeRate = breakdown.onTimeDeliveryRate,
                cancellationRate = breakdown.cancellationRate,
                discrepancyRate = breakdown.discrepancyRate,
                returnRate = breakdown.returnRate
            )
        )
        return breakdown
    }

    override fun getApiSuppliersTrustScoreFlow(supplierId: Long): Flow<TrustScoreBreakdown?> = flow {
        val breakdown = getApiSuppliersTrustScore(supplierId)
        emit(breakdown)
    }

    override fun getApiSupplierTrustScoreComparisonFlow(): Flow<List<TrustScoreBreakdown>> = flow {
        val users = userDao.getAllUsers().filter { it.role != UserRole.STOCKIST.name && it.role != UserRole.ADMIN.name }
        val list = users.map { sup ->
            getApiSuppliersTrustScore(sup.id)
        }
        emit(list)
    }

    private suspend fun recalculateSupplierTrustScore(supplierId: Long) {
        try {
            getApiSuppliersTrustScore(supplierId)
        } catch (_: Exception) {}
    }

    override fun getApiShortageAlertsFlow(): Flow<List<ShortageEvaluationResult>> = flow {
        emit(evaluateAllShortageRisks())
    }

    override suspend fun evaluateAllShortageRisks(): List<ShortageEvaluationResult> {
        val products = productDao.getAllProducts().filter { it.isActive }
        val results = mutableListOf<ShortageEvaluationResult>()
        val allOffers = offerDao.getAllActiveOffers()
        val allSuppliers = userDao.getAllUsers().associateBy { it.id }

        val settingsMap = allSuppliers.keys.associateWith {
            analyticsDao.getSupplierSettings(it) ?: SupplierSettingsEntity(supplierId = it)
        }

        for (product in products) {
            val offers = allOffers.filter { it.productId == product.id }
            val snapshots = analyticsDao.getRecentStockRecords(product.id, 10)
            val result = evaluateShortageRiskUseCase.evaluate(product, offers, snapshots, settingsMap)
            results.add(result)

            analyticsDao.insertOrUpdateShortageRisk(
                ShortageRiskEntity(
                    productId = product.id,
                    riskLevel = result.riskLevel.name,
                    triggeredReasonsCsv = result.triggeredReasons.joinToString(";;"),
                    totalAvailableQuantity = result.totalAvailableQuantity,
                    availableSuppliersCount = result.availableSuppliersCount,
                    avgLeadTimeDays = result.avgLeadTimeDays,
                    evaluatedAtEpochMillis = result.lastUpdatedEpochMillis
                )
            )
        }
        return results
    }

    private suspend fun evaluateProductShortageRiskInternal(productId: Long) {
        val product = productDao.getProductById(productId) ?: return
        val offers = offerDao.getActiveOffersForProduct(productId)
        val snapshots = analyticsDao.getRecentStockRecords(productId, 10)
        val settingsMap = offers.map { it.supplierId }.distinct().associateWith {
            analyticsDao.getSupplierSettings(it) ?: SupplierSettingsEntity(supplierId = it)
        }
        val result = evaluateShortageRiskUseCase.evaluate(product, offers, snapshots, settingsMap)
        analyticsDao.insertOrUpdateShortageRisk(
            ShortageRiskEntity(
                productId = product.id,
                riskLevel = result.riskLevel.name,
                triggeredReasonsCsv = result.triggeredReasons.joinToString(";;"),
                totalAvailableQuantity = result.totalAvailableQuantity,
                availableSuppliersCount = result.availableSuppliersCount,
                avgLeadTimeDays = result.avgLeadTimeDays,
                evaluatedAtEpochMillis = result.lastUpdatedEpochMillis
            )
        )
    }

    override fun getApiRecallsFlow(): Flow<List<RecallWithAffectedStock>> {
        return combine(
            recallDao.getActiveRecallsFlow(),
            batchDao.getAllBatchesFlow()
        ) { recalls, batches ->
            recalls.map { recall ->
                val recalledNumbers = recall.batchNumbersCsv.split(",").map { it.trim() }
                val affected = batches.filter { it.productId == recall.productId && recalledNumbers.contains(it.batchNumber) }
                val units = affected.sumOf { it.remainingQuantity }
                RecallWithAffectedStock(recall, affected, units)
            }
        }
    }

    override suspend fun postApiRecalls(
        productId: Long,
        batchNumbersCsv: String,
        reason: String,
        severity: String
    ): Result<Long> {
        val current = getApiUsersMe() ?: return Result.failure(IllegalStateException("Please login."))
        val product = productDao.getProductById(productId) ?: return Result.failure(IllegalArgumentException("Product not found."))

        val recallId = recallDao.insertRecall(
            RecallEntity(
                productId = productId,
                productName = product.name,
                batchNumbersCsv = batchNumbersCsv.trim(),
                reason = reason.trim(),
                severity = severity,
                createdBySupplierId = current.id
            )
        )

        // Find buyers holding affected batches and notify them
        val targetBatches = batchNumbersCsv.split(",").map { it.trim() }
        val affectedBatches = batchDao.getBatchesByNumbers(targetBatches)
        val affectedSupplierIds = affectedBatches.map { it.supplierId }.distinct()

        val allBuyers = userDao.getAllUsers().filter { it.role == UserRole.STOCKIST.name }
        for (buyer in allBuyers) {
            notificationDao.insertNotification(
                NotificationEntity(
                    recipientUserId = buyer.id,
                    type = NotificationType.RECALL_ALERT.name,
                    title = "URGENT BATCH RECALL: ${product.name}",
                    message = "Recall initiated for batches ($batchNumbersCsv). Reason: $reason. Check stock immediately.",
                    referenceId = recallId.toString()
                )
            )
        }

        return Result.success(recallId)
    }

    override fun getApiReturnsFlow(): Flow<List<ReturnWithDetails>> {
        return combine(
            returnDao.getAllReturnsFlow(),
            orderDao.getAllOrdersFlow(),
            productDao.getAllActiveProductsFlow(),
            returnDao.getAllCreditNotesFlow()
        ) { returns, orders, prods, cns ->
            val ordMap = orders.associateBy { it.id }
            val prodMap = prods.associateBy { it.id }
            val cnMap = cns.associateBy { it.returnId }

            returns.map { ret ->
                val ord = ordMap[ret.orderId]
                val prod = prodMap[ret.productId]
                ReturnWithDetails(
                    returnRecord = ret,
                    productName = prod?.name ?: "Product #${ret.productId}",
                    orderNumber = ord?.orderNumber ?: "ORD-${ret.orderId}",
                    creditNote = cnMap[ret.id]
                )
            }
        }
    }

    override suspend fun postApiReturns(
        orderId: Long,
        orderItemId: Long,
        quantity: Int,
        reason: ReturnReason,
        batchNumber: String
    ): Result<Long> {
        val buyer = getApiUsersMe() ?: return Result.failure(IllegalStateException("Please login."))
        val order = orderDao.getOrderById(orderId) ?: return Result.failure(IllegalArgumentException("Order not found."))
        val item = orderDao.getOrderItemById(orderItemId) ?: return Result.failure(IllegalArgumentException("Item not found."))

        val returnId = returnDao.insertReturn(
            ReturnEntity(
                orderId = orderId,
                orderItemId = orderItemId,
                buyerId = buyer.id,
                supplierId = order.supplierId,
                productId = item.productId,
                batchNumber = batchNumber.trim(),
                quantity = quantity,
                reason = reason.name,
                status = ReturnStatus.PENDING.name
            )
        )

        // Notify supplier
        notificationDao.insertNotification(
            NotificationEntity(
                recipientUserId = order.supplierId,
                type = NotificationType.RETURN_UPDATE.name,
                title = "Return Raised for Order ${order.orderNumber}",
                message = "${buyer.businessName} requested return of $quantity units (${reason.displayName}).",
                referenceId = returnId.toString()
            )
        )

        return Result.success(returnId)
    }

    override suspend fun patchApiReturnStatus(returnId: Long, isApproved: Boolean, decisionNotes: String): Result<Unit> {
        val ret = returnDao.getReturnById(returnId) ?: return Result.failure(IllegalArgumentException("Return not found."))
        val item = orderDao.getOrderItemById(ret.orderItemId)

        db.withTransaction {
            val newStatus = if (isApproved) ReturnStatus.APPROVED.name else ReturnStatus.REJECTED.name
            returnDao.updateReturn(
                ret.copy(
                    status = newStatus,
                    decisionAtEpochMillis = System.currentTimeMillis(),
                    decisionNotes = decisionNotes.trim()
                )
            )

            if (isApproved && item != null) {
                // Generate Credit Note
                val cnNumber = Formatters.generateCreditNoteNumber()
                val unitPrice = item.effectivePrice
                val amount = unitPrice * ret.quantity
                val gst = (amount * item.gstRatePercent) / 100.0
                val total = amount + gst

                returnDao.insertCreditNote(
                    CreditNoteEntity(
                        creditNoteNumber = cnNumber,
                        returnId = returnId,
                        orderId = ret.orderId,
                        buyerId = ret.buyerId,
                        supplierId = ret.supplierId,
                        amount = amount,
                        gstAmount = gst,
                        totalAmount = total
                    )
                )

                // Add credit entry to ledger
                val currentBal = ledgerDao.getLatestBalanceBetweenParties(ret.buyerId, ret.supplierId) ?: 0.0
                val newBal = currentBal - total
                ledgerDao.insertEntry(
                    CreditLedgerEntryEntity(
                        buyerId = ret.buyerId,
                        supplierId = ret.supplierId,
                        entryType = "CREDIT",
                        amount = total,
                        balanceAfter = newBal,
                        referenceType = "RETURN_CREDIT_NOTE",
                        referenceId = cnNumber,
                        description = "Return Credit Note $cnNumber ($decisionNotes)"
                    )
                )
            }

            // Notify buyer
            notificationDao.insertNotification(
                NotificationEntity(
                    recipientUserId = ret.buyerId,
                    type = NotificationType.RETURN_UPDATE.name,
                    title = if (isApproved) "Return Approved" else "Return Rejected",
                    message = if (isApproved) "Credit Note issued. Notes: $decisionNotes" else "Reason: $decisionNotes",
                    referenceId = returnId.toString()
                )
            )

            // Recalculate supplier trust score
            recalculateSupplierTrustScore(ret.supplierId)
        }

        return Result.success(Unit)
    }

    override fun getApiCreditNotesFlow(): Flow<List<CreditNoteEntity>> = returnDao.getAllCreditNotesFlow()

    override fun getApiLedgerEntriesFlow(counterpartyUserId: Long?): Flow<List<CreditLedgerEntryEntity>> = flow {
        val user = getApiUsersMe()
        if (user == null) {
            emit(emptyList())
        } else if (counterpartyUserId != null) {
            if (user.role.isSupplier) {
                ledgerDao.getLedgerBetweenPartiesFlow(counterpartyUserId, user.id).collect { emit(it) }
            } else {
                ledgerDao.getLedgerBetweenPartiesFlow(user.id, counterpartyUserId).collect { emit(it) }
            }
        } else {
            if (user.role == UserRole.ADMIN) {
                ledgerDao.getAllLedgerEntriesFlow().collect { emit(it) }
            } else if (user.role.isSupplier) {
                ledgerDao.getLedgerEntriesForSupplierFlow(user.id).collect { emit(it) }
            } else {
                ledgerDao.getLedgerEntriesForBuyerFlow(user.id).collect { emit(it) }
            }
        }
    }

    override suspend fun getApiLedgerBalance(counterpartyUserId: Long): Double {
        val user = getApiUsersMe() ?: return 0.0
        return if (user.role.isSupplier) {
            ledgerDao.getLatestBalanceBetweenParties(counterpartyUserId, user.id) ?: 0.0
        } else {
            ledgerDao.getLatestBalanceBetweenParties(user.id, counterpartyUserId) ?: 0.0
        }
    }

    override suspend fun postApiLedgerPayment(buyerId: Long, amount: Double, notes: String): Result<Unit> {
        val supplier = getApiUsersMe() ?: return Result.failure(IllegalStateException("Please login."))
        if (!supplier.role.isSupplier && supplier.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("Only suppliers can record payments received."))
        }

        val currentBal = ledgerDao.getLatestBalanceBetweenParties(buyerId, supplier.id) ?: 0.0
        val newBal = currentBal - amount
        val refId = "PAY-${System.currentTimeMillis().toString().takeLast(6)}"

        ledgerDao.insertEntry(
            CreditLedgerEntryEntity(
                buyerId = buyerId,
                supplierId = supplier.id,
                entryType = "CREDIT",
                amount = amount,
                balanceAfter = newBal,
                referenceType = "PAYMENT",
                referenceId = refId,
                description = if (notes.isNotBlank()) "Payment Received: $notes" else "Payment Received ($refId)"
            )
        )

        // Notify buyer
        notificationDao.insertNotification(
            NotificationEntity(
                recipientUserId = buyerId,
                type = NotificationType.ORDER_STATUS_CHANGED.name,
                title = "Payment Acknowledged",
                message = "${supplier.businessName} recorded payment of ${Formatters.formatInr(amount)}.",
                referenceId = refId
            )
        )

        return Result.success(Unit)
    }

    override suspend fun getApiSupplierSettings(supplierId: Long): SupplierSettingsEntity {
        return analyticsDao.getSupplierSettings(supplierId) ?: SupplierSettingsEntity(supplierId = supplierId)
    }

    override suspend fun putApiSupplierSettings(settings: SupplierSettingsEntity): Result<Unit> {
        analyticsDao.insertOrUpdateSupplierSettings(settings)
        return Result.success(Unit)
    }

    override suspend fun getDashboardStats(): DashboardKpis {
        val user = getApiUsersMe() ?: return DashboardKpis(role = UserRole.STOCKIST)
        val now = System.currentTimeMillis()

        return when (user.role) {
            UserRole.STOCKIST -> {
                val openOrders = orderDao.getOpenOrderCountForBuyer(user.id)
                val basket = basketDao.getActiveBasketForBuyer(user.id)
                val basketItemsCount = if (basket != null) basketDao.getBasketItems(basket.id).size else 0
                val nearExpiryCount = batchDao.getNearExpiryBatchCount(now + (90L * 24 * 60 * 60 * 1000))
                val shortageAlertsCount = analyticsDao.getHighRiskShortageCount()
                val ledgerBal = ledgerDao.getTotalOutstandingBalanceForBuyer(user.id) ?: 0.0
                DashboardKpis(
                    role = UserRole.STOCKIST,
                    openOrdersCount = openOrders,
                    basketItemsCount = basketItemsCount,
                    nearExpiryBatchesCount = nearExpiryCount,
                    shortageAlertsCount = shortageAlertsCount,
                    ledgerBalance = ledgerBal
                )
            }
            UserRole.MANUFACTURER, UserRole.PHARMA_COMPANY -> {
                val activeOffers = offerDao.getActiveOfferCountForSupplier(user.id)
                val pendingOrders = orderDao.getPendingOrderCountForSupplier(user.id)
                val supplierOffers = offerDao.getOffersBySupplier(user.id)
                val lowStock = supplierOffers.count { it.availableQuantity in 1..50 }
                val perf = analyticsDao.getSupplierPerformance(user.id)
                val trustScore = if ((perf?.completedOrdersCount ?: 0) >= 5) perf?.overallScore else null
                DashboardKpis(
                    role = user.role,
                    activeOffersCount = activeOffers,
                    pendingOrdersCount = pendingOrders,
                    lowStockCount = lowStock,
                    trustScore = trustScore
                )
            }
            UserRole.ADMIN -> {
                val totalUsers = userDao.getUserCount()
                val mfgCount = userDao.getUserCountByRole(UserRole.MANUFACTURER.name)
                val pharmaCount = userDao.getUserCountByRole(UserRole.PHARMA_COMPANY.name)
                val stockistCount = userDao.getUserCountByRole(UserRole.STOCKIST.name)
                val totalProducts = productDao.getActiveProductCount()
                val totalOrders = orderDao.getTotalOrderCount()
                val activeRecalls = recallDao.getActiveRecallCount()
                DashboardKpis(
                    role = UserRole.ADMIN,
                    totalUsersCount = totalUsers,
                    manufacturersCount = mfgCount,
                    pharmaCompaniesCount = pharmaCount,
                    stockistsCount = stockistCount,
                    totalProductsCount = totalProducts,
                    totalOrdersCount = totalOrders,
                    activeRecallsCount = activeRecalls
                )
            }
        }
    }

    private fun mapToProductDetail(
        p: ProductEntity,
        minPrice: Double?,
        offerCount: Int,
        minMrp: Double? = null,
        marginPercent: Int? = null,
        bestOfferId: Long? = null
    ): ProductDetail {
        val form = try { DosageForm.valueOf(p.dosageForm) } catch (_: Exception) { DosageForm.TABLET }
        val storage = try { StorageCondition.valueOf(p.storageCondition) } catch (_: Exception) { StorageCondition.ROOM_TEMPERATURE }
        return ProductDetail(
            id = p.id,
            name = p.name,
            brand = p.brand,
            saltComposition = p.saltComposition,
            strength = p.strength,
            dosageForm = form,
            therapeuticCategory = p.therapeuticCategory,
            manufacturerName = p.manufacturerName,
            packSize = p.packSize,
            hsnCode = p.hsnCode,
            gstRatePercent = p.gstRatePercent,
            storageCondition = storage,
            isRx = p.isRx,
            isActive = p.isActive,
            createdBy = p.createdBy,
            minOfferPrice = minPrice,
            activeOfferCount = offerCount,
            minMrp = minMrp,
            marginPercent = marginPercent,
            bestOfferId = bestOfferId
        )
    }
}

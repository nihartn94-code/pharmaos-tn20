package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.local.SecureTokenStorageException
import com.example.data.local.entity.BasketEntity
import com.example.data.local.entity.BasketItemEntity
import com.example.data.local.entity.BatchEntity
import com.example.data.local.entity.CreditLedgerEntryEntity
import com.example.data.local.entity.CreditNoteEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.OrderEntity
import com.example.data.local.entity.OrderItemEntity
import com.example.data.local.entity.OrderStatusHistoryEntity
import com.example.data.local.entity.OrganizationEntity
import com.example.data.local.entity.OrganizationMemberEntity
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
import com.example.data.local.entity.UserAddressEntity
import com.example.data.local.entity.UserEntity
import com.example.data.remote.config.ApiConfig
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.remote.dto.ComplianceVerificationDto
import com.example.data.remote.dto.CreateRecallRequestDto
import com.example.data.remote.dto.CreateReturnRequestDto
import com.example.data.remote.dto.FulfillOrderItemRequestDto
import com.example.data.remote.dto.OrderDto
import com.example.data.remote.dto.RegisterRequestDto
import com.example.data.remote.dto.SubmitPodRequestDto
import com.example.data.remote.dto.UpdateOrderStatusRequestDto
import com.example.data.remote.dto.UpdateUserProfileRequestDto
import com.example.data.remote.dto.UserAddressDto
import com.example.data.remote.mapper.toDomain
import com.example.data.remote.mapper.toDto
import com.example.data.remote.mapper.toEntity
import com.example.data.remote.source.AuthRemoteDataSource
import com.example.data.remote.source.PharmaRemoteDataSource
import com.example.data.remote.util.NetworkResult
import com.example.di.NetworkModule
import com.example.domain.model.AccountStatus
import com.example.domain.model.AlternativeBrandQuotation
import com.example.domain.model.AuthUser
import com.example.domain.model.ContactVerificationChannel
import com.example.domain.model.DosageForm
import com.example.domain.model.ExpiryRiskStatus
import com.example.domain.model.InventoryDashboardSummary
import com.example.domain.model.InventoryStockItem
import com.example.domain.model.LegalDocument
import com.example.domain.model.LegalDocumentsCatalog
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
import com.example.domain.model.SupplierQuotationItem
import com.example.domain.model.SupplierQuotationSummary
import com.example.domain.model.TaxInvoiceData
import com.example.domain.model.TrustScoreBreakdown
import com.example.domain.model.UserAddress
import com.example.domain.model.UserRole
import com.example.domain.model.VerifiedStockInfo
import com.example.domain.repository.BasketItemDetail
import com.example.domain.repository.BasketLoadReport
import com.example.domain.repository.BasketWithDetails
import com.example.domain.repository.BatchWithProduct
import com.example.domain.repository.DashboardKpis
import com.example.domain.repository.DuplicateBasketTemplateException
import com.example.domain.repository.LaunchDestination
import com.example.domain.repository.NamedBasketSummary
import com.example.domain.repository.OrderDetailsWithHistory
import com.example.domain.repository.PharmaRepository
import com.example.domain.repository.RecallWithAffectedStock
import com.example.domain.repository.ReturnWithDetails
import com.example.domain.repository.SessionRestorationResult
import com.example.domain.repository.SupplierSubtotal
import com.example.domain.usecase.CalculateTrustScoreUseCase
import com.example.domain.usecase.EvaluateShortageRiskUseCase
import com.example.domain.usecase.OptimizeBasketUseCase
import com.example.domain.usecase.ProductQuotationUseCase
import com.example.domain.usecase.TaxInvoiceBuilder
import com.example.util.Formatters
import com.example.util.PasswordHasher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

class PharmaRepositoryImpl(
    private val db: AppDatabase,
    private val dataStoreManager: DataStoreManager,
    private val optimizeBasketUseCase: OptimizeBasketUseCase = OptimizeBasketUseCase(),
    private val calculateTrustScoreUseCase: CalculateTrustScoreUseCase = CalculateTrustScoreUseCase(),
    private val evaluateShortageRiskUseCase: EvaluateShortageRiskUseCase = EvaluateShortageRiskUseCase(),
    private val authRemoteDataSource: AuthRemoteDataSource? = null,
    private val pharmaRemoteDataSource: PharmaRemoteDataSource? = null,
    private val networkModule: NetworkModule? = null,
    private val productQuotationUseCase: ProductQuotationUseCase = ProductQuotationUseCase(optimizeBasketUseCase)
) : PharmaRepository {

    private val userDao = db.userDao()
    private val userAddressDao = db.userAddressDao()
    private val productDao = db.productDao()
    private val productInventoryDao = db.productInventoryDao()
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

    private data class CommittedBasketSnapshot(
        val seq: Long,
        val buyerId: Long,
        val basket: BasketWithDetails
    )

    private val remoteSupplierDirectory = MutableStateFlow<Map<Long, Pair<String?, String?>>>(emptyMap())
    private val remoteSavedBasketsCache = MutableStateFlow<Map<Long, List<NamedBasketSummary>>>(emptyMap())
    private val basketMutationSeq = AtomicLong(0L)
    private val basketRefreshTrigger = MutableStateFlow(0L)
    private val latestCommittedBasket = MutableStateFlow<CommittedBasketSnapshot?>(null)
    private val orderSubmitMutex = Mutex()
    private val inFlightOrderSubmissionKeys = mutableSetOf<String>()

    private suspend fun notifyBasketChanged(explicitBuyerId: Long? = null) {
        val newSeq = basketMutationSeq.incrementAndGet()
        val buyerId = explicitBuyerId ?: resolveActiveBuyerId()
        if (buyerId != null) {
            val freshBasket = readBasketDetailsForBuyer(buyerId)
            latestCommittedBasket.value = CommittedBasketSnapshot(
                seq = newSeq,
                buyerId = buyerId,
                basket = freshBasket
            )
        } else {
            latestCommittedBasket.value = null
        }
        basketRefreshTrigger.value = newSeq
    }

    private fun resolveEffectiveBuyerId(
        loggedInUserId: Long?,
        users: List<UserEntity>,
        isProductionMode: Boolean,
        allowLocalFallback: Boolean = true
    ): Long? {
        if (loggedInUserId != null && users.any { it.id == loggedInUserId }) {
            return loggedInUserId
        }
        if (!allowLocalFallback || isProductionMode) return null
        return users.firstOrNull { it.role == UserRole.STOCKIST.name }?.id
            ?: users.firstOrNull()?.id
    }

    private suspend fun resolveActiveBuyerId(allowLocalFallback: Boolean = true): Long? {
        val signedIn = getApiUsersMe()?.id
        if (signedIn != null) return signedIn
        if (!allowLocalFallback || isProductionHttpMode()) return null
        val users = userDao.getAllUsers()
        return users.firstOrNull { it.role == UserRole.STOCKIST.name }?.id
            ?: users.firstOrNull()?.id
    }

    private suspend fun isProductionHttpMode(): Boolean {
        return dataStoreManager.authExecutionModeFlow.first() == AuthExecutionMode.PRODUCTION_BACKEND_HTTP
    }

    private suspend fun upsertCachedUser(
        remoteUser: UserEntity,
        isProductionMode: Boolean = true
    ): Long {
        val existing = userDao.getUserById(remoteUser.id)
            ?: userDao.getUserByEmail(remoteUser.email.lowercase())
        return if (existing != null) {
            userDao.updateUser(
                existing.copy(
                    fullName = remoteUser.fullName,
                    businessName = remoteUser.businessName,
                    email = remoteUser.email,
                    phone = remoteUser.phone,
                    gstin = remoteUser.gstin,
                    address = remoteUser.address,
                    role = remoteUser.role,
                    status = remoteUser.status,
                    emailVerified = remoteUser.emailVerified,
                    phoneVerified = remoteUser.phoneVerified,
                    profileBio = remoteUser.profileBio,
                    // In production mode, overwrite authoritative org/role/permission fields verbatim
                    // so revoked backend permissions or missing orgId are never masked by stale local cache.
                    organizationId = if (isProductionMode) remoteUser.organizationId else (if (remoteUser.organizationId > 0L) remoteUser.organizationId else existing.organizationId),
                    organizationType = if (isProductionMode) remoteUser.organizationType else remoteUser.organizationType.ifBlank { existing.organizationType },
                    organizationName = if (isProductionMode) remoteUser.organizationName else remoteUser.organizationName.ifBlank { existing.organizationName },
                    assignedRolesCsv = if (isProductionMode) remoteUser.assignedRolesCsv else remoteUser.assignedRolesCsv.ifBlank { existing.assignedRolesCsv },
                    permissionsCsv = if (isProductionMode) remoteUser.permissionsCsv else remoteUser.permissionsCsv.ifBlank { existing.permissionsCsv }
                )
            )
            existing.id
        } else {
            userDao.insertUser(remoteUser)
        }
    }

    override suspend fun postApiAuthLogin(
        email: String,
        password: String,
        expectedRole: UserRole?
    ): Result<AuthUser> {
        if (isProductionHttpMode()) {
            val remote = authRemoteDataSource
                ?: return Result.failure(IllegalStateException("HTTP authentication service is not initialized."))
            return when (val result = remote.login(email.trim(), password, expectedRole?.name)) {
                is NetworkResult.Success -> {
                    val payload = result.data
                    val authUserCandidate = payload.user.toAuthUser(isProductionMode = true)
                    if (expectedRole != null && authUserCandidate.role != expectedRole && !authUserCandidate.assignedRoles.contains(expectedRole)) {
                        dataStoreManager.clearAuthSession()
                        return Result.failure(
                            IllegalArgumentException("This account is registered as '${authUserCandidate.role.displayName}'. Please switch to the ${authUserCandidate.role.displayName} login tab.")
                        )
                    }
                    if (authUserCandidate.status != AccountStatus.ACTIVE) {
                        dataStoreManager.clearAuthSession()
                        val msg = if (authUserCandidate.status == AccountStatus.SUSPENDED) {
                            "This account is currently suspended. Please contact administrator."
                        } else {
                            "Account status could not be verified. Please contact administrator."
                        }
                        return Result.failure(IllegalStateException(msg))
                    }
                    val previousUserId = dataStoreManager.loggedInUserIdFlow.first()
                    if (previousUserId != null && previousUserId != payload.user.id) {
                        // Account switch in production mode: clear previous user's private local caches first
                        db.withTransaction {
                            db.workspaceManagementDao().clearAllPrivateSessionCaches()
                        }
                        remoteSupplierDirectory.value = emptyMap()
                        latestCommittedBasket.value = null
                    }
                    try {
                        dataStoreManager.setAuthTokens(payload.accessToken, payload.refreshToken)
                        val cachedUserId = upsertCachedUser(payload.user.toUserEntityCache(isProductionMode = true))
                        dataStoreManager.setLoggedInUserId(cachedUserId)
                        dataStoreManager.setWelcomeCompleted(true)
                        val authUser = authUserCandidate.copy(id = cachedUserId)
                        val remote = pharmaRemoteDataSource
                        if (authUser.role == UserRole.ADMIN) {
                            dataStoreManager.setAccountOnboardingCompleted(cachedUserId, true)
                        } else if (remote != null && isBackendConfigured()) {
                            val complianceRes = runCatching { remote.fetchComplianceVerification(cachedUserId) }.getOrNull()
                            if (complianceRes is NetworkResult.Success) {
                                complianceDao.insertOrUpdateCompliance(complianceRes.data.toEntity())
                                dataStoreManager.setAccountOnboardingCompleted(cachedUserId, true)
                            } else if (complianceDao.getComplianceByUserId(cachedUserId) != null) {
                                dataStoreManager.setAccountOnboardingCompleted(cachedUserId, true)
                            }
                        }
                        Result.success(authUser)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        runCatching { dataStoreManager.clearAuthSession() }
                        Result.failure(
                            IllegalStateException(
                                "Sign-in verified, but secure local session setup failed (${e.message ?: "storage error"}). Please try signing in again.",
                                e
                            )
                        )
                    }
                }
                else -> Result.failure(result.toException())
            }
        }
        return loginLocallyDevMode(email, password, expectedRole)
    }

    private suspend fun loginLocallyDevMode(
        email: String,
        password: String,
        expectedRole: UserRole?
    ): Result<AuthUser> {
        val trimmed = email.trim()
        val normalizedPhoneDigits = trimmed
            .removePrefix("+91")
            .replace(" ", "")
            .replace("-", "")
        val isPhoneLike = normalizedPhoneDigits.matches("^\\d{10}$".toRegex())

        var user = userDao.getUserByIdentifier(trimmed)
            ?: userDao.getUserByEmail(trimmed.lowercase())
            ?: if (isPhoneLike) {
                userDao.getUserByIdentifier(normalizedPhoneDigits)
                    ?: userDao.getUserByIdentifier("+91$normalizedPhoneDigits")
            } else {
                null
            }

        // Development Local Mode only: auto-provision testing manufacturer account
        if (user == null && (
            trimmed.equals("manufacturer@pharmaos.com", ignoreCase = true) ||
            normalizedPhoneDigits == "9876543220" ||
            trimmed.contains("manufacturer", ignoreCase = true)
        )) {
            user = ensureDefaultManufacturerUser()
        }

        // Development Local Mode only: auto-provision testing stockist account
        if (user == null && (
            trimmed.equals("stockist@pharmaos.com", ignoreCase = true) ||
            trimmed.equals("rohan.sharma@apexpharma.com", ignoreCase = true) ||
            normalizedPhoneDigits == "9876543210" ||
            trimmed.contains("stockist", ignoreCase = true)
        )) {
            user = ensureDefaultStockistUser(setAsLoggedIn = false)
        }

        if (user == null) {
            return Result.failure(IllegalArgumentException("No account registered with '$trimmed'."))
        }

        val assignedRoles = com.example.domain.model.RolePermissionMatrix.parseRolesCsv(
            csv = user.assignedRolesCsv,
            fallback = UserRole.parseStrict(user.role),
            isProductionMode = false
        )
        if (expectedRole != null && user.role != expectedRole.name && !assignedRoles.contains(expectedRole)) {
            val actualRoleName = try { UserRole.valueOf(user.role).displayName } catch (_: Exception) { user.role }
            return Result.failure(IllegalArgumentException("This account is registered as '$actualRoleName'. Please switch to the $actualRoleName login page."))
        }

        if (user.status == AccountStatus.SUSPENDED.name) {
            return Result.failure(IllegalStateException("This account is currently suspended. Please contact administrator."))
        }

        val isSeededDevTestAccount =
            user.email.equals("manufacturer@pharmaos.com", ignoreCase = true) ||
                user.email.equals("stockist@pharmaos.com", ignoreCase = true) ||
                user.email.equals("rohan.sharma@apexpharma.com", ignoreCase = true) ||
                user.email.equals("company@pharmaos.com", ignoreCase = true) ||
                user.email.equals("dual.lead@pharmaos.com", ignoreCase = true)

        val isValid = PasswordHasher.verifyPassword(password, user.passwordHash, user.passwordSalt) ||
            (isSeededDevTestAccount && (
                password == "Password@123" ||
                    (user.role == UserRole.MANUFACTURER.name && password == "PharmaPass123") ||
                    (user.role == UserRole.STOCKIST.name && password == "Stockist@123")
                ))
        if (!isValid) {
            return Result.failure(IllegalArgumentException("Incorrect password entered."))
        }

        dataStoreManager.setLoggedInUserId(user.id)
        dataStoreManager.setWelcomeCompleted(true)
        val hasCompliance = complianceDao.getComplianceByUserId(user.id) != null
        if (isSeededDevTestAccount || user.role == UserRole.ADMIN.name || hasCompliance) {
            dataStoreManager.setAccountOnboardingCompleted(user.id, true)
        }
        return Result.success(user.toDomainAuthUser(isProductionMode = false))
    }

    override suspend fun postApiAuthGoogle(
        email: String,
        displayName: String,
        role: UserRole
    ): Result<AuthUser> {
        if (isProductionHttpMode()) {
            val remote = authRemoteDataSource
                ?: return Result.failure(IllegalStateException("HTTP authentication service is not initialized."))
            return when (val result = remote.googleSignIn(email, displayName, role.name)) {
                is NetworkResult.Success -> {
                    val payload = result.data
                    dataStoreManager.setAuthTokens(payload.accessToken, payload.refreshToken)
                    val cachedUserId = upsertCachedUser(payload.user.toUserEntityCache())
                    dataStoreManager.setLoggedInUserId(cachedUserId)
                    Result.success(payload.user.toAuthUser().copy(id = cachedUserId))
                }
                else -> Result.failure(result.toException())
            }
        }
        return googleSignInLocallyDevMode(email, displayName, role)
    }

    private suspend fun googleSignInLocallyDevMode(
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
            return Result.success(existing.toDomainAuthUser(isProductionMode = false))
        }

        val effectiveRole = role
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

        val defaultOrgType = com.example.domain.model.OrganizationType.fromString(
            raw = null,
            fallbackRole = effectiveRole,
            isProductionMode = false
        )
        val defaultPerms = com.example.domain.model.RolePermissionMatrix.defaultPermissionsFor(effectiveRole)
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
            status = AccountStatus.ACTIVE.name,
            organizationType = defaultOrgType.name,
            organizationName = enterpriseName,
            assignedRolesCsv = effectiveRole.name,
            permissionsCsv = com.example.domain.model.PharmaPermission.toCsv(defaultPerms)
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

        return Result.success(entity.copy(id = id).toDomainAuthUser(isProductionMode = false))
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
        if (role == UserRole.ADMIN) {
            return Result.failure(
                SecurityException("Administrator accounts cannot be self-registered. Contact platform administration.")
            )
        }
        val cleanFullName = fullName.trim()
        val cleanBusiness = businessName.trim()
        val cleanEmail = email.trim().lowercase()
        val cleanPhone = phone.trim().removePrefix("+91").replace(" ", "").replace("-", "")
        val cleanAddress = address.trim().ifBlank { "Licensed B2B Commercial Premises" }
        val cleanGstin = gstin?.trim()?.uppercase()?.ifBlank { null }

        if (cleanFullName.isBlank() || cleanBusiness.isBlank() || cleanEmail.isBlank() || cleanPhone.isBlank() || password.length < 8) {
            return Result.failure(IllegalArgumentException("Please provide valid registration details and a password of at least 8 characters."))
        }

        if (isProductionHttpMode()) {
            val remote = authRemoteDataSource
                ?: return Result.failure(IllegalStateException("HTTP registration service is not initialized."))
            val request = RegisterRequestDto(
                fullName = cleanFullName,
                businessName = cleanBusiness,
                email = cleanEmail,
                phone = cleanPhone,
                gstin = cleanGstin,
                address = cleanAddress,
                password = password,
                role = role.name
            )
            return try {
                when (val result = remote.register(request)) {
                    is NetworkResult.Success -> {
                        val payload = result.data
                        if (payload.accessToken.isBlank() || payload.user.id <= 0L) {
                            return Result.failure(
                                IllegalStateException("Registration response from server was incomplete. Please sign in or contact support.")
                            )
                        }
                        val registeredCandidate = payload.user.toAuthUser(isProductionMode = true)
                        try {
                            val previousUserId = dataStoreManager.loggedInUserIdFlow.firstOrNull()
                            if (previousUserId != null && previousUserId != payload.user.id) {
                                db.withTransaction {
                                    db.workspaceManagementDao().clearAllPrivateSessionCaches()
                                }
                                remoteSupplierDirectory.value = emptyMap()
                                latestCommittedBasket.value = null
                            }
                            dataStoreManager.setAuthTokens(payload.accessToken, payload.refreshToken)
                            val cachedUserId = upsertCachedUser(
                                payload.user.toUserEntityCache(isProductionMode = true),
                                isProductionMode = true
                            )
                            dataStoreManager.setLoggedInUserId(cachedUserId)
                            dataStoreManager.setWelcomeCompleted(true)
                            // Never mark compliance/onboarding completed for newly registered non-admin accounts.
                            val registeredUser = registeredCandidate.copy(id = cachedUserId)
                            if (registeredUser.role == UserRole.ADMIN) {
                                dataStoreManager.setAccountOnboardingCompleted(cachedUserId, true)
                            }
                            Result.success(registeredUser)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (localErr: SecureTokenStorageException) {
                            runCatching { dataStoreManager.clearAuthSession() }
                            Result.failure(
                                IllegalStateException(
                                    "Your account was created on the server, but secure token encryption failed on this device. Please sign in without registering again.",
                                    localErr
                                )
                            )
                        } catch (localErr: Exception) {
                            runCatching { dataStoreManager.clearAuthSession() }
                            Result.failure(
                                IllegalStateException(
                                    "Your account was created on the server, but local session initialization failed (${localErr.message ?: "storage error"}). Please sign in without registering again.",
                                    localErr
                                )
                            )
                        }
                    }
                    else -> Result.failure(result.toException())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(IllegalStateException(e.message ?: "Unable to complete registration.", e))
            }
        }
        return registerLocallyDevMode(
            fullName = cleanFullName,
            businessName = cleanBusiness,
            email = cleanEmail,
            phone = cleanPhone,
            gstin = cleanGstin,
            address = cleanAddress,
            password = password,
            role = role
        )
    }

    private suspend fun registerLocallyDevMode(
        fullName: String,
        businessName: String,
        email: String,
        phone: String,
        gstin: String?,
        address: String,
        password: String,
        role: UserRole
    ): Result<AuthUser> {
        return try {
            val normalizedEmail = email.trim().lowercase()
            val normalizedPhone = phone.trim().removePrefix("+91").replace(" ", "").replace("-", "")

            if (userDao.getUserByEmail(normalizedEmail) != null) {
                return Result.failure(IllegalArgumentException("An account with email $normalizedEmail already exists."))
            }
            if (normalizedPhone.isNotBlank() && (
                    userDao.getUserByIdentifier(normalizedPhone) != null ||
                    userDao.getUserByIdentifier("+91$normalizedPhone") != null
                )
            ) {
                return Result.failure(IllegalArgumentException("An account with mobile number $normalizedPhone already exists."))
            }

            // Never silently promote the first registered user to ADMIN.
            val effectiveRole = role
            val orgType = com.example.domain.model.OrganizationType.fromString(
                raw = null,
                fallbackRole = effectiveRole,
                isProductionMode = false
            )
            val defaultPerms = com.example.domain.model.RolePermissionMatrix.defaultPermissionsFor(effectiveRole)
            val permsCsv = com.example.domain.model.PharmaPermission.toCsv(defaultPerms)
            val hashResult = PasswordHasher.hashPassword(password)
            val workspaceDao = db.workspaceManagementDao()

            val createdEntity = db.withTransaction {
                // Preserve shared PHARMACEUTICAL_ORGANIZATION for Manufacturer & Company accounts;
                // create distinct STOCKIST organization for Stockist accounts.
                val resolvedOrg = if (orgType == com.example.domain.model.OrganizationType.PHARMACEUTICAL_ORGANIZATION) {
                    val existingPharmaOrgs = workspaceDao.getOrganizationsByType(orgType.name)
                    existingPharmaOrgs.firstOrNull { org ->
                        org.name.equals(businessName.trim(), ignoreCase = true) ||
                            (!gstin.isNullOrBlank() && org.gstin.equals(gstin.trim(), ignoreCase = true))
                    } ?: run {
                        val newOrg = OrganizationEntity(
                            name = businessName.trim(),
                            type = orgType.name,
                            gstin = gstin?.trim()?.ifBlank { null },
                            drugLicenseNumber = null,
                            address = address.trim(),
                            isActive = true
                        )
                        val orgId = workspaceDao.upsertOrganization(newOrg)
                        newOrg.copy(id = orgId)
                    }
                } else {
                    val newOrg = OrganizationEntity(
                        name = businessName.trim(),
                        type = orgType.name,
                        gstin = gstin?.trim()?.ifBlank { null },
                        drugLicenseNumber = null,
                        address = address.trim(),
                        isActive = true
                    )
                    val orgId = workspaceDao.upsertOrganization(newOrg)
                    newOrg.copy(id = orgId)
                }

                val entity = UserEntity(
                    fullName = fullName.trim(),
                    businessName = businessName.trim(),
                    email = normalizedEmail,
                    phone = normalizedPhone,
                    gstin = gstin?.trim()?.ifBlank { null },
                    address = address.trim(),
                    passwordHash = hashResult.hashHex,
                    passwordSalt = hashResult.saltHex,
                    role = effectiveRole.name,
                    status = AccountStatus.ACTIVE.name,
                    organizationId = resolvedOrg.id,
                    organizationType = orgType.name,
                    organizationName = resolvedOrg.name,
                    assignedRolesCsv = effectiveRole.name,
                    permissionsCsv = permsCsv
                )

                val insertedId = userDao.insertUser(entity)
                workspaceDao.upsertMember(
                    OrganizationMemberEntity(
                        organizationId = resolvedOrg.id,
                        userId = insertedId,
                        fullName = fullName.trim(),
                        email = normalizedEmail,
                        rolesCsv = effectiveRole.name,
                        permissionsCsv = permsCsv,
                        isActive = true
                    )
                )

                if (effectiveRole.isSupplier) {
                    analyticsDao.insertOrUpdateSupplierSettings(
                        SupplierSettingsEntity(
                            supplierId = insertedId,
                            deliveryCharge = 250.0,
                            freeDeliveryThreshold = 5000.0,
                            averageLeadTimeDays = 3.0
                        )
                    )
                }

                entity.copy(id = insertedId)
            }

            try {
                dataStoreManager.setLoggedInUserId(createdEntity.id)
                dataStoreManager.setWelcomeCompleted(true)
            } catch (e: CancellationException) {
                throw e
            } catch (sessionErr: Exception) {
                runCatching { dataStoreManager.clearAuthSession() }
                return Result.failure(
                    IllegalStateException(
                        "Account created locally, but session initialization failed (${sessionErr.message ?: "preferences error"}). Please sign in to continue.",
                        sessionErr
                    )
                )
            }

            Result.success(createdEntity.toDomainAuthUser(isProductionMode = false))
        } catch (e: CancellationException) {
            throw e
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            Result.failure(IllegalArgumentException("An account with these credentials already exists.", e))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Unable to complete account registration.", e))
        }
    }

    override suspend fun provisionAdminAccountForDev(
        fullName: String,
        businessName: String,
        email: String,
        phone: String,
        password: String,
        gstin: String?,
        address: String
    ): Result<AuthUser> {
        if (isProductionHttpMode()) {
            return Result.failure(
                SecurityException("Administrator provisioning is restricted to backend-authorized operations in production.")
            )
        }
        return try {
            val normalizedEmail = email.trim().lowercase()
            val normalizedPhone = phone.trim().removePrefix("+91").replace(" ", "").replace("-", "")
            val existing = userDao.getUserByEmail(normalizedEmail)
            if (existing != null) {
                return Result.failure(IllegalArgumentException("An account with email $normalizedEmail already exists."))
            }
            val adminPerms = com.example.domain.model.RolePermissionMatrix.adminPermissions
            val permsCsv = com.example.domain.model.PharmaPermission.toCsv(adminPerms)
            val hashResult = PasswordHasher.hashPassword(password)
            val workspaceDao = db.workspaceManagementDao()
            val entity = db.withTransaction {
                val orgId = workspaceDao.upsertOrganization(
                    OrganizationEntity(
                        name = businessName.trim(),
                        type = com.example.domain.model.OrganizationType.PHARMACEUTICAL_ORGANIZATION.name,
                        gstin = gstin?.trim()?.ifBlank { null },
                        drugLicenseNumber = null,
                        address = address.trim(),
                        isActive = true
                    )
                )
                val userEntity = UserEntity(
                    fullName = fullName.trim(),
                    businessName = businessName.trim(),
                    email = normalizedEmail,
                    phone = normalizedPhone,
                    gstin = gstin?.trim()?.ifBlank { null },
                    address = address.trim(),
                    passwordHash = hashResult.hashHex,
                    passwordSalt = hashResult.saltHex,
                    role = UserRole.ADMIN.name,
                    status = AccountStatus.ACTIVE.name,
                    organizationId = orgId,
                    organizationType = com.example.domain.model.OrganizationType.PHARMACEUTICAL_ORGANIZATION.name,
                    organizationName = businessName.trim(),
                    assignedRolesCsv = UserRole.ADMIN.name,
                    permissionsCsv = permsCsv
                )
                val id = userDao.insertUser(userEntity)
                workspaceDao.upsertMember(
                    OrganizationMemberEntity(
                        organizationId = orgId,
                        userId = id,
                        fullName = fullName.trim(),
                        email = normalizedEmail,
                        rolesCsv = UserRole.ADMIN.name,
                        permissionsCsv = permsCsv,
                        isActive = true
                    )
                )
                userEntity.copy(id = id)
            }
            dataStoreManager.setLoggedInUserId(entity.id)
            dataStoreManager.setAccountOnboardingCompleted(entity.id, true)
            Result.success(entity.toDomainAuthUser(isProductionMode = false))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun UserEntity.toDomainAuthUser(isProductionMode: Boolean = false): AuthUser {
        val strictRole = UserRole.parseStrict(role)
        val parsedStatus = AccountStatus.fromString(status, isProductionMode = isProductionMode)
        val parsedRoles = com.example.domain.model.RolePermissionMatrix.parseRolesCsv(
            csv = assignedRolesCsv,
            fallback = if (isProductionMode) strictRole else (strictRole ?: UserRole.STOCKIST),
            isProductionMode = isProductionMode
        )
        val hasInvalidRole = isProductionMode && (strictRole == null || parsedRoles.isEmpty())
        val parsedRole = strictRole ?: parsedRoles.firstOrNull() ?: UserRole.STOCKIST
        val rawTokens = permissionsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val resolution = if (hasInvalidRole) {
            com.example.domain.model.PermissionResolution(
                permissions = emptySet(),
                status = com.example.domain.model.PermissionResolutionStatus.INVALID
            )
        } else {
            com.example.domain.model.RolePermissionMatrix.resolvePermissionState(
                roles = parsedRoles,
                rawPermissions = if (permissionsCsv.isBlank()) {
                    if (isProductionMode) emptyList() else null
                } else {
                    rawTokens
                },
                isProductionMode = isProductionMode
            )
        }
        val resolvedOrgType = com.example.domain.model.OrganizationType.fromString(
            raw = organizationType.takeIf { it.isNotBlank() },
            fallbackRole = parsedRole,
            isProductionMode = isProductionMode
        )
        // Never substitute userId for missing organizationId in production mode!
        val resolvedOrgId = when {
            hasInvalidRole -> 0L
            organizationId > 0L -> organizationId
            isProductionMode -> 0L
            else -> id
        }
        return AuthUser(
            id = id,
            fullName = fullName,
            businessName = businessName,
            email = email,
            phone = phone,
            gstin = gstin,
            address = address,
            role = parsedRole,
            status = parsedStatus,
            emailVerified = emailVerified,
            phoneVerified = phoneVerified,
            profileBio = profileBio,
            organizationId = resolvedOrgId,
            organizationType = resolvedOrgType,
            organizationName = organizationName.ifBlank { businessName },
            assignedRoles = if (hasInvalidRole) emptySet() else parsedRoles,
            permissions = resolution.permissions,
            permissionStatus = resolution.status,
            isProductionContext = isProductionMode
        )
    }

    private fun UserAddressEntity.toDomainAddress(): UserAddress = UserAddress(
        id = id,
        userId = userId,
        addressName = addressName,
        contactPerson = contactPerson,
        phone = phone,
        addressLine1 = addressLine1,
        addressLine2 = addressLine2,
        city = city,
        state = state,
        postalCode = postalCode,
        country = country,
        isDefault = isDefault,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    private fun UserAddress.toEntity(ownerId: Long): UserAddressEntity = UserAddressEntity(
        id = id,
        userId = ownerId,
        addressName = addressName.trim(),
        contactPerson = contactPerson.trim(),
        phone = phone.trim(),
        addressLine1 = addressLine1.trim(),
        addressLine2 = addressLine2.trim(),
        city = city.trim(),
        state = state.trim(),
        postalCode = postalCode.trim(),
        country = country.trim().ifBlank { "India" },
        isDefault = isDefault,
        updatedAtEpochMillis = System.currentTimeMillis()
    )

    private fun UserAddress.toRemoteDto(ownerId: Long): UserAddressDto = UserAddressDto(
        id = id,
        userId = ownerId,
        addressName = addressName.trim(),
        contactPerson = contactPerson.trim(),
        phone = phone.trim(),
        addressLine1 = addressLine1.trim(),
        addressLine2 = addressLine2.trim(),
        city = city.trim(),
        state = state.trim(),
        postalCode = postalCode.trim(),
        country = country.trim().ifBlank { "India" },
        isDefault = isDefault,
        updatedAtEpochMillis = System.currentTimeMillis()
    )

    private fun UserAddressDto.toLocalEntity(ownerId: Long): UserAddressEntity = UserAddressEntity(
        id = id,
        userId = if (userId > 0L) userId else ownerId,
        addressName = addressName.trim(),
        contactPerson = contactPerson.trim(),
        phone = phone.trim(),
        addressLine1 = addressLine1.trim(),
        addressLine2 = addressLine2.trim(),
        city = city.trim(),
        state = state.trim(),
        postalCode = postalCode.trim(),
        country = country.trim().ifBlank { "India" },
        isDefault = isDefault,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    private fun validateProfileInput(
        fullName: String,
        businessName: String,
        email: String,
        phone: String,
        gstin: String?
    ): String? {
        if (fullName.trim().length < 2) return "Full name must be at least 2 characters."
        if (businessName.trim().isEmpty()) return "Business or pharmacy name is required."
        val trimmedEmail = email.trim()
        val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        if (!emailRegex.matches(trimmedEmail)) return "Please enter a valid email address."
        val digitsOnly = phone.filter { it.isDigit() }
        if (digitsOnly.length !in 10..15) return "Please enter a valid 10 to 15 digit phone number."
        val trimmedGstin = gstin?.trim().orEmpty()
        if (trimmedGstin.isNotEmpty() && !Regex("^[0-9A-Za-z]{15}$").matches(trimmedGstin)) {
            return "GSTIN must be exactly 15 alphanumeric characters."
        }
        return null
    }

    private fun validateAddressInput(address: UserAddress): String? {
        if (address.addressName.trim().isEmpty()) return "Address name/type is required."
        if (address.contactPerson.trim().isEmpty()) return "Contact person is required."
        val phoneDigits = address.phone.filter { it.isDigit() }
        if (phoneDigits.length !in 10..15) return "Please enter a valid 10 to 15 digit contact phone number."
        if (address.addressLine1.trim().isEmpty()) return "Address line 1 is required."
        if (address.city.trim().isEmpty()) return "City is required."
        if (address.state.trim().isEmpty()) return "State is required."
        val postal = address.postalCode.trim()
        if (postal.length < 4 || postal.length > 12) return "Please enter a valid postal code."
        if (address.country.trim().isEmpty()) return "Country is required."
        return null
    }

    private suspend fun syncPrimaryAddressSummaryOnUser(userId: Long) {
        val addresses = userAddressDao.getAddressesForUser(userId)
        val defaultAddr = addresses.firstOrNull { it.isDefault } ?: addresses.firstOrNull()
        val user = userDao.getUserById(userId) ?: return
        if (defaultAddr != null) {
            val formatted = defaultAddr.toDomainAddress().formattedSingleLine()
            if (formatted.isNotBlank() && user.address != formatted) {
                userDao.updateUser(user.copy(address = formatted))
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getApiUsersMeFlow(): Flow<AuthUser?> {
        return combine(
            dataStoreManager.loggedInUserIdFlow,
            dataStoreManager.authExecutionModeFlow
        ) { userId, mode ->
            userId to (mode == AuthExecutionMode.PRODUCTION_BACKEND_HTTP)
        }.flatMapLatest { (userId, isProd) ->
            if (userId == null) {
                flowOf(null)
            } else {
                userDao.getUserByIdFlow(userId).map { entity ->
                    entity?.toDomainAuthUser(isProductionMode = isProd)
                }
            }
        }
    }

    override suspend fun getApiUsersMe(): AuthUser? {
        val userId = dataStoreManager.loggedInUserIdFlow.firstOrNull()
        val cachedUser = if (userId != null) userDao.getUserById(userId) else null
        val isProd = isProductionHttpMode()

        if (isProd) {
            if (cachedUser != null) {
                return cachedUser.toDomainAuthUser(isProductionMode = true)
            }
            if (!dataStoreManager.getAccessToken().isNullOrBlank() && isBackendConfigured()) {
                val remoteRes = pharmaRemoteDataSource?.fetchCurrentUser()
                if (remoteRes is NetworkResult.Success) {
                    val savedId = upsertCachedUser(remoteRes.data.toUserEntityCache(isProductionMode = true), isProductionMode = true)
                    dataStoreManager.setLoggedInUserId(savedId)
                    return remoteRes.data.toAuthUser(isProductionMode = true).copy(id = savedId)
                }
            }
            return null
        }

        return cachedUser?.toDomainAuthUser(isProductionMode = false)
    }

    override suspend fun refreshCurrentUserProfile(): Result<AuthUser> {
        val currentUserId = dataStoreManager.loggedInUserIdFlow.firstOrNull()
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.fetchCurrentUser()) {
                is NetworkResult.Success -> {
                    val savedId = upsertCachedUser(res.data.toUserEntityCache(isProductionMode = true), isProductionMode = true)
                    dataStoreManager.setLoggedInUserId(savedId)
                    val fresh = userDao.getUserById(savedId)?.toDomainAuthUser(isProductionMode = true)
                        ?: res.data.toAuthUser(isProductionMode = true).copy(id = savedId)
                    Result.success(fresh)
                }
                else -> Result.failure(res.toException())
            }
        }
        val local = currentUserId?.let { userDao.getUserById(it) }
            ?: return Result.failure(IllegalStateException("No active user session found."))
        return Result.success(local.toDomainAuthUser(isProductionMode = false))
    }

    override suspend fun putApiUserProfile(
        fullName: String,
        businessName: String,
        email: String,
        phone: String,
        gstin: String?,
        address: String,
        profileBio: String?
    ): Result<AuthUser> {
        val validationError = validateProfileInput(fullName, businessName, email, phone, gstin)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }
        val currentUserId = dataStoreManager.loggedInUserIdFlow.firstOrNull()
            ?: return Result.failure(IllegalStateException("Please login to update your profile."))
        val existing = userDao.getUserById(currentUserId)
            ?: return Result.failure(IllegalStateException("User profile not found."))

        val cleanName = fullName.trim()
        val cleanBusiness = businessName.trim()
        val cleanEmail = email.trim().lowercase()
        val cleanPhone = phone.trim()
        val cleanGstin = gstin?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        val cleanAddress = address.trim().ifBlank { existing.address }
        val cleanBio = profileBio?.trim()?.takeIf { it.isNotEmpty() }

        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            val req = UpdateUserProfileRequestDto(
                fullName = cleanName,
                businessName = cleanBusiness,
                email = cleanEmail,
                phone = cleanPhone,
                gstin = cleanGstin,
                address = cleanAddress,
                profileBio = cleanBio
            )
            return when (val res = remote.updateCurrentUserProfile(req)) {
                is NetworkResult.Success -> {
                    val savedId = upsertCachedUser(res.data.toUserEntityCache(isProductionMode = true), isProductionMode = true)
                    dataStoreManager.setLoggedInUserId(savedId)
                    // Refresh profile from backend after successful update to guarantee parity
                    val refreshed = remote.fetchCurrentUser()
                    if (refreshed is NetworkResult.Success) {
                        upsertCachedUser(refreshed.data.toUserEntityCache(isProductionMode = true), isProductionMode = true)
                    }
                    val latest = userDao.getUserById(savedId)?.toDomainAuthUser(isProductionMode = true)
                        ?: res.data.toAuthUser(isProductionMode = true).copy(id = savedId)
                    Result.success(latest)
                }
                else -> Result.failure(res.toException())
            }
        }

        // Local development mode persistence with duplicate email guard & honest verification reset
        val emailOwner = userDao.getUserByEmail(cleanEmail)
        if (emailOwner != null && emailOwner.id != existing.id) {
            return Result.failure(IllegalArgumentException("This email address is already registered to another account."))
        }
        val emailChanged = !existing.email.equals(cleanEmail, ignoreCase = true)
        val phoneChanged = existing.phone.trim() != cleanPhone
        val updated = existing.copy(
            fullName = cleanName,
            businessName = cleanBusiness,
            email = cleanEmail,
            phone = cleanPhone,
            gstin = cleanGstin,
            address = cleanAddress,
            emailVerified = if (emailChanged) false else existing.emailVerified,
            phoneVerified = if (phoneChanged) false else existing.phoneVerified,
            profileBio = cleanBio
        )
        userDao.updateUser(updated)
        val refreshed = userDao.getUserById(existing.id)?.toDomainAuthUser() ?: updated.toDomainAuthUser()
        return Result.success(refreshed)
    }

    override suspend fun requestContactVerification(
        channel: ContactVerificationChannel,
        value: String
    ): Result<String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("${channel.label} cannot be empty."))
        }
        if (!isProductionHttpMode()) {
            return Result.failure(
                IllegalStateException(
                    "${channel.label} verification requires an active backend server. Connect a configured backend in Backend API & Sync to verify."
                )
            )
        }
        val remote = pharmaRemoteDataSource
            ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
        return when (val res = remote.requestContactVerification(channel.apiValue, trimmed)) {
            is NetworkResult.Success -> Result.success(
                res.data.message?.takeIf { it.isNotBlank() }
                    ?: "Verification code sent to $trimmed."
            )
            else -> Result.failure(res.toException())
        }
    }

    override suspend fun confirmContactVerification(
        channel: ContactVerificationChannel,
        value: String,
        code: String
    ): Result<AuthUser> {
        val trimmedCode = code.trim()
        if (trimmedCode.length < 4) {
            return Result.failure(IllegalArgumentException("Please enter the verification code received from the server."))
        }
        if (!isProductionHttpMode()) {
            return Result.failure(
                IllegalStateException(
                    "${channel.label} verification cannot be completed offline without backend confirmation."
                )
            )
        }
        val remote = pharmaRemoteDataSource
            ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
        val currentUserId = dataStoreManager.loggedInUserIdFlow.firstOrNull()
            ?: return Result.failure(IllegalStateException("Please login to verify your account."))

        return when (val res = remote.confirmContactVerification(channel.apiValue, value.trim(), trimmedCode)) {
            is NetworkResult.Success -> {
                if (!res.data.verified) {
                    return Result.failure(
                        IllegalStateException(
                            res.data.message?.takeIf { it.isNotBlank() }
                                ?: "${channel.label} verification was not confirmed by the server."
                        )
                    )
                }
                val existing = userDao.getUserById(currentUserId)
                if (existing != null) {
                    userDao.updateUser(
                        when (channel) {
                            ContactVerificationChannel.EMAIL -> existing.copy(emailVerified = true)
                            ContactVerificationChannel.PHONE -> existing.copy(phoneVerified = true)
                        }
                    )
                }
                val refreshed = remote.fetchCurrentUser()
                if (refreshed is NetworkResult.Success) {
                    upsertCachedUser(refreshed.data.toUserEntityCache())
                }
                val updatedUser = userDao.getUserById(currentUserId)?.toDomainAuthUser()
                    ?: return Result.failure(IllegalStateException("Unable to load refreshed profile."))
                Result.success(updatedUser)
            }
            else -> Result.failure(res.toException())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getApiUserAddressesFlow(): Flow<List<UserAddress>> {
        return dataStoreManager.loggedInUserIdFlow.flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                userAddressDao.getAddressesForUserFlow(userId).map { entities ->
                    entities.map { it.toDomainAddress() }
                }
            }
        }
    }

    override suspend fun refreshUserAddresses(): Result<List<UserAddress>> {
        val userId = dataStoreManager.loggedInUserIdFlow.firstOrNull()
            ?: return Result.failure(IllegalStateException("Please login to view addresses."))
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.fetchUserAddresses()) {
                is NetworkResult.Success -> {
                    val entities = res.data.map { it.toLocalEntity(userId) }
                    db.withTransaction {
                        userAddressDao.deleteAllForUser(userId)
                        if (entities.isNotEmpty()) {
                            userAddressDao.insertAll(entities)
                        }
                    }
                    syncPrimaryAddressSummaryOnUser(userId)
                    Result.success(userAddressDao.getAddressesForUser(userId).map { it.toDomainAddress() })
                }
                else -> Result.failure(res.toException())
            }
        }
        return Result.success(userAddressDao.getAddressesForUser(userId).map { it.toDomainAddress() })
    }

    override suspend fun postApiUserAddress(address: UserAddress): Result<UserAddress> {
        val validationError = validateAddressInput(address)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }
        val userId = dataStoreManager.loggedInUserIdFlow.firstOrNull()
            ?: return Result.failure(IllegalStateException("Please login to add an address."))

        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.createUserAddress(address.toRemoteDto(userId))) {
                is NetworkResult.Success -> {
                    val savedEntity = res.data.toLocalEntity(userId)
                    db.withTransaction {
                        if (savedEntity.isDefault) {
                            userAddressDao.clearDefaultForUser(userId)
                        }
                        userAddressDao.insertAddress(savedEntity)
                    }
                    syncPrimaryAddressSummaryOnUser(userId)
                    Result.success(savedEntity.toDomainAddress())
                }
                else -> Result.failure(res.toException())
            }
        }

        val saved = db.withTransaction {
            val existingList = userAddressDao.getAddressesForUser(userId)
            val shouldBeDefault = address.isDefault || existingList.isEmpty()
            if (shouldBeDefault) {
                userAddressDao.clearDefaultForUser(userId)
            }
            val entity = address.copy(id = 0L, isDefault = shouldBeDefault).toEntity(userId)
            val insertedId = userAddressDao.insertAddress(entity)
            entity.copy(id = insertedId)
        }
        syncPrimaryAddressSummaryOnUser(userId)
        return Result.success(saved.toDomainAddress())
    }

    override suspend fun putApiUserAddress(address: UserAddress): Result<UserAddress> {
        if (address.id <= 0L) {
            return Result.failure(IllegalArgumentException("Invalid address ID."))
        }
        val validationError = validateAddressInput(address)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }
        val userId = dataStoreManager.loggedInUserIdFlow.firstOrNull()
            ?: return Result.failure(IllegalStateException("Please login to update this address."))

        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.updateUserAddress(address.id, address.toRemoteDto(userId))) {
                is NetworkResult.Success -> {
                    val updatedEntity = res.data.toLocalEntity(userId)
                    db.withTransaction {
                        if (updatedEntity.isDefault) {
                            userAddressDao.clearDefaultForUser(userId)
                        }
                        userAddressDao.insertAddress(updatedEntity)
                    }
                    syncPrimaryAddressSummaryOnUser(userId)
                    Result.success(updatedEntity.toDomainAddress())
                }
                else -> Result.failure(res.toException())
            }
        }

        val existing = userAddressDao.getAddressById(address.id)
            ?: return Result.failure(IllegalArgumentException("Address not found."))
        if (existing.userId != userId) {
            return Result.failure(SecurityException("You do not have permission to edit this address."))
        }

        val updated = db.withTransaction {
            val allForUser = userAddressDao.getAddressesForUser(userId)
            val forceDefault = address.isDefault || (existing.isDefault && allForUser.size == 1)
            if (forceDefault) {
                userAddressDao.clearDefaultForUser(userId)
            }
            val entity = address.copy(isDefault = forceDefault).toEntity(userId)
            userAddressDao.updateAddress(entity)
            // Ensure at least one default exists if this was previously default and unchecked
            if (existing.isDefault && !forceDefault) {
                val nextDefault = userAddressDao.getAddressesForUser(userId).firstOrNull { it.id != entity.id }
                if (nextDefault != null) {
                    userAddressDao.markAsDefault(nextDefault.id)
                }
            }
            entity
        }
        syncPrimaryAddressSummaryOnUser(userId)
        return Result.success(updated.toDomainAddress())
    }

    override suspend fun deleteApiUserAddress(addressId: Long): Result<Unit> {
        val userId = dataStoreManager.loggedInUserIdFlow.firstOrNull()
            ?: return Result.failure(IllegalStateException("Please login to delete an address."))

        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.deleteUserAddress(addressId)) {
                is NetworkResult.Success -> {
                    db.withTransaction {
                        val existing = userAddressDao.getAddressById(addressId)
                        userAddressDao.deleteAddressById(addressId)
                        if (existing?.isDefault == true) {
                            val remaining = userAddressDao.getAddressesForUser(userId)
                            remaining.firstOrNull()?.let { userAddressDao.markAsDefault(it.id) }
                        }
                    }
                    syncPrimaryAddressSummaryOnUser(userId)
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
        }

        val existing = userAddressDao.getAddressById(addressId)
            ?: return Result.failure(IllegalArgumentException("Address not found."))
        if (existing.userId != userId) {
            return Result.failure(SecurityException("You do not have permission to delete this address."))
        }

        db.withTransaction {
            userAddressDao.deleteAddressById(addressId)
            if (existing.isDefault) {
                val remaining = userAddressDao.getAddressesForUser(userId)
                remaining.firstOrNull()?.let { userAddressDao.markAsDefault(it.id) }
            }
        }
        syncPrimaryAddressSummaryOnUser(userId)
        return Result.success(Unit)
    }

    override suspend fun patchApiDefaultUserAddress(addressId: Long): Result<UserAddress> {
        val userId = dataStoreManager.loggedInUserIdFlow.firstOrNull()
            ?: return Result.failure(IllegalStateException("Please login to set a default address."))

        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.setDefaultUserAddress(addressId)) {
                is NetworkResult.Success -> {
                    val entity = res.data.toLocalEntity(userId).copy(isDefault = true)
                    db.withTransaction {
                        userAddressDao.clearDefaultForUser(userId)
                        userAddressDao.insertAddress(entity)
                    }
                    syncPrimaryAddressSummaryOnUser(userId)
                    Result.success(entity.toDomainAddress())
                }
                else -> Result.failure(res.toException())
            }
        }

        val target = userAddressDao.getAddressById(addressId)
            ?: return Result.failure(IllegalArgumentException("Address not found."))
        if (target.userId != userId) {
            return Result.failure(SecurityException("You do not have permission to update this address."))
        }

        val updated = db.withTransaction {
            userAddressDao.clearDefaultForUser(userId)
            userAddressDao.markAsDefault(addressId)
            userAddressDao.getAddressById(addressId) ?: target.copy(isDefault = true)
        }
        syncPrimaryAddressSummaryOnUser(userId)
        return Result.success(updated.toDomainAddress())
    }

    override suspend fun getApiLegalDocuments(): Result<List<LegalDocument>> {
        val baseUrl = dataStoreManager.backendBaseUrlFlow.firstOrNull()
        val remoteOverrides = mutableMapOf<String, Pair<String?, String?>>()
        if (isProductionHttpMode() && isBackendConfigured()) {
            val res = pharmaRemoteDataSource?.fetchLegalDocuments()
            if (res is NetworkResult.Success) {
                res.data.forEach { dto ->
                    remoteOverrides[dto.id] = dto.url to dto.version
                    dto.slug?.let { slug -> remoteOverrides[slug] = dto.url to dto.version }
                }
            }
        }
        return Result.success(
            LegalDocumentsCatalog.resolveDocuments(
                backendBaseUrl = baseUrl,
                remoteOverrides = remoteOverrides
            )
        )
    }

    override suspend fun ensureDefaultManufacturerUser(): UserEntity {
        val existingByEmail = userDao.getUserByEmail("manufacturer@pharmaos.com")
        if (existingByEmail != null) return existingByEmail

        val existingByPhone = userDao.getUserByIdentifier("9876543220")
        if (existingByPhone != null) return existingByPhone

        return createManufacturerTestingUser()
    }

    override suspend fun createManufacturerTestingUser(
        fullName: String,
        businessName: String,
        email: String,
        phone: String,
        password: String,
        gstin: String,
        address: String
    ): UserEntity {
        val normalizedEmail = email.lowercase().trim()
        val existingByEmail = userDao.getUserByEmail(normalizedEmail)
        if (existingByEmail != null) return existingByEmail

        val existingByIdentifier = userDao.getUserByIdentifier(phone.trim())
        if (existingByIdentifier != null) return existingByIdentifier

        val hash = PasswordHasher.hashPassword(password)
        val entity = UserEntity(
            fullName = fullName,
            businessName = businessName,
            email = normalizedEmail,
            phone = phone.trim(),
            gstin = gstin.trim(),
            address = address.trim(),
            passwordHash = hash.hashHex,
            passwordSalt = hash.saltHex,
            role = UserRole.MANUFACTURER.name,
            status = AccountStatus.ACTIVE.name,
            createdAtEpochMillis = System.currentTimeMillis()
        )
        val id = try {
            userDao.insertUser(entity)
        } catch (_: Exception) {
            userDao.getUserByEmail(normalizedEmail)?.id ?: 0L
        }

        // Seed pre-verified WHO-GMP compliance certification for testing manufacturer
        try {
            complianceDao.insertOrUpdateCompliance(
                com.example.data.local.entity.ComplianceVerificationEntity(
                    userId = id,
                    gstin = gstin.trim(),
                    isGstinVerified = true,
                    drugLicenseNumber = "MH-TZ-20B-184920 / 21B-184921",
                    drugLicenseIssueDate = "01/01/2023",
                    drugLicenseExpiryDate = "31/12/2028",
                    drugLicenseDocUri = null,
                    whoGmpCertificateNumber = "WHO-GMP-MH-2024-MFG-00842",
                    whoGmpIssueDate = "15/03/2024",
                    whoGmpExpiryDate = "14/03/2027",
                    whoGmpDocUri = null,
                    isWhoGmpApplicable = true,
                    status = "VERIFIED",
                    verifiedAtEpochMillis = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {
            // Silently ignore if already seeded
        }

        return entity.copy(id = id)
    }

    private suspend fun ensureDefaultSupplierUser(): UserEntity {
        return ensureDefaultManufacturerUser()
    }

    override suspend fun ensureDefaultStockistUser(): UserEntity {
        return ensureDefaultStockistUser(setAsLoggedIn = false)
    }

    private suspend fun ensureDefaultStockistUser(setAsLoggedIn: Boolean = false): UserEntity {
        val existingPreferred = userDao.getUserByEmail("stockist@pharmaos.com")
            ?: userDao.getAllUsers().firstOrNull { it.role == UserRole.STOCKIST.name }
        if (existingPreferred != null) {
            if (setAsLoggedIn) {
                dataStoreManager.setLoggedInUserId(existingPreferred.id)
            }
            return existingPreferred
        }
        val hash = PasswordHasher.hashPassword("Password@123")
        val newUser = UserEntity(
            fullName = "Rohan Sharma",
            businessName = "Apex Pharma Distributors",
            email = "stockist@pharmaos.com",
            phone = "9876543210",
            gstin = "27AABCA9988K1Z2",
            address = "Shop 12, Medical Market, Andheri East, Mumbai, MH",
            passwordHash = hash.hashHex,
            passwordSalt = hash.saltHex,
            role = UserRole.STOCKIST.name,
            status = AccountStatus.ACTIVE.name,
            emailVerified = true,
            phoneVerified = true,
            organizationId = 2001L,
            organizationType = com.example.domain.model.OrganizationType.STOCKIST.name,
            organizationName = "Apex Pharma Distributors",
            assignedRolesCsv = UserRole.STOCKIST.name,
            permissionsCsv = com.example.domain.model.PharmaPermission.toCsv(
                com.example.domain.model.RolePermissionMatrix.stockistPermissions
            )
        )
        val id = try {
            userDao.insertUser(newUser)
        } catch (_: Exception) {
            userDao.getUserByEmail("stockist@pharmaos.com")?.id
                ?: userDao.getAllUsers().firstOrNull { it.role == UserRole.STOCKIST.name }?.id
                ?: 0L
        }
        if (id > 0L) {
            try {
                if (complianceDao.getComplianceByUserId(id) == null) {
                    complianceDao.insertOrUpdateCompliance(
                        com.example.data.local.entity.ComplianceVerificationEntity(
                            userId = id,
                            gstin = "27AABCA9988K1Z2",
                            isGstinVerified = true,
                            drugLicenseNumber = "MH-MUM-20B-2026-4410",
                            drugLicenseIssueDate = "01/01/2023",
                            drugLicenseExpiryDate = "31/12/2028",
                            drugLicenseDocUri = null,
                            whoGmpCertificateNumber = null,
                            whoGmpIssueDate = null,
                            whoGmpExpiryDate = null,
                            whoGmpDocUri = null,
                            isWhoGmpApplicable = false,
                            status = "VERIFIED",
                            verifiedAtEpochMillis = System.currentTimeMillis()
                        )
                    )
                }
            } catch (_: Exception) {
            }
            if (setAsLoggedIn) {
                dataStoreManager.setLoggedInUserId(id)
                dataStoreManager.setAccountOnboardingCompleted(id, true)
            }
        }
        return newUser.copy(id = id)
    }

    override suspend fun logout() {
        val wasProductionMode = isProductionHttpMode()
        if (wasProductionMode && isBackendConfigured()) {
            try {
                authRemoteDataSource?.logout()
            } catch (_: Exception) {
                // Local session cleanup must always proceed
            }
        }
        latestCommittedBasket.value = null
        remoteSupplierDirectory.value = emptyMap()
        basketRefreshTrigger.value = basketMutationSeq.incrementAndGet()
        dataStoreManager.clearAuthSession()
        if (wasProductionMode) {
            // Wipe ALL private local Room caches in production mode without deleting remote backend records
            db.withTransaction {
                db.workspaceManagementDao().clearAllPrivateSessionCaches()
            }
        }
    }

    override fun getApiUsersFlow(): Flow<List<UserEntity>> = userDao.getAllUsersFlow()

    override suspend fun patchApiUserStatus(userId: Long, status: String): Result<Unit> {
        val current = getApiUsersMe()
        if (current?.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("Only Administrator can change account status."))
        }
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.updateUserStatus(userId, status)) {
                is NetworkResult.Success -> {
                    userDao.updateUserStatus(userId, status)
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
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
        if (userId <= 0L) {
            return Result.failure(SecurityException("An authenticated account is required to submit compliance verification."))
        }
        val cleanGstin = gstin.trim().uppercase()
        val cleanDlNumber = drugLicenseNumber.trim().uppercase()
        val cleanDlIssue = drugLicenseIssueDate.trim()
        val cleanDlExpiry = drugLicenseExpiryDate.trim()
        if (!cleanGstin.matches("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$".toRegex())) {
            return Result.failure(IllegalArgumentException("Please enter a valid 15-character GSTIN."))
        }
        if (cleanDlNumber.isBlank()) {
            return Result.failure(IllegalArgumentException("Drug License number is required."))
        }
        if (cleanDlIssue.isBlank() || cleanDlExpiry.isBlank()) {
            return Result.failure(IllegalArgumentException("Drug License issue and expiry dates are required."))
        }
        val cleanGmpNumber = if (isWhoGmpApplicable) whoGmpCertificateNumber?.trim()?.uppercase()?.ifBlank { null } else null
        val cleanGmpIssue = if (isWhoGmpApplicable) whoGmpIssueDate?.trim()?.ifBlank { null } else null
        val cleanGmpExpiry = if (isWhoGmpApplicable) whoGmpExpiryDate?.trim()?.ifBlank { null } else null
        if (isWhoGmpApplicable && (cleanGmpNumber == null || cleanGmpIssue == null || cleanGmpExpiry == null)) {
            return Result.failure(IllegalArgumentException("WHO-GMP certificate number, issue date, and expiry date are required for manufacturers."))
        }

        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            val dto = ComplianceVerificationDto(
                userId = userId,
                gstin = cleanGstin,
                // Never fabricate GSTIN verification on the client in production; backend determines verification status.
                isGstinVerified = false,
                drugLicenseNumber = cleanDlNumber,
                drugLicenseIssueDate = cleanDlIssue,
                drugLicenseExpiryDate = cleanDlExpiry,
                drugLicenseDocUri = drugLicenseDocUri?.takeIf { it.isNotBlank() },
                whoGmpCertificateNumber = cleanGmpNumber,
                whoGmpIssueDate = cleanGmpIssue,
                whoGmpExpiryDate = cleanGmpExpiry,
                whoGmpDocUri = if (isWhoGmpApplicable) whoGmpDocUri?.takeIf { it.isNotBlank() } else null,
                isWhoGmpApplicable = isWhoGmpApplicable,
                status = "SUBMITTED"
            )
            return try {
                when (val res = remote.upsertComplianceVerification(dto)) {
                    is NetworkResult.Success -> {
                        complianceDao.insertOrUpdateCompliance(res.data.toEntity())
                        userDao.getUserById(userId)?.let { userDao.updateUser(it.copy(gstin = cleanGstin)) }
                        dataStoreManager.setAccountOnboardingCompleted(userId, true)
                        Result.success(Unit)
                    }
                    else -> Result.failure(res.toException())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        return try {
            val user = userDao.getUserById(userId)
                ?: return Result.failure(IllegalStateException("Authenticated user account was not found."))
            val entity = com.example.data.local.entity.ComplianceVerificationEntity(
                userId = userId,
                gstin = cleanGstin,
                isGstinVerified = true,
                drugLicenseNumber = cleanDlNumber,
                drugLicenseIssueDate = cleanDlIssue,
                drugLicenseExpiryDate = cleanDlExpiry,
                drugLicenseDocUri = drugLicenseDocUri?.takeIf { it.isNotBlank() },
                whoGmpCertificateNumber = cleanGmpNumber,
                whoGmpIssueDate = cleanGmpIssue,
                whoGmpExpiryDate = cleanGmpExpiry,
                whoGmpDocUri = if (isWhoGmpApplicable) whoGmpDocUri?.takeIf { it.isNotBlank() } else null,
                isWhoGmpApplicable = isWhoGmpApplicable,
                status = "VERIFIED",
                verifiedAtEpochMillis = System.currentTimeMillis()
            )
            complianceDao.insertOrUpdateCompliance(entity)
            userDao.updateUser(user.copy(gstin = cleanGstin))
            dataStoreManager.setAccountOnboardingCompleted(userId, true)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getComplianceVerificationFlow(userId: Long): Flow<com.example.data.local.entity.ComplianceVerificationEntity?> {
        return complianceDao.getComplianceByUserIdFlow(userId)
    }

    override fun getOnboardingCompletedFlow(): Flow<Boolean> = dataStoreManager.isOnboardingCompletedFlow

    override suspend fun isOnboardingCompleted(): Boolean = dataStoreManager.isOnboardingCompletedFlow.first()

    override suspend fun isWelcomeCompleted(): Boolean = dataStoreManager.isWelcomeCompletedFlow.first()

    override suspend fun setWelcomeCompleted(completed: Boolean) {
        dataStoreManager.setWelcomeCompleted(completed)
    }

    override suspend fun isAccountOnboardingCompleted(userId: Long): Boolean {
        if (userId <= 0L) return false
        val completedIds = dataStoreManager.completedOnboardingUserIdsFlow.first()
        if (completedIds.contains(userId)) return true
        val existingCompliance = runCatching { complianceDao.getComplianceByUserId(userId) }.getOrNull()
        return existingCompliance != null
    }

    override suspend fun requiresComplianceForUser(user: AuthUser): Boolean {
        if (user.role == UserRole.ADMIN) return false
        return !isAccountOnboardingCompleted(user.id)
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStoreManager.setOnboardingCompleted(completed)
    }

    override suspend fun resolveSessionRestoration(): SessionRestorationResult {
        return try {
            val isProd = isProductionHttpMode()
            val savedUserId = dataStoreManager.loggedInUserIdFlow.firstOrNull()
            val welcomeCompleted = dataStoreManager.isWelcomeCompletedFlow.first()

            if (savedUserId == null || savedUserId <= 0L) {
                return if (welcomeCompleted) {
                    SessionRestorationResult.Login
                } else {
                    SessionRestorationResult.Welcome
                }
            }

            if (isProd) {
                val accessToken = runCatching { dataStoreManager.getAccessToken() }.getOrNull()
                if (accessToken.isNullOrBlank()) {
                    // Token missing or scrubbed due to Keystore invalidation/corruption — recover cleanly to Login without wiping app data
                    dataStoreManager.clearAuthSession()
                    return SessionRestorationResult.Login
                }
            }

            val currentUser = getApiUsersMe()
            if (currentUser == null) {
                // Stale/orphaned userId in DataStore (e.g. account removed or cache missing while backend unreachable)
                dataStoreManager.clearAuthSession()
                return SessionRestorationResult.Login
            }

            if (currentUser.status != AccountStatus.ACTIVE) {
                val reason = if (currentUser.status == AccountStatus.SUSPENDED) {
                    "Your account is currently suspended. Contact your organization administrator for assistance."
                } else {
                    "Your account status could not be verified. Please contact your organization administrator or sign in again."
                }
                return SessionRestorationResult.UnauthorizedAccount(currentUser, reason)
            }

            if (currentUser.authorizedWorkspaces().isEmpty()) {
                return SessionRestorationResult.UnauthorizedAccount(
                    user = currentUser,
                    reason = "Your account does not currently have an authorized workspace or confirmed organization permissions."
                )
            }

            if (requiresComplianceForUser(currentUser)) {
                return SessionRestorationResult.ComplianceRequired(currentUser)
            }

            SessionRestorationResult.AuthorizedWorkspace(currentUser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never auto-wipe user database or crash-loop on storage/migration/startup errors
            SessionRestorationResult.RecoverableError(
                title = "Session Restoration Failed",
                message = e.message ?: "Unable to verify your saved session right now. Retry or return to Login."
            )
        }
    }

    override suspend fun resolveLaunchDestination(): LaunchDestination {
        return when (resolveSessionRestoration()) {
            SessionRestorationResult.Welcome -> LaunchDestination.WELCOME
            SessionRestorationResult.Login -> LaunchDestination.LOGIN
            is SessionRestorationResult.ComplianceRequired -> LaunchDestination.COMPLIANCE_VERIFICATION
            is SessionRestorationResult.AuthorizedWorkspace -> LaunchDestination.DASHBOARD
            is SessionRestorationResult.UnauthorizedAccount -> LaunchDestination.LOGIN
            is SessionRestorationResult.RecoverableError -> LaunchDestination.LOGIN
        }
    }

    override fun getAuthExecutionModeFlow(): Flow<AuthExecutionMode> = dataStoreManager.authExecutionModeFlow

    override suspend fun setAuthExecutionMode(mode: AuthExecutionMode) {
        dataStoreManager.setAuthExecutionMode(mode)
    }

    override fun getBackendBaseUrlFlow(): Flow<String> = dataStoreManager.backendBaseUrlFlow

    override suspend fun setBackendBaseUrl(baseUrl: String): Result<String> {
        val normalized = ApiConfig.normalizeBaseUrl(baseUrl)
        val isValidTarget = ApiConfig.isSecureForProduction(normalized) ||
            ApiConfig.isAllowedLocalHttpUrl(normalized) ||
            !ApiConfig.isBackendUrlConfigured(normalized)
        if (!isValidTarget) {
            return Result.failure(
                IllegalArgumentException("Cleartext HTTP is restricted to local development hosts (10.0.2.2, localhost, LAN IP). Production servers must use https://.")
            )
        }
        dataStoreManager.setBackendBaseUrl(normalized)
        networkModule?.updateBaseUrl(normalized)
        return Result.success(normalized)
    }

    override fun isBackendConfigured(): Boolean = networkModule?.isBackendConfigured() ?: false

    override suspend fun syncCatalogueFromBackend(): Result<Int> {
        val remote = pharmaRemoteDataSource
            ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
        return when (val res = remote.fetchProducts()) {
            is NetworkResult.Success -> {
                val entities = res.data.map { it.toEntity() }
                if (entities.isNotEmpty()) {
                    productDao.insertProducts(entities)
                }
                Result.success(entities.size)
            }
            else -> Result.failure(res.toException())
        }
    }

    private suspend fun upsertRemoteOrder(dto: OrderDto): Long {
        val entity = dto.toEntity()
        val existingByNum = orderDao.getOrderByOrderNumber(entity.orderNumber)
        val target = if (existingByNum != null && existingByNum.id != entity.id) {
            entity.copy(id = existingByNum.id)
        } else {
            entity
        }
        return orderDao.insertOrder(target)
    }

    override suspend fun syncOrdersFromBackend(): Result<Int> {
        val remote = pharmaRemoteDataSource
            ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
        return when (val res = remote.fetchOrders()) {
            is NetworkResult.Success -> {
                db.withTransaction {
                    res.data.forEach { upsertRemoteOrder(it) }
                }
                Result.success(res.data.size)
            }
            else -> Result.failure(res.toException())
        }
    }

    override suspend fun syncLivePipelineFromBackend(): Result<Int> {
        val user = getApiUsersMe()
            ?: return Result.failure(IllegalStateException("Please login to access Live Pipeline."))
        if (!user.role.isSupplier && user.role != UserRole.ADMIN) {
            return Result.failure(
                SecurityException("Access denied: Live Pipeline is restricted to Manufacturer and Pharma Company accounts.")
            )
        }
        if (!isProductionHttpMode()) {
            val count = if (user.role == UserRole.ADMIN) {
                orderDao.getAllOrders().size
            } else {
                orderDao.getOrdersForSupplier(user.id).size
            }
            return Result.success(count)
        }
        val remote = pharmaRemoteDataSource
            ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
        return when (val res = remote.fetchLivePipelineOrders()) {
            is NetworkResult.Success -> {
                db.withTransaction {
                    res.data.forEach { upsertRemoteOrder(it) }
                }
                Result.success(res.data.size)
            }
            else -> Result.failure(res.toException())
        }
    }

    override suspend fun syncNotificationsFromBackend(): Result<Int> {
        val remote = pharmaRemoteDataSource
            ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
        return when (val res = remote.fetchNotifications()) {
            is NetworkResult.Success -> {
                notificationDao.insertNotifications(res.data.map { it.toEntity() })
                Result.success(res.data.size)
            }
            else -> Result.failure(res.toException())
        }
    }

    override suspend fun ensureDefaultCatalogueSeeded() {
        if (isProductionHttpMode()) return
        if (productDao.getAllProducts().isNotEmpty() && productInventoryDao.getAllInventory().isNotEmpty()) {
            return
        }
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

    if (productInventoryDao.getAllInventory().isEmpty()) {
        val sampleInventory = listOf(
            ProductInventoryEntity(
                productName = "Augmentin 625 Duo",
                sku = "SKU-AUG-625-10T",
                currentStock = 14,
                reorderThreshold = 25,
                saltComposition = "Amoxicillin (500mg) + Clavulanic Acid (125mg)",
                dosageForm = "Tablet",
                packSize = "Box of 10 Strips x 10 Tabs",
                batchNumber = "AUG-24K2",
                unitPricePtr = 142.50,
                mrp = 198.00,
                storageCondition = "ROOM_TEMPERATURE",
                locationRack = "RACK-A-01",
                supplierName = "Cipla Wholesale Direct Hub"
            ),
            ProductInventoryEntity(
                productName = "Pan-D Gastro-Resistant",
                sku = "SKU-PAND-15C",
                currentStock = 8,
                reorderThreshold = 20,
                saltComposition = "Pantoprazole (40mg) + Domperidone (30mg SR)",
                dosageForm = "Capsule",
                packSize = "Strip of 15 Capsules",
                batchNumber = "PND-2025-V4",
                unitPricePtr = 118.00,
                mrp = 165.00,
                storageCondition = "ROOM_TEMPERATURE",
                locationRack = "RACK-A-03",
                supplierName = "Cipla Wholesale Direct Hub"
            ),
            ProductInventoryEntity(
                productName = "Mixtard 30/70 100IU",
                sku = "SKU-MIXT-100IU-10ML",
                currentStock = 5,
                reorderThreshold = 15,
                saltComposition = "Biphasic Isophane Insulin Injection IP",
                dosageForm = "Injection",
                packSize = "Vial of 10ml (Cold Chain)",
                batchNumber = "MIX-2024-C9",
                unitPricePtr = 185.00,
                mrp = 225.00,
                storageCondition = "COLD_CHAIN_2_TO_8",
                locationRack = "FRIDGE-01",
                supplierName = "Sun Pharma Direct Depo"
            ),
            ProductInventoryEntity(
                productName = "Paracetamol 650mg (Dolo)",
                sku = "SKU-DOLO-650-15T",
                currentStock = 120,
                reorderThreshold = 50,
                saltComposition = "Paracetamol (650mg)",
                dosageForm = "Tablet",
                packSize = "Strip of 15 Tablets",
                batchNumber = "DOL-2025-P2",
                unitPricePtr = 28.50,
                mrp = 34.00,
                storageCondition = "ROOM_TEMPERATURE",
                locationRack = "RACK-B-01",
                supplierName = "Micro Labs Wholesale"
            ),
            ProductInventoryEntity(
                productName = "Azithromycin 500mg (Azithral)",
                sku = "SKU-AZTH-500-5T",
                currentStock = 0,
                reorderThreshold = 30,
                saltComposition = "Azithromycin IP (500mg)",
                dosageForm = "Tablet",
                packSize = "Strip of 5 Tablets",
                batchNumber = "AZI-2024-X8",
                unitPricePtr = 94.00,
                mrp = 118.00,
                storageCondition = "ROOM_TEMPERATURE",
                locationRack = "RACK-B-05",
                supplierName = "Alembic Direct Hub"
            )
        )
        productInventoryDao.insertAll(sampleInventory)
    }
}

    private fun isOfferEligible(offer: SupplierOfferEntity, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val notExpired = offer.validTillEpochMillis <= 0L || offer.validTillEpochMillis > nowMillis
        return offer.isActive &&
            notExpired &&
            offer.unitPricePtr > 0.0 &&
            offer.moq > 0 &&
            offer.availableQuantity > 0 &&
            offer.availableQuantity >= offer.moq
    }

    private fun buildProductSummaryFields(
        product: ProductEntity,
        offers: List<SupplierOfferEntity>,
        settingsMap: Map<Long, SupplierSettingsEntity>
    ): ProductDetail {
        val eligibleOffers = offers.filter { isOfferEligible(it) }
        val scored = eligibleOffers.map { offer ->
            val scheme = try { SchemeType.valueOf(offer.schemeType) } catch (_: Exception) { SchemeType.NONE }
            val breakdown = productQuotationUseCase.calculateTradeMarginBreakdown(
                unitPricePtr = offer.unitPricePtr,
                mrp = offer.mrp,
                moq = offer.moq,
                gstRatePercent = product.gstRatePercent,
                schemeType = scheme,
                schemeBuyQty = offer.schemeBuyQty,
                schemeFreeQty = offer.schemeFreeQty,
                schemeDiscountPercent = offer.schemeDiscountPercent,
                schemeMinQtyForDiscount = offer.schemeMinQtyForDiscount,
                supplierSettings = settingsMap[offer.supplierId]
            )
            offer to breakdown
        }.sortedWith(
            compareBy<Pair<SupplierOfferEntity, com.example.domain.model.TradeMarginBreakdown>>(
                { it.second.effectiveUnitCost ?: Double.MAX_VALUE },
                { it.first.unitPricePtr }
            )
        )
        val bestPair = scored.firstOrNull()
        val bestOffer = bestPair?.first
        val minPrice = bestOffer?.unitPricePtr
        val minMrp = bestOffer?.mrp?.takeIf { it > 0.0 }
        val margin = bestPair?.second?.marginPercent?.let { Math.round(it).toInt() }
        return mapToProductDetail(product, minPrice, eligibleOffers.size, minMrp, margin, bestOffer?.id)
    }

    override fun getApiProductsFlow(): Flow<List<ProductDetail>> {
        return combine(
            productDao.getAllActiveProductsFlow(),
            offerDao.getAllActiveOffersFlow(),
            analyticsDao.getAllSupplierSettingsFlow()
        ) { products, offers, settings ->
            val settingsMap = settings.associateBy { it.supplierId }
            products
                .filter { p ->
                    com.example.domain.model.AccessControlPolicy.isMedicineDiscoverableByStockist(
                        approvalStatus = com.example.domain.model.MedicineSubmissionStatus.fromString(p.approvalStatus),
                        publicationStatus = com.example.domain.model.CataloguePublicationStatus.fromString(p.catalogueStatus),
                        isActive = p.isActive
                    )
                }
                .map { p ->
                    val prodOffers = offers.filter { it.productId == p.id }
                    buildProductSummaryFields(p, prodOffers, settingsMap)
                }
        }
    }

    override suspend fun getApiProducts(): List<ProductDetail> {
        val products = productDao.getAllProducts().filter { p ->
            com.example.domain.model.AccessControlPolicy.isMedicineDiscoverableByStockist(
                approvalStatus = com.example.domain.model.MedicineSubmissionStatus.fromString(p.approvalStatus),
                publicationStatus = com.example.domain.model.CataloguePublicationStatus.fromString(p.catalogueStatus),
                isActive = p.isActive
            )
        }
        val offers = offerDao.getAllActiveOffers()
        val settingsMap = analyticsDao.getAllSupplierSettings().associateBy { it.supplierId }
        return products.map { p ->
            val prodOffers = offers.filter { it.productId == p.id }
            buildProductSummaryFields(p, prodOffers, settingsMap)
        }
    }

    private fun isProductVisibleToUser(product: ProductEntity, user: AuthUser?): Boolean {
        val discoverable = com.example.domain.model.AccessControlPolicy.isMedicineDiscoverableByStockist(
            approvalStatus = com.example.domain.model.MedicineSubmissionStatus.fromString(product.approvalStatus),
            publicationStatus = com.example.domain.model.CataloguePublicationStatus.fromString(product.catalogueStatus),
            isActive = product.isActive
        )
        if (discoverable) return true
        if (user == null) return false
        if (user.role == UserRole.ADMIN) return true
        // Stockists may NEVER view unapproved, inactive, or unknown medicines via direct product details
        if (user.organizationType == com.example.domain.model.OrganizationType.STOCKIST || user.role == UserRole.STOCKIST) {
            return false
        }
        // Authorized pharmaceutical organization members may view their own organization's products
        return user.hasValidOrganizationContext() &&
            product.organizationId > 0L &&
            product.organizationId == user.organizationId
    }

    override suspend fun getApiProductById(id: Long): ProductDetail? {
        val p = productDao.getProductById(id) ?: return null
        val currentUser = getApiUsersMe()
        if (!isProductVisibleToUser(p, currentUser)) return null
        val offers = offerDao.getActiveOffersForProduct(id)
        val settingsMap = analyticsDao.getAllSupplierSettings().associateBy { it.supplierId }
        return buildProductSummaryFields(p, offers, settingsMap)
    }

    override fun getApiProductByIdFlow(id: Long): Flow<ProductDetail?> {
        return combine(
            productDao.getProductByIdFlow(id),
            offerDao.getAllActiveOffersFlow(),
            analyticsDao.getAllSupplierSettingsFlow(),
            getApiUsersMeFlow()
        ) { p, offers, settings, currentUser ->
            if (p == null || !isProductVisibleToUser(p, currentUser)) null
            else {
                val settingsMap = settings.associateBy { it.supplierId }
                val prodOffers = offers.filter { it.productId == p.id }
                buildProductSummaryFields(p, prodOffers, settingsMap)
            }
        }
    }

    override suspend fun postApiProducts(product: ProductEntity): Result<Long> {
        val current = getApiUsersMe()
        if (current == null || current.role == UserRole.STOCKIST) {
            return Result.failure(SecurityException("Only Suppliers and Admin can create products."))
        }
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.createProduct(product.copy(createdBy = current.id).toDto())) {
                is NetworkResult.Success -> {
                    val savedId = productDao.insertProduct(res.data.toEntity())
                    Result.success(savedId)
                }
                else -> Result.failure(res.toException())
            }
        }
        val id = productDao.insertProduct(product.copy(createdBy = current.id))
        return Result.success(id)
    }

    override suspend fun putApiProducts(product: ProductEntity): Result<Unit> {
        val current = getApiUsersMe()
        if (current == null || current.role == UserRole.STOCKIST) {
            return Result.failure(SecurityException("Only Suppliers and Admin can edit products."))
        }
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.updateProduct(product.id, product.toDto())) {
                is NetworkResult.Success -> {
                    productDao.updateProduct(res.data.toEntity())
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
        }
        productDao.updateProduct(product)
        return Result.success(Unit)
    }

    override suspend fun patchApiProductDeactivate(id: Long, isActive: Boolean): Result<Unit> {
        val current = getApiUsersMe()
        if (current == null || current.role == UserRole.STOCKIST) {
            return Result.failure(SecurityException("Only Suppliers and Admin can activate/deactivate products."))
        }
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.updateProductStatus(id, isActive)) {
                is NetworkResult.Success -> {
                    productDao.setProductActiveStatus(id, isActive)
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
        }
        productDao.setProductActiveStatus(id, isActive)
        return Result.success(Unit)
    }

    override fun getApiProductAlternativeBrandsFlow(id: Long): Flow<List<ProductEntity>> = flow {
        val prod = productDao.getProductById(id)
        if (prod == null) {
            emit(emptyList())
        } else {
            productDao.getAlternativeBrandsFlow(prod.saltComposition, prod.strength, id).collect { candidates ->
                emit(
                    candidates.filter { alt ->
                        com.example.domain.model.AccessControlPolicy.isMedicineDiscoverableByStockist(
                            approvalStatus = com.example.domain.model.MedicineSubmissionStatus.fromString(alt.approvalStatus),
                            publicationStatus = com.example.domain.model.CataloguePublicationStatus.fromString(alt.catalogueStatus),
                            isActive = alt.isActive
                        )
                    }
                )
            }
        }
    }

    override suspend fun refreshProductDetailsFromBackend(productId: Long): Result<Unit> {
        if (!isProductionHttpMode()) return Result.success(Unit)
        val remote = pharmaRemoteDataSource
            ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
        if (!isBackendConfigured()) {
            return Result.failure(IllegalStateException("Spring Boot backend URL is not configured."))
        }

        when (val prodRes = remote.fetchProductById(productId)) {
            is NetworkResult.Success -> {
                productDao.insertProduct(prodRes.data.toEntity())
            }
            else -> return Result.failure(prodRes.toException())
        }

        val supplierIdsToEnrich = mutableSetOf<Long>()
        when (val offersRes = remote.fetchOffersForProduct(productId)) {
            is NetworkResult.Success -> {
                val offerDtos = offersRes.data
                val dir = remoteSupplierDirectory.value.toMutableMap()
                offerDtos.forEach { dto ->
                    if (!dto.supplierName.isNullOrBlank() || !dto.supplierBusinessName.isNullOrBlank()) {
                        dir[dto.supplierId] = dto.supplierName to dto.supplierBusinessName
                    }
                    supplierIdsToEnrich.add(dto.supplierId)
                }
                remoteSupplierDirectory.value = dir
                if (offerDtos.isNotEmpty()) {
                    offerDao.insertOffers(offerDtos.map { it.toEntity() })
                }
            }
            else -> return Result.failure(offersRes.toException())
        }

        // Fetch alternative brands and their offers if available
        when (val altRes = remote.fetchAlternativeBrands(productId)) {
            is NetworkResult.Success -> {
                val altEntities = altRes.data.map { it.toEntity() }
                if (altEntities.isNotEmpty()) {
                    productDao.insertProducts(altEntities)
                }
                altEntities.forEach { alt ->
                    val altOffersRes = remote.fetchOffersForProduct(alt.id)
                    if (altOffersRes is NetworkResult.Success) {
                        val dir = remoteSupplierDirectory.value.toMutableMap()
                        altOffersRes.data.forEach { dto ->
                            if (!dto.supplierName.isNullOrBlank() || !dto.supplierBusinessName.isNullOrBlank()) {
                                dir[dto.supplierId] = dto.supplierName to dto.supplierBusinessName
                            }
                            supplierIdsToEnrich.add(dto.supplierId)
                        }
                        remoteSupplierDirectory.value = dir
                        if (altOffersRes.data.isNotEmpty()) {
                            offerDao.insertOffers(altOffersRes.data.map { it.toEntity() })
                        }
                    }
                }
            }
            else -> {
                // Non-fatal if alternatives endpoint returns 404/empty
            }
        }

        // Enrich supplier settings & trust scores from backend without fabricating missing entries
        for (supplierId in supplierIdsToEnrich) {
            when (val settingsRes = remote.fetchSupplierSettings(supplierId)) {
                is NetworkResult.Success -> {
                    analyticsDao.insertOrUpdateSupplierSettings(settingsRes.data.toEntity())
                }
                else -> Unit
            }
            when (val trustRes = remote.fetchSupplierTrustScore(supplierId)) {
                is NetworkResult.Success -> {
                    analyticsDao.insertOrUpdatePerformance(trustRes.data.toEntity())
                }
                else -> Unit
            }
        }

        // Opportunistically refresh verified batch & inventory stock records ONLY for authorized Brand/Manufacturer roles
        val activeRole = getApiUsersMe()?.role
        if (activeRole?.canAccessRealTimeInventory == true) {
            when (val batchesRes = remote.fetchBatches()) {
                is NetworkResult.Success -> {
                    if (batchesRes.data.isNotEmpty()) {
                        batchDao.insertBatches(batchesRes.data.map { it.toEntity() })
                    }
                }
                else -> Unit
            }
            when (val invRes = remote.fetchInventory()) {
                is NetworkResult.Success -> {
                    if (invRes.data.isNotEmpty()) {
                        productInventoryDao.insertAll(invRes.data.map { it.toEntity() })
                    }
                }
                else -> Unit
            }
        }

        return Result.success(Unit)
    }

    private fun toOfferWithSupplier(
        o: SupplierOfferEntity,
        usersMap: Map<Long, UserEntity>,
        perfMap: Map<Long, SupplierPerformanceEntity>,
        remoteDir: Map<Long, Pair<String?, String?>>
    ): OfferWithSupplier {
        val sup = usersMap[o.supplierId]
        val remoteNames = remoteDir[o.supplierId]
        val perf = perfMap[o.supplierId]
        val scheme = try { SchemeType.valueOf(o.schemeType) } catch (_: Exception) { SchemeType.NONE }
        val resolvedName = sup?.fullName ?: remoteNames?.first ?: "Supplier #${o.supplierId}"
        val resolvedBusiness = sup?.businessName ?: remoteNames?.second ?: remoteNames?.first ?: "Supplier #${o.supplierId}"
        val hasTrustData = (perf?.completedOrdersCount ?: 0) >= 5
        return OfferWithSupplier(
            offerId = o.id,
            productId = o.productId,
            supplierId = o.supplierId,
            supplierName = resolvedName,
            supplierBusinessName = resolvedBusiness,
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
            trustScore = if (hasTrustData) perf?.overallScore else null,
            hasSufficientTrustData = hasTrustData
        )
    }

    override fun getApiProductQuotationsFlow(
        productId: Long
    ): Flow<Pair<SupplierQuotationSummary, List<SupplierQuotationItem>>> {
        val coreFlow = combine(
            productDao.getProductByIdFlow(productId),
            offerDao.getAllOffersForProductFlow(productId),
            userDao.getAllUsersFlow(),
            analyticsDao.getAllSupplierPerformancesFlow(),
            analyticsDao.getAllSupplierSettingsFlow()
        ) { product, offers, users, perfs, settings ->
            Quintet(product, offers, users, perfs, settings)
        }
        val stockFlow = combine(
            batchDao.getBatchesForProductFlow(productId),
            recallDao.getActiveRecallsFlow(),
            productInventoryDao.getAllInventoryFlow(),
            remoteSupplierDirectory
        ) { batches, recalls, inventories, remoteDir ->
            Quartet(batches, recalls, inventories, remoteDir)
        }
        return combine(coreFlow, stockFlow) { core, stock ->
            val (product, offers, users, perfs, settingsList) = core
            val (batches, recalls, inventories, remoteDir) = stock
            val usersMap = users.associateBy { it.id }
            val perfMap = perfs.associateBy { it.supplierId }
            val settingsMap = settingsList.associateBy { it.supplierId }
            val now = System.currentTimeMillis()

            val recalledBatchNumbers = recalls
                .filter { it.productId == productId && it.isActive }
                .flatMap { it.batchNumbersCsv.split(",") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

            val validBatches = batches.filter { b ->
                b.remainingQuantity > 0 &&
                    (b.expiryDateEpochMillis <= 0L || b.expiryDateEpochMillis > now) &&
                    b.batchNumber !in recalledBatchNumbers
            }
            val matchingInventory = inventories.filter { inv ->
                inv.isActive && (
                    inv.productId == productId ||
                        (product != null && inv.productName.equals(product.name, ignoreCase = true))
                    )
            }
            val hasVerifiedRecords = batches.isNotEmpty() || matchingInventory.isNotEmpty()
            val verifiedUnits = if (hasVerifiedRecords) {
                validBatches.sumOf { it.remainingQuantity } + matchingInventory.sumOf { it.currentStock.coerceAtLeast(0) }
            } else {
                null
            }
            val verifiedStockInfo = VerifiedStockInfo(
                verifiedAvailableUnits = verifiedUnits,
                verifiedBatchCount = validBatches.size,
                verifiedInventorySkuCount = matchingInventory.size,
                hasVerifiedStockRecords = hasVerifiedRecords
            )

            val gstRate = product?.gstRatePercent ?: -1.0
            val items = offers.map { o ->
                val supplierBatches = batches.filter { it.supplierId == o.supplierId }
                val validSupplierBatches = validBatches.filter { it.supplierId == o.supplierId }
                val supplierHasVerified = supplierBatches.isNotEmpty()
                val supplierVerifiedStock = if (supplierHasVerified) {
                    validSupplierBatches.sumOf { it.remainingQuantity }
                } else {
                    null
                }
                productQuotationUseCase.buildSupplierQuotationItem(
                    offer = toOfferWithSupplier(o, usersMap, perfMap, remoteDir),
                    gstRatePercent = gstRate,
                    supplierSettings = settingsMap[o.supplierId],
                    verifiedSupplierStock = supplierVerifiedStock,
                    hasVerifiedSupplierStockRecord = supplierHasVerified,
                    nowMillis = now
                )
            }
            productQuotationUseCase.sortAndSummarizeQuotations(items, verifiedStockInfo)
        }
    }

    override fun getApiAlternativeBrandQuotationsFlow(
        productId: Long
    ): Flow<List<AlternativeBrandQuotation>> {
        val coreFlow = combine(
            productDao.getProductByIdFlow(productId),
            productDao.getAllActiveProductsFlow(),
            offerDao.getAllOffersFlow(),
            userDao.getAllUsersFlow(),
            analyticsDao.getAllSupplierPerformancesFlow()
        ) { target, allActive, allOffers, users, perfs ->
            Quintet(target, allActive, allOffers, users, perfs)
        }
        return combine(
            coreFlow,
            analyticsDao.getAllSupplierSettingsFlow(),
            remoteSupplierDirectory
        ) { core, settingsList, remoteDir ->
            val (target, allActive, allOffers, users, perfs) = core
            if (target == null) return@combine emptyList()
            val usersMap = users.associateBy { it.id }
            val perfMap = perfs.associateBy { it.supplierId }
            val settingsMap = settingsList.associateBy { it.supplierId }
            val now = System.currentTimeMillis()

            val alternatives = allActive
                .filter {
                    it.id != productId &&
                        it.saltComposition.equals(target.saltComposition, ignoreCase = true) &&
                        it.strength.equals(target.strength, ignoreCase = true)
                }
                .sortedBy { it.name.lowercase() }

            alternatives.map { alt ->
                val altOffers = allOffers
                    .filter { it.productId == alt.id }
                    .map { toOfferWithSupplier(it, usersMap, perfMap, remoteDir) }
                productQuotationUseCase.buildAlternativeBrandQuotation(
                    product = alt,
                    offers = altOffers,
                    settingsBySupplier = settingsMap,
                    nowMillis = now
                )
            }
        }
    }

    override fun getApiProductOffersFlow(productId: Long): Flow<List<OfferWithSupplier>> {
        return combine(
            offerDao.getActiveOffersForProductFlow(productId),
            userDao.getAllUsersFlow(),
            analyticsDao.getAllSupplierPerformancesFlow(),
            remoteSupplierDirectory
        ) { offers, users, perfs, remoteDir ->
            val usersMap = users.associateBy { it.id }
            val perfMap = perfs.associateBy { it.supplierId }
            offers.map { o -> toOfferWithSupplier(o, usersMap, perfMap, remoteDir) }
        }
    }

    override suspend fun getApiProductOffers(productId: Long): List<OfferWithSupplier> {
        val offers = offerDao.getActiveOffersForProduct(productId)
        val users = userDao.getAllUsers().associateBy { it.id }
        val remoteDir = remoteSupplierDirectory.value
        val perfMap = offers.map { it.supplierId }.distinct()
            .mapNotNull { id -> analyticsDao.getSupplierPerformance(id)?.let { id to it } }
            .toMap()
        return offers.map { o -> toOfferWithSupplier(o, users, perfMap, remoteDir) }
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
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.createOffer(offer.copy(supplierId = current.id).toDto())) {
                is NetworkResult.Success -> {
                    val savedId = offerDao.insertOffer(res.data.toEntity())
                    Result.success(savedId)
                }
                else -> Result.failure(res.toException())
            }
        }
        val id = offerDao.insertOffer(offer.copy(supplierId = current.id))
        analyticsDao.insertStockRecord(
            StockRecordEntity(
                productId = offer.productId,
                supplierId = current.id,
                offerId = id,
                availableQuantity = offer.availableQuantity,
                reason = "Offer created"
            )
        )
        evaluateProductShortageRiskInternal(offer.productId)
        return Result.success(id)
    }

    override suspend fun putApiSupplierOffers(offer: SupplierOfferEntity): Result<Unit> {
        val current = getApiUsersMe()
        if (current == null || !current.role.isSupplier && current.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("Only Suppliers and Admin can update offers."))
        }
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.updateOffer(offer.id, offer.toDto())) {
                is NetworkResult.Success -> {
                    offerDao.updateOffer(res.data.toEntity())
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
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
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.updateOfferStatus(offerId, isActive)) {
                is NetworkResult.Success -> {
                    offerDao.setOfferActiveStatus(offerId, isActive)
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
        }
        offerDao.setOfferActiveStatus(offerId, isActive)
        return Result.success(Unit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getApiBasketFlow(): Flow<BasketWithDetails> {
        val coreFlow = combine(
            dataStoreManager.loggedInUserIdFlow,
            basketDao.getAllBasketsFlow(),
            basketDao.getAllBasketItemsFlow(),
            productDao.getAllProductsFlow(),
            offerDao.getAllOffersFlow()
        ) { buyerId, baskets, allItems, products, offers ->
            Quintet(buyerId, baskets, allItems, products, offers)
        }
        val metadataFlow = combine(
            userDao.getAllUsersFlow(),
            analyticsDao.getAllSupplierSettingsFlow(),
            remoteSupplierDirectory,
            basketRefreshTrigger,
            latestCommittedBasket
        ) { users, settingsList, remoteDir, refreshSeq, committed ->
            Quintet(users, settingsList, remoteDir, refreshSeq, committed)
        }
        val sessionFlow = combine(
            coreFlow,
            metadataFlow,
            dataStoreManager.authExecutionModeFlow
        ) { core, meta, execMode ->
            Triple(core, meta, execMode)
        }
        return sessionFlow.mapLatest { (core, meta, execMode) ->
            val seqBeforeRead = basketMutationSeq.get()
            val (buyerId, baskets, _, products, offers) = core
            val (users, settingsList, remoteDir, _, committedSnapshot) = meta
            val isProdMode = execMode == AuthExecutionMode.PRODUCTION_BACKEND_HTTP
            val effectiveBuyerId = resolveEffectiveBuyerId(
                loggedInUserId = buyerId,
                users = users,
                isProductionMode = isProdMode,
                allowLocalFallback = true
            ) ?: return@mapLatest emptyBasketWithDetails()

            val basket = basketDao.getActiveBasketForBuyer(effectiveBuyerId)
                ?: baskets.firstOrNull { it.buyerId == effectiveBuyerId && !it.isSaved }
                ?: return@mapLatest emptyBasketWithDetails()
            // Always read the latest committed rows for this basket from Room; mapLatest cancels
            // any superseded in-flight calculation so stale snapshots can never overtake new writes.
            val items = basketDao.getBasketItems(basket.id)
            val productsMap = products.associateByTo(mutableMapOf()) { it.id }
            val offersMap = offers.associateByTo(mutableMapOf()) { it.id }
            for (item in items) {
                if (!productsMap.containsKey(item.productId)) {
                    productDao.getProductById(item.productId)?.let { productsMap[it.id] = it }
                }
                if (!offersMap.containsKey(item.offerId)) {
                    offerDao.getOfferById(item.offerId)?.let { offersMap[it.id] = it }
                }
            }
            val usersMap = users.associateBy { it.id }
            val settingsMap = settingsList.associateBy { it.supplierId }

            val computed = buildBasketDetails(
                basketId = basket.id,
                items = items,
                productsMap = productsMap,
                offersMap = offersMap,
                usersMap = usersMap,
                settingsMap = settingsMap,
                remoteDir = remoteDir
            )

            // Guard against emitting a superseded Room snapshot if a newer committed mutation
            // completed while this query block was executing.
            val latest = latestCommittedBasket.value ?: committedSnapshot
            if (latest != null && latest.buyerId == effectiveBuyerId && latest.seq > seqBeforeRead) {
                latest.basket
            } else {
                computed
            }
        }.distinctUntilChanged()
    }

    private suspend fun readBasketDetailsForBuyer(buyerId: Long): BasketWithDetails {
        val basket = basketDao.getActiveBasketForBuyer(buyerId) ?: return emptyBasketWithDetails()
        val items = basketDao.getBasketItems(basket.id)
        val products = productDao.getAllProducts().associateBy { it.id }
        val offers = offerDao.getAllActiveOffers().associateByTo(mutableMapOf()) { it.id }
        for (item in items) {
            if (!offers.containsKey(item.offerId)) {
                offerDao.getOfferById(item.offerId)?.let { offers[it.id] = it }
            }
        }
        val users = userDao.getAllUsers().associateBy { it.id }
        val settingsMap = analyticsDao.getAllSupplierSettings().associateBy { it.supplierId }
        return buildBasketDetails(
            basketId = basket.id,
            items = items,
            productsMap = products,
            offersMap = offers,
            usersMap = users,
            settingsMap = settingsMap,
            remoteDir = remoteSupplierDirectory.value
        )
    }

    override suspend fun getApiBasket(): BasketWithDetails {
        val buyerId = resolveActiveBuyerId() ?: return emptyBasketWithDetails()
        return readBasketDetailsForBuyer(buyerId)
    }

    override suspend fun refreshBasketFromBackend(): Result<Unit> {
        if (!isProductionHttpMode()) return Result.success(Unit)
        val remote = pharmaRemoteDataSource
            ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
        val buyerId = getApiUsersMe()?.id
            ?: return Result.failure(IllegalStateException("Please sign in to synchronize your basket."))
        val seqBeforeFetch = basketMutationSeq.get()
        return when (val res = remote.fetchActiveBasket()) {
            is NetworkResult.Success -> {
                // Only apply fetched snapshot if no newer basket mutation occurred while GET /basket was in flight.
                if (basketMutationSeq.get() == seqBeforeFetch) {
                    syncRemoteBasketToRoom(buyerId, res.data.items)
                }
                Result.success(Unit)
            }
            else -> Result.failure(res.toException())
        }
    }

    override suspend fun refreshSavedBasketsFromBackend(): Result<Unit> {
        if (!isProductionHttpMode()) return Result.success(Unit)
        val remote = pharmaRemoteDataSource
            ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
        val buyerId = getApiUsersMe()?.id
            ?: return Result.failure(IllegalStateException("Please sign in to load saved templates."))
        return when (val res = remote.fetchSavedBaskets()) {
            is NetworkResult.Success -> {
                val mapped = res.data.map { dto ->
                    NamedBasketSummary(
                        id = dto.id,
                        name = dto.name,
                        itemCount = dto.itemCount,
                        updatedAtEpochMillis = dto.updatedAtEpochMillis
                    )
                }.sortedByDescending { it.updatedAtEpochMillis }
                remoteSavedBasketsCache.value = remoteSavedBasketsCache.value + (buyerId to mapped)
                Result.success(Unit)
            }
            else -> Result.failure(res.toException())
        }
    }

    private suspend fun buildBasketDetails(
        basketId: Long,
        items: List<BasketItemEntity>,
        productsMap: Map<Long, ProductEntity>,
        offersMap: Map<Long, SupplierOfferEntity>,
        usersMap: Map<Long, UserEntity>,
        settingsMap: Map<Long, SupplierSettingsEntity> = emptyMap(),
        remoteDir: Map<Long, Pair<String?, String?>> = emptyMap()
    ): BasketWithDetails {
        val details = mutableListOf<BasketItemDetail>()
        val supplierTotals = mutableMapOf<Long, SupplierAccumulator>()

        for (item in items) {
            val prod = productsMap[item.productId] ?: continue
            val offer = offersMap[item.offerId] ?: continue
            val supplier = usersMap[offer.supplierId]
            val supplierName = supplier?.businessName?.takeIf { it.isNotBlank() }
                ?: supplier?.fullName?.takeIf { it.isNotBlank() }
                ?: remoteDir[offer.supplierId]?.first?.takeIf { it.isNotBlank() }
                ?: "Supplier #${offer.supplierId}"

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
                val settings = settingsMap[offer.supplierId]
                    ?: analyticsDao.getSupplierSettings(offer.supplierId)
                    ?: SupplierSettingsEntity(supplierId = offer.supplierId)
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
        if (quantity <= 0) {
            return Result.failure(IllegalArgumentException("Quantity must be greater than 0."))
        }
        val product = productDao.getProductById(productId)
        if (product != null) {
            if (!product.isActive) {
                return Result.failure(IllegalStateException("This product is currently inactive and cannot be added."))
            }
            if (!product.approvalStatus.equals(com.example.domain.model.MedicineSubmissionStatus.APPROVED.name, ignoreCase = true)) {
                return Result.failure(IllegalStateException("This medicine is not yet approved for commercial trading."))
            }
            if (!product.catalogueStatus.equals(com.example.domain.model.CataloguePublicationStatus.ACTIVE.name, ignoreCase = true)) {
                return Result.failure(IllegalStateException("This medicine is not commercially active in the catalogue (${product.catalogueStatus})."))
            }
        }

        val now = System.currentTimeMillis()
        val cachedOffer = if (offerId > 0L) {
            offerDao.getOfferById(offerId)
        } else {
            offerDao.getActiveOffersForProduct(productId)
                .filter { isOfferEligible(it, now) }
                .minByOrNull { it.unitPricePtr }
        }

        if (cachedOffer != null) {
            if (!cachedOffer.isActive) {
                return Result.failure(IllegalStateException("Selected supplier quotation is inactive."))
            }
            if (cachedOffer.validTillEpochMillis in 1..now) {
                return Result.failure(IllegalStateException("Selected supplier quotation has expired."))
            }
            if (cachedOffer.unitPricePtr <= 0.0) {
                return Result.failure(IllegalStateException("Selected supplier quotation does not have a valid unit price."))
            }
            if (cachedOffer.availableQuantity <= 0) {
                return Result.failure(IllegalStateException("Selected supplier offer is currently out of stock."))
            }
            if (cachedOffer.availableQuantity < cachedOffer.moq) {
                return Result.failure(
                    IllegalStateException(
                        "Offer unavailable: available stock (${cachedOffer.availableQuantity}) is below supplier MOQ (${cachedOffer.moq})."
                    )
                )
            }
            if (quantity < cachedOffer.moq) {
                return Result.failure(
                    IllegalArgumentException("Minimum order quantity (MOQ) for this supplier is ${cachedOffer.moq} units.")
                )
            }
            if (quantity > cachedOffer.availableQuantity) {
                return Result.failure(
                    IllegalArgumentException("Requested quantity ($quantity) exceeds available supplier stock (${cachedOffer.availableQuantity}).")
                )
            }
        }

        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            val buyerId = getApiUsersMe()?.id
                ?: return Result.failure(IllegalStateException("Please sign in before modifying the basket."))
            val resolvedOfferId = cachedOffer?.id ?: offerId
            if (resolvedOfferId <= 0L) {
                return Result.failure(IllegalArgumentException("Please select an eligible supplier quotation."))
            }
            if (cachedOffer != null) {
                val activeBasket = basketDao.getActiveBasketForBuyer(buyerId)
                if (activeBasket != null) {
                    val existingSameOffer = basketDao.getBasketItemByProductAndOffer(activeBasket.id, productId, resolvedOfferId)
                    if (existingSameOffer != null && existingSameOffer.quantity + quantity > cachedOffer.availableQuantity) {
                        return Result.failure(
                            IllegalArgumentException(
                                "Cannot add $quantity more units: basket already has ${existingSameOffer.quantity} and only ${cachedOffer.availableQuantity} are available."
                            )
                        )
                    }
                }
            }
            return when (val res = remote.addBasketItem(productId, resolvedOfferId, quantity)) {
                is NetworkResult.Success -> {
                    syncRemoteBasketToRoom(buyerId, res.data.items)
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
        }

        val safeOffer = cachedOffer
            ?: return Result.failure(IllegalStateException("No eligible supplier quotation is available for this product."))
        val buyerId = getApiUsersMe()?.id ?: ensureDefaultStockistUser().id

        return try {
            db.withTransaction {
                var basket = basketDao.getActiveBasketForBuyer(buyerId)
                if (basket == null) {
                    val newId = basketDao.insertBasket(BasketEntity(buyerId = buyerId, isSaved = false))
                    basket = BasketEntity(id = newId, buyerId = buyerId, isSaved = false)
                }

                val existingRows = basketDao.getBasketItemsByProduct(basket.id, productId)
                if (existingRows.isNotEmpty()) {
                    val primaryRow = existingRows.firstOrNull { it.offerId == safeOffer.id } ?: existingRows.first()
                    existingRows.filter { it.id != primaryRow.id }.forEach { duplicate ->
                        basketDao.deleteBasketItem(duplicate.id)
                    }
                    if (primaryRow.offerId == safeOffer.id) {
                        val combinedQty = primaryRow.quantity + quantity
                        if (combinedQty > safeOffer.availableQuantity) {
                            throw IllegalArgumentException(
                                "Cannot add $quantity more units: basket already has ${primaryRow.quantity} units and supplier has ${safeOffer.availableQuantity} available."
                            )
                        }
                        basketDao.updateBasketItem(primaryRow.id, combinedQty, safeOffer.id, safeOffer.supplierId)
                    } else {
                        basketDao.updateBasketItem(primaryRow.id, quantity, safeOffer.id, safeOffer.supplierId)
                    }
                } else {
                    basketDao.insertBasketItem(
                        BasketItemEntity(
                            basketId = basket.id,
                            productId = productId,
                            offerId = safeOffer.id,
                            supplierId = safeOffer.supplierId,
                            quantity = quantity
                        )
                    )
                }
                basketDao.updateBasketTimestamp(basket.id, now)
            }
            notifyBasketChanged()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun syncRemoteBasketToRoom(
        buyerId: Long,
        remoteItems: List<com.example.data.remote.dto.BasketItemDto>
    ) {
        val now = System.currentTimeMillis()
        val supplierUpdates = mutableMapOf<Long, Pair<String?, String?>>()
        db.withTransaction {
            var basket = basketDao.getActiveBasketForBuyer(buyerId)
            if (basket == null) {
                val newId = basketDao.insertBasket(BasketEntity(buyerId = buyerId, isSaved = false, updatedAtEpochMillis = now))
                basket = BasketEntity(id = newId, buyerId = buyerId, isSaved = false, updatedAtEpochMillis = now)
            }
            basketDao.clearBasket(basket.id)
            remoteItems.forEach { item ->
                if (productDao.getProductById(item.productId) == null) {
                    productDao.insertProduct(
                        ProductEntity(
                            id = item.productId,
                            name = item.productName,
                            brand = item.brand,
                            saltComposition = item.saltComposition,
                            strength = "",
                            dosageForm = DosageForm.TABLET.name,
                            therapeuticCategory = "General",
                            manufacturerName = item.supplierName.ifBlank { "Authorized Manufacturer" },
                            packSize = item.packSize,
                            hsnCode = item.hsnCode,
                            gstRatePercent = item.gstRatePercent,
                            storageCondition = StorageCondition.ROOM_TEMPERATURE.name,
                            isRx = true,
                            isActive = true,
                            createdBy = item.supplierId
                        )
                    )
                }
                val existingOffer = offerDao.getOfferById(item.offerId)
                if (existingOffer == null) {
                    offerDao.insertOffer(
                        SupplierOfferEntity(
                            id = item.offerId,
                            productId = item.productId,
                            supplierId = item.supplierId,
                            unitPricePtr = item.unitPricePtr,
                            mrp = item.mrp,
                            moq = item.moq,
                            availableQuantity = item.availableQuantity,
                            schemeType = SchemeType.NONE.name,
                            validTillEpochMillis = now + 30L * 24L * 60L * 60L * 1000L,
                            isActive = true,
                            lastUpdatedEpochMillis = now
                        )
                    )
                } else {
                    offerDao.insertOffer(
                        existingOffer.copy(
                            unitPricePtr = item.unitPricePtr,
                            mrp = item.mrp,
                            moq = item.moq,
                            availableQuantity = item.availableQuantity,
                            isActive = true,
                            lastUpdatedEpochMillis = now
                        )
                    )
                }
                if (item.supplierName.isNotBlank()) {
                    supplierUpdates[item.supplierId] = (item.supplierName to null)
                }
                basketDao.insertBasketItem(
                    BasketItemEntity(
                        id = item.itemId,
                        basketId = basket.id,
                        productId = item.productId,
                        offerId = item.offerId,
                        supplierId = item.supplierId,
                        quantity = item.quantity
                    )
                )
            }
            basketDao.updateBasketTimestamp(basket.id, now)
        }
        if (supplierUpdates.isNotEmpty()) {
            remoteSupplierDirectory.value = remoteSupplierDirectory.value + supplierUpdates
        }
        notifyBasketChanged(buyerId)
    }

    override suspend fun putApiBasketItemQuantity(itemId: Long, quantity: Int): Result<Unit> {
        if (quantity < 0) {
            return Result.failure(IllegalArgumentException("Quantity cannot be negative."))
        }
        if (quantity == 0) {
            return Result.failure(IllegalArgumentException("Quantity must be at least 1. Tap Remove to delete this item."))
        }

        val now = System.currentTimeMillis()
        val buyerId = resolveActiveBuyerId()
        val basket = buyerId?.let { basketDao.getActiveBasketForBuyer(it) }
        val existingItem = basket?.let { basketDao.getBasketItems(it.id).firstOrNull { item -> item.id == itemId } }
        val offer = existingItem?.let { offerDao.getOfferById(it.offerId) }

        if (offer != null) {
            if (!offer.isActive) {
                return Result.failure(IllegalStateException("This supplier quotation is no longer active."))
            }
            if (offer.validTillEpochMillis in 1..now) {
                return Result.failure(IllegalStateException("This supplier quotation has expired."))
            }
            if (offer.availableQuantity <= 0) {
                return Result.failure(IllegalStateException("This item is currently out of stock."))
            }
            if (quantity < offer.moq) {
                return Result.failure(IllegalArgumentException("Quantity cannot be below supplier MOQ (${offer.moq})."))
            }
            if (quantity > offer.availableQuantity) {
                return Result.failure(IllegalArgumentException("Quantity ($quantity) exceeds available stock (${offer.availableQuantity})."))
            }
        }

        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            val signedInBuyerId = getApiUsersMe()?.id
                ?: return Result.failure(IllegalStateException("Please sign in."))
            return when (val res = remote.updateBasketItemQuantity(itemId, quantity)) {
                is NetworkResult.Success -> {
                    syncRemoteBasketToRoom(signedInBuyerId, res.data.items)
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
        }

        if (basket == null || existingItem == null) {
            return Result.failure(IllegalStateException("Basket item not found."))
        }
        db.withTransaction {
            basketDao.updateBasketItemQuantity(itemId, quantity)
            basketDao.updateBasketTimestamp(basket.id, now)
        }
        notifyBasketChanged(buyerId)
        return Result.success(Unit)
    }

    override suspend fun deleteApiBasketItem(itemId: Long): Result<Unit> {
        val buyerId = resolveActiveBuyerId()
        val activeBasket = buyerId?.let { basketDao.getActiveBasketForBuyer(it) }
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            getApiUsersMe()?.id ?: return Result.failure(IllegalStateException("Please sign in."))
            return when (val res = remote.removeBasketItem(itemId)) {
                is NetworkResult.Success -> {
                    db.withTransaction {
                        basketDao.deleteBasketItem(itemId)
                        activeBasket?.let { basketDao.updateBasketTimestamp(it.id) }
                    }
                    notifyBasketChanged(buyerId)
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
        }
        db.withTransaction {
            basketDao.deleteBasketItem(itemId)
            activeBasket?.let { basketDao.updateBasketTimestamp(it.id) }
        }
        notifyBasketChanged(buyerId)
        return Result.success(Unit)
    }

    override suspend fun deleteApiBasketClear(): Result<Unit> {
        val buyerId = resolveActiveBuyerId() ?: return Result.success(Unit)
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            val signedInBuyerId = getApiUsersMe()?.id
                ?: return Result.failure(IllegalStateException("Please sign in."))
            return when (val res = remote.clearBasket()) {
                is NetworkResult.Success -> {
                    basketDao.getActiveBasketForBuyer(signedInBuyerId)?.let { basket ->
                        db.withTransaction {
                            basketDao.clearBasket(basket.id)
                            basketDao.updateBasketTimestamp(basket.id)
                        }
                    }
                    notifyBasketChanged(signedInBuyerId)
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
        }
        val basket = basketDao.getActiveBasketForBuyer(buyerId) ?: return Result.success(Unit)
        db.withTransaction {
            basketDao.clearBasket(basket.id)
            basketDao.updateBasketTimestamp(basket.id)
        }
        notifyBasketChanged(buyerId)
        return Result.success(Unit)
    }

    override suspend fun postApiBasketSaveNamed(name: String): Result<Long> =
        postApiBasketSaveNamed(name = name, overwriteExisting = false)

    override suspend fun postApiBasketSaveNamed(name: String, overwriteExisting: Boolean): Result<Long> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter a template name."))
        }
        if (trimmedName.length > 60) {
            return Result.failure(IllegalArgumentException("Template name must be 60 characters or fewer."))
        }
        val buyer = getApiUsersMe() ?: return Result.failure(IllegalStateException("Please sign in to save basket templates."))

        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            val activeBasketItems = basketDao.getActiveBasketForBuyer(buyer.id)?.let { basketDao.getBasketItems(it.id) }.orEmpty()
            if (activeBasketItems.isEmpty()) {
                return Result.failure(IllegalStateException("Cannot save an empty basket as a template."))
            }
            if (!overwriteExisting) {
                refreshSavedBasketsFromBackend()
                val existingRemote = remoteSavedBasketsCache.value[buyer.id].orEmpty()
                    .firstOrNull { it.name.trim().equals(trimmedName, ignoreCase = true) }
                if (existingRemote != null) {
                    return Result.failure(DuplicateBasketTemplateException(trimmedName))
                }
            }
            return when (val res = remote.saveNamedBasket(trimmedName)) {
                is NetworkResult.Success -> {
                    val savedDto = res.data
                    val summary = NamedBasketSummary(
                        id = savedDto.id,
                        name = savedDto.name,
                        itemCount = savedDto.itemCount,
                        updatedAtEpochMillis = savedDto.updatedAtEpochMillis
                    )
                    val existingList = remoteSavedBasketsCache.value[buyer.id].orEmpty()
                        .filterNot { it.id == savedDto.id || it.name.trim().equals(trimmedName, ignoreCase = true) }
                    remoteSavedBasketsCache.value = remoteSavedBasketsCache.value +
                        (buyer.id to (listOf(summary) + existingList).sortedByDescending { it.updatedAtEpochMillis })
                    Result.success(savedDto.id)
                }
                else -> Result.failure(res.toException())
            }
        }

        val activeBasket = basketDao.getActiveBasketForBuyer(buyer.id)
            ?: return Result.failure(IllegalStateException("Cannot save an empty basket as a template."))
        val items = basketDao.getBasketItems(activeBasket.id)
        if (items.isEmpty()) {
            return Result.failure(IllegalStateException("Cannot save an empty basket as a template."))
        }

        val existingDuplicate = basketDao.getSavedBasketByName(buyer.id, trimmedName)
        if (existingDuplicate != null && !overwriteExisting) {
            return Result.failure(DuplicateBasketTemplateException(trimmedName))
        }

        val now = System.currentTimeMillis()
        val savedBasketId = db.withTransaction {
            if (existingDuplicate != null && overwriteExisting) {
                basketDao.clearBasket(existingDuplicate.id)
                basketDao.deleteBasket(existingDuplicate.id)
            }
            val newId = basketDao.insertBasket(
                BasketEntity(
                    buyerId = buyer.id,
                    name = trimmedName,
                    isSaved = true,
                    updatedAtEpochMillis = now
                )
            )
            for (item in items) {
                basketDao.insertBasketItem(
                    item.copy(id = 0, basketId = newId)
                )
            }
            val persistedBasket = basketDao.getBasketById(newId)
            val persistedItems = basketDao.getBasketItems(newId)
            if (persistedBasket == null || !persistedBasket.isSaved || persistedItems.size != items.size) {
                throw IllegalStateException("Failed to verify saved template persistence.")
            }
            newId
        }
        return Result.success(savedBasketId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getApiSavedBasketsFlow(): Flow<List<NamedBasketSummary>> {
        return dataStoreManager.loggedInUserIdFlow.flatMapLatest { loggedId ->
            if (loggedId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    dataStoreManager.authExecutionModeFlow,
                    basketDao.getSavedBasketsFlow(loggedId),
                    basketDao.getAllBasketItemsFlow(),
                    remoteSavedBasketsCache
                ) { mode, baskets, allItems, remoteMap ->
                    if (mode == AuthExecutionMode.PRODUCTION_BACKEND_HTTP) {
                        remoteMap[loggedId].orEmpty()
                    } else {
                        baskets.map { b ->
                            val count = allItems.count { it.basketId == b.id }
                            NamedBasketSummary(
                                id = b.id,
                                name = b.name ?: "Saved Basket #${b.id}",
                                itemCount = count,
                                updatedAtEpochMillis = b.updatedAtEpochMillis
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun postApiBasketLoadSaved(savedBasketId: Long): Result<BasketLoadReport> {
        val buyer = getApiUsersMe() ?: return Result.failure(IllegalStateException("Please sign in."))
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            val loadRes = remote.loadSavedBasket(savedBasketId)
            if (loadRes !is NetworkResult.Success) {
                return Result.failure(loadRes.toException())
            }
            val refreshRes = refreshBasketFromBackend()
            if (refreshRes.isFailure) {
                return Result.failure(refreshRes.exceptionOrNull() ?: IllegalStateException("Loaded template on server, but failed to refresh basket."))
            }
            val dto = loadRes.data
            return Result.success(
                BasketLoadReport(
                    itemsLoadedCount = dto.itemsLoadedCount,
                    priceChangeWarnings = dto.priceChangeWarnings,
                    unavailableWarnings = dto.unavailableWarnings
                )
            )
        }

        val savedBasket = basketDao.getBasketById(savedBasketId)
            ?: return Result.failure(IllegalStateException("Saved basket template was not found."))
        val savedItems = basketDao.getBasketItems(savedBasketId)
        if (savedItems.isEmpty()) {
            return Result.failure(IllegalStateException("Saved basket template has no items."))
        }

        val now = System.currentTimeMillis()
        val priceWarnings = mutableListOf<String>()
        val unavailableWarnings = mutableListOf<String>()
        val itemsToLoad = mutableListOf<BasketItemEntity>()

        for (item in savedItems) {
            val offer = offerDao.getOfferById(item.offerId)
            val prod = productDao.getProductById(item.productId)
            val prodName = prod?.name ?: "Product #${item.productId}"

            if (prod != null && !prod.isActive) {
                unavailableWarnings.add("$prodName is no longer active in the catalogue.")
                continue
            }
            if (offer == null || !offer.isActive || (offer.validTillEpochMillis in 1..now) || offer.availableQuantity <= 0) {
                unavailableWarnings.add("$prodName is currently out of stock or supplier quotation expired.")
                continue
            }
            if (offer.availableQuantity < offer.moq) {
                unavailableWarnings.add("$prodName cannot be ordered because available stock (${offer.availableQuantity}) is below MOQ (${offer.moq}).")
                continue
            }

            var adjustedQty = item.quantity.coerceAtMost(offer.availableQuantity)
            if (adjustedQty < item.quantity) {
                unavailableWarnings.add("$prodName quantity reduced from ${item.quantity} to $adjustedQty due to available stock.")
            }
            if (adjustedQty < offer.moq) {
                adjustedQty = offer.moq
                unavailableWarnings.add("$prodName quantity raised to supplier MOQ (${offer.moq} units).")
            }

            itemsToLoad.add(
                BasketItemEntity(
                    basketId = 0L, // resolved inside transaction
                    productId = item.productId,
                    offerId = offer.id,
                    supplierId = offer.supplierId,
                    quantity = adjustedQty
                )
            )
        }

        if (itemsToLoad.isEmpty()) {
            return Result.failure(
                IllegalStateException(
                    unavailableWarnings.firstOrNull()
                        ?: "None of the items in '${savedBasket.name ?: "template"}' are currently available."
                )
            )
        }

        db.withTransaction {
            var activeBasket = basketDao.getActiveBasketForBuyer(buyer.id)
            if (activeBasket == null) {
                val newId = basketDao.insertBasket(BasketEntity(buyerId = buyer.id, isSaved = false, updatedAtEpochMillis = now))
                activeBasket = BasketEntity(id = newId, buyerId = buyer.id, isSaved = false, updatedAtEpochMillis = now)
            } else {
                basketDao.clearBasket(activeBasket.id)
            }
            itemsToLoad.forEach { entity ->
                basketDao.insertBasketItem(entity.copy(basketId = activeBasket.id))
            }
            basketDao.updateBasketTimestamp(activeBasket.id, now)
        }
        notifyBasketChanged()

        return Result.success(
            BasketLoadReport(
                itemsLoadedCount = itemsToLoad.size,
                priceChangeWarnings = priceWarnings,
                unavailableWarnings = unavailableWarnings
            )
        )
    }

    override suspend fun deleteApiSavedBasket(savedBasketId: Long): Result<Unit> {
        if (isProductionHttpMode()) {
            return Result.failure(UnsupportedOperationException("Deleting saved templates on remote server is not yet supported."))
        }
        db.withTransaction {
            basketDao.clearBasket(savedBasketId)
            basketDao.deleteBasket(savedBasketId)
        }
        return Result.success(Unit)
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
        notifyBasketChanged()
        return Result.success(Unit)
    }

    override suspend fun ensureDefaultOrdersSeeded() {
        if (isProductionHttpMode()) return
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

        // 4. PO-44780: Delivered Order (Completed past transaction)
        val o4 = OrderEntity(
            orderNumber = "PO-44780",
            buyerId = buyer.id,
            supplierId = supplier.id,
            status = OrderStatus.DELIVERED.name,
            subtotal = 89400.0,
            gstAmount = 10728.0,
            deliveryCharge = 0.0,
            totalAmount = 100128.0,
            deliveryAddress = "Apollo Health Hub, HSR Layout, Bengaluru, KA",
            notes = "Delivered and verified against digital invoice",
            isEwayBillGenerated = true,
            ewayBillNumber = "EWB-991827364510",
            vehicleNumber = "KA-01-MJ-8821",
            transporterName = "Gati KWE Express",
            dispatchEpochMillis = now - 96 * 3600 * 1000L,
            expectedDeliveryEpochMillis = now - 24 * 3600 * 1000L,
            deliveredEpochMillis = now - 22 * 3600 * 1000L,
            createdAtEpochMillis = now - 120 * 3600 * 1000L
        )
        val o4Id = orderDao.insertOrder(o4)
        val p4 = productDao.getAllProducts().firstOrNull { it.name.contains("Azithromycin", true) }
        val p4Id = p4?.id ?: 4L
        orderDao.insertOrderItems(
            listOf(
                OrderItemEntity(
                    orderId = o4Id,
                    productId = p4Id,
                    offerId = 4L,
                    productName = p4?.name ?: "Azithromycin 500mg",
                    brand = "Azithral 500",
                    packSize = "Strip of 5 Tabs",
                    hsnCode = "30042010",
                    gstRatePercent = 12.0,
                    orderedQuantity = 1200,
                    unitPricePtr = 74.50,
                    effectivePrice = 74.50,
                    batchNumber = "AZM-2024-K2"
                )
            )
        )
        orderDao.insertStatusHistory(OrderStatusHistoryEntity(orderId = o4Id, status = OrderStatus.PLACED.name, changedAtEpochMillis = now - 120 * 3600 * 1000L, note = "Purchase order generated", changedByUserId = buyer.id))
        orderDao.insertStatusHistory(OrderStatusHistoryEntity(orderId = o4Id, status = OrderStatus.ACCEPTED.name, changedAtEpochMillis = now - 110 * 3600 * 1000L, note = "PO accepted by stockist", changedByUserId = supplier.id))
        orderDao.insertStatusHistory(OrderStatusHistoryEntity(orderId = o4Id, status = OrderStatus.PACKED.name, changedAtEpochMillis = now - 100 * 3600 * 1000L, note = "Goods packed and stamped", changedByUserId = supplier.id))
        orderDao.insertStatusHistory(OrderStatusHistoryEntity(orderId = o4Id, status = OrderStatus.DISPATCHED.name, changedAtEpochMillis = now - 96 * 3600 * 1000L, note = "Out for delivery via Gati KWE", changedByUserId = supplier.id))
        orderDao.insertStatusHistory(OrderStatusHistoryEntity(orderId = o4Id, status = OrderStatus.DELIVERED.name, changedAtEpochMillis = now - 22 * 3600 * 1000L, note = "Consignment received & inspected by Store Manager", changedByUserId = buyer.id))

        // 5. PO-44835: Fresh Pending Order (Newly placed transaction)
        val o5 = OrderEntity(
            orderNumber = "PO-44835",
            buyerId = buyer.id,
            supplierId = supplier.id,
            status = OrderStatus.PLACED.name,
            subtotal = 38500.0,
            gstAmount = 4620.0,
            deliveryCharge = 500.0,
            totalAmount = 43620.0,
            deliveryAddress = "Max Healthcare Logistics Bay, Okhla Phase III, New Delhi, DL",
            notes = "Urgent replenishment for emergency room inventory",
            isEwayBillGenerated = false,
            createdAtEpochMillis = now - 3 * 3600 * 1000L
        )
        val o5Id = orderDao.insertOrder(o5)
        val p5 = productDao.getAllProducts().firstOrNull { it.name.contains("Pantoprazole", true) || it.name.contains("Metformin", true) }
        val p5Id = p5?.id ?: 5L
        orderDao.insertOrderItems(
            listOf(
                OrderItemEntity(
                    orderId = o5Id,
                    productId = p5Id,
                    offerId = 5L,
                    productName = p5?.name ?: "Pantoprazole 40mg IV",
                    brand = "Pan-IV 40",
                    packSize = "Vial of 10ml",
                    hsnCode = "30049099",
                    gstRatePercent = 12.0,
                    orderedQuantity = 500,
                    unitPricePtr = 77.0,
                    effectivePrice = 77.0,
                    batchNumber = "PNT-2024-V9"
                )
            )
        )
        orderDao.insertStatusHistory(OrderStatusHistoryEntity(orderId = o5Id, status = OrderStatus.PLACED.name, changedAtEpochMillis = now - 3 * 3600 * 1000L, note = "PO submitted by Hospital Procurement Officer", changedByUserId = buyer.id))
    }

    private fun buildOrderIdempotencyKey(
        buyerId: Long,
        deliveryAddress: String,
        notes: String,
        basketSignature: String
    ): String {
        val raw = "$buyerId|${deliveryAddress.trim()}|${notes.trim()}|$basketSignature"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    override suspend fun postApiOrders(deliveryAddress: String, notes: String): Result<List<Long>> {
        val trimmedAddress = deliveryAddress.trim()
        if (trimmedAddress.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter a valid delivery address."))
        }
        val buyer = getApiUsersMe() ?: return Result.failure(IllegalStateException("Please login to place order."))
        val activeBasket = basketDao.getActiveBasketForBuyer(buyer.id)
        val activeItems = if (activeBasket != null) basketDao.getBasketItems(activeBasket.id) else emptyList()
        if (activeItems.isEmpty()) {
            return Result.failure(IllegalStateException("Cannot place order with empty basket."))
        }
        // Commercial availability validation: every product in basket must be APPROVED + ACTIVE catalogue + active offer with stock
        for (item in activeItems) {
            val prod = productDao.getProductById(item.productId)
            if (prod != null) {
                if (!prod.isActive ||
                    !prod.approvalStatus.equals(com.example.domain.model.MedicineSubmissionStatus.APPROVED.name, ignoreCase = true) ||
                    !prod.catalogueStatus.equals(com.example.domain.model.CataloguePublicationStatus.ACTIVE.name, ignoreCase = true)
                ) {
                    return Result.failure(
                        IllegalStateException("'${prod.name}' is not commercially available for purchase (status: ${prod.approvalStatus}/${prod.catalogueStatus}).")
                    )
                }
            }
            val offer = offerDao.getOfferById(item.offerId)
            if (offer != null && (!offer.isActive || offer.availableQuantity < item.quantity)) {
                return Result.failure(
                    IllegalStateException("Supplier offer #${offer.id} does not have sufficient active stock (${offer.availableQuantity}) for requested quantity (${item.quantity}).")
                )
            }
        }
        val basketSignature = activeItems.joinToString(",") { "${it.productId}:${it.offerId}:${it.quantity}" }
        val submissionKey = buildOrderIdempotencyKey(buyer.id, trimmedAddress, notes.trim(), basketSignature)

        val acquired = orderSubmitMutex.withLock {
            if (inFlightOrderSubmissionKeys.contains(submissionKey)) {
                false
            } else {
                inFlightOrderSubmissionKeys.add(submissionKey)
                true
            }
        }
        if (!acquired) {
            return Result.failure(IllegalStateException("Order placement already in progress for this basket."))
        }

        try {
            if (isProductionHttpMode()) {
                val remote = pharmaRemoteDataSource
                    ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
                return when (
                    val res = remote.createOrdersFromBasket(
                        deliveryAddress = trimmedAddress,
                        notes = notes.trim(),
                        idempotencyKey = submissionKey
                    )
                ) {
                    is NetworkResult.Success -> {
                        if (res.data.isEmpty()) {
                            return Result.failure(IllegalStateException("Backend did not confirm order creation."))
                        }
                        val createdIds = db.withTransaction {
                            val ids = res.data.map { upsertRemoteOrder(it) }
                            basketDao.getActiveBasketForBuyer(buyer.id)?.let { basketDao.clearBasket(it.id) }
                            ids
                        }
                        notifyBasketChanged()
                        Result.success(createdIds)
                    }
                    else -> Result.failure(res.toException())
                }
            }
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

        notifyBasketChanged()
        return Result.success(orderIds)
        } finally {
            orderSubmitMutex.withLock {
                inFlightOrderSubmissionKeys.remove(submissionKey)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getApiOrdersFlow(): Flow<List<OrderEntity>> =
        dataStoreManager.loggedInUserIdFlow.flatMapLatest { userId ->
            val user = userId?.let { userDao.getUserById(it) }
            when {
                user == null -> flowOf(emptyList())
                user.role == UserRole.ADMIN.name -> orderDao.getAllOrdersFlow()
                UserRole.fromString(user.role).isSupplier -> orderDao.getOrdersForSupplierFlow(user.id)
                else -> orderDao.getOrdersForBuyerFlow(user.id)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getApiLivePipelineOrdersFlow(): Flow<List<OrderEntity>> =
        dataStoreManager.loggedInUserIdFlow.flatMapLatest { userId ->
            val user = userId?.let { userDao.getUserById(it) }
            when {
                user == null -> flowOf(emptyList())
                user.role == UserRole.ADMIN.name -> orderDao.getAllOrdersFlow()
                UserRole.fromString(user.role).isSupplier -> orderDao.getOrdersForSupplierFlow(user.id)
                else -> flowOf(emptyList())
            }
        }

    override suspend fun getOrderTaxInvoice(orderId: Long): Result<TaxInvoiceData> {
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.fetchOrderTaxInvoice(orderId)) {
                is NetworkResult.Success -> Result.success(res.data.toDomain())
                else -> Result.failure(res.toException())
            }
        }
        val details = getApiOrderById(orderId)
            ?: return Result.failure(IllegalArgumentException("Order not found."))
        val sellerCompliance = complianceDao.getComplianceByUserId(details.order.supplierId)
        return Result.success(
            TaxInvoiceBuilder.build(
                details = details,
                sellerComplianceGstin = sellerCompliance?.gstin
            )
        )
    }

    override suspend fun downloadBackendTaxInvoicePdf(orderId: Long): Result<ByteArray> {
        if (!isProductionHttpMode()) {
            return Result.failure(IllegalStateException("Backend PDF stream is only available in production backend mode."))
        }
        val remote = pharmaRemoteDataSource
            ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
        return when (val res = remote.downloadOrderTaxInvoicePdf(orderId)) {
            is NetworkResult.Success -> Result.success(res.data)
            else -> Result.failure(res.toException())
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
        if (!user.role.isSupplier && user.role != UserRole.ADMIN) {
            return Result.failure(
                SecurityException("Unauthorized: Only supplier or manufacturer accounts can progress order fulfillment statuses.")
            )
        }
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            val req = UpdateOrderStatusRequestDto(
                newStatus = newStatus,
                note = note,
                dispatchEpochMillis = dispatchEpochMillis,
                expectedDeliveryEpochMillis = expectedDeliveryEpochMillis,
                ewayBillNumber = ewayBillNumber,
                vehicleNumber = vehicleNumber,
                transporterName = transporterName
            )
            return when (val res = remote.updateOrderStatus(orderId, req)) {
                is NetworkResult.Success -> {
                    upsertRemoteOrder(res.data)
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
        }
        val order = orderDao.getOrderById(orderId) ?: return Result.failure(IllegalArgumentException("Order not found."))
        if (user.role != UserRole.ADMIN && order.supplierId != user.id) {
            return Result.failure(
                SecurityException("Unauthorized: Order ${order.orderNumber} belongs to another supplier.")
            )
        }

        db.withTransaction {
            val shouldIssueInvoice = newStatus == OrderStatus.PACKED.name ||
                newStatus == OrderStatus.DISPATCHED.name ||
                newStatus == OrderStatus.DELIVERED.name ||
                newStatus == OrderStatus.PARTIALLY_FULFILLED.name
            val resolvedInvoiceNumber = if (shouldIssueInvoice && order.invoiceNumber.isNullOrBlank()) {
                "INV-${order.orderNumber.removePrefix("PO-")}"
            } else {
                order.invoiceNumber
            }
            val resolvedInvoiceIssuedAt = if (resolvedInvoiceNumber != null) {
                order.invoiceIssuedAtEpochMillis ?: System.currentTimeMillis()
            } else {
                order.invoiceIssuedAtEpochMillis
            }

            val updated = order.copy(
                status = newStatus,
                dispatchEpochMillis = dispatchEpochMillis ?: order.dispatchEpochMillis,
                expectedDeliveryEpochMillis = expectedDeliveryEpochMillis ?: order.expectedDeliveryEpochMillis,
                deliveredEpochMillis = if (newStatus == OrderStatus.DELIVERED.name) System.currentTimeMillis() else order.deliveredEpochMillis,
                isEwayBillGenerated = !ewayBillNumber.isNullOrBlank() || order.isEwayBillGenerated,
                ewayBillNumber = ewayBillNumber ?: order.ewayBillNumber,
                vehicleNumber = vehicleNumber ?: order.vehicleNumber,
                transporterName = transporterName ?: order.transporterName,
                invoiceNumber = resolvedInvoiceNumber,
                invoiceIssuedAtEpochMillis = resolvedInvoiceIssuedAt
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
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (
                val res = remote.updateOrderItemFulfillment(
                    orderItemId,
                    FulfillOrderItemRequestDto(fulfilledQty, batchNumber?.trim(), expiryDateEpochMillis)
                )
            ) {
                is NetworkResult.Success -> {
                    orderDao.getOrderItemById(orderItemId)?.let { item ->
                        orderDao.updateOrderItem(
                            item.copy(
                                fulfilledQuantity = fulfilledQty,
                                batchNumber = batchNumber?.trim(),
                                expiryDateEpochMillis = expiryDateEpochMillis
                            )
                        )
                    }
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
        }
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
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (
                val res = remote.submitProofOfDelivery(
                    orderId,
                    SubmitPodRequestDto(
                        receivedByName = receivedByName.trim(),
                        notes = notes.trim(),
                        photoUri = photoUri,
                        shortQuantity = shortQuantity,
                        damagedQuantity = damagedQuantity
                    )
                )
            ) {
                is NetworkResult.Success -> Result.success(Unit)
                else -> Result.failure(res.toException())
            }
        }
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
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.reorderPastOrder(pastOrderId)) {
                is NetworkResult.Success -> {
                    refreshBasketFromBackend()
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
        }
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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getOrderHistoryTransactionsFlow(): Flow<List<com.example.ui.components.OrderTransactionUiModel>> {
        return combine(
            dataStoreManager.loggedInUserIdFlow,
            orderDao.getAllOrdersFlow(),
            orderDao.getAllOrderItemsFlow(),
            userDao.getAllUsersFlow()
        ) { currentUserId, allOrders, items, users ->
            val usersById = users.associateBy { it.id }
            val currentUser = currentUserId?.let { usersById[it] }
            val scopedOrders = when {
                currentUser == null -> emptyList()
                currentUser.role == UserRole.ADMIN.name -> allOrders
                UserRole.fromString(currentUser.role).isSupplier -> allOrders.filter { it.supplierId == currentUser.id }
                else -> allOrders.filter { it.buyerId == currentUser.id }
            }
            val itemsByOrderId = items.groupBy { it.orderId }

            scopedOrders.map { order ->
                val orderItems = itemsByOrderId[order.id] ?: emptyList()
                val buyer = usersById[order.buyerId]
                val supplier = usersById[order.supplierId]

                val status = try {
                    OrderStatus.valueOf(order.status)
                } catch (_: Exception) {
                    OrderStatus.PLACED
                }

                val itemsSummary = if (orderItems.isNotEmpty()) {
                    val names = orderItems.take(2).joinToString(", ") { it.productName }
                    if (orderItems.size > 2) "$names (+${orderItems.size - 2} more)" else names
                } else "Pharmaceutical Batch Supplies"

                val stageDesc = when (status) {
                    OrderStatus.PLACED -> "Order Acknowledged"
                    OrderStatus.ACCEPTED -> "Pallet Picking & Staging"
                    OrderStatus.PACKED -> "Serialization & QC Done"
                    OrderStatus.DISPATCHED -> "In Transit via ${order.transporterName ?: "Express"}"
                    OrderStatus.DELIVERED -> "Delivered & Verified"
                    OrderStatus.CANCELLED -> "Cancelled"
                    OrderStatus.PARTIALLY_FULFILLED -> "Partially Dispatched"
                }

                com.example.ui.components.OrderTransactionUiModel(
                    orderId = order.id,
                    orderNumber = order.orderNumber,
                    formattedDate = Formatters.formatDateTime(order.createdAtEpochMillis),
                    rawEpochMillis = order.createdAtEpochMillis,
                    status = status,
                    statusStageDescription = stageDesc,
                    buyerName = buyer?.businessName ?: "Apex Regional Stockist",
                    supplierName = supplier?.businessName ?: "Cipla Direct Dist. Hub",
                    totalAmount = order.totalAmount,
                    formattedTotalAmount = Formatters.formatInr(order.totalAmount),
                    subtotal = order.subtotal,
                    gstAmount = order.gstAmount,
                    deliveryCharge = order.deliveryCharge,
                    itemCount = if (orderItems.isNotEmpty()) orderItems.sumOf { it.orderedQuantity } else 100,
                    itemsSummary = itemsSummary,
                    isEwayBillGenerated = order.isEwayBillGenerated,
                    ewayBillNumber = order.ewayBillNumber,
                    transporterName = order.transporterName,
                    vehicleNumber = order.vehicleNumber,
                    expectedDeliveryDate = Formatters.formatDate(order.expectedDeliveryEpochMillis),
                    deliveryAddress = order.deliveryAddress
                )
            }
        }
    }

    private suspend fun requireInventoryAuthorizedUser(): Result<AuthUser> {
        val user = getApiUsersMe()
            ?: return Result.failure(SecurityException("Authentication required to access Real-Time Inventory."))
        if (!user.role.canAccessRealTimeInventory) {
            return Result.failure(
                SecurityException("Access denied (403): Real-Time Inventory is restricted to authorized Brand and Manufacturer accounts.")
            )
        }
        return Result.success(user)
    }

    override suspend fun isProductionBackendConnected(): Boolean = isProductionHttpMode()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getApiBatchesFlow(): Flow<List<BatchWithProduct>> =
        dataStoreManager.loggedInUserIdFlow.flatMapLatest { userId ->
            val user = userId?.let { userDao.getUserById(it) }
            val role = user?.let { UserRole.fromString(it.role) }
            if (user == null || role?.canAccessRealTimeInventory != true) {
                flowOf(emptyList())
            } else {
                combine(
                    batchDao.getBatchesForSupplierFlow(user.id),
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
        }

    override suspend fun getApiBatches(): List<BatchWithProduct> {
        val user = requireInventoryAuthorizedUser().getOrNull() ?: return emptyList()
        val batches = batchDao.getBatchesForSupplier(user.id)
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
        val authorizedUser = requireInventoryAuthorizedUser().getOrElse { return Result.failure(it) }
        val scopedBatch = batch.copy(supplierId = authorizedUser.id)
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.createBatch(scopedBatch.toDto())) {
                is NetworkResult.Success -> {
                    val returned = res.data.toEntity()
                    val persisted = if (returned.supplierId <= 0L) returned.copy(supplierId = authorizedUser.id) else returned
                    val savedId = batchDao.insertBatch(persisted)
                    Result.success(savedId)
                }
                else -> Result.failure(res.toException())
            }
        }
        val id = batchDao.insertBatch(scopedBatch)
        return Result.success(id)
    }

    override suspend fun refreshRealTimeInventory(): Result<Unit> {
        val authorizedUser = requireInventoryAuthorizedUser().getOrElse { return Result.failure(it) }
        if (!isProductionHttpMode()) {
            return Result.success(Unit)
        }
        val remote = pharmaRemoteDataSource
            ?: return Result.failure(IllegalStateException("Remote API is not initialized."))

        val batchesResult = remote.fetchBatches()
        return when (batchesResult) {
            is NetworkResult.Success -> {
                val remoteEntities = batchesResult.data.map { dto ->
                    val entity = dto.toEntity()
                    if (entity.supplierId <= 0L) entity.copy(supplierId = authorizedUser.id) else entity
                }
                db.withTransaction {
                    batchDao.deleteBatchesForSupplier(authorizedUser.id)
                    if (remoteEntities.isNotEmpty()) {
                        batchDao.insertBatches(remoteEntities)
                    }
                }
                // Also refresh SKU-level inventory records when endpoint is available
                when (val invRes = remote.fetchInventory()) {
                    is NetworkResult.Success -> {
                        if (invRes.data.isNotEmpty()) {
                            productInventoryDao.insertAll(invRes.data.map { it.toEntity() })
                        }
                    }
                    else -> Unit
                }
                Result.success(Unit)
            }
            else -> Result.failure(batchesResult.toException())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getRealTimeInventoryFlow(): Flow<List<InventoryStockItem>> =
        dataStoreManager.loggedInUserIdFlow.flatMapLatest { userId ->
            val user = userId?.let { userDao.getUserById(it) }
            val role = user?.let { UserRole.fromString(it.role) }
            if (user == null || role?.canAccessRealTimeInventory != true) {
                flowOf(emptyList())
            } else {
                combine(
                    batchDao.getBatchesForSupplierFlow(user.id),
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
                            mfgDateEpochMillis = b.mfgDateEpochMillis,
                            expiryDateEpochMillis = b.expiryDateEpochMillis,
                            daysUntilExpiry = daysUntilExpiry,
                            expiryStatus = expiryStatus,
                            storageCondition = storage,
                            mrp = b.mrp,
                            ptr = unitPrice,
                            totalValuation = totalVal,
                            isRecalled = isRecalled,
                            recallReason = matchingRecall?.reason,
                            criticalFlags = flags,
                            supplierId = b.supplierId
                        )
                    }

                    // Products owned by this brand/manufacturer with 0 batches registered -> Flag as Out of Stock
                    val productIdsWithBatches = batches.map { it.productId }.toSet()
                    val missingProductItems = products
                        .filter { it.createdBy == user.id && !productIdsWithBatches.contains(it.id) }
                        .map { p ->
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
                                mfgDateEpochMillis = null,
                                expiryDateEpochMillis = System.currentTimeMillis() + 86400000L * 365,
                                daysUntilExpiry = 365,
                                expiryStatus = ExpiryRiskStatus.HEALTHY,
                                storageCondition = storage,
                                mrp = 0.0,
                                ptr = 0.0,
                                totalValuation = 0.0,
                                isRecalled = false,
                                recallReason = null,
                                criticalFlags = listOf("No Stock Recorded / 0 Units Available"),
                                supplierId = user.id
                            )
                        }

                    (batchItems + missingProductItems).sortedWith(
                        compareByDescending<InventoryStockItem> { it.isCritical }
                            .thenBy { it.remainingQuantity }
                            .thenBy { it.daysUntilExpiry }
                    )
                }
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
        val authorizedUser = requireInventoryAuthorizedUser().getOrElse { return Result.failure(it) }
        val safeQty = newQuantity.coerceAtLeast(0)
        if (isProductionHttpMode() && batchId > 0) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.adjustBatchStock(batchId, safeQty, reason)) {
                is NetworkResult.Success -> {
                    val returned = res.data.toEntity()
                    val persisted = if (returned.supplierId <= 0L) returned.copy(supplierId = authorizedUser.id) else returned
                    batchDao.insertBatch(persisted)
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
        }
        return try {
            if (batchId > 0) {
                val existing = batchDao.getBatchById(batchId)
                    ?: return Result.failure(IllegalArgumentException("Batch not found."))
                if (existing.supplierId != authorizedUser.id) {
                    return Result.failure(
                        SecurityException("Access denied (403): You can only modify stock for your own organization's batches.")
                    )
                }
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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getProductInventoryFlow(): Flow<List<ProductInventoryEntity>> =
        dataStoreManager.loggedInUserIdFlow.flatMapLatest { userId ->
            val user = userId?.let { userDao.getUserById(it) }
            val role = user?.let { UserRole.fromString(it.role) }
            if (user == null || role?.canAccessRealTimeInventory != true) {
                flowOf(emptyList())
            } else {
                productInventoryDao.getAllInventoryFlow()
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getLowStockProductInventoryFlow(): Flow<List<ProductInventoryEntity>> =
        dataStoreManager.loggedInUserIdFlow.flatMapLatest { userId ->
            val user = userId?.let { userDao.getUserById(it) }
            val role = user?.let { UserRole.fromString(it.role) }
            if (user == null || role?.canAccessRealTimeInventory != true) {
                flowOf(emptyList())
            } else {
                productInventoryDao.getLowStockInventoryFlow()
            }
        }

    override suspend fun getProductInventoryBySku(sku: String): ProductInventoryEntity? {
        requireInventoryAuthorizedUser().getOrNull() ?: return null
        return productInventoryDao.getInventoryBySku(sku)
    }

    override suspend fun insertOrUpdateProductInventory(inventory: ProductInventoryEntity): Long {
        requireInventoryAuthorizedUser().getOrElse { throw it }
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource ?: throw IllegalStateException("Remote API is not initialized.")
            return when (val res = remote.upsertInventoryItem(inventory.toDto())) {
                is NetworkResult.Success -> productInventoryDao.insertInventory(res.data.toEntity())
                else -> throw res.toException()
            }
        }
        return productInventoryDao.insertInventory(inventory)
    }

    override suspend fun updateProductInventoryStock(sku: String, newStock: Int): Result<Unit> {
        requireInventoryAuthorizedUser().getOrElse { return Result.failure(it) }
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.adjustInventoryStock(sku = sku, newStock = newStock, reason = "Inventory Update")) {
                is NetworkResult.Success -> {
                    productInventoryDao.insertInventory(res.data.toEntity())
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
        }
        return try {
            productInventoryDao.updateCurrentStockBySku(sku, newStock)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateProductInventoryReorderThreshold(sku: String, newThreshold: Int): Result<Unit> {
        requireInventoryAuthorizedUser().getOrElse { return Result.failure(it) }
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.updateReorderThreshold(sku, newThreshold)) {
                is NetworkResult.Success -> {
                    productInventoryDao.insertInventory(res.data.toEntity())
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
        }
        return try {
            productInventoryDao.updateReorderThresholdBySku(sku, newThreshold)
            Result.success(Unit)
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
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.markNotificationRead(id)) {
                is NetworkResult.Success -> {
                    notificationDao.markAsRead(id)
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
        }
        notificationDao.markAsRead(id)
        return Result.success(Unit)
    }

    override suspend fun patchApiNotificationsReadAll(): Result<Unit> {
        val user = getApiUsersMe() ?: return Result.success(Unit)
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.markAllNotificationsRead()) {
                is NetworkResult.Success -> {
                    notificationDao.markAllAsRead(user.id)
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
        }
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
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (
                val res = remote.createRecall(
                    CreateRecallRequestDto(
                        productId = productId,
                        batchNumbersCsv = batchNumbersCsv.trim(),
                        reason = reason.trim(),
                        severity = severity
                    )
                )
            ) {
                is NetworkResult.Success -> {
                    val savedId = recallDao.insertRecall(res.data.toEntity())
                    Result.success(savedId)
                }
                else -> Result.failure(res.toException())
            }
        }
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
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (
                val res = remote.createReturn(
                    CreateReturnRequestDto(
                        orderId = orderId,
                        orderItemId = orderItemId,
                        quantity = quantity,
                        reason = reason.name,
                        batchNumber = batchNumber.trim()
                    )
                )
            ) {
                is NetworkResult.Success -> {
                    val savedId = returnDao.insertReturn(res.data.toEntity())
                    Result.success(savedId)
                }
                else -> Result.failure(res.toException())
            }
        }
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
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.decideReturn(returnId, isApproved, decisionNotes.trim())) {
                is NetworkResult.Success -> Result.success(Unit)
                else -> Result.failure(res.toException())
            }
        }
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
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.recordLedgerPayment(buyerId, amount, notes)) {
                is NetworkResult.Success -> Result.success(Unit)
                else -> Result.failure(res.toException())
            }
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
        if (isProductionHttpMode()) {
            val remote = pharmaRemoteDataSource
                ?: return Result.failure(IllegalStateException("Remote API is not initialized."))
            return when (val res = remote.updateSupplierSettings(settings.supplierId, settings.toDto())) {
                is NetworkResult.Success -> {
                    analyticsDao.insertOrUpdateSupplierSettings(res.data.toEntity())
                    Result.success(Unit)
                }
                else -> Result.failure(res.toException())
            }
        }
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
                val shortageAlertsCount = analyticsDao.getHighRiskShortageCount()
                val ledgerBal = ledgerDao.getTotalOutstandingBalanceForBuyer(user.id) ?: 0.0
                DashboardKpis(
                    role = UserRole.STOCKIST,
                    openOrdersCount = openOrders,
                    basketItemsCount = basketItemsCount,
                    nearExpiryBatchesCount = 0,
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

    private data class Quartet<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    private data class Quintet<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )
}

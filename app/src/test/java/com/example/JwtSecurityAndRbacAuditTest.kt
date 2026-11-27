package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.local.SecureTokenStorage
import com.example.data.local.SecureTokenStorageException
import com.example.data.local.entity.BasketEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.OfferBannerEntity
import com.example.data.local.entity.OrderEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.UserAddressEntity
import com.example.data.local.entity.UserEntity
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.remote.dto.UserDto
import com.example.data.remote.interceptor.ApiLoggingInterceptor
import com.example.data.remote.interceptor.JwtTokenAuthenticator
import com.example.data.remote.interceptor.TokenProvider
import com.example.data.remote.source.WorkspaceRemoteDataSource
import com.example.data.remote.upload.MediaUploadKind
import com.example.data.remote.upload.MediaUploadManager
import com.example.data.repository.PharmaRepositoryImpl
import com.example.data.repository.WorkspaceRepositoryImpl
import com.example.di.NetworkModule
import com.example.domain.model.AccessControlPolicy
import com.example.domain.model.AccountStatus
import com.example.domain.model.AuthUser
import com.example.domain.model.BannerStatus
import com.example.domain.model.CataloguePublicationStatus
import com.example.domain.model.MedicineSubmission
import com.example.domain.model.MedicineSubmissionStatus
import com.example.domain.model.OrganizationType
import com.example.domain.model.PermissionResolutionStatus
import com.example.domain.model.PharmaPermission
import com.example.domain.model.RolePermissionMatrix
import com.example.domain.model.SyncHealthStatus
import com.example.domain.model.UserRole
import com.example.domain.model.WorkspaceType
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive security, RBAC, JWT refresh, log scrubbing, media validation,
 * PUT-before-POST approval resubmit, and banner schedule reactivity verification suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JwtSecurityAndRbacAuditTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun productionAuthUserFailsClosedWhenPermissionsOrOrganizationIdMissing() {
        val missingPermissionsDto = UserDto(
            id = 42L,
            fullName = "Priya Nair",
            businessName = "Bharat Bio-Pharma",
            email = "priya@bharatbio.com",
            phone = "9876500001",
            address = "Mumbai",
            role = "PHARMA_COMPANY",
            organizationId = null,
            permissions = null
        )

        val prodUser = missingPermissionsDto.toAuthUser(isProductionMode = true)
        assertEquals(PermissionResolutionStatus.MISSING, prodUser.permissionStatus)
        assertTrue("Production mode must never synthesize default permissions", prodUser.permissions.isEmpty())
        assertEquals("Production mode must never substitute userId for missing organizationId", 0L, prodUser.organizationId)
        assertFalse(prodUser.hasValidOrganizationContext())
        assertFalse(WorkspaceType.COMPANY in prodUser.authorizedWorkspaces())

        val unknownPermDto = missingPermissionsDto.copy(
            organizationId = 200L,
            permissions = listOf("UNKNOWN_CUSTOM_SCOPE")
        )
        val unknownProdUser = unknownPermDto.toAuthUser(isProductionMode = true)
        assertEquals(PermissionResolutionStatus.INVALID, unknownProdUser.permissionStatus)
        assertTrue(unknownProdUser.permissions.isEmpty())

        val emptyPermDto = missingPermissionsDto.copy(
            organizationId = 200L,
            permissions = emptyList()
        )
        val emptyProdUser = emptyPermDto.toAuthUser(isProductionMode = true)
        assertEquals(PermissionResolutionStatus.EXPLICITLY_EMPTY, emptyProdUser.permissionStatus)
        assertTrue(emptyProdUser.permissions.isEmpty())
    }

    @Test
    fun defaultCompanyPermissionsExcludeRoleAssignAndManufacturerCannotEditApprovedProducts() {
        assertFalse(PharmaPermission.ROLE_ASSIGN in RolePermissionMatrix.companyPermissions)
        assertFalse(PharmaPermission.ORGANIZATION_MEMBER_MANAGE in RolePermissionMatrix.companyPermissions)
        assertTrue(PharmaPermission.ROLE_ASSIGN in RolePermissionMatrix.companyOrgAdminPermissions)

        val standardCompanyUser = AuthUser(
            id = 30L,
            fullName = "QA Officer",
            businessName = "Bharat Bio-Pharma",
            email = "qa@bharatbio.com",
            phone = "9876543211",
            gstin = null,
            address = "Mumbai",
            role = UserRole.PHARMA_COMPANY,
            status = AccountStatus.ACTIVE,
            organizationId = 200L,
            organizationType = OrganizationType.PHARMACEUTICAL_ORGANIZATION,
            organizationName = "Bharat Bio-Pharma",
            assignedRoles = setOf(UserRole.PHARMA_COMPANY),
            permissions = RolePermissionMatrix.companyPermissions
        )
        val orgAdminCompanyUser = standardCompanyUser.copy(
            permissions = RolePermissionMatrix.companyOrgAdminPermissions
        )

        assertTrue(AccessControlPolicy.canManageOrganizationRoles(standardCompanyUser, 200L).isFailure)
        assertTrue(AccessControlPolicy.canManageOrganizationRoles(orgAdminCompanyUser, 200L).isSuccess)

        val manufacturer = standardCompanyUser.copy(
            id = 20L,
            role = UserRole.MANUFACTURER,
            assignedRoles = setOf(UserRole.MANUFACTURER),
            permissions = RolePermissionMatrix.manufacturerPermissions
        )
        val draftSub = MedicineSubmission(
            id = 1L,
            organizationId = 200L,
            organizationName = "Bharat Bio-Pharma",
            submittedByUserId = 20L,
            submittedByName = "Ops",
            submittedByRole = UserRole.MANUFACTURER,
            medicineName = "CardioShield 40",
            brandName = "CardioShield",
            genericName = "Atorvastatin",
            saltComposition = "Atorvastatin 40mg",
            dosageForm = "Tablet",
            strength = "40mg",
            packSize = "10 Tabs",
            therapeuticCategory = "Cardiology",
            mrp = 150.0,
            wholesaleRatePtr = 100.0,
            hsnCode = "30049099",
            gstRatePercent = 12.0,
            approvalStatus = MedicineSubmissionStatus.DRAFT
        )
        val approvedSub = draftSub.copy(approvalStatus = MedicineSubmissionStatus.APPROVED)

        assertTrue(AccessControlPolicy.canEditMedicineSubmission(manufacturer, draftSub).isSuccess)
        assertTrue(AccessControlPolicy.canEditMedicineSubmission(manufacturer, approvedSub).isFailure)
        assertTrue(AccessControlPolicy.canEditApprovedCatalogueProduct(manufacturer, 200L).isFailure)
        assertTrue(AccessControlPolicy.canEditApprovedCatalogueProduct(standardCompanyUser, 200L).isSuccess)
    }

    @Test
    fun secureTokenStorageEncryptsAtRestAndReleaseDataStoreBlocksDevModeAndCleartext() = runBlocking {
        val prefsName = "test_secure_tokens_${System.nanoTime()}"
        val storage = SecureTokenStorage.createForUnitTest(context, prefsFileName = prefsName)
        val sampleAccess = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMCJ9.signature_access_12345"
        val sampleRefresh = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMCJ9.signature_refresh_67890"

        storage.saveTokens(sampleAccess, sampleRefresh)
        assertEquals(sampleAccess, storage.getAccessToken())
        assertEquals(sampleRefresh, storage.getRefreshToken())

        // Raw SharedPreferences file must NEVER store plaintext JWTs
        val rawPrefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val allRawValues = rawPrefs.all.values.joinToString(" ")
        assertFalse("Raw prefs must not contain plaintext access JWT", allRawValues.contains(sampleAccess))
        assertFalse("Raw prefs must not contain plaintext refresh JWT", allRawValues.contains(sampleRefresh))

        storage.clearTokens()
        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())

        // Release-configured DataStoreManager blocks dev mode & cleartext overrides
        val releaseDataStore = DataStoreManager(
            context = context,
            secureTokenStorage = storage,
            allowDevModeOverrides = false
        )
        val devModeBlocked = runCatching {
            releaseDataStore.setAuthExecutionMode(AuthExecutionMode.DEVELOPMENT_LOCAL_MODE)
        }.exceptionOrNull()
        assertTrue("Release mode must reject DEVELOPMENT_LOCAL_MODE with SecurityException", devModeBlocked is SecurityException)
        assertEquals(AuthExecutionMode.PRODUCTION_BACKEND_HTTP, releaseDataStore.authExecutionModeFlow.first())

        val cleartextBlocked = runCatching {
            releaseDataStore.setBackendBaseUrl("http://insecure.example.com/")
        }.exceptionOrNull()
        assertTrue("Release mode must reject cleartext http:// URL override with SecurityException", cleartextBlocked is SecurityException)
        val resolvedUrl = releaseDataStore.backendBaseUrlFlow.first()
        assertFalse("Release mode must not expose cleartext http:// URL", resolvedUrl.startsWith("http://insecure"))
    }

    @Test
    fun networkModuleSingleFlightRotatesTokensAndSeparates401From403() = runBlocking {
        val tokenStore = object : TokenProvider {
            @Volatile var access: String? = "expired_access_token"
            @Volatile var refresh: String? = "valid_refresh_v1"
            override fun getAccessToken(): String? = access
            override fun getRefreshToken(): String? = refresh
            override fun updateTokens(accessToken: String?, refreshToken: String?) {
                access = accessToken
                if (!refreshToken.isNullOrBlank()) refresh = refreshToken
            }
            override fun clearTokens() {
                access = null
                refresh = null
            }
        }

        val refreshCallCount = AtomicInteger(0)
        val mockBackend = Interceptor { chain ->
            val req = chain.request()
            val path = req.url.encodedPath
            val auth = req.header("Authorization")
            when {
                path.endsWith("/api/v1/auth/refresh") -> {
                    val count = refreshCallCount.incrementAndGet()
                    Thread.sleep(60)
                    val body = """
                        {
                          "success": true,
                          "data": {
                            "accessToken": "rotated_access_v$count",
                            "refreshToken": "rotated_refresh_v$count",
                            "user": {
                              "id": 10,
                              "fullName": "Rohan",
                              "businessName": "Apex",
                              "email": "r@a.com",
                              "phone": "9999999999",
                              "address": "Mumbai",
                              "role": "STOCKIST"
                            }
                          }
                        }
                    """.trimIndent()
                    Response.Builder()
                        .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                path.endsWith("/forbidden") -> {
                    Response.Builder()
                        .request(req).protocol(Protocol.HTTP_1_1).code(403).message("Forbidden")
                        .body("""{"message":"Permission denied"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                path.endsWith("/always-401") -> {
                    Response.Builder()
                        .request(req).protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized")
                        .body("""{"message":"Still unauthorized"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                auth == "Bearer rotated_access_v1" -> {
                    Response.Builder()
                        .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("""{"ok":true}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                else -> {
                    Response.Builder()
                        .request(req).protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized")
                        .body("""{"message":"Token expired"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            }
        }

        val refreshClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor(mockBackend)
            .build()
        val sessionExpiredEvents = CopyOnWriteArrayList<String>()
        val moshi = com.squareup.moshi.Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val authenticator = com.example.data.remote.interceptor.JwtTokenAuthenticator(
            tokenProvider = tokenStore,
            refreshHttpClient = refreshClient,
            moshi = moshi,
            baseUrlProvider = { "https://api.pharmaos.example" },
            onSessionExpired = { sessionExpiredEvents.add(it) }
        )

        val networkModule = NetworkModule(
            tokenProvider = tokenStore,
            initialBaseUrl = "https://api.pharmaos.example/",
            extraInterceptors = listOf(mockBackend)
        )

        val forbiddenEvents = CopyOnWriteArrayList<String>()
        val forbiddenJob = launch(Dispatchers.Unconfined) {
            networkModule.permissionDeniedEvents.collect { forbiddenEvents.add(it) }
        }

        try {
            // 1. Two concurrent 401 challenges must trigger ONLY ONE refresh call (single-flight)
            val retriedRequests = listOf(1, 2).map { idx ->
                async(Dispatchers.IO) {
                    val failedReq = Request.Builder()
                        .url("https://api.pharmaos.example/api/v1/orders/$idx")
                        .header("Authorization", "Bearer expired_access_token")
                        .build()
                    val unauthorizedResp = Response.Builder()
                        .request(failedReq)
                        .protocol(Protocol.HTTP_1_1)
                        .code(401)
                        .message("Unauthorized")
                        .body("""{"message":"Token expired"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                    authenticator.authenticate(null, unauthorizedResp)
                }
            }.awaitAll()

            assertTrue(retriedRequests.all { it != null })
            assertEquals(
                listOf("Bearer rotated_access_v1", "Bearer rotated_access_v1"),
                retriedRequests.map { it?.header("Authorization") }
            )
            assertEquals("Concurrent 401s must execute exactly 1 refresh call", 1, refreshCallCount.get())
            assertEquals("rotated_access_v1", tokenStore.getAccessToken())
            assertEquals("rotated_refresh_v1", tokenStore.getRefreshToken())
            assertTrue(sessionExpiredEvents.isEmpty())

            // 2. HTTP 403 Forbidden emits permissionDeniedEvents and NEVER triggers refresh or logout
            val forbiddenReq = Request.Builder().url("https://api.pharmaos.example/api/v1/forbidden").build()
            val forbiddenCode = networkModule.okHttpClient.newCall(forbiddenReq).execute().use { it.code }
            assertEquals(403, forbiddenCode)
            assertEquals(1, refreshCallCount.get())
            assertTrue(sessionExpiredEvents.isEmpty())
            assertEquals(1, forbiddenEvents.size)
            assertNotNull("Tokens must remain intact after HTTP 403", tokenStore.getAccessToken())

            // 3. Already-retried 401 stops immediately (max 1 retry loop guard) and expires session
            val secondFailureReq = retriedRequests.first()!!
            val second401Resp = Response.Builder()
                .request(secondFailureReq)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .body("""{"message":"Still unauthorized"}""".toResponseBody("application/json".toMediaType()))
                .build()
            val abortResult = authenticator.authenticate(null, second401Resp)
            assertNull("Authenticator must abort after max 1 retry to prevent 401 loops", abortResult)
            assertEquals(1, sessionExpiredEvents.size)
            assertNull("Tokens must be cleared after unrecoverable 401", tokenStore.getAccessToken())
        } finally {
            forbiddenJob.cancel()
        }
    }

    @Test
    fun apiLoggingInterceptorRedactsTokensPasswordsAndJwts() {
        val rawJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        val sensitiveLine = """{"email":"a@b.com","password":"SuperSecret123!","accessToken":"$rawJwt","refreshToken":"ref_999"}"""
        val sanitized = ApiLoggingInterceptor.sanitizeLogLine(sensitiveLine)

        assertFalse(sanitized.contains("SuperSecret123!"))
        assertFalse(sanitized.contains(rawJwt))
        assertFalse(sanitized.contains("ref_999"))
        assertTrue(sanitized.contains("[REDACTED]"))

        val headerSanitized = ApiLoggingInterceptor.sanitizeLogLine("Authorization: Bearer $rawJwt")
        assertFalse(headerSanitized.contains(rawJwt))
        assertTrue(headerSanitized.contains("Bearer [REDACTED]"))
    }

    @Test
    fun mediaUploadManagerValidatesMimeSizeAndBlocksLocalUrisInProduction() {
        assertTrue(MediaUploadManager.validateMediaAsset("image/jpeg", 1024L, MediaUploadKind.IMAGE).isSuccess)
        assertTrue(MediaUploadManager.validateMediaAsset("application/pdf", 2048L, MediaUploadKind.DOCUMENT).isSuccess)
        assertTrue(MediaUploadManager.validateMediaAsset("application/x-msdownload", 1024L, MediaUploadKind.IMAGE).isFailure)
        assertTrue(MediaUploadManager.validateMediaAsset("image/png", 10L * 1024L * 1024L, MediaUploadKind.IMAGE).isFailure)

        assertFalse(MediaUploadManager.isPersistableProductionMediaUrl("content://media/external/images/media/42"))
        assertFalse(MediaUploadManager.isPersistableProductionMediaUrl("file:///storage/emulated/0/banner.jpg"))
        assertFalse(MediaUploadManager.isPersistableProductionMediaUrl("http://insecure.cdn.com/banner.jpg"))
        assertTrue(MediaUploadManager.isPersistableProductionMediaUrl("https://cdn.pharmaos.com/media/banner.jpg"))
        assertTrue(MediaUploadManager.isPersistableProductionMediaUrl(null))
    }

    @Test
    fun productionResubmitCallsPutBeforePostAndReusesOperationIdempotencyKey() = runBlocking {
        val recordedCalls = CopyOnWriteArrayList<Pair<String, String?>>()
        var failFirstPost = true

        val mockInterceptor = Interceptor { chain ->
            val req = chain.request()
            val methodPath = "${req.method} ${req.url.encodedPath}"
            recordedCalls.add(methodPath to req.header("Idempotency-Key"))

            val body = when {
                req.method == "PUT" && req.url.encodedPath.endsWith("/api/v1/medicines/submissions/77") -> {
                    """
                    {"success":true,"data":{
                      "id":77,"organizationId":200,"organizationName":"Bharat Bio-Pharma",
                      "submittedByUserId":20,"submittedByName":"Ops","submittedByRole":"MANUFACTURER",
                      "medicineName":"CardioMax 40","brandName":"CardioMax","genericName":"Atorvastatin",
                      "saltComposition":"Atorvastatin 40mg","dosageForm":"Tablet","strength":"40mg",
                      "packSize":"Strip of 10","therapeuticCategory":"Cardiology","mrp":160.0,
                      "wholesaleRatePtr":110.0,"hsnCode":"30049099","gstRatePercent":12.0,
                      "approvalStatus":"DRAFT","catalogueStatus":"INACTIVE"
                    }}
                    """.trimIndent()
                }
                req.method == "POST" && req.url.encodedPath.endsWith("/api/v1/medicines/submissions/77/submit") -> {
                    if (failFirstPost) {
                        failFirstPost = false
                        return@Interceptor Response.Builder()
                            .request(req).protocol(Protocol.HTTP_1_1).code(503).message("Unavailable")
                            .body("""{"message":"Temporary outage"}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    """
                    {"success":true,"data":{
                      "id":77,"organizationId":200,"organizationName":"Bharat Bio-Pharma",
                      "submittedByUserId":20,"submittedByName":"Ops","submittedByRole":"MANUFACTURER",
                      "medicineName":"CardioMax 40","brandName":"CardioMax","genericName":"Atorvastatin",
                      "saltComposition":"Atorvastatin 40mg","dosageForm":"Tablet","strength":"40mg",
                      "packSize":"Strip of 10","therapeuticCategory":"Cardiology","mrp":160.0,
                      "wholesaleRatePtr":110.0,"hsnCode":"30049099","gstRatePercent":12.0,
                      "approvalStatus":"PENDING_APPROVAL","catalogueStatus":"INACTIVE"
                    }}
                    """.trimIndent()
                }
                else -> """{"success":true,"data":[]}"""
            }
            Response.Builder()
                .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }

        val storage = SecureTokenStorage.createForUnitTest(context, prefsFileName = "test_repo_tokens_${System.nanoTime()}")
        val dataStore = DataStoreManager(context, secureTokenStorage = storage, allowDevModeOverrides = false)
        dataStore.setLoggedInUserId(20L)
        dataStore.setAuthTokens("valid_prod_access_jwt", "valid_prod_refresh_jwt")

        db.userDao().insertUser(
            UserEntity(
                id = 20L,
                fullName = "Ops",
                businessName = "Bharat Bio-Pharma",
                email = "ops@bharatbio.com",
                phone = "9876543210",
                gstin = "27AABCB1234F1Z9",
                address = "Mumbai",
                passwordHash = "",
                passwordSalt = "",
                role = UserRole.MANUFACTURER.name,
                status = AccountStatus.ACTIVE.name,
                organizationId = 200L,
                organizationType = OrganizationType.PHARMACEUTICAL_ORGANIZATION.name,
                organizationName = "Bharat Bio-Pharma",
                assignedRolesCsv = UserRole.MANUFACTURER.name,
                permissionsCsv = PharmaPermission.toCsv(RolePermissionMatrix.manufacturerPermissions)
            )
        )

        val networkModule = NetworkModule(
            tokenProvider = dataStore,
            initialBaseUrl = "https://api.pharmaos.example/",
            extraInterceptors = listOf(mockInterceptor)
        )
        val pharmaRepo = PharmaRepositoryImpl(db = db, dataStoreManager = dataStore, networkModule = networkModule)
        val repo = WorkspaceRepositoryImpl(
            db = db,
            dataStoreManager = dataStore,
            pharmaRepository = pharmaRepo,
            remoteDataSource = WorkspaceRemoteDataSource(networkModule)
        )

        // Blank HSN must be rejected explicitly (no silent 30049099 fallback)
        val invalidSubmission = MedicineSubmission(
            id = 77L,
            organizationId = 200L,
            organizationName = "Bharat Bio-Pharma",
            submittedByUserId = 20L,
            submittedByName = "Ops",
            submittedByRole = UserRole.MANUFACTURER,
            medicineName = "CardioMax 40",
            brandName = "CardioMax",
            genericName = "Atorvastatin",
            saltComposition = "Atorvastatin 40mg",
            dosageForm = "Tablet",
            strength = "40mg",
            packSize = "Strip of 10",
            therapeuticCategory = "Cardiology",
            mrp = 160.0,
            wholesaleRatePtr = 110.0,
            hsnCode = "",
            gstRatePercent = 12.0,
            approvalStatus = MedicineSubmissionStatus.REJECTED
        )
        assertTrue(repo.submitMedicineForApproval(invalidSubmission).isFailure)

        val validSubmission = invalidSubmission.copy(hsnCode = "30049099")
        // First attempt: PUT succeeds, POST fails with 503
        val attempt1 = repo.submitMedicineForApproval(validSubmission)
        assertTrue(attempt1.isFailure)

        // Retry: must call PUT before POST and reuse the exact same Idempotency-Key for POST submit
        val attempt2 = repo.submitMedicineForApproval(validSubmission)
        assertTrue(attempt2.isSuccess)

        val postKeys = recordedCalls
            .filter { it.first == "POST /api/v1/medicines/submissions/77/submit" }
            .mapNotNull { it.second }
        assertEquals(2, postKeys.size)
        assertEquals("Retried submission must reuse the exact same Idempotency-Key", postKeys[0], postKeys[1])
        val putIndices = recordedCalls.mapIndexedNotNull { idx, pair ->
            if (pair.first == "PUT /api/v1/medicines/submissions/77") idx else null
        }
        val postIndices = recordedCalls.mapIndexedNotNull { idx, pair ->
            if (pair.first == "POST /api/v1/medicines/submissions/77/submit") idx else null
        }
        assertTrue("PUT must precede POST on draft/rejected edit-resubmit", putIndices.first() < postIndices.first())
    }

    @Test
    fun bannerScheduleFlowReactsToTimeTransitionsForScheduledAndExpiredBanners() = runBlocking {
        val storage = SecureTokenStorage.createForUnitTest(context, prefsFileName = "test_banner_tokens_${System.nanoTime()}")
        val dataStore = DataStoreManager(context, secureTokenStorage = storage, allowDevModeOverrides = true)
        val pharmaRepo = PharmaRepositoryImpl(db = db, dataStoreManager = dataStore)
        val repo = WorkspaceRepositoryImpl(
            db = db,
            dataStoreManager = dataStore,
            pharmaRepository = pharmaRepo,
            remoteDataSource = null
        )

        val t0 = 1_800_000_000_000L
        val tStart = t0 + 10_000L
        val tEnd = t0 + 50_000L

        db.workspaceManagementDao().insertBanner(
            OfferBannerEntity(
                organizationId = 200L,
                organizationName = "Bharat Bio-Pharma",
                title = "Timed Flash Offer",
                subtitle = "10% Extra",
                offerType = "PERCENT_DISCOUNT",
                discountOrOfferSummary = "10% OFF",
                startDateEpochMillis = tStart,
                endDateEpochMillis = tEnd,
                priorityOrder = 1,
                status = BannerStatus.SCHEDULED.name,
                targetAudience = "STOCKISTS",
                ctaText = "View",
                ctaDestinationRoute = "explore",
                createdByUserId = 20L
            )
        )

        // Before start time: scheduled banner must NOT be visible to Stockist
        repo.refreshBannerClock(t0)
        assertTrue(repo.getStockistActiveBannersFlow().first().isEmpty())

        // During active window: scheduled banner auto-activates without restart
        repo.refreshBannerClock(tStart + 5_000L)
        val activeDuringWindow = repo.getStockistActiveBannersFlow().first()
        assertEquals(1, activeDuringWindow.size)
        assertEquals("Timed Flash Offer", activeDuringWindow.first().title)

        // After end time: expired banner auto-hides immediately
        repo.refreshBannerClock(tEnd + 5_000L)
        assertTrue(repo.getStockistActiveBannersFlow().first().isEmpty())
    }

    @Test
    fun productionSecureTokenStorageFailsClosedWhenAndroidKeystoreIsUnavailable() {
        val prefsName = "test_fail_closed_keystore_${System.nanoTime()}"
        val productionStorage = SecureTokenStorage(context, prefsFileName = prefsName)
        val err = runCatching {
            productionStorage.saveTokens("jwt_access", "jwt_refresh")
        }.exceptionOrNull()
        assertTrue(
            "Production SecureTokenStorage must fail closed with SecureTokenStorageException when Keystore is unavailable",
            err is SecureTokenStorageException
        )
        assertFalse(productionStorage.hasEncryptedTokens())

        // Corrupt ciphertext in test storage is scrubbed and fails closed to null
        val testPrefs = "test_corrupt_tokens_${System.nanoTime()}"
        val testStorage = SecureTokenStorage.createForUnitTest(context, prefsFileName = testPrefs)
        context.getSharedPreferences(testPrefs, Context.MODE_PRIVATE)
            .edit()
            .putString("enc_access_token_v1", "not-valid-ciphertext")
            .commit()
        assertNull(testStorage.getAccessToken())
        assertFalse(testStorage.hasEncryptedTokens())
    }

    @Test
    fun failClosedStatusRoleOrganizationAndPermissionParsingRejectsMalformedValues() {
        assertEquals(MedicineSubmissionStatus.UNKNOWN, MedicineSubmissionStatus.fromString(null))
        assertEquals(MedicineSubmissionStatus.UNKNOWN, MedicineSubmissionStatus.fromString("GARBAGE_STATUS"))
        assertEquals(CataloguePublicationStatus.UNKNOWN, CataloguePublicationStatus.fromString(null))
        assertEquals(CataloguePublicationStatus.UNKNOWN, CataloguePublicationStatus.fromString("BROKEN"))
        assertEquals(AccountStatus.UNKNOWN, AccountStatus.fromString(null))
        assertEquals(AccountStatus.UNKNOWN, AccountStatus.fromString("INVALID_ACCOUNT_STATE"))
        assertNull(UserRole.parseStrict(null))
        assertNull(UserRole.parseStrict("SUPER_HACKER"))
        assertEquals(UserRole.PHARMA_COMPANY, UserRole.parseStrict("PHARMA_COMPANY"))
        assertEquals(
            OrganizationType.UNKNOWN,
            OrganizationType.fromString(null, fallbackRole = UserRole.STOCKIST, isProductionMode = true)
        )
        assertEquals(
            OrganizationType.UNKNOWN,
            OrganizationType.fromString("UNRECOGNIZED_ORG", fallbackRole = UserRole.MANUFACTURER, isProductionMode = true)
        )

        // Mixed valid + unknown permission tokens preserve valid permissions while recording unknown tokens (never defaulting)
        val mixedState = RolePermissionMatrix.resolvePermissionState(
            rawPermissions = listOf("PRODUCT_VIEW", "MALFORMED_PERMISSION_TOKEN"),
            roles = setOf(UserRole.STOCKIST),
            isProductionMode = true
        )
        assertEquals(PermissionResolutionStatus.CONFIRMED, mixedState.status)
        assertEquals(setOf(PharmaPermission.PRODUCT_VIEW), mixedState.permissions)
        assertEquals(setOf("MALFORMED_PERMISSION_TOKEN"), mixedState.unrecognizedTokens)

        // Exclusively unknown permission tokens fail closed as INVALID with empty permissions
        val onlyUnknownState = RolePermissionMatrix.resolvePermissionState(
            rawPermissions = listOf("MALFORMED_PERMISSION_TOKEN"),
            roles = setOf(UserRole.STOCKIST),
            isProductionMode = true
        )
        assertEquals(PermissionResolutionStatus.INVALID, onlyUnknownState.status)
        assertTrue(onlyUnknownState.permissions.isEmpty())
        assertEquals(setOf("MALFORMED_PERMISSION_TOKEN"), onlyUnknownState.unrecognizedTokens)

        // Unknown approval/catalogue statuses must NEVER be discoverable by Stockists
        assertFalse(
            AccessControlPolicy.isMedicineDiscoverableByStockist(
                MedicineSubmissionStatus.UNKNOWN,
                CataloguePublicationStatus.ACTIVE,
                true
            )
        )
        assertFalse(
            AccessControlPolicy.isMedicineDiscoverableByStockist(
                MedicineSubmissionStatus.APPROVED,
                CataloguePublicationStatus.UNKNOWN,
                true
            )
        )
    }

    @Test
    fun jwtAuthenticatorPreservesSessionOnTransientErrorsExpiresOnAuthRejectionAndBlocksStaleRefreshAfterLogout() {
        val generation = AtomicLong(1L)
        val tokenStore = object : TokenProvider {
            @Volatile var access: String? = "expired_access"
            @Volatile var refresh: String? = "valid_refresh"
            override fun getAccessToken(): String? = access
            override fun getRefreshToken(): String? = refresh
            override fun getSessionGeneration(): Long = generation.get()
            override fun updateTokens(accessToken: String?, refreshToken: String?) {
                access = accessToken
                if (!refreshToken.isNullOrBlank()) refresh = refreshToken
            }
            override fun clearTokens() {
                access = null
                refresh = null
                generation.incrementAndGet()
            }
        }

        val moshi = com.squareup.moshi.Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val expiredEvents = CopyOnWriteArrayList<String>()

        fun make401(token: String = "expired_access"): Response {
            val req = Request.Builder()
                .url("https://api.pharmaos.example/api/v1/orders")
                .header("Authorization", "Bearer $token")
                .build()
            return Response.Builder()
                .request(req).protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized")
                .body("""{"message":"Expired"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }

        // 1. Transient IOException preserves tokens and does NOT expire session
        val ioAuthenticator = JwtTokenAuthenticator(
            tokenProvider = tokenStore,
            refreshHttpClient = okhttp3.OkHttpClient.Builder()
                .addInterceptor(Interceptor { throw IOException("Socket timeout") })
                .build(),
            moshi = moshi,
            baseUrlProvider = { "https://api.pharmaos.example" },
            onSessionExpired = { expiredEvents.add(it) }
        )
        assertNull(ioAuthenticator.authenticate(null, make401()))
        assertEquals("expired_access", tokenStore.getAccessToken())
        assertEquals("valid_refresh", tokenStore.getRefreshToken())
        assertTrue("Transient IOException must NOT emit sessionExpired", expiredEvents.isEmpty())

        // 2. Transient HTTP 503 preserves tokens and does NOT expire session
        val outageAuthenticator = JwtTokenAuthenticator(
            tokenProvider = tokenStore,
            refreshHttpClient = okhttp3.OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("Unavailable")
                        .body("""{"message":"Backend restarting"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                })
                .build(),
            moshi = moshi,
            baseUrlProvider = { "https://api.pharmaos.example" },
            onSessionExpired = { expiredEvents.add(it) }
        )
        assertNull(outageAuthenticator.authenticate(null, make401()))
        assertEquals("expired_access", tokenStore.getAccessToken())
        assertEquals("valid_refresh", tokenStore.getRefreshToken())
        assertTrue("HTTP 503 must NOT emit sessionExpired", expiredEvents.isEmpty())

        // 3. Session logout/account switch mid-refresh discards rotated tokens via sessionGeneration
        val raceAuthenticator = JwtTokenAuthenticator(
            tokenProvider = tokenStore,
            refreshHttpClient = okhttp3.OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    // Simulate user logging out while refresh call is in flight
                    tokenStore.clearTokens()
                    val body = """{"success":true,"data":{"accessToken":"stale_rotated","refreshToken":"stale_ref"}}"""
                    Response.Builder()
                        .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                })
                .build(),
            moshi = moshi,
            baseUrlProvider = { "https://api.pharmaos.example" },
            onSessionExpired = { expiredEvents.add(it) }
        )
        assertNull(raceAuthenticator.authenticate(null, make401()))
        assertNull("Stale in-flight refresh must NEVER restore logged-out access token", tokenStore.getAccessToken())
        assertNull("Stale in-flight refresh must NEVER restore logged-out refresh token", tokenStore.getRefreshToken())
        assertTrue("Intentional logout mid-refresh must NOT fire spurious sessionExpired", expiredEvents.isEmpty())

        // 4. Genuine 401 refresh rejection clears tokens and fires onSessionExpired
        tokenStore.updateTokens("expired_access_2", "revoked_refresh")
        val revokedAuthenticator = JwtTokenAuthenticator(
            tokenProvider = tokenStore,
            refreshHttpClient = okhttp3.OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request()).protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized")
                        .body("""{"message":"Refresh token revoked"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                })
                .build(),
            moshi = moshi,
            baseUrlProvider = { "https://api.pharmaos.example" },
            onSessionExpired = { expiredEvents.add(it) }
        )
        assertNull(revokedAuthenticator.authenticate(null, make401("expired_access_2")))
        assertNull("401 refresh rejection must clear access token", tokenStore.getAccessToken())
        assertNull("401 refresh rejection must clear refresh token", tokenStore.getRefreshToken())
        assertEquals(1, expiredEvents.size)
    }

    @Test
    fun syncActiveWorkspaceReportsPartialFailureAndSyncCompanyCatalogueValidatesOwnershipAndReconciles() = runBlocking {
        var failMasterData = false
        val cleanCataloguePayload = """
            {"success":true,"data":[
              {
                "id":901,"name":"Valid Approved Med","brand":"CardioValid","saltComposition":"Atorvastatin",
                "strength":"40mg","dosageForm":"Tablet","therapeuticCategory":"Cardiology",
                "manufacturerName":"Bharat Bio-Pharma","packSize":"10 Tabs","hsnCode":"30049099",
                "gstRatePercent":12.0,"storageCondition":"ROOM_TEMPERATURE","isRx":true,"isActive":true,
                "createdBy":30,"organizationId":200,"approvalStatus":"APPROVED","catalogueStatus":"ACTIVE"
              }
            ]}
        """.trimIndent()
        val dirtyCataloguePayload = """
            {"success":true,"data":[
              {
                "id":901,"name":"Valid Approved Med","brand":"CardioValid","saltComposition":"Atorvastatin",
                "strength":"40mg","dosageForm":"Tablet","therapeuticCategory":"Cardiology",
                "manufacturerName":"Bharat Bio-Pharma","packSize":"10 Tabs","hsnCode":"30049099",
                "gstRatePercent":12.0,"storageCondition":"ROOM_TEMPERATURE","isRx":true,"isActive":true,
                "createdBy":30,"organizationId":200,"approvalStatus":"APPROVED","catalogueStatus":"ACTIVE"
              },
              {
                "id":902,"name":"Missing Org Med","brand":"NoOrg","saltComposition":"Salt",
                "strength":"10mg","dosageForm":"Tablet","therapeuticCategory":"General",
                "manufacturerName":"Unknown","packSize":"10 Tabs","hsnCode":"30049099",
                "gstRatePercent":12.0,"storageCondition":"ROOM_TEMPERATURE","isRx":false,"isActive":true,
                "createdBy":30,"approvalStatus":"APPROVED","catalogueStatus":"ACTIVE"
              },
              {
                "id":903,"name":"Foreign Org Med","brand":"Foreign","saltComposition":"Salt",
                "strength":"10mg","dosageForm":"Tablet","therapeuticCategory":"General",
                "manufacturerName":"Other Corp","packSize":"10 Tabs","hsnCode":"30049099",
                "gstRatePercent":12.0,"storageCondition":"ROOM_TEMPERATURE","isRx":false,"isActive":true,
                "createdBy":99,"organizationId":999,"approvalStatus":"APPROVED","catalogueStatus":"ACTIVE"
              },
              {
                "id":904,"name":"Unknown Status Med","brand":"UnknownStatus","saltComposition":"Salt",
                "strength":"10mg","dosageForm":"Tablet","therapeuticCategory":"General",
                "manufacturerName":"Bharat Bio-Pharma","packSize":"10 Tabs","hsnCode":"30049099",
                "gstRatePercent":12.0,"storageCondition":"ROOM_TEMPERATURE","isRx":false,"isActive":true,
                "createdBy":30,"organizationId":200,"approvalStatus":"GARBAGE","catalogueStatus":"ACTIVE"
              }
            ]}
        """.trimIndent()
        var cataloguePayload = cleanCataloguePayload

        val mockInterceptor = Interceptor { chain ->
            val req = chain.request()
            val path = req.url.encodedPath
            when {
                path.endsWith("/api/v1/auth/context") -> {
                    val body = """
                        {"success":true,"data":{
                          "user":{
                            "id":30,"fullName":"Dr. Ananya","businessName":"Bharat Bio-Pharma",
                            "email":"company@bharatbio.com","phone":"9876543230","address":"Hyderabad",
                            "role":"PHARMA_COMPANY","status":"ACTIVE",
                            "organizationId":200,"organizationType":"PHARMACEUTICAL_ORGANIZATION",
                            "organizationName":"Bharat Bio-Pharma",
                            "roles":["PHARMA_COMPANY"],
                            "permissions":["PRODUCT_VIEW","MEDICINE_APPROVE","MEDICINE_REJECT","MEDICINE_CATALOGUE_MANAGE","COMPOSITION_MANAGE","DOSAGE_FORM_MANAGE","BRAND_MANAGE"]
                          },
                          "organization":{
                            "id":200,"name":"Bharat Bio-Pharma","type":"PHARMACEUTICAL_ORGANIZATION","active":true
                          },
                          "roles":["PHARMA_COMPANY"],
                          "permissions":["PRODUCT_VIEW","MEDICINE_APPROVE","MEDICINE_REJECT","MEDICINE_CATALOGUE_MANAGE","COMPOSITION_MANAGE","DOSAGE_FORM_MANAGE","BRAND_MANAGE"],
                          "authorizedWorkspaces":["COMPANY"]
                        }}
                    """.trimIndent()
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                }
                path.endsWith("/api/v1/company/catalogue") -> {
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(cataloguePayload.toResponseBody("application/json".toMediaType())).build()
                }
                path.endsWith("/api/v1/company/master-data") && failMasterData -> {
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(500).message("Server Error")
                        .body("""{"message":"Master data DB offline"}""".toResponseBody("application/json".toMediaType())).build()
                }
                else -> {
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("""{"success":true,"data":[]}""".toResponseBody("application/json".toMediaType())).build()
                }
            }
        }

        val storage = SecureTokenStorage.createForUnitTest(context, prefsFileName = "test_sync_tokens_${System.nanoTime()}")
        val dataStore = DataStoreManager(context, secureTokenStorage = storage, allowDevModeOverrides = false)
        dataStore.setLoggedInUserId(30L)
        dataStore.setAuthTokens("valid_company_jwt", "valid_company_refresh")
        dataStore.setActiveWorkspace(WorkspaceType.COMPANY)

        db.userDao().insertUser(
            UserEntity(
                id = 30L, fullName = "Dr. Ananya", businessName = "Bharat Bio-Pharma",
                email = "company@bharatbio.com", phone = "9876543230", gstin = null,
                address = "Hyderabad", passwordHash = "", passwordSalt = "",
                role = UserRole.PHARMA_COMPANY.name, status = AccountStatus.ACTIVE.name,
                organizationId = 200L, organizationType = OrganizationType.PHARMACEUTICAL_ORGANIZATION.name,
                organizationName = "Bharat Bio-Pharma", assignedRolesCsv = UserRole.PHARMA_COMPANY.name,
                permissionsCsv = PharmaPermission.toCsv(RolePermissionMatrix.companyPermissions)
            )
        )
        // Seed a stale product owned by org 200 that is no longer returned by backend
        db.productDao().insertProduct(
            ProductEntity(
                id = 999L, name = "Stale Removed Med", brand = "Old", saltComposition = "Old",
                strength = "5mg", dosageForm = "Tablet", therapeuticCategory = "General",
                manufacturerName = "Bharat Bio-Pharma", packSize = "10", hsnCode = "30049099",
                gstRatePercent = 12.0, storageCondition = "ROOM_TEMPERATURE", isRx = false,
                isActive = true, createdBy = 30L, organizationId = 200L,
                approvalStatus = MedicineSubmissionStatus.APPROVED.name,
                catalogueStatus = CataloguePublicationStatus.ACTIVE.name
            )
        )

        val networkModule = NetworkModule(
            tokenProvider = dataStore,
            initialBaseUrl = "https://api.pharmaos.example/",
            extraInterceptors = listOf(mockInterceptor)
        )
        val pharmaRepo = PharmaRepositoryImpl(db = db, dataStoreManager = dataStore, networkModule = networkModule)
        val repo = WorkspaceRepositoryImpl(
            db = db,
            dataStoreManager = dataStore,
            pharmaRepository = pharmaRepo,
            remoteDataSource = WorkspaceRemoteDataSource(networkModule)
        )

        // 1. Healthy sync succeeds and reconciles removed stale product 999
        val initialSync = repo.syncActiveWorkspace()
        assertTrue(initialSync.isSuccess)
        val healthyState = repo.workspaceSyncStateFlow.value
        assertEquals(SyncHealthStatus.SUCCESS, healthyState.status)
        assertNotNull(healthyState.lastSuccessfulSyncEpochMillis)
        assertFalse(healthyState.isStaleCache)
        assertEquals(listOf(901L), db.productDao().getProductsByOrganization(200L).map { it.id })
        assertNull("Removed stale product 999 must be reconciled out", db.productDao().getProductById(999L))

        // 2. Dirty catalogue payload rejects missing-org, foreign-org, and unknown-status records and surfaces failure
        cataloguePayload = dirtyCataloguePayload
        val dirtyCatalogueResult = repo.syncCompanyCatalogue()
        assertTrue("syncCompanyCatalogue must surface skipped invalid records rather than silent success", dirtyCatalogueResult.isFailure)
        assertEquals(listOf(901L), db.productDao().getProductsByOrganization(200L).map { it.id })
        assertNull("Missing-org product 902 must be rejected", db.productDao().getProductById(902L))
        assertNull("Foreign-org product 903 must be rejected", db.productDao().getProductById(903L))
        assertNull("Unknown-status product 904 must be rejected", db.productDao().getProductById(904L))

        // 3. Partial failure when a required step fails returns Result.failure and marks stale cache + retry
        cataloguePayload = cleanCataloguePayload
        failMasterData = true
        val partialSync = repo.syncActiveWorkspace()
        assertTrue("syncActiveWorkspace must NOT silently return success when a required step fails", partialSync.isFailure)
        val failedState = repo.workspaceSyncStateFlow.value
        assertEquals(SyncHealthStatus.PARTIAL_SUCCESS, failedState.status)
        assertTrue(failedState.isStaleCache)
        assertTrue(failedState.canRetry)
        assertNotNull("Previous lastSuccessfulSyncEpochMillis must be preserved on partial failure", failedState.lastSuccessfulSyncEpochMillis)
        assertTrue(failedState.failedSteps.any { it.stepName == "master_data" })

        // 3. Clearing sensitive local session caches in production mode wipes ALL private Room tables
        db.orderDao().insertOrder(
            OrderEntity(
                orderNumber = "ORD-PRIV-1", buyerId = 30L, supplierId = 200L, status = "PLACED",
                subtotal = 100.0, gstAmount = 12.0, deliveryCharge = 0.0, totalAmount = 112.0,
                deliveryAddress = "Mumbai", notes = ""
            )
        )
        db.basketDao().insertBasket(BasketEntity(buyerId = 30L, name = "Private Basket", isSaved = false))
        db.notificationDao().insertNotification(
            NotificationEntity(recipientUserId = 30L, type = "ORDER", title = "Private", message = "Secret")
        )
        db.userAddressDao().insertAddress(
            UserAddressEntity(
                userId = 30L, addressName = "HQ", contactPerson = "Ananya", phone = "9876543230",
                addressLine1 = "Plot 1", addressLine2 = "", city = "Hyderabad", state = "TS",
                postalCode = "500001", country = "India", isDefault = true
            )
        )

        repo.clearSensitiveLocalSessionCaches(previousUser = null)
        assertTrue(db.productDao().getAllProducts().isEmpty())
        assertTrue(db.orderDao().getOrdersForBuyerFlow(30L).first().isEmpty())
        assertTrue(db.notificationDao().getNotificationsForUserFlow(30L).first().isEmpty())
        assertTrue(db.userAddressDao().getAddressesForUserFlow(30L).first().isEmpty())
    }
}

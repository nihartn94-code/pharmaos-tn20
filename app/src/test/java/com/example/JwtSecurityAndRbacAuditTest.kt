package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.local.SecureTokenStorage
import com.example.data.local.entity.OfferBannerEntity
import com.example.data.local.entity.UserEntity
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.remote.dto.UserDto
import com.example.data.remote.interceptor.ApiLoggingInterceptor
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
import com.example.domain.model.MedicineSubmission
import com.example.domain.model.MedicineSubmissionStatus
import com.example.domain.model.OrganizationType
import com.example.domain.model.PermissionResolutionStatus
import com.example.domain.model.PharmaPermission
import com.example.domain.model.RolePermissionMatrix
import com.example.domain.model.UserRole
import com.example.domain.model.WorkspaceType
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
        val storage = SecureTokenStorage(context, prefsFileName = prefsName)
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

        val storage = SecureTokenStorage(context, prefsFileName = "test_repo_tokens_${System.nanoTime()}")
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
        val storage = SecureTokenStorage(context, prefsFileName = "test_banner_tokens_${System.nanoTime()}")
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
}

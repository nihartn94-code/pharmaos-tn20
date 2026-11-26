package com.example

import com.example.data.remote.api.WorkspaceApiService
import com.example.data.remote.dto.ApprovalDecisionRequestDto
import com.example.data.remote.dto.UserDto
import com.example.data.remote.util.NetworkResult
import com.example.data.remote.util.safeApiEnvelopeCall
import com.example.domain.model.AccessControlPolicy
import com.example.domain.model.AccountStatus
import com.example.domain.model.AuthUser
import com.example.domain.model.BannerOfferType
import com.example.domain.model.BannerStatus
import com.example.domain.model.CataloguePublicationStatus
import com.example.domain.model.MedicineSubmission
import com.example.domain.model.MedicineSubmissionStatus
import com.example.domain.model.OfferBanner
import com.example.domain.model.OrganizationType
import com.example.domain.model.PharmaPermission
import com.example.domain.model.RolePermissionMatrix
import com.example.domain.model.UserRole
import com.example.domain.model.WorkspaceType
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Pure JVM unit & mocked HTTP tests verifying PharmaOS Role-Based Application Transformation:
 * - Unified Organization + 22 Granular Permissions Matrix
 * - Stockist / Manufacturer / Company / Dual-Permission Workspace Routing & Isolation
 * - Compulsory Medicine Approval Workflow (self-approval block, cross-org denial, mandatory reason)
 * - Dynamic Offer Banner Lifecycle & Expiry Visibility
 * - Strict Inventory Separation (Stockist Purchased vs Manufacturer Warehouse)
 * - Stateless HTTP Client Status Code Classification (200/201/400/401/403/404/409/500)
 */
class RoleBasedTransformationUnitTest {

    private val moshi: Moshi = Moshi.Builder().build()

    private fun stockistUser(id: Long = 10L, orgId: Long = 100L) = AuthUser(
        id = id,
        fullName = "Rohan Sharma",
        businessName = "Apex Pharma Distributors",
        email = "stockist@pharmaos.com",
        phone = "9876543210",
        gstin = "27ABCDE1234F1Z5",
        address = "Mumbai",
        role = UserRole.STOCKIST,
        status = AccountStatus.ACTIVE,
        organizationId = orgId,
        organizationType = OrganizationType.STOCKIST,
        organizationName = "Apex Pharma Distributors",
        assignedRoles = setOf(UserRole.STOCKIST),
        permissions = RolePermissionMatrix.stockistPermissions
    )

    private fun manufacturerUser(id: Long = 20L, orgId: Long = 200L) = AuthUser(
        id = id,
        fullName = "Vikram Mehta",
        businessName = "Bharat Bio-Pharma",
        email = "manufacturer@pharmaos.com",
        phone = "9876543220",
        gstin = "27BHARAT1234F1Z9",
        address = "Hyderabad",
        role = UserRole.MANUFACTURER,
        status = AccountStatus.ACTIVE,
        organizationId = orgId,
        organizationType = OrganizationType.PHARMACEUTICAL_ORGANIZATION,
        organizationName = "Bharat Bio-Pharma",
        assignedRoles = setOf(UserRole.MANUFACTURER),
        permissions = RolePermissionMatrix.manufacturerPermissions
    )

    private fun companyApproverUser(id: Long = 30L, orgId: Long = 200L) = AuthUser(
        id = id,
        fullName = "Dr. Ananya Rao",
        businessName = "Bharat Bio-Pharma",
        email = "company@pharmaos.com",
        phone = "9876543230",
        gstin = "27BHARAT1234F1Z9",
        address = "Hyderabad",
        role = UserRole.PHARMA_COMPANY,
        status = AccountStatus.ACTIVE,
        organizationId = orgId,
        organizationType = OrganizationType.PHARMACEUTICAL_ORGANIZATION,
        organizationName = "Bharat Bio-Pharma",
        assignedRoles = setOf(UserRole.PHARMA_COMPANY),
        permissions = RolePermissionMatrix.companyPermissions
    )

    private fun dualPermissionUser(id: Long = 40L, orgId: Long = 200L) = AuthUser(
        id = id,
        fullName = "Siddharth Nair",
        businessName = "Bharat Bio-Pharma",
        email = "dual.lead@pharmaos.com",
        phone = "9876543240",
        gstin = "27BHARAT1234F1Z9",
        address = "Hyderabad",
        role = UserRole.MANUFACTURER,
        status = AccountStatus.ACTIVE,
        organizationId = orgId,
        organizationType = OrganizationType.PHARMACEUTICAL_ORGANIZATION,
        organizationName = "Bharat Bio-Pharma",
        assignedRoles = setOf(UserRole.MANUFACTURER, UserRole.PHARMA_COMPANY),
        permissions = RolePermissionMatrix.resolvePermissions(
            setOf(UserRole.MANUFACTURER, UserRole.PHARMA_COMPANY)
        )
    )

    private fun pendingSubmission(
        id: Long = 501L,
        orgId: Long = 200L,
        submittedByUserId: Long = 20L,
        status: MedicineSubmissionStatus = MedicineSubmissionStatus.PENDING_APPROVAL
    ) = MedicineSubmission(
        id = id,
        organizationId = orgId,
        organizationName = "Bharat Bio-Pharma",
        submittedByUserId = submittedByUserId,
        submittedByName = "Vikram Mehta",
        submittedByRole = UserRole.MANUFACTURER,
        medicineName = "CardioShield 40",
        brandName = "CardioShield",
        genericName = "Telmisartan",
        saltComposition = "Telmisartan 40mg",
        dosageForm = "Tablet",
        strength = "40mg",
        packSize = "10x10 Strips",
        therapeuticCategory = "Cardiology",
        mrp = 148.0,
        wholesaleRatePtr = 104.5,
        approvalStatus = status,
        catalogueStatus = CataloguePublicationStatus.INACTIVE
    )

    @Test
    fun `enum compatibility preserves PHARMA_COMPANY while displaying Company in UI`() {
        assertEquals("PHARMA_COMPANY", UserRole.PHARMA_COMPANY.name)
        assertEquals("Company", UserRole.PHARMA_COMPANY.displayName)
        assertEquals(2, OrganizationType.entries.size)
        assertTrue(OrganizationType.entries.contains(OrganizationType.STOCKIST))
        assertTrue(OrganizationType.entries.contains(OrganizationType.PHARMACEUTICAL_ORGANIZATION))
        assertEquals(22, PharmaPermission.entries.size)
    }

    @Test
    fun `unified organization model separates Stockist and unlocks dual Manufacturer-Company switching`() {
        val stockist = stockistUser()
        val manufacturer = manufacturerUser()
        val company = companyApproverUser()
        val dual = dualPermissionUser()

        // Same Pharmaceutical Organization shared by Manufacturer, Company, and Dual Lead
        assertEquals(manufacturer.organizationId, company.organizationId)
        assertEquals(OrganizationType.PHARMACEUTICAL_ORGANIZATION, manufacturer.organizationType)
        assertEquals(OrganizationType.PHARMACEUTICAL_ORGANIZATION, company.organizationType)

        // Stockist can ONLY access Stockist workspace
        assertEquals(listOf(WorkspaceType.STOCKIST), stockist.authorizedWorkspaces())
        assertFalse(AccessControlPolicy.canAccessWorkspace(stockist, WorkspaceType.MANUFACTURER))
        assertFalse(AccessControlPolicy.canAccessWorkspace(stockist, WorkspaceType.COMPANY))
        assertFalse(stockist.canSwitchBetweenManufacturerAndCompany())

        // Single-role Manufacturer & Company access only their authorized workspace
        assertEquals(listOf(WorkspaceType.MANUFACTURER), manufacturer.authorizedWorkspaces())
        assertFalse(manufacturer.canSwitchBetweenManufacturerAndCompany())
        assertEquals(listOf(WorkspaceType.COMPANY), company.authorizedWorkspaces())
        assertFalse(company.canSwitchBetweenManufacturerAndCompany())

        // Dual-role employee can seamlessly switch between Manufacturer and Company without re-login
        assertTrue(dual.canSwitchBetweenManufacturerAndCompany())
        assertTrue(AccessControlPolicy.canAccessWorkspace(dual, WorkspaceType.MANUFACTURER))
        assertTrue(AccessControlPolicy.canAccessWorkspace(dual, WorkspaceType.COMPANY))
        assertFalse(AccessControlPolicy.canAccessWorkspace(dual, WorkspaceType.STOCKIST))
    }

    @Test
    fun `strict inventory isolation separates Stockist purchased stock from Manufacturer warehouse stock`() {
        val stockist = stockistUser()
        val manufacturer = manufacturerUser()
        val company = companyApproverUser()

        // Stockist CANNOT access manufacturer real-time inventory, CAN access own purchased inventory
        assertFalse(AccessControlPolicy.canAccessManufacturerRealTimeInventory(stockist))
        assertTrue(AccessControlPolicy.canAccessStockistPurchasedInventory(stockist))

        // Manufacturer & Company CAN access real-time inventory, CANNOT access stockist purchased inventory
        assertTrue(AccessControlPolicy.canAccessManufacturerRealTimeInventory(manufacturer))
        assertFalse(AccessControlPolicy.canAccessStockistPurchasedInventory(manufacturer))
        assertTrue(AccessControlPolicy.canAccessManufacturerRealTimeInventory(company))
        assertFalse(AccessControlPolicy.canAccessStockistPurchasedInventory(company))
    }

    @Test
    fun `compulsory medicine approval enforces non-self-approval, org ownership, mandatory rejection reason, and stockist filter`() {
        val manufacturer = manufacturerUser(id = 20L, orgId = 200L)
        val sameOrgApprover = companyApproverUser(id = 30L, orgId = 200L)
        val otherOrgApprover = companyApproverUser(id = 31L, orgId = 999L)
        val dualSubmitter = dualPermissionUser(id = 40L, orgId = 200L)

        val submissionByManufacturer = pendingSubmission(submittedByUserId = manufacturer.id, orgId = 200L)
        val submissionByDual = pendingSubmission(submittedByUserId = dualSubmitter.id, orgId = 200L)

        // Manufacturer without MEDICINE_APPROVE cannot approve
        assertTrue(AccessControlPolicy.canApproveSubmission(manufacturer, submissionByManufacturer).isFailure)

        // Cross-organization approver is denied
        assertTrue(AccessControlPolicy.canApproveSubmission(otherOrgApprover, submissionByManufacturer).isFailure)

        // Self-approval is disabled by default even for dual-role user with MEDICINE_APPROVE
        assertTrue(AccessControlPolicy.canApproveSubmission(dualSubmitter, submissionByDual, allowSelfApproval = false).isFailure)

        // Different authorized approver in the same organization succeeds
        assertTrue(AccessControlPolicy.canApproveSubmission(sameOrgApprover, submissionByManufacturer).isSuccess)

        // Rejection requires mandatory reason >= 5 chars
        assertTrue(AccessControlPolicy.canRejectSubmission(sameOrgApprover, submissionByManufacturer, "   ").isFailure)
        assertTrue(AccessControlPolicy.canRejectSubmission(sameOrgApprover, submissionByManufacturer, "No").isFailure)
        assertTrue(
            AccessControlPolicy.canRejectSubmission(
                sameOrgApprover,
                submissionByManufacturer,
                "HSN code does not match Schedule H1 tablet classification."
            ).isSuccess
        )

        // Stockist discovery filter: ONLY APPROVED + ACTIVE + isActive=true
        assertTrue(
            AccessControlPolicy.isMedicineDiscoverableByStockist(
                MedicineSubmissionStatus.APPROVED,
                CataloguePublicationStatus.ACTIVE,
                true
            )
        )
        assertFalse(
            AccessControlPolicy.isMedicineDiscoverableByStockist(
                MedicineSubmissionStatus.PENDING_APPROVAL,
                CataloguePublicationStatus.ACTIVE,
                true
            )
        )
        assertFalse(
            AccessControlPolicy.isMedicineDiscoverableByStockist(
                MedicineSubmissionStatus.APPROVED,
                CataloguePublicationStatus.INACTIVE,
                true
            )
        )
        assertFalse(
            AccessControlPolicy.isMedicineDiscoverableByStockist(
                MedicineSubmissionStatus.APPROVED,
                CataloguePublicationStatus.ARCHIVED,
                true
            )
        )
        assertFalse(
            AccessControlPolicy.isMedicineDiscoverableByStockist(
                MedicineSubmissionStatus.REJECTED,
                CataloguePublicationStatus.ACTIVE,
                true
            )
        )
    }

    @Test
    fun `offer banner lifecycle automatically hides expired, scheduled, draft, inactive, and archived banners from stockists`() {
        val now = 1_800_000_000_000L
        val oneDay = 86_400_000L

        val liveBanner = OfferBanner(
            id = 1L,
            organizationId = 200L,
            organizationName = "Bharat Bio-Pharma",
            title = "Monsoon Trade Scheme",
            subtitle = "Flat 10+2 Free Strips",
            offerType = BannerOfferType.FLAT_SCHEME,
            discountOrOfferSummary = "10+2 Bonus",
            startDateEpochMillis = now - oneDay,
            endDateEpochMillis = now + oneDay,
            status = BannerStatus.ACTIVE,
            createdByUserId = 20L
        )
        assertEquals(BannerStatus.ACTIVE, liveBanner.effectiveStatus(now))
        assertTrue(liveBanner.isEligibleForStockist(now))

        val expiredBanner = liveBanner.copy(endDateEpochMillis = now - 1_000L)
        assertEquals(BannerStatus.EXPIRED, expiredBanner.effectiveStatus(now))
        assertFalse(expiredBanner.isEligibleForStockist(now))

        val futureBanner = liveBanner.copy(startDateEpochMillis = now + oneDay, endDateEpochMillis = now + 5 * oneDay)
        assertEquals(BannerStatus.SCHEDULED, futureBanner.effectiveStatus(now))
        assertFalse(futureBanner.isEligibleForStockist(now))

        assertFalse(liveBanner.copy(status = BannerStatus.DRAFT).isEligibleForStockist(now))
        assertFalse(liveBanner.copy(status = BannerStatus.INACTIVE).isEligibleForStockist(now))
        assertFalse(liveBanner.copy(status = BannerStatus.ARCHIVED).isEligibleForStockist(now))
    }

    @Test
    fun `UserDto maps backend roles, organizationId, and permissions cleanly with backward-compatible fallback`() {
        val explicitDto = UserDto(
            id = 40L,
            fullName = "Siddharth Nair",
            businessName = "Bharat Bio-Pharma",
            email = "dual.lead@pharmaos.com",
            phone = "9876543240",
            address = "Hyderabad",
            role = "MANUFACTURER",
            organizationId = 200L,
            organizationType = "PHARMACEUTICAL_ORGANIZATION",
            organizationName = "Bharat Bio-Pharma",
            roles = listOf("MANUFACTURER", "PHARMA_COMPANY"),
            permissions = listOf("BANNER_CREATE", "MEDICINE_APPROVE", "MEDICINE_CATALOGUE_MANAGE")
        )
        val mapped = explicitDto.toAuthUser()
        assertEquals(200L, mapped.organizationId)
        assertEquals(OrganizationType.PHARMACEUTICAL_ORGANIZATION, mapped.organizationType)
        assertTrue(mapped.canSwitchBetweenManufacturerAndCompany())
        assertTrue(mapped.hasPermission(PharmaPermission.BANNER_CREATE))
        assertTrue(mapped.hasPermission(PharmaPermission.MEDICINE_APPROVE))

        // Legacy backend response without organization/permissions fields still maps safely
        val legacyDto = UserDto(
            id = 10L,
            fullName = "Legacy Stockist",
            businessName = "City Meds",
            email = "legacy@citymeds.in",
            phone = "9876543211",
            address = "Pune",
            role = "STOCKIST"
        )
        val legacyMapped = legacyDto.toAuthUser()
        assertEquals(OrganizationType.STOCKIST, legacyMapped.organizationType)
        assertTrue(legacyMapped.hasPermission(PharmaPermission.BASKET_MANAGE))
        assertFalse(legacyMapped.hasPermission(PharmaPermission.MEDICINE_APPROVE))
    }

    @Test
    fun `stateless HTTP WorkspaceApiService handles 200, 400, 401, 403, 404, 409, and 500 explicitly without faking success`() = runBlocking {
        fun buildMockService(statusCode: Int, jsonBody: String): WorkspaceApiService {
            val interceptor = Interceptor { chain ->
                Response.Builder()
                    .code(statusCode)
                    .message("Mock-$statusCode")
                    .protocol(Protocol.HTTP_1_1)
                    .request(chain.request())
                    .body(jsonBody.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
            val retrofit = Retrofit.Builder()
                .baseUrl("https://mock.pharmaos.local/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
            return retrofit.create(WorkspaceApiService::class.java)
        }

        // 200 OK
        val okService = buildMockService(
            200,
            """{"success":true,"message":"OK","data":[]}"""
        )
        val okResult = safeApiEnvelopeCall(moshi) { okService.getStockistActiveBanners() }
        assertTrue(okResult is NetworkResult.Success)
        assertEquals(200, (okResult as NetworkResult.Success).httpCode)

        // 400, 401, 403, 404, 409, 500 error responses must NEVER be converted to Success
        val errorCodes = listOf(400, 401, 403, 404, 409, 500)
        for (code in errorCodes) {
            val errService = buildMockService(
                code,
                """{"status":$code,"error":"HTTP $code","message":"Backend rejected with $code"}"""
            )
            val result = safeApiEnvelopeCall(moshi) {
                errService.approveMedicineSubmission(
                    idempotencyKey = "test-key-$code",
                    submissionId = 501L,
                    request = ApprovalDecisionRequestDto(notes = "Review")
                )
            }
            assertTrue("Expected HttpError for code $code", result is NetworkResult.HttpError)
            assertEquals(code, (result as NetworkResult.HttpError).code)
            assertTrue(result.message.contains("Backend rejected with $code"))
        }
    }
}

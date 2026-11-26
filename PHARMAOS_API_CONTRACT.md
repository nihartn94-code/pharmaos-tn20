# PharmaOS REST API Contract Specification

> **STATUS: `[PROPOSED — AWAITING BACKEND CONFIRMATION]`**  
> **Backend Stack:** Java Spring Boot + PostgreSQL (JDBC & raw SQL — no Hibernate/JPA)  
> **Current Live Backend URL:** **NOT AVAILABLE / NOT CONNECTED**  
>
> None of the endpoints below are live yet. All paths, JSON fields, and status codes are proposed contracts derived from the Android Retrofit/Moshi networking layer and awaiting confirmation from the Spring Boot backend developer.

---

## 1. Base URL & Transport Security Configuration

The Android app centralizes backend routing in `ApiConfig` (`app/src/main/java/com/example/data/remote/config/ApiConfig.kt`), `NetworkModule` (`app/src/main/java/com/example/di/NetworkModule.kt`), `DataStoreManager`, and `app/build.gradle.kts` (`BuildConfig.PHARMAOS_API_BASE_URL`).

| Environment | Base URL | Transport | Notes |
|---|---|---|---|
| **Android Emulator** | `http://10.0.2.2:8080/` | Cleartext HTTP allowed only for local host | Routes emulator to developer host Spring Boot port `8080` |
| **Physical Android Device** | `http://YOUR_LOCAL_LAN_IP:8080/` | Cleartext HTTP allowed only for explicit dev/LAN testing | Replace placeholder with host LAN IP in app Backend Config or BuildConfig |
| **Production Hosted** | `https://BACKEND_URL_NOT_CONFIGURED.pharmaos.invalid/` | **HTTPS strictly enforced** | `network_security_config.xml` blocks arbitrary cleartext HTTP |

### Execution Modes (`AuthExecutionMode`)
- **`PRODUCTION_BACKEND_HTTP` (Default):** Authoritative login, registration, Google sign-in, and domain writes call the Spring Boot REST API. On unconfigured URLs or HTTP failures, the app **fails explicitly** and never silently substitutes local Room credentials or fake success.
- **`DEVELOPMENT_LOCAL_MODE`:** Explicitly selectable offline testing mode inside Settings / Login Config dialog for local Room DB verification (including the seeded `manufacturer@pharmaos.com` testing account).

---

## 2. Headers, Authentication & Response Envelopes

### Request Headers (`AuthInterceptor`)
- `Accept: application/json`
- `X-Client-Platform: Android-PharmaOS`
- `Authorization: Bearer <accessToken>` — injected automatically on all endpoints **except** public auth routes:
  - `/api/v1/auth/login`
  - `/api/v1/auth/register`
  - `/api/v1/auth/google`
  - `/api/v1/auth/refresh`

### Standard Success Envelope (`ApiResponseDto<T>`)
```json
{
  "success": true,
  "message": "OK",
  "data": { ... },
  "timestamp": "2026-11-13T12:00:00Z",
  "path": "/api/v1/auth/login"
}
```

### Standard Error Payload (`ApiErrorDto`)
```json
{
  "timestamp": "2026-11-13T12:00:00Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "errorCode": "VALIDATION_ERROR",
  "message": "Validation failed",
  "path": "/api/v1/auth/register",
  "fieldErrors": {
    "email": "Email is already registered"
  }
}
```

### Handled HTTP Status Codes (`safeApiCall`)
`200 OK`, `201 Created`, `204 No Content`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict`, `422 Unprocessable Entity`, `500+ Server Error`, plus typed IO/JSON classification (`TIMEOUT`, `NO_INTERNET`, `SERVER_UNAVAILABLE`, `INVALID_JSON`).

---

## 3. Proposed REST Endpoints `[PROPOSED — AWAITING BACKEND CONFIRMATION]`

### A. Authentication (`AuthApiService`)

| Method | Path | Auth | Request DTO | Response `data` DTO |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Public | `AuthRequestDto` | `AuthResponseDto` |
| `POST` | `/api/v1/auth/register` | Public | `RegisterRequestDto` | `AuthResponseDto` |
| `POST` | `/api/v1/auth/google` | Public | `GoogleAuthRequestDto` | `AuthResponseDto` |
| `POST` | `/api/v1/auth/refresh` | Public | `RefreshTokenRequestDto` | `AuthResponseDto` |
| `POST` | `/api/v1/auth/logout` | Bearer | — | `204 No Content` / `Unit` |

**Example `POST /api/v1/auth/login` Request:**
```json
{
  "identifier": "rohan.sharma@apexpharma.com",
  "password": "Password@123",
  "expectedRole": "STOCKIST"
}
```

**Example `POST /api/v1/auth/register` Request:**
```json
{
  "fullName": "Rohan Sharma",
  "businessName": "Apex Pharma Distributors",
  "email": "rohan.sharma@apexpharma.com",
  "phone": "9876543210",
  "gstin": "27ABCDE1234F1Z5",
  "address": "Shop 12, Medical Market, Mumbai, MH",
  "password": "Password@123",
  "role": "STOCKIST"
}
```

**Example `AuthResponseDto` (`data` field):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "dGhpcy1pcy1hLXJlZnJlc2g...",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "user": {
    "id": 101,
    "fullName": "Rohan Sharma",
    "businessName": "Apex Pharma Distributors",
    "email": "rohan.sharma@apexpharma.com",
    "phone": "9876543210",
    "gstin": "27ABCDE1234F1Z5",
    "address": "Shop 12, Medical Market, Mumbai, MH",
    "role": "STOCKIST",
    "status": "ACTIVE",
    "createdAtEpochMillis": 1794571200000
  }
}
```

---

### B. Users & Regulatory Compliance (`UserApiService`)

| Method | Path | Auth | Request | Response `data` |
|---|---|---|---|---|
| `GET` | `/api/v1/users/me` | Bearer | — | `UserDto` |
| `GET` | `/api/v1/users` | Bearer (Admin) | — | `List<UserDto>` |
| `PATCH` | `/api/v1/users/{userId}/status` | Bearer (Admin) | `UpdateUserStatusRequestDto` | `UserDto` |
| `GET` | `/api/v1/compliance/{userId}` | Bearer | — | `ComplianceVerificationDto` |
| `PUT` | `/api/v1/compliance` | Bearer | `ComplianceVerificationDto` | `ComplianceVerificationDto` |

---

### C. Product Catalogue (`ProductApiService`)

| Method | Path | Auth | Request / Query | Response `data` |
|---|---|---|---|---|
| `GET` | `/api/v1/products` | Bearer | `?query=&activeOnly=true` | `List<ProductDto>` |
| `GET` | `/api/v1/products/{productId}` | Bearer | — | `ProductDto` |
| `GET` | `/api/v1/products/{productId}/alternatives` | Bearer | — | `List<ProductDto>` |
| `POST` | `/api/v1/products` | Bearer | `ProductDto` | `ProductDto` |
| `PUT` | `/api/v1/products/{productId}` | Bearer | `ProductDto` | `ProductDto` |
| `PATCH` | `/api/v1/products/{productId}/status` | Bearer | `UpdateProductStatusRequestDto` | `Unit` |
| `DELETE` | `/api/v1/products/{productId}` | Bearer | — | `Unit` |

---

### D. Inventory, Batches, Shortage Alerts & Recalls (`InventoryApiService`)

> **RBAC Enforcement:** All `/api/v1/inventory/**` and `/api/v1/batches/**` endpoints are **exclusively restricted** to `MANUFACTURER` and `PHARMA_COMPANY` (Brand) roles (`@PreAuthorize("hasAnyRole('MANUFACTURER', 'PHARMA_COMPANY')")`). Requests authenticated with a `STOCKIST` token **MUST** return `403 Forbidden` with an `ApiErrorDto` payload and must never return manufacturer batch or warehouse stock data.

| Method | Path | Auth / Role | Request / Query | Response `data` |
|---|---|---|---|---|
| `GET` | `/api/v1/inventory` | Bearer (`MANUFACTURER`, `PHARMA_COMPANY`) | `?lowStockOnly=false` | `List<ProductInventoryDto>` |
| `GET` | `/api/v1/inventory/{sku}` | Bearer (`MANUFACTURER`, `PHARMA_COMPANY`) | — | `ProductInventoryDto` |
| `POST` | `/api/v1/inventory` | Bearer (`MANUFACTURER`, `PHARMA_COMPANY`) | `ProductInventoryDto` | `ProductInventoryDto` |
| `PATCH` | `/api/v1/inventory/{sku}/stock` | Bearer (`MANUFACTURER`, `PHARMA_COMPANY`) | `AdjustInventoryStockRequestDto` | `ProductInventoryDto` |
| `PATCH` | `/api/v1/inventory/{sku}/threshold` | Bearer (`MANUFACTURER`, `PHARMA_COMPANY`) | `UpdateReorderThresholdRequestDto` | `ProductInventoryDto` |
| `DELETE` | `/api/v1/inventory/{sku}` | Bearer (`MANUFACTURER`, `PHARMA_COMPANY`) | — | `Unit` |
| `GET` | `/api/v1/batches` | Bearer (`MANUFACTURER`, `PHARMA_COMPANY`) | — | `List<BatchDto>` |
| `POST` | `/api/v1/batches` | Bearer (`MANUFACTURER`, `PHARMA_COMPANY`) | `BatchDto` | `BatchDto` |
| `PATCH` | `/api/v1/batches/{batchId}/stock` | Bearer (`MANUFACTURER`, `PHARMA_COMPANY`) | `AdjustBatchStockRequestDto` | `BatchDto` |
| `GET` | `/api/v1/shortage-alerts` | Bearer | — | `List<ShortageAlertDto>` |
| `POST` | `/api/v1/shortage-alerts/evaluate` | Bearer | — | `List<ShortageAlertDto>` |
| `GET` | `/api/v1/recalls` | Bearer | — | `List<RecallDto>` |
| `POST` | `/api/v1/recalls` | Bearer (`MANUFACTURER`, `PHARMA_COMPANY`) | `CreateRecallRequestDto` | `RecallDto` |

---

### E. Basket & Best-Buy Optimizer (`BasketApiService`)

| Method | Path | Auth | Request | Response `data` |
|---|---|---|---|---|
| `GET` | `/api/v1/basket` | Bearer | — | `BasketDto` |
| `POST` | `/api/v1/basket/items` | Bearer | `AddBasketItemRequestDto` | `BasketDto` |
| `PUT` | `/api/v1/basket/items/{itemId}` | Bearer | `UpdateBasketQtyRequestDto` | `BasketDto` |
| `DELETE` | `/api/v1/basket/items/{itemId}` | Bearer | — | `Unit` |
| `DELETE` | `/api/v1/basket` | Bearer | — | `Unit` |
| `GET` | `/api/v1/basket/saved` | Bearer | — | `List<NamedBasketDto>` |
| `POST` | `/api/v1/basket/saved` | Bearer | `SaveNamedBasketRequestDto` | `NamedBasketDto` |
| `POST` | `/api/v1/basket/saved/{savedBasketId}/load` | Bearer | — | `BasketLoadReportDto` |
| `POST` | `/api/v1/basket/optimize` | Bearer | `OptimizeBasketRequestDto` | `OptimizationResultDto` |
| `POST` | `/api/v1/basket/optimize/apply` | Bearer | `OptimizationResultDto` | `BasketDto` |

---

### F. Orders, Tracking, POD, Returns & Credit Ledger (`OrderApiService`)

| Method | Path | Auth | Request / Query | Response `data` |
|---|---|---|---|---|
| `GET` | `/api/v1/orders` | Bearer | `?supplierId=` | `List<OrderDto>` |
| `GET` | `/api/v1/orders/{orderId}` | Bearer | — | `OrderDetailsDto` |
| `POST` | `/api/v1/orders` | Bearer | `CreateOrderRequestDto` | `List<OrderDto>` |
| `PATCH` | `/api/v1/orders/{orderId}/status` | Bearer | `UpdateOrderStatusRequestDto` | `OrderDto` |
| `PATCH` | `/api/v1/orders/items/{orderItemId}/fulfillment` | Bearer | `FulfillOrderItemRequestDto` | `Unit` |
| `POST` | `/api/v1/orders/{orderId}/pod` | Bearer | `SubmitPodRequestDto` | `Unit` |
| `POST` | `/api/v1/orders/{orderId}/reorder` | Bearer | — | `Unit` |
| `GET` | `/api/v1/returns` | Bearer | — | `List<ReturnDto>` |
| `POST` | `/api/v1/returns` | Bearer | `CreateReturnRequestDto` | `ReturnDto` |
| `PATCH` | `/api/v1/returns/{returnId}/status` | Bearer | `ReturnDecisionRequestDto` | `Unit` |
| `GET` | `/api/v1/credit-notes` | Bearer | — | `List<CreditNoteDto>` |
| `GET` | `/api/v1/ledger/entries` | Bearer | `?counterpartyUserId=` | `List<CreditLedgerEntryDto>` |
| `GET` | `/api/v1/ledger/balance/{counterpartyUserId}` | Bearer | — | `LedgerBalanceDto` |
| `POST` | `/api/v1/ledger/payments` | Bearer | `RecordLedgerPaymentRequestDto` | `Unit` |

---

### G. Supplier Offers, Settings, Trust Score & Analytics (`SupplierApiService`)

| Method | Path | Auth | Request | Response `data` |
|---|---|---|---|---|
| `GET` | `/api/v1/products/{productId}/offers` | Bearer | — | `List<SupplierOfferDto>` |
| `GET` | `/api/v1/suppliers/{supplierId}/offers` | Bearer | — | `List<SupplierOfferDto>` |
| `POST` | `/api/v1/offers` | Bearer | `SupplierOfferDto` | `SupplierOfferDto` |
| `PUT` | `/api/v1/offers/{offerId}` | Bearer | `SupplierOfferDto` | `SupplierOfferDto` |
| `PATCH` | `/api/v1/offers/{offerId}/status` | Bearer | `UpdateProductStatusRequestDto` | `Unit` |
| `GET` | `/api/v1/suppliers/{supplierId}/settings` | Bearer | — | `SupplierSettingsDto` |
| `PUT` | `/api/v1/suppliers/{supplierId}/settings` | Bearer | `SupplierSettingsDto` | `SupplierSettingsDto` |
| `GET` | `/api/v1/suppliers/{supplierId}/trust-score` | Bearer | — | `TrustScoreDto` |
| `GET` | `/api/v1/suppliers/trust-scores` | Bearer | — | `List<TrustScoreDto>` |
| `GET` | `/api/v1/analytics/dashboard` | Bearer | — | `DashboardStatsDto` |

---

### H. Notifications (`NotificationApiService`)

| Method | Path | Auth | Request | Response `data` |
|---|---|---|---|---|
| `GET` | `/api/v1/notifications` | Bearer | — | `List<NotificationDto>` |
| `PATCH` | `/api/v1/notifications/{notificationId}/read` | Bearer | — | `Unit` |
| `PATCH` | `/api/v1/notifications/read-all` | Bearer | — | `Unit` |

---

### I. Organization, Workspace Auth Context & Role-Permission RBAC (`WorkspaceApiService`) `[PROPOSED]`

**Core Organization Model:**
- Exactly two primary organization types: `STOCKIST` and `PHARMACEUTICAL_ORGANIZATION`.
- **Manufacturer** and **Company** belong to the **same** `PHARMACEUTICAL_ORGANIZATION` (`organizationId`).
- UI displays `"Company"` while backend/enum retains `PHARMA_COMPANY` for full backward compatibility.
- Employees with both Manufacturer and Company permissions inside the same organization switch workspaces without re-authenticating.

| Method | Path | Required Permission / Rule | Headers / Query | Request / Response `data` |
|---|---|---|---|---|
| `GET` | `/api/v1/auth/me` | Authenticated Bearer | — | `UserDto` (includes `organizationId`, `organizationType`, `organizationName`, `roles[]`, `permissions[]`) |
| `GET` | `/api/v1/organizations/{orgId}` | Same org member or `ADMIN` | — | `OrganizationSummaryDto` |
| `GET` | `/api/v1/organizations/{orgId}/members` | `ORGANIZATION_MEMBER_MANAGE` or same org | — | `List<OrganizationMemberDto>` |
| `PUT` | `/api/v1/organizations/{orgId}/members/{userId}/roles` | `ROLE_ASSIGN` + same `orgId` | `Idempotency-Key` | `UpdateMemberRolesRequestDto` → `OrganizationMemberDto` |

#### 22 Granular Permissions Matrix

| Workspace / Scope | Granular Permissions |
|---|---|
| **Stockist** (`STOCKIST`) | `PRODUCT_VIEW`, `BASKET_MANAGE`, `ORDER_CREATE`, `ORDER_VIEW`, `STOCKIST_INVENTORY_MANAGE`, `INVOICE_VIEW` |
| **Manufacturer** (`PHARMACEUTICAL_ORGANIZATION`) | `BANNER_CREATE`, `BANNER_UPDATE`, `BANNER_DELETE`, `CAMPAIGN_MANAGE`, `MEDICINE_ANNOUNCEMENT_CREATE`, `MEDICINE_SUBMIT`, `ORDER_VIEW`, `ANALYTICS_VIEW` |
| **Company** (`PHARMACEUTICAL_ORGANIZATION`) | `MEDICINE_SUBMIT`, `MEDICINE_EDIT`, `MEDICINE_APPROVE`, `MEDICINE_REJECT`, `MEDICINE_CATALOGUE_MANAGE`, `COMPOSITION_MANAGE`, `DOSAGE_FORM_MANAGE`, `BRAND_MANAGE`, `ANALYTICS_VIEW`, `ORGANIZATION_MEMBER_MANAGE`, `ROLE_ASSIGN`, `ORDER_VIEW` |

---

### J. Compulsory Medicine Approval Workflow & Company Catalogue (`WorkspaceApiService`) `[PROPOSED]`

> **Compulsory Governance Rules (Server-Enforced):**
> 1. Every new medicine enters `DRAFT` or `PENDING_APPROVAL` — never directly discoverable by Stockists.
> 2. Transitions: `DRAFT` → `PENDING_APPROVAL` → `APPROVED` | `REJECTED` | `WITHDRAWN`. Rejected submissions may be edited and re-submitted back to `PENDING_APPROVAL`.
> 3. **Self-approval is disabled by default** (`submitted_by_user_id != approved_by_user_id`, returning `403 Forbidden` unless explicitly overridden by a platform `ADMIN`).
> 4. **Rejection requires a mandatory non-blank `rejectionReason`** (returns `400 Bad Request` otherwise).
> 5. **Stockist Discovery Filter:** `GET /api/v1/products` for Stockists **MUST** enforce `approval_status = 'APPROVED' AND catalogue_status = 'ACTIVE' AND is_active = TRUE`.
> 6. **No Hard Delete:** Catalogue entries use soft status transitions (`ACTIVE`, `INACTIVE`, `ARCHIVED`) to preserve historical orders and audit trails.

| Method | Path | Required Permission | Headers / Query | Request / Response `data` |
|---|---|---|---|---|
| `GET` | `/api/v1/company/catalogue` | `MEDICINE_CATALOGUE_MANAGE` (org-scoped) | `?query=&status=` | `List<ProductDto>` |
| `PUT` | `/api/v1/company/catalogue/{productId}` | `MEDICINE_EDIT` + org ownership | `Idempotency-Key` | `ProductDto` → `ProductDto` |
| `PATCH` | `/api/v1/company/catalogue/{productId}/publication-status` | `MEDICINE_CATALOGUE_MANAGE` + org ownership | `Idempotency-Key` | `UpdateCatalogueStatusRequestDto` (`ACTIVE`/`INACTIVE`/`ARCHIVED`) → `Unit` |
| `GET` | `/api/v1/medicine-submissions` | `MEDICINE_SUBMIT` or `MEDICINE_APPROVE` (org-scoped) | `?status=` | `List<MedicineSubmissionDto>` |
| `POST` | `/api/v1/medicine-submissions` | `MEDICINE_SUBMIT` | `Idempotency-Key` | `MedicineSubmissionDto` → `MedicineSubmissionDto` (`201 Created`) |
| `PUT` | `/api/v1/medicine-submissions/{submissionId}` | `MEDICINE_SUBMIT` / `MEDICINE_EDIT` + org | `Idempotency-Key` | `MedicineSubmissionDto` → `MedicineSubmissionDto` |
| `POST` | `/api/v1/medicine-submissions/{submissionId}/submit` | `MEDICINE_SUBMIT` + org | `Idempotency-Key` | — → `MedicineSubmissionDto` |
| `POST` | `/api/v1/medicine-submissions/{submissionId}/withdraw` | `MEDICINE_SUBMIT` + org | `Idempotency-Key` | — → `MedicineSubmissionDto` |
| `POST` | `/api/v1/medicine-submissions/{submissionId}/approve` | `MEDICINE_APPROVE` + org + non-self | `Idempotency-Key` | `ApprovalDecisionRequestDto` → `MedicineSubmissionDto` |
| `POST` | `/api/v1/medicine-submissions/{submissionId}/reject` | `MEDICINE_REJECT` + org + mandatory reason | `Idempotency-Key` | `ApprovalDecisionRequestDto` → `MedicineSubmissionDto` |
| `GET` | `/api/v1/medicine-submissions/audit` | `MEDICINE_APPROVE` or `MEDICINE_SUBMIT` (org-scoped) | `?submissionId=` | `List<ApprovalAuditEntryDto>` |
| `GET` | `/api/v1/company/master-data` | `COMPOSITION_MANAGE` / `DOSAGE_FORM_MANAGE` / `BRAND_MANAGE` | `?type=` | `List<MasterDataItemDto>` |
| `POST` | `/api/v1/company/master-data` | Matching master-data permission | `Idempotency-Key` | `MasterDataItemDto` → `MasterDataItemDto` (`201 Created`) |
| `PATCH` | `/api/v1/company/master-data/{id}/status` | Matching master-data permission | `Idempotency-Key` | `UpdateMasterDataStatusRequestDto` → `Unit` |

---

### K. Manufacturer Offer Banners, Campaigns, Announcements & Media Upload (`WorkspaceApiService`) `[PROPOSED]`

> **Banner & Announcement Integrity Rules:**
> - Linked `linkedProductIds` / `productId` **MUST** belong to the caller's `organizationId` AND be `APPROVED` + `ACTIVE` (returns `400 Bad Request` / `403 Forbidden` otherwise).
> - `GET /api/v1/stockist/active-banners` **MUST** return only banners where `status = 'ACTIVE' AND start_date <= NOW() AND end_date >= NOW()` (auto-expiring/hiding expired or inactive banners).
> - Media upload returns a server-hosted HTTPS URL stored on `banner.imageUrl` or `submission.imageUri`.

| Method | Path | Required Permission | Headers / Query | Request / Response `data` |
|---|---|---|---|---|
| `GET` | `/api/v1/manufacturer/banners` | `BANNER_CREATE` / `BANNER_UPDATE` (org-scoped) | `?status=` | `List<OfferBannerDto>` |
| `POST` | `/api/v1/manufacturer/banners` | `BANNER_CREATE` | `Idempotency-Key` | `OfferBannerDto` → `OfferBannerDto` (`201 Created`) |
| `PUT` | `/api/v1/manufacturer/banners/{bannerId}` | `BANNER_UPDATE` + org ownership | `Idempotency-Key` | `OfferBannerDto` → `OfferBannerDto` |
| `PATCH` | `/api/v1/manufacturer/banners/{bannerId}/status` | `BANNER_UPDATE` + org ownership | `Idempotency-Key` | `UpdateBannerStatusRequestDto` → `Unit` |
| `DELETE` | `/api/v1/manufacturer/banners/{bannerId}` | `BANNER_DELETE` + org ownership (soft archive) | — | `Unit` |
| `GET` | `/api/v1/manufacturer/campaigns` | `CAMPAIGN_MANAGE` (org-scoped) | — | `List<PromotionalCampaignDto>` |
| `POST` | `/api/v1/manufacturer/campaigns` | `CAMPAIGN_MANAGE` | `Idempotency-Key` | `PromotionalCampaignDto` → `PromotionalCampaignDto` (`201 Created`) |
| `PATCH` | `/api/v1/manufacturer/campaigns/{campaignId}/status` | `CAMPAIGN_MANAGE` + org ownership | `Idempotency-Key` | `UpdateCampaignStatusRequestDto` → `Unit` |
| `GET` | `/api/v1/manufacturer/announcements` | `MEDICINE_ANNOUNCEMENT_CREATE` (org-scoped) | — | `List<MedicineAnnouncementDto>` |
| `POST` | `/api/v1/manufacturer/announcements` | `MEDICINE_ANNOUNCEMENT_CREATE` | `Idempotency-Key` | `MedicineAnnouncementDto` → `MedicineAnnouncementDto` (`201 Created`) |
| `POST` | `/api/v1/media/upload` | `BANNER_CREATE` or `MEDICINE_SUBMIT` | `Multipart` (`file`, `category`) | `MediaUploadResponseDto` (`url`, `mimeType`, `sizeBytes`) |

---

### L. Stockist Promotional Feed & Strict Purchased Inventory Isolation (`WorkspaceApiService`) `[PROPOSED]`

> **Strict Inventory Isolation Rule:**
> - Stockists **NEVER** access `/api/v1/inventory/**` or `/api/v1/batches/**` (Manufacturer warehouse stock → `403 Forbidden`).
> - Stockists manage only their **own purchased inventory** under `/api/v1/stockist/purchased-inventory/**`, strictly partitioned by `stockist_user_id` + `stockist_organization_id`.
> - Manufacturer / Company accounts attempting `/api/v1/stockist/purchased-inventory/**` **MUST** receive `403 Forbidden`.

| Method | Path | Required Permission | Headers / Query | Request / Response `data` |
|---|---|---|---|---|
| `GET` | `/api/v1/stockist/active-banners` | `PRODUCT_VIEW` | — | `List<OfferBannerDto>` (eligible active & unexpired only) |
| `GET` | `/api/v1/stockist/announcements` | `PRODUCT_VIEW` | — | `List<MedicineAnnouncementDto>` (`PUBLISHED` only) |
| `GET` | `/api/v1/stockist/purchased-inventory` | `STOCKIST_INVENTORY_MANAGE` (user/org-scoped) | — | `List<StockistPurchasedInventoryDto>` |
| `POST` | `/api/v1/stockist/purchased-inventory` | `STOCKIST_INVENTORY_MANAGE` | `Idempotency-Key` | `StockistPurchasedInventoryDto` → `StockistPurchasedInventoryDto` (`201 Created`) |
| `PATCH` | `/api/v1/stockist/purchased-inventory/{itemId}/quantity` | `STOCKIST_INVENTORY_MANAGE` + ownership | `Idempotency-Key` | `AdjustStockistPurchasedQtyRequestDto` → `StockistPurchasedInventoryDto` |

---

## 4. Idempotency & Error Handling Contract `[PROPOSED]`

1. **Idempotency Keys:** All mutating `POST`/`PUT`/`PATCH` requests across orders, approvals, banners, campaigns, announcements, master data, and purchased inventory send `Idempotency-Key: <UUID>`. Spring Boot should deduplicate repeated keys per `(user_id, idempotency_key)` in an `idempotency_keys` PostgreSQL table (TTL 24h) and replay the original `200`/`201` response on retry, or return `409 Conflict` if the payload hash differs.
2. **HTTP 401 Session Expiration:** Whenever any protected endpoint returns `401 Unauthorized`, the Android `AuthInterceptor` emits `NetworkModule.sessionExpiredEvents`, which automatically logs out the user, clears auth tokens & sensitive local session state, and navigates to `LoginScreen`.
3. **HTTP 403 Permission Denial:** Both backend (`@PreAuthorize` + organization ownership check) and Android UI (`WorkspaceAccessDeniedScreen`) deny unauthorized role/org access without leaking protected records.

---

## 5. Proposed PostgreSQL Database Schema (DDL — Raw SQL, No Hibernate/JPA)

```sql
-- 1. Organizations (Two primary types only: STOCKIST, PHARMACEUTICAL_ORGANIZATION)
CREATE TABLE organizations (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,
    organization_type   VARCHAR(40)  NOT NULL CHECK (organization_type IN ('STOCKIST', 'PHARMACEUTICAL_ORGANIZATION')),
    gstin               VARCHAR(32),
    drug_license_number VARCHAR(64),
    contact_email       VARCHAR(160),
    contact_phone       VARCHAR(32),
    address             TEXT,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 2. Users & Organization Memberships (Multi-role per user inside unified org)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS organization_id   BIGINT REFERENCES organizations(id),
    ADD COLUMN IF NOT EXISTS organization_type VARCHAR(40) NOT NULL DEFAULT 'STOCKIST',
    ADD COLUMN IF NOT EXISTS organization_name VARCHAR(200) NOT NULL DEFAULT '';

CREATE TABLE organization_memberships (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       NOT NULL REFERENCES organizations(id),
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, user_id)
);

CREATE TABLE membership_roles (
    membership_id   BIGINT      NOT NULL REFERENCES organization_memberships(id) ON DELETE CASCADE,
    role_code       VARCHAR(40) NOT NULL CHECK (role_code IN ('STOCKIST', 'MANUFACTURER', 'PHARMA_COMPANY', 'ADMIN')),
    PRIMARY KEY (membership_id, role_code)
);

CREATE TABLE role_permissions (
    role_code       VARCHAR(40) NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_code, permission_code)
);

-- 3. Products Catalogue Extensions (Approval + Soft Publication Lifecycle)
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS organization_id        BIGINT REFERENCES organizations(id),
    ADD COLUMN IF NOT EXISTS approval_status        VARCHAR(32) NOT NULL DEFAULT 'APPROVED'
        CHECK (approval_status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    ADD COLUMN IF NOT EXISTS catalogue_status       VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        CHECK (catalogue_status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    ADD COLUMN IF NOT EXISTS submitted_by_user_id   BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS approved_by_user_id    BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS approved_at            TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejection_reason       TEXT,
    ADD COLUMN IF NOT EXISTS brand_name             VARCHAR(160) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS generic_name           VARCHAR(160) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS therapeutic_category   VARCHAR(120) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS packaging_type         VARCHAR(80)  NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS shelf_life_months      INT          NOT NULL DEFAULT 24,
    ADD COLUMN IF NOT EXISTS storage_conditions     VARCHAR(160) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS prescription_required  BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS gst_percentage         NUMERIC(5,2) NOT NULL DEFAULT 12.00,
    ADD COLUMN IF NOT EXISTS min_order_quantity     INT          NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS image_uri              TEXT,
    ADD COLUMN IF NOT EXISTS notes                  TEXT;

CREATE INDEX IF NOT EXISTS idx_products_stockist_discovery
    ON products (approval_status, catalogue_status, is_active);

-- 4. Compulsory Medicine Approval Submissions & Immutable Audit Trail
CREATE TABLE medicine_submissions (
    id                      BIGSERIAL PRIMARY KEY,
    organization_id         BIGINT       NOT NULL REFERENCES organizations(id),
    linked_product_id       BIGINT       REFERENCES products(id),
    medicine_name           VARCHAR(200) NOT NULL,
    brand_name              VARCHAR(160) NOT NULL DEFAULT '',
    generic_name            VARCHAR(160) NOT NULL DEFAULT '',
    salt_composition        VARCHAR(240) NOT NULL,
    dosage_form             VARCHAR(80)  NOT NULL,
    strength                VARCHAR(80)  NOT NULL,
    pack_size               VARCHAR(80)  NOT NULL,
    packaging_type          VARCHAR(80)  NOT NULL DEFAULT '',
    therapeutic_category    VARCHAR(120) NOT NULL DEFAULT '',
    manufacturer_name       VARCHAR(200) NOT NULL,
    hsn_code                VARCHAR(32)  NOT NULL DEFAULT '30049099',
    gst_percentage          NUMERIC(5,2) NOT NULL DEFAULT 12.00,
    mrp                     NUMERIC(12,2) NOT NULL CHECK (mrp > 0),
    ptr                     NUMERIC(12,2) NOT NULL CHECK (ptr > 0 AND ptr <= mrp),
    min_order_quantity      INT          NOT NULL DEFAULT 1,
    shelf_life_months       INT          NOT NULL DEFAULT 24,
    storage_conditions      VARCHAR(160) NOT NULL DEFAULT '',
    prescription_required   BOOLEAN      NOT NULL DEFAULT TRUE,
    image_uri               TEXT,
    notes                   TEXT,
    status                  VARCHAR(32)  NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    catalogue_status        VARCHAR(32)  NOT NULL DEFAULT 'INACTIVE'
        CHECK (catalogue_status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    submitted_by_user_id    BIGINT       NOT NULL REFERENCES users(id),
    submitted_by_name       VARCHAR(160) NOT NULL,
    submitted_at            TIMESTAMPTZ,
    reviewed_by_user_id     BIGINT       REFERENCES users(id),
    reviewed_by_name        VARCHAR(160),
    reviewed_at             TIMESTAMPTZ,
    rejection_reason        TEXT,
    revision_number         INT          NOT NULL DEFAULT 1,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_medicine_submissions_org_status ON medicine_submissions (organization_id, status);

CREATE TABLE approval_audit_logs (
    id                  BIGSERIAL PRIMARY KEY,
    submission_id       BIGINT       NOT NULL REFERENCES medicine_submissions(id) ON DELETE CASCADE,
    organization_id     BIGINT       NOT NULL REFERENCES organizations(id),
    action              VARCHAR(40)  NOT NULL,
    from_status         VARCHAR(32)  NOT NULL,
    to_status           VARCHAR(32)  NOT NULL,
    actor_user_id       BIGINT       NOT NULL REFERENCES users(id),
    actor_name          VARCHAR(160) NOT NULL,
    reason_or_comment   TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 5. Company Master Data (7 categories)
CREATE TABLE master_data_items (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       NOT NULL REFERENCES organizations(id),
    data_type       VARCHAR(48)  NOT NULL CHECK (data_type IN (
        'SALT_COMPOSITION', 'DOSAGE_FORM', 'THERAPEUTIC_CATEGORY',
        'BRAND_NAME', 'MANUFACTURER_DIVISION', 'PACK_SIZE', 'GST_HSN_PRESET'
    )),
    name            VARCHAR(200) NOT NULL,
    code            VARCHAR(64)  NOT NULL DEFAULT '',
    description     TEXT         NOT NULL DEFAULT '',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, data_type, name)
);

-- 6. Manufacturer Offer Banners, Campaigns & Announcements
CREATE TABLE offer_banners (
    id                      BIGSERIAL PRIMARY KEY,
    organization_id         BIGINT        NOT NULL REFERENCES organizations(id),
    created_by_user_id      BIGINT        NOT NULL REFERENCES users(id),
    title                   VARCHAR(160)  NOT NULL,
    subtitle                VARCHAR(240)  NOT NULL DEFAULT '',
    description             TEXT          NOT NULL DEFAULT '',
    image_url               TEXT          NOT NULL DEFAULT '',
    offer_type              VARCHAR(40)   NOT NULL,
    discount_or_scheme_text VARCHAR(120)  NOT NULL,
    linked_product_ids      BIGINT[]      NOT NULL DEFAULT '{}',
    linked_category         VARCHAR(120)  NOT NULL DEFAULT '',
    start_date              TIMESTAMPTZ   NOT NULL,
    end_date                TIMESTAMPTZ   NOT NULL CHECK (end_date > start_date),
    min_order_quantity      INT           NOT NULL DEFAULT 1,
    target_audience         VARCHAR(40)   NOT NULL DEFAULT 'ALL_STOCKISTS',
    priority                INT           NOT NULL DEFAULT 1,
    cta_text                VARCHAR(60)   NOT NULL DEFAULT 'Explore Offer',
    cta_destination_route   VARCHAR(120)  NOT NULL DEFAULT 'medicine_catalogue',
    status                  VARCHAR(32)   NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'EXPIRED', 'INACTIVE', 'ARCHIVED')),
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_offer_banners_active_feed ON offer_banners (status, start_date, end_date, priority);

CREATE TABLE promotional_campaigns (
    id                  BIGSERIAL PRIMARY KEY,
    organization_id     BIGINT       NOT NULL REFERENCES organizations(id),
    created_by_user_id  BIGINT       NOT NULL REFERENCES users(id),
    name                VARCHAR(200) NOT NULL,
    description         TEXT         NOT NULL DEFAULT '',
    start_date          TIMESTAMPTZ  NOT NULL,
    end_date            TIMESTAMPTZ  NOT NULL CHECK (end_date > start_date),
    linked_banner_ids   BIGINT[]     NOT NULL DEFAULT '{}',
    linked_product_ids  BIGINT[]     NOT NULL DEFAULT '{}',
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE medicine_announcements (
    id                  BIGSERIAL PRIMARY KEY,
    organization_id     BIGINT       NOT NULL REFERENCES organizations(id),
    created_by_user_id  BIGINT       NOT NULL REFERENCES users(id),
    product_id          BIGINT       NOT NULL REFERENCES products(id),
    title               VARCHAR(200) NOT NULL,
    message             TEXT         NOT NULL,
    category            VARCHAR(40)  NOT NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'PUBLISHED',
    published_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 7. Stockist Purchased Inventory (Strictly isolated from manufacturer warehouse inventory)
CREATE TABLE stockist_purchased_inventory (
    id                          BIGSERIAL PRIMARY KEY,
    stockist_user_id            BIGINT        NOT NULL REFERENCES users(id),
    stockist_organization_id    BIGINT        NOT NULL REFERENCES organizations(id),
    product_id                  BIGINT        NOT NULL REFERENCES products(id),
    product_name                VARCHAR(200)  NOT NULL,
    salt_composition            VARCHAR(240)  NOT NULL DEFAULT '',
    batch_number                VARCHAR(64)   NOT NULL,
    expiry_date                 DATE          NOT NULL,
    quantity_on_hand            INT           NOT NULL CHECK (quantity_on_hand >= 0),
    reorder_level               INT           NOT NULL DEFAULT 10,
    purchase_price_per_unit     NUMERIC(12,2) NOT NULL DEFAULT 0,
    source_order_id             BIGINT,
    supplier_name               VARCHAR(200)  NOT NULL DEFAULT '',
    updated_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (stockist_user_id, product_id, batch_number)
);
```

### Entity-Relationship Diagram (Mermaid / ASCII)
```
organizations (1) ──< (N) organization_memberships (N) >── (1) users
                                │
                                └──< membership_roles (N) >── role_permissions

organizations (1) ──< (N) products (approval_status='APPROVED', catalogue_status='ACTIVE')
                  ├──< (N) medicine_submissions (1) ──< (N) approval_audit_logs
                  ├──< (N) master_data_items
                  ├──< (N) offer_banners (linked_product_ids -> APPROVED products)
                  ├──< (N) promotional_campaigns
                  └──< (N) medicine_announcements (product_id -> APPROVED product)

users [STOCKIST] (1) ──< (N) stockist_purchased_inventory  (STRICTLY ISOLATED from manufacturer warehouse batches)
```

---

## 6. Migration Plan (Backward-Compatible Existing Database Upgrade)
1. **V1 → V2 (Organizations & Memberships):** Create `organizations`, `organization_memberships`, `membership_roles`, and `role_permissions`. Seed two org records per existing business (`STOCKIST` for existing stockists, `PHARMACEUTICAL_ORGANIZATION` for existing manufacturers/companies) and backfill `users.organization_id`.
2. **V2 → V3 (Catalogue Governance):** Add `organization_id`, `approval_status`, and `catalogue_status` columns to `products` with safe defaults (`APPROVED`, `ACTIVE`) so existing live medicines remain discoverable without disruption.
3. **V3 → V4 (Workflow & Promotions):** Create `medicine_submissions`, `approval_audit_logs`, `master_data_items`, `offer_banners`, `promotional_campaigns`, `medicine_announcements`, and `stockist_purchased_inventory` with indexes.

---

## 7. Spring Boot + JDBC Integration Guide (No Hibernate / No JPA)

1. **Pure JDBC (`NamedParameterJdbcTemplate`):** Use `spring-boot-starter-jdbc` and `org.postgresql:postgresql` with explicit parameterized SQL (`:orgId`, `:status`, `:userId`) — zero ORM/JPA/Hibernate dependencies.
2. **Stateless JWT Filter:** Extract `userId`, `organizationId`, `organizationType`, `roles[]`, and `permissions[]` from signed JWT claims (or verify active membership via indexed JDBC query on sensitive writes like `MEDICINE_APPROVE` and `ROLE_ASSIGN`).
3. **Atomic Approval Transaction (`@Transactional`):**
   - `SELECT * FROM medicine_submissions WHERE id = :id AND organization_id = :orgId FOR UPDATE`
   - Verify `status == 'PENDING_APPROVAL'` and `submitted_by_user_id != :actorUserId` (unless platform `ADMIN`).
   - Upsert approved record into `products` with `approval_status = 'APPROVED'`, `catalogue_status = 'ACTIVE'`.
   - Update `medicine_submissions` to `APPROVED` and insert immutable row in `approval_audit_logs`.
4. **Atomic Order & Stock Updates (JDBC):** `POST /api/v1/orders` and `PATCH /api/v1/batches/{batchId}/stock` execute inside explicit `@Transactional` JDBC blocks with `WHERE available_quantity >= :qty`.
5. **Confirming Contracts:** Once the Spring Boot backend URL and JSON contract are finalized, update either `BuildConfig.PHARMAOS_API_BASE_URL` in `app/build.gradle.kts` or configure it at runtime via the **Configure** button on the Login/Register/Settings screens.


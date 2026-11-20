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

| Method | Path | Auth | Request / Query | Response `data` |
|---|---|---|---|---|
| `GET` | `/api/v1/inventory` | Bearer | `?lowStockOnly=false` | `List<ProductInventoryDto>` |
| `GET` | `/api/v1/inventory/{sku}` | Bearer | — | `ProductInventoryDto` |
| `POST` | `/api/v1/inventory` | Bearer | `ProductInventoryDto` | `ProductInventoryDto` |
| `PATCH` | `/api/v1/inventory/{sku}/stock` | Bearer | `AdjustInventoryStockRequestDto` | `ProductInventoryDto` |
| `PATCH` | `/api/v1/inventory/{sku}/threshold` | Bearer | `UpdateReorderThresholdRequestDto` | `ProductInventoryDto` |
| `DELETE` | `/api/v1/inventory/{sku}` | Bearer | — | `Unit` |
| `GET` | `/api/v1/batches` | Bearer | — | `List<BatchDto>` |
| `POST` | `/api/v1/batches` | Bearer | `BatchDto` | `BatchDto` |
| `PATCH` | `/api/v1/batches/{batchId}/stock` | Bearer | `AdjustBatchStockRequestDto` | `BatchDto` |
| `GET` | `/api/v1/shortage-alerts` | Bearer | — | `List<ShortageAlertDto>` |
| `POST` | `/api/v1/shortage-alerts/evaluate` | Bearer | — | `List<ShortageAlertDto>` |
| `GET` | `/api/v1/recalls` | Bearer | — | `List<RecallDto>` |
| `POST` | `/api/v1/recalls` | Bearer | `CreateRecallRequestDto` | `RecallDto` |

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

## 4. PostgreSQL + JDBC Integration Notes for Backend Team

1. **IDs & Conflict Handling:** PostgreSQL `BIGSERIAL PRIMARY KEY` maps to `Long` IDs on the Android side. Room caches use upsert (`insert` or `update`) without relying on password hashes from the server (`passwordHash` and `passwordSalt` are never returned in `UserDto`).
2. **Passwords & Hashing:** Spring Boot backend should store BCrypt/Argon2 password hashes in PostgreSQL and never expose password hashes in API responses.
3. **Role Enforcement:** Valid enum strings are `STOCKIST`, `PHARMA_COMPANY`, `MANUFACTURER`, `ADMIN`. Account status strings are `ACTIVE`, `PENDING`, `SUSPENDED`.
4. **Atomic Order & Stock Updates (JDBC):** `POST /api/v1/orders` and `PATCH /api/v1/batches/{batchId}/stock` should execute inside explicit Spring `@Transactional` JDBC transactions with row-level checks (`WHERE available_quantity >= ?`) to avoid overselling.
5. **Confirming Contracts:** Once the Spring Boot backend URL and JSON contract are finalized, update either `BuildConfig.PHARMAOS_API_BASE_URL` in `app/build.gradle.kts` or configure it at runtime via the **Configure** button on the Login/Register/Settings screens.

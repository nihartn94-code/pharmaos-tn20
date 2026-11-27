package com.example

import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.ProductInventoryDao
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ProductInventoryEntity
import com.example.data.remote.config.ApiConfig
import com.example.data.remote.dto.ApiErrorDto
import com.example.data.remote.dto.ApiResponseDto
import com.example.data.remote.dto.AuthRequestDto
import com.example.data.remote.dto.AuthResponseDto
import com.example.data.remote.dto.OrderDto
import com.example.data.remote.dto.ProductDto
import com.example.data.remote.dto.ProductInventoryDto
import com.example.data.remote.dto.RegisterRequestDto
import com.example.data.remote.dto.UserDto
import com.example.data.remote.interceptor.TokenProvider
import com.example.data.remote.source.AuthRemoteDataSource
import com.example.data.remote.source.PharmaRemoteDataSource
import com.example.data.remote.util.ApiException
import com.example.data.remote.util.NetworkFailureType
import com.example.data.remote.util.NetworkResult
import com.example.data.remote.util.safeApiCall
import com.example.data.repository.ProductRepository
import com.example.di.NetworkModule
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * [MOCKED HTTP TEST — NOT LIVE SERVER]
 *
 * Verifies PharmaOS Retrofit/OkHttp/Moshi configuration, URL security validation,
 * Bearer auth header injection, HTTP status & failure mapping, and remote-guarded
 * repository behavior using an in-memory OkHttp mock interceptor.
 */
class NetworkIntegrationTests {

    private class InMemoryTokenProvider(var token: String? = null) : TokenProvider {
        override fun getAccessToken(): String? = token
    }

    private fun jsonResponse(request: Request, code: Int, json: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error $code")
            .body(json.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    private fun emptyResponse(request: Request, code: Int): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("No Content")
            .body("".toResponseBody(null))
            .build()
    }

    // 1. ApiConfig & NetworkModule URL Validation Tests
    @Test
    fun testApiConfigAndDynamicBaseUrlValidation() {
        assertFalse(ApiConfig.isBackendUrlConfigured(ApiConfig.PLACEHOLDER_UNCONFIGURED_BASE_URL))
        assertFalse(ApiConfig.isBackendUrlConfigured(ApiConfig.PHYSICAL_DEVICE_PLACEHOLDER_URL))
        assertFalse(ApiConfig.isBackendUrlConfigured("   "))
        assertFalse(ApiConfig.isBackendUrlConfigured("http://public.example.com:8080/"))

        assertTrue(ApiConfig.isBackendUrlConfigured(ApiConfig.EMULATOR_DEFAULT_BASE_URL))
        assertTrue(ApiConfig.isAllowedLocalHttpUrl("http://10.0.2.2:8080"))
        assertTrue(ApiConfig.isAllowedLocalHttpUrl("http://192.168.1.45:8080/"))
        assertTrue(ApiConfig.isAllowedLocalHttpUrl("http://localhost:8080/"))
        assertFalse(ApiConfig.isAllowedLocalHttpUrl("http://api.external.com/"))

        assertFalse(ApiConfig.isSecureForProduction("http://10.0.2.2:8080/"))
        assertFalse(ApiConfig.isSecureForProduction(ApiConfig.PLACEHOLDER_UNCONFIGURED_BASE_URL))
        assertTrue(ApiConfig.isSecureForProduction("https://api.pharmaos.example.org"))

        assertEquals("https://api.pharmaos.example.org/", ApiConfig.normalizeBaseUrl("https://api.pharmaos.example.org"))

        val module = NetworkModule(
            tokenProvider = InMemoryTokenProvider(),
            initialBaseUrl = ApiConfig.PLACEHOLDER_UNCONFIGURED_BASE_URL
        )
        assertFalse(module.isBackendConfigured())

        module.updateBaseUrl("http://10.0.2.2:8080")
        assertTrue(module.isBackendConfigured())
        assertEquals("http://10.0.2.2:8080/", module.getCurrentBaseUrl())
    }

    // 2. Moshi DTO Serialization & Deserialization Tests
    @Test
    fun testMoshiDtoSerializationAndDeserialization() {
        val moshi = NetworkModule(InMemoryTokenProvider()).moshi

        val loginReq = AuthRequestDto("mfg@pharmaos.com", "Secret@123", "MANUFACTURER")
        val loginJson = moshi.adapter(AuthRequestDto::class.java).toJson(loginReq)
        val decodedLogin = moshi.adapter(AuthRequestDto::class.java).fromJson(loginJson)
        assertEquals(loginReq, decodedLogin)

        val regReq = RegisterRequestDto(
            fullName = "Rohan Sharma",
            businessName = "Apex Pharma",
            email = "rohan@apex.com",
            phone = "9876543210",
            gstin = "27ABCDE1234F1Z5",
            address = "Mumbai",
            password = "Password@123",
            role = "STOCKIST"
        )
        val regJson = moshi.adapter(RegisterRequestDto::class.java).toJson(regReq)
        assertTrue(regJson.contains("\"businessName\":\"Apex Pharma\""))

        val envelopeType = Types.newParameterizedType(ApiResponseDto::class.java, AuthResponseDto::class.java)
        val envelopeAdapter = moshi.adapter<ApiResponseDto<AuthResponseDto>>(envelopeType)
        val sampleJson = """
            {
              "success": true,
              "message": "Authenticated",
              "data": {
                "accessToken": "jwt-access-123",
                "refreshToken": "jwt-refresh-456",
                "tokenType": "Bearer",
                "expiresInSeconds": 3600,
                "user": {
                  "id": 77,
                  "fullName": "Dr. Vikram",
                  "businessName": "Bharat Bio-Pharma",
                  "email": "mfg@pharmaos.com",
                  "phone": "9876543220",
                  "address": "MIDC Tarapur",
                  "role": "MANUFACTURER",
                  "status": "ACTIVE",
                  "createdAtEpochMillis": 1700000000000
                }
              }
            }
        """.trimIndent()

        val parsedEnvelope = envelopeAdapter.fromJson(sampleJson)
        assertNotNull(parsedEnvelope)
        assertTrue(parsedEnvelope!!.success)
        assertEquals("jwt-access-123", parsedEnvelope.data?.accessToken)
        assertEquals(77L, parsedEnvelope.data?.user?.id)
        assertEquals("MANUFACTURER", parsedEnvelope.data?.user?.role)

        val productDto = ProductDto(
            id = 12L,
            name = "Augmentin 625 Duo",
            brand = "GSK",
            saltComposition = "Amoxicillin + Clavulanic Acid",
            strength = "625mg",
            dosageForm = "TABLET",
            therapeuticCategory = "Antibiotic",
            manufacturerName = "GSK",
            packSize = "10s",
            hsnCode = "3004",
            gstRatePercent = 12.0,
            storageCondition = "ROOM_TEMPERATURE",
            isRx = true
        )
        val productJson = moshi.adapter(ProductDto::class.java).toJson(productDto)
        assertEquals(productDto, moshi.adapter(ProductDto::class.java).fromJson(productJson))

        val invDto = ProductInventoryDto(
            id = 5L,
            productName = "Augmentin 625",
            sku = "SKU-AUG-625",
            currentStock = 14,
            reorderThreshold = 25
        )
        val invJson = moshi.adapter(ProductInventoryDto::class.java).toJson(invDto)
        assertEquals(invDto, moshi.adapter(ProductInventoryDto::class.java).fromJson(invJson))

        val orderDto = OrderDto(
            id = 90L,
            orderNumber = "PO-2026-0090",
            buyerId = 10L,
            supplierId = 20L,
            status = "PLACED",
            subtotal = 1000.0,
            gstAmount = 120.0,
            deliveryCharge = 50.0,
            totalAmount = 1170.0,
            deliveryAddress = "Mumbai",
            createdAtEpochMillis = 1700000000000L
        )
        val orderJson = moshi.adapter(OrderDto::class.java).toJson(orderDto)
        assertEquals(orderDto, moshi.adapter(OrderDto::class.java).fromJson(orderJson))

        val errorDto = ApiErrorDto(
            status = 422,
            message = "Validation failed",
            fieldErrors = mapOf("email" to "Already registered")
        )
        val friendly = errorDto.toUserFriendlyMessage("Fallback")
        assertTrue(friendly.contains("Validation failed"))
        assertTrue(friendly.contains("email: Already registered"))
    }

    // 3. AuthInterceptor Bearer Header Injection vs Public Route Exclusion
    @Test
    fun testAuthInterceptorHeaderInjection() = runBlocking {
        val capturedHeaders = mutableListOf<Pair<String, String?>>()
        val tokenProvider = InMemoryTokenProvider("secret-jwt-token")

        val captureInterceptor = Interceptor { chain ->
            val req = chain.request()
            capturedHeaders.add(req.url.encodedPath to req.header("Authorization"))
            assertEquals("application/json", req.header("Accept"))
            assertEquals("Android-PharmaOS", req.header("X-Client-Platform"))

            val path = req.url.encodedPath
            when {
                path.endsWith("/auth/login") -> jsonResponse(
                    req,
                    200,
                    """{"success":true,"data":{"accessToken":"t","tokenType":"Bearer","user":{"id":1,"fullName":"A","businessName":"B","email":"a@b.com","phone":"9999999999","address":"Addr","role":"STOCKIST","status":"ACTIVE","createdAtEpochMillis":1}}}"""
                )
                path.endsWith("/products") -> jsonResponse(
                    req,
                    200,
                    """{"success":true,"data":[]}"""
                )
                else -> emptyResponse(req, 204)
            }
        }

        val module = NetworkModule(
            tokenProvider = tokenProvider,
            initialBaseUrl = ApiConfig.EMULATOR_DEFAULT_BASE_URL,
            extraInterceptors = listOf(captureInterceptor)
        )

        val authSource = AuthRemoteDataSource(module)
        val loginRes = authSource.login("a@b.com", "Password@123", "STOCKIST")
        assertTrue(loginRes is NetworkResult.Success)

        val prodRes = PharmaRemoteDataSource(module).fetchProducts()
        assertTrue(prodRes is NetworkResult.Success)

        assertEquals(2, capturedHeaders.size)
        // Public login route must NOT send Authorization header
        assertEquals("/api/v1/auth/login", capturedHeaders[0].first)
        assertNull(capturedHeaders[0].second)
        // Protected products route MUST send Authorization: Bearer <token>
        assertEquals("/api/v1/products", capturedHeaders[1].first)
        assertEquals("Bearer secret-jwt-token", capturedHeaders[1].second)
    }

    // 4. HTTP Status Codes & Network Exceptions Handling
    @Test
    fun testSafeApiCallHttpStatusesAndExceptions() = runBlocking {
        var nextStatusCode = 200
        var nextBody = """{"success":true,"data":[]}"""
        var nextException: Throwable? = null

        val mockInterceptor = Interceptor { chain ->
            nextException?.let { throw it }
            val req = chain.request()
            if (nextStatusCode == 204) emptyResponse(req, 204) else jsonResponse(req, nextStatusCode, nextBody)
        }

        val module = NetworkModule(
            tokenProvider = InMemoryTokenProvider("jwt"),
            initialBaseUrl = ApiConfig.EMULATOR_DEFAULT_BASE_URL,
            extraInterceptors = listOf(mockInterceptor)
        )
        val api = module.productApiService()

        // HTTP 401 Unauthorized
        nextStatusCode = 401
        nextBody = """{"status":401,"message":"Invalid credentials"}"""
        val res401 = safeApiCall(module.moshi) { api.getProducts() }
        assertTrue(res401 is NetworkResult.HttpError)
        assertEquals(401, (res401 as NetworkResult.HttpError).code)
        assertEquals("Invalid credentials", res401.message)

        // HTTP 404 Not Found
        nextStatusCode = 404
        nextBody = """{"status":404,"message":"Product not found"}"""
        val res404 = safeApiCall(module.moshi) { api.getProductById(999L) }
        assertTrue(res404 is NetworkResult.HttpError)
        assertEquals(404, (res404 as NetworkResult.HttpError).code)

        // HTTP 422 Validation Error with fieldErrors
        nextStatusCode = 422
        nextBody = """{"status":422,"message":"Validation error","fieldErrors":{"sku":"Already exists"}}"""
        val res422 = safeApiCall(module.moshi) { api.getProducts() }
        assertTrue(res422 is NetworkResult.HttpError)
        assertTrue((res422 as NetworkResult.HttpError).message.contains("sku: Already exists"))

        // HTTP 500 Server Error
        nextStatusCode = 500
        nextBody = """{"status":500,"message":"PostgreSQL connection failed"}"""
        val res500 = safeApiCall(module.moshi) { api.getProducts() }
        assertTrue(res500 is NetworkResult.HttpError)
        assertEquals(500, (res500 as NetworkResult.HttpError).code)

        // HTTP 204 No Content
        nextStatusCode = 204
        val res204 = safeApiCall(module.moshi) { api.deleteProduct(1L) }
        assertTrue(res204 is NetworkResult.Success)
        assertEquals(204, (res204 as NetworkResult.Success).httpCode)

        // Invalid JSON
        nextStatusCode = 200
        nextBody = """{ invalid json"""
        val resJsonErr = safeApiCall(module.moshi) { api.getProducts() }
        assertTrue(resJsonErr is NetworkResult.NetworkFailure)
        assertEquals(NetworkFailureType.INVALID_JSON, (resJsonErr as NetworkResult.NetworkFailure).type)

        // Timeout
        nextException = SocketTimeoutException("Read timed out")
        val resTimeout = safeApiCall(module.moshi) { api.getProducts() }
        assertTrue(resTimeout is NetworkResult.NetworkFailure)
        assertEquals(NetworkFailureType.TIMEOUT, (resTimeout as NetworkResult.NetworkFailure).type)

        // No internet / DNS
        nextException = UnknownHostException("Unable to resolve host")
        val resDns = safeApiCall(module.moshi) { api.getProducts() }
        assertTrue(resDns is NetworkResult.NetworkFailure)
        assertEquals(NetworkFailureType.NO_INTERNET, (resDns as NetworkResult.NetworkFailure).type)

        // Server Unavailable
        nextException = ConnectException("Connection refused")
        val resConnect = safeApiCall(module.moshi) { api.getProducts() }
        assertTrue(resConnect is NetworkResult.NetworkFailure)
        assertEquals(NetworkFailureType.SERVER_UNAVAILABLE, (resConnect as NetworkResult.NetworkFailure).type)
    }

    // 5. Unconfigured Backend & Remote-Guarded ProductRepository Write Failure Test
    @Test
    fun testUnconfiguredBackendAndNoSilentLocalFallbackOnRemoteFailure() = runBlocking {
        // Unconfigured URL must immediately return NotConfigured without HTTP calls
        val unconfiguredModule = NetworkModule(
            tokenProvider = InMemoryTokenProvider(),
            initialBaseUrl = ApiConfig.PLACEHOLDER_UNCONFIGURED_BASE_URL
        )
        val unconfiguredAuth = AuthRemoteDataSource(unconfiguredModule)
        val notConfiguredRes = unconfiguredAuth.login("a@b.com", "Password@123", "STOCKIST")
        assertTrue(notConfiguredRes is NetworkResult.NotConfigured)

        // Configured URL with mocked HTTP responses for ProductRepository
        var shouldFailServer = false
        val mockInterceptor = Interceptor { chain ->
            val req = chain.request()
            if (shouldFailServer) {
                jsonResponse(req, 500, """{"status":500,"message":"Database write error"}""")
            } else if (req.method == "POST" && req.url.encodedPath.endsWith("/products")) {
                jsonResponse(
                    req,
                    201,
                    """{"success":true,"data":{"id":501,"name":"Dolo 650","brand":"Micro Labs","saltComposition":"Paracetamol","strength":"650mg","dosageForm":"TABLET","therapeuticCategory":"Analgesic","manufacturerName":"Micro Labs","packSize":"15s","hsnCode":"3004","gstRatePercent":12.0,"storageCondition":"ROOM_TEMPERATURE","isRx":false,"isActive":true,"createdBy":1,"createdAtEpochMillis":1700000000000}}"""
                )
            } else {
                jsonResponse(req, 200, """{"success":true,"data":[]}""")
            }
        }

        val module = NetworkModule(
            tokenProvider = InMemoryTokenProvider("jwt"),
            initialBaseUrl = ApiConfig.EMULATOR_DEFAULT_BASE_URL,
            extraInterceptors = listOf(mockInterceptor)
        )
        val fakeProductDao = MockTestProductDao()
        val fakeInventoryDao = MockTestProductInventoryDao()
        val repo = ProductRepository(
            productDao = fakeProductDao,
            inventoryDao = fakeInventoryDao,
            ioDispatcher = Dispatchers.Unconfined,
            remoteDataSource = PharmaRemoteDataSource(module),
            isRemoteSyncEnabled = { true }
        )

        val productToCreate = ProductEntity(
            name = "Dolo 650",
            brand = "Micro Labs",
            saltComposition = "Paracetamol",
            strength = "650mg",
            dosageForm = "TABLET",
            therapeuticCategory = "Analgesic",
            manufacturerName = "Micro Labs",
            packSize = "15s",
            hsnCode = "3004",
            gstRatePercent = 12.0,
            storageCondition = "ROOM_TEMPERATURE",
            isRx = false,
            createdBy = 1L
        )

        // Remote success -> returns server ID 501 and caches in Room DAO
        val createdId = repo.insertProduct(productToCreate)
        assertEquals(501L, createdId)
        assertEquals(1, fakeProductDao.getAllProducts().size)

        // Remote failure -> throws ApiException and must NOT insert second local item silently
        shouldFailServer = true
        try {
            repo.insertProduct(productToCreate.copy(name = "Should Not Persist"))
            fail("Expected ApiException when remote HTTP 500 occurs")
        } catch (ex: ApiException) {
            assertEquals(500, ex.httpCode)
            assertEquals(1, fakeProductDao.getAllProducts().size)
        }
    }
}

private class MockTestProductDao : ProductDao {
    private val items = mutableListOf<ProductEntity>()
    private var nextId = 1L

    override suspend fun insertProduct(product: ProductEntity): Long {
        val id = if (product.id > 0) product.id else nextId++
        val saved = product.copy(id = id)
        items.removeAll { it.id == id }
        items.add(saved)
        return id
    }

    override suspend fun insertProducts(products: List<ProductEntity>): List<Long> =
        products.map { insertProduct(it) }

    override suspend fun updateProduct(product: ProductEntity) {
        val idx = items.indexOfFirst { it.id == product.id }
        if (idx >= 0) items[idx] = product
    }

    override suspend fun deleteProduct(product: ProductEntity) {
        items.removeAll { it.id == product.id }
    }

    override suspend fun deleteProductById(productId: Long) {
        items.removeAll { it.id == productId }
    }

    override suspend fun deleteAllProducts() {
        items.clear()
    }

    override suspend fun getProductById(productId: Long): ProductEntity? =
        items.firstOrNull { it.id == productId }

    override fun getProductByIdFlow(productId: Long): Flow<ProductEntity?> =
        flowOf(items.firstOrNull { it.id == productId })

    override fun getAllActiveProductsFlow(): Flow<List<ProductEntity>> =
        flowOf(items.filter { it.isActive })

    override fun getAllProductsFlow(): Flow<List<ProductEntity>> = flowOf(items.toList())
    override suspend fun getAllProducts(): List<ProductEntity> = items.toList()
    override suspend fun getActiveProductCount(): Int = items.count { it.isActive }
    override fun getAlternativeBrandsFlow(saltComposition: String, strength: String, currentProductId: Long): Flow<List<ProductEntity>> = flowOf(emptyList())
    override suspend fun setProductActiveStatus(productId: Long, isActive: Boolean) {
        val idx = items.indexOfFirst { it.id == productId }
        if (idx >= 0) items[idx] = items[idx].copy(isActive = isActive)
    }
    override fun searchProductsFlow(query: String): Flow<List<ProductEntity>> = flowOf(emptyList())
    override suspend fun getProductsByOrganization(orgId: Long): List<ProductEntity> =
        items.filter { it.organizationId == orgId }
    override suspend fun deleteProductsByOrganization(orgId: Long) {
        items.removeAll { it.organizationId == orgId }
    }
    override suspend fun deleteProductsByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        items.removeAll { it.id in idSet }
    }
}

private class MockTestProductInventoryDao : ProductInventoryDao {
    private val items = mutableListOf<ProductInventoryEntity>()
    private val flow = MutableStateFlow<List<ProductInventoryEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insertInventory(inventory: ProductInventoryEntity): Long {
        val id = if (inventory.id > 0) inventory.id else nextId++
        items.removeAll { it.sku == inventory.sku || it.id == id }
        items.add(inventory.copy(id = id))
        flow.value = items.toList()
        return id
    }

    override suspend fun insertAll(items: List<ProductInventoryEntity>): List<Long> = items.map { insertInventory(it) }
    override suspend fun updateInventory(inventory: ProductInventoryEntity) {
        val idx = items.indexOfFirst { it.id == inventory.id || it.sku == inventory.sku }
        if (idx >= 0) items[idx] = inventory
        flow.value = items.toList()
    }
    override suspend fun deleteInventory(inventory: ProductInventoryEntity) {
        items.removeAll { it.id == inventory.id || it.sku == inventory.sku }
        flow.value = items.toList()
    }
    override fun getAllInventoryFlow(): Flow<List<ProductInventoryEntity>> = flow
    override fun getAllInventoryIncludingInactiveFlow(): Flow<List<ProductInventoryEntity>> = flow
    override fun getLowStockInventoryFlow(): Flow<List<ProductInventoryEntity>> = flowOf(emptyList())
    override fun getOutOfStockInventoryFlow(): Flow<List<ProductInventoryEntity>> = flowOf(emptyList())
    override fun getInventoryByIdFlow(id: Long): Flow<ProductInventoryEntity?> = flowOf(null)
    override fun getInventoryBySkuFlow(sku: String): Flow<ProductInventoryEntity?> = flowOf(null)
    override fun searchInventoryFlow(query: String): Flow<List<ProductInventoryEntity>> = flowOf(emptyList())
    override suspend fun getAllInventory(): List<ProductInventoryEntity> = items.toList()
    override suspend fun getInventoryById(id: Long): ProductInventoryEntity? = items.firstOrNull { it.id == id }
    override suspend fun getInventoryBySku(sku: String): ProductInventoryEntity? = items.firstOrNull { it.sku == sku }
    override suspend fun getLowStockItems(): List<ProductInventoryEntity> = emptyList()
    override suspend fun getItemsBelowThreshold(threshold: Int): List<ProductInventoryEntity> = emptyList()
    override suspend fun getLowStockCount(): Int = 0
    override suspend fun getOutOfStockCount(): Int = 0
    override suspend fun getTotalStockUnits(): Int = 0
    override suspend fun getTotalSkuCount(): Int = items.size
    override suspend fun updateCurrentStock(id: Long, newStock: Int, timestamp: Long) {}
    override suspend fun updateCurrentStockBySku(sku: String, newStock: Int, timestamp: Long) {}
    override suspend fun updateReorderThreshold(id: Long, newThreshold: Int) {}
    override suspend fun updateReorderThresholdBySku(sku: String, newThreshold: Int) {}
    override suspend fun addStock(sku: String, quantityToAdd: Int, timestamp: Long) {}
    override suspend fun deductStock(sku: String, quantityToDeduct: Int, timestamp: Long) {}
    override suspend fun deleteById(id: Long) {}
    override suspend fun deleteBySku(sku: String) {
        items.removeAll { it.sku == sku }
        flow.value = items.toList()
    }
    override suspend fun deleteAll() { items.clear(); flow.value = emptyList() }
}

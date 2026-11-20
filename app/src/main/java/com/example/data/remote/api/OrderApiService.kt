package com.example.data.remote.api

import com.example.data.remote.dto.ApiResponseDto
import com.example.data.remote.dto.CreateOrderRequestDto
import com.example.data.remote.dto.CreateReturnRequestDto
import com.example.data.remote.dto.CreditLedgerEntryDto
import com.example.data.remote.dto.CreditNoteDto
import com.example.data.remote.dto.FulfillOrderItemRequestDto
import com.example.data.remote.dto.LedgerBalanceDto
import com.example.data.remote.dto.OrderDetailsDto
import com.example.data.remote.dto.OrderDto
import com.example.data.remote.dto.RecordLedgerPaymentRequestDto
import com.example.data.remote.dto.ReturnDecisionRequestDto
import com.example.data.remote.dto.ReturnDto
import com.example.data.remote.dto.SubmitPodRequestDto
import com.example.data.remote.dto.TaxInvoiceDto
import com.example.data.remote.dto.UpdateOrderStatusRequestDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * Purchase orders, order tracking, POD, returns, credit notes, and credit ledger REST endpoints.
 */
interface OrderApiService {

    @GET("api/v1/orders")
    suspend fun getOrders(
        @Query("supplierId") supplierId: Long? = null
    ): Response<ApiResponseDto<List<OrderDto>>>

    @GET("api/v1/orders/{orderId}")
    suspend fun getOrderById(
        @Path("orderId") orderId: Long
    ): Response<ApiResponseDto<OrderDetailsDto>>

    /**
     * [PROPOSED — AWAITING BACKEND CONFIRMATION]
     * Live Pipeline endpoint restricted strictly to Manufacturer & Brand accounts.
     * Spring Boot controller/security contract MUST enforce:
     * @PreAuthorize("hasAnyRole('MANUFACTURER', 'PHARMA_COMPANY')")
     * and return HTTP 403 Forbidden for STOCKIST tokens.
     */
    @GET("api/v1/orders/pipeline")
    suspend fun getLivePipelineOrders(): Response<ApiResponseDto<List<OrderDto>>>

    /**
     * [PROPOSED — AWAITING BACKEND CONFIRMATION]
     * Authoritative backend tax invoice breakdown & eligibility evaluation for an order.
     */
    @GET("api/v1/orders/{orderId}/invoice")
    suspend fun getOrderTaxInvoice(
        @Path("orderId") orderId: Long
    ): Response<ApiResponseDto<TaxInvoiceDto>>

    /**
     * [PROPOSED — AWAITING BACKEND CONFIRMATION]
     * Direct backend-generated tax invoice PDF stream, if server-side PDF generation is enabled.
     */
    @Streaming
    @GET("api/v1/orders/{orderId}/invoice/pdf")
    suspend fun downloadOrderTaxInvoicePdf(
        @Path("orderId") orderId: Long
    ): Response<ResponseBody>

    @POST("api/v1/orders")
    suspend fun createOrdersFromBasket(
        @Body request: CreateOrderRequestDto
    ): Response<ApiResponseDto<List<OrderDto>>>

    @PATCH("api/v1/orders/{orderId}/status")
    suspend fun updateOrderStatus(
        @Path("orderId") orderId: Long,
        @Body request: UpdateOrderStatusRequestDto
    ): Response<ApiResponseDto<OrderDto>>

    @PATCH("api/v1/orders/items/{orderItemId}/fulfillment")
    suspend fun updateOrderItemFulfillment(
        @Path("orderItemId") orderItemId: Long,
        @Body request: FulfillOrderItemRequestDto
    ): Response<Unit>

    @POST("api/v1/orders/{orderId}/pod")
    suspend fun submitProofOfDelivery(
        @Path("orderId") orderId: Long,
        @Body request: SubmitPodRequestDto
    ): Response<Unit>

    @POST("api/v1/orders/{orderId}/reorder")
    suspend fun reorderPastOrder(
        @Path("orderId") orderId: Long
    ): Response<Unit>

    // Returns & Credit Notes
    @GET("api/v1/returns")
    suspend fun getReturns(): Response<ApiResponseDto<List<ReturnDto>>>

    @POST("api/v1/returns")
    suspend fun createReturn(
        @Body request: CreateReturnRequestDto
    ): Response<ApiResponseDto<ReturnDto>>

    @PATCH("api/v1/returns/{returnId}/status")
    suspend fun decideReturn(
        @Path("returnId") returnId: Long,
        @Body request: ReturnDecisionRequestDto
    ): Response<Unit>

    @GET("api/v1/credit-notes")
    suspend fun getCreditNotes(): Response<ApiResponseDto<List<CreditNoteDto>>>

    // Credit Ledger
    @GET("api/v1/ledger/entries")
    suspend fun getLedgerEntries(
        @Query("counterpartyUserId") counterpartyUserId: Long? = null
    ): Response<ApiResponseDto<List<CreditLedgerEntryDto>>>

    @GET("api/v1/ledger/balance/{counterpartyUserId}")
    suspend fun getLedgerBalance(
        @Path("counterpartyUserId") counterpartyUserId: Long
    ): Response<ApiResponseDto<LedgerBalanceDto>>

    @POST("api/v1/ledger/payments")
    suspend fun recordLedgerPayment(
        @Body request: RecordLedgerPaymentRequestDto
    ): Response<Unit>
}

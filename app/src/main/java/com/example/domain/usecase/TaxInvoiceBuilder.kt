package com.example.domain.usecase

import com.example.domain.model.OrderStatus
import com.example.domain.model.TaxInvoiceData
import com.example.domain.model.TaxInvoiceLineItem
import com.example.domain.model.TaxInvoiceParty
import com.example.domain.repository.OrderDetailsWithHistory
import kotlin.math.abs
import kotlin.math.round

/**
 * Pure domain builder for GST Tax Invoices.
 *
 * Strictly adheres to real persisted order/billing data and backend rules:
 * - NEVER fabricates GSTINs, tax values, or invoice numbers.
 * - Explicitly tracks missing billing fields in [TaxInvoiceData.missingFields].
 * - Computes CGST/SGST vs IGST split only when both seller and buyer GSTINs carry
 *   valid 2-digit Indian state codes.
 */
object TaxInvoiceBuilder {

    private val gstinStateRegex = Regex("^([0-9]{2})[A-Z0-9]{13}$")

    fun isStatusEligibleForInvoice(status: OrderStatus): Boolean {
        return when (status) {
            OrderStatus.PACKED,
            OrderStatus.DISPATCHED,
            OrderStatus.DELIVERED,
            OrderStatus.PARTIALLY_FULFILLED -> true
            OrderStatus.PLACED,
            OrderStatus.ACCEPTED,
            OrderStatus.CANCELLED -> false
        }
    }

    fun eligibilityFailureReason(status: OrderStatus): String? {
        return when (status) {
            OrderStatus.PLACED, OrderStatus.ACCEPTED ->
                "Tax invoice will be issued once the supplier packs or dispatches this order."
            OrderStatus.CANCELLED ->
                "No tax invoice is generated for cancelled orders."
            else -> null
        }
    }

    fun build(
        details: OrderDetailsWithHistory,
        backendInvoice: TaxInvoiceData? = null,
        sellerComplianceGstin: String? = null
    ): TaxInvoiceData {
        // When the backend supplies an authoritative invoice payload, follow its rules verbatim.
        if (backendInvoice != null) {
            return backendInvoice.copy(isBackendAuthoritative = true)
        }

        val order = details.order
        val status = try {
            OrderStatus.valueOf(order.status.uppercase())
        } catch (_: Exception) {
            OrderStatus.PLACED
        }

        val isEligible = isStatusEligibleForInvoice(status)
        val eligibilityReason = eligibilityFailureReason(status)

        val sellerGstin = details.supplier?.gstin?.trim()?.takeIf { it.isNotEmpty() }
            ?: sellerComplianceGstin?.trim()?.takeIf { it.isNotEmpty() }
        val buyerGstin = details.buyer?.gstin?.trim()?.takeIf { it.isNotEmpty() }

        val invoiceNumber = order.invoiceNumber?.trim()?.takeIf { it.isNotEmpty() }
        val invoiceDateMillis = when {
            !isEligible -> null
            order.invoiceIssuedAtEpochMillis != null -> order.invoiceIssuedAtEpochMillis
            invoiceNumber != null -> order.dispatchEpochMillis ?: order.deliveredEpochMillis ?: order.createdAtEpochMillis
            else -> null
        }

        val missingFields = mutableListOf<String>()
        if (isEligible && invoiceNumber == null) {
            missingFields.add("Invoice Number (not yet issued)")
        }
        if (details.supplier == null || details.supplier.businessName.isBlank()) {
            missingFields.add("Seller Legal Name")
        }
        if (sellerGstin == null) {
            missingFields.add("Seller GSTIN")
        }
        if (details.buyer == null || details.buyer.businessName.isBlank()) {
            missingFields.add("Buyer Legal Name")
        }
        if (buyerGstin == null) {
            missingFields.add("Buyer GSTIN")
        }

        val sellerState = extractGstinStateCode(sellerGstin)
        val buyerState = extractGstinStateCode(buyerGstin)
        val canSplitCgstSgstVsIgst = sellerState != null && buyerState != null
        val isIntraState = canSplitCgstSgstVsIgst && sellerState == buyerState

        val taxType = when {
            !canSplitCgstSgstVsIgst -> null
            isIntraState -> "CGST + SGST"
            else -> "IGST"
        }

        val placeOfSupply = buyerState?.let { "State Code $it" }

        val builtItems = details.items.map { item ->
            val taxable = roundTwoDecimals(item.effectivePrice * item.orderedQuantity)
            val hasRate = item.gstRatePercent > 0.0
            val itemGst = if (hasRate) roundTwoDecimals(taxable * item.gstRatePercent / 100.0) else null
            val cgst = if (itemGst != null && isIntraState) roundTwoDecimals(itemGst / 2.0) else null
            val sgst = if (itemGst != null && isIntraState && cgst != null) roundTwoDecimals(itemGst - cgst) else null
            val igst = if (itemGst != null && canSplitCgstSgstVsIgst && !isIntraState) itemGst else null
            val lineTotal = roundTwoDecimals(taxable + (itemGst ?: 0.0))

            if (item.hsnCode.isBlank()) {
                val hsnMsg = "HSN code for ${item.productName}"
                if (!missingFields.contains(hsnMsg)) missingFields.add(hsnMsg)
            }

            TaxInvoiceLineItem(
                productName = item.productName,
                brand = item.brand,
                packSize = item.packSize,
                hsnCode = item.hsnCode,
                batchNumber = item.batchNumber,
                quantity = item.orderedQuantity,
                freeQuantity = item.freeQuantity,
                unitPricePtr = item.unitPricePtr,
                effectiveUnitPrice = item.effectivePrice,
                taxableValue = taxable,
                gstRatePercent = item.gstRatePercent,
                gstAmount = itemGst,
                cgstAmount = cgst,
                sgstAmount = sgst,
                igstAmount = igst,
                lineTotal = lineTotal
            )
        }

        val subtotalTaxable = if (order.subtotal > 0.0) {
            order.subtotal
        } else {
            roundTwoDecimals(builtItems.sumOf { it.taxableValue })
        }

        val itemsGstSum = if (builtItems.isNotEmpty() && builtItems.all { it.gstAmount != null }) {
            roundTwoDecimals(builtItems.sumOf { it.gstAmount ?: 0.0 })
        } else null

        val totalGstAmount: Double? = when {
            order.gstAmount > 0.0 -> roundTwoDecimals(order.gstAmount)
            itemsGstSum != null && itemsGstSum > 0.0 -> itemsGstSum
            details.items.isNotEmpty() && details.items.all { it.gstRatePercent == 0.0 } -> 0.0
            else -> {
                missingFields.add("GST Tax Amount")
                null
            }
        }

        if (totalGstAmount != null && itemsGstSum != null && abs(totalGstAmount - itemsGstSum) > 5.0) {
            missingFields.add("Tax Sum Reconciliation Check")
        }

        val cgstTotal = if (totalGstAmount != null && isIntraState) roundTwoDecimals(totalGstAmount / 2.0) else null
        val sgstTotal = if (totalGstAmount != null && isIntraState && cgstTotal != null) roundTwoDecimals(totalGstAmount - cgstTotal) else null
        val igstTotal = if (totalGstAmount != null && canSplitCgstSgstVsIgst && !isIntraState) totalGstAmount else null

        val grandTotal = if (order.totalAmount > 0.0) {
            order.totalAmount
        } else {
            roundTwoDecimals(subtotalTaxable + (totalGstAmount ?: 0.0) + order.deliveryCharge)
        }

        return TaxInvoiceData(
            orderId = order.id,
            orderNumber = order.orderNumber,
            orderStatus = status,
            orderDateEpochMillis = order.createdAtEpochMillis,
            invoiceNumber = invoiceNumber,
            invoiceDateEpochMillis = invoiceDateMillis,
            isEligible = isEligible,
            eligibilityReason = eligibilityReason,
            isBackendAuthoritative = false,
            seller = TaxInvoiceParty(
                legalName = details.supplier?.businessName?.takeIf { it.isNotBlank() },
                contactName = details.supplier?.fullName?.takeIf { it.isNotBlank() },
                address = details.supplier?.address?.takeIf { it.isNotBlank() },
                gstin = sellerGstin,
                phone = details.supplier?.phone?.takeIf { it.isNotBlank() },
                email = details.supplier?.email?.takeIf { it.isNotBlank() }
            ),
            buyer = TaxInvoiceParty(
                legalName = details.buyer?.businessName?.takeIf { it.isNotBlank() },
                contactName = details.buyer?.fullName?.takeIf { it.isNotBlank() },
                address = details.buyer?.address?.takeIf { it.isNotBlank() },
                shippingAddress = order.deliveryAddress.takeIf { it.isNotBlank() },
                gstin = buyerGstin,
                phone = details.buyer?.phone?.takeIf { it.isNotBlank() },
                email = details.buyer?.email?.takeIf { it.isNotBlank() }
            ),
            placeOfSupply = placeOfSupply,
            gstTaxType = taxType,
            items = builtItems,
            subtotalTaxable = subtotalTaxable,
            cgstTotal = cgstTotal,
            sgstTotal = sgstTotal,
            igstTotal = igstTotal,
            totalGstAmount = totalGstAmount,
            deliveryCharge = order.deliveryCharge,
            grandTotal = grandTotal,
            ewayBillNumber = order.ewayBillNumber?.takeIf { it.isNotBlank() },
            vehicleNumber = order.vehicleNumber?.takeIf { it.isNotBlank() },
            transporterName = order.transporterName?.takeIf { it.isNotBlank() },
            missingFields = missingFields
        )
    }

    internal fun extractGstinStateCode(gstin: String?): String? {
        if (gstin.isNullOrBlank()) return null
        val normalized = gstin.trim().uppercase()
        val match = gstinStateRegex.matchEntire(normalized) ?: return null
        return match.groupValues[1]
    }

    private fun roundTwoDecimals(value: Double): Double {
        return round(value * 100.0) / 100.0
    }
}

package com.example.domain.usecase

import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SupplierSettingsEntity
import com.example.domain.model.AlternativeBrandQuotation
import com.example.domain.model.OfferWithSupplier
import com.example.domain.model.SchemeType
import com.example.domain.model.SupplierQuotationItem
import com.example.domain.model.SupplierQuotationSummary
import com.example.domain.model.TradeMarginBreakdown
import com.example.domain.model.VerifiedStockInfo

/**
 * Pure domain calculator for supplier quotation evaluation, effective unit cost comparison,
 * documented trade margin calculation, and alternative brand enrichment.
 *
 * Documented Pricing & Trade Margin Formula (aligned with [OptimizeBasketUseCase]):
 * 1. Gross Amount = PTR * Q  (where Q = max(1, evaluationQuantity, MOQ))
 * 2. Scheme Adjustment:
 *    - BUY_X_GET_Y_FREE: freeQty = (Q / buyQty) * freeQty; receivedQty = Q + freeQty
 *    - PERCENT_DISCOUNT_ABOVE_QTY: discount = gross * discountPercent / 100 (when Q >= minQtyForDiscount)
 *    - Net Amount = max(0, Gross - discount)
 * 3. Tax (GST): gstAmount = Net Amount * gstRatePercent / 100
 * 4. Supplier Delivery Cost (only when [SupplierSettingsEntity] exists):
 *    - deliveryCost = deliveryCharge if Net Amount < freeDeliveryThreshold and deliveryCharge > 0 else 0
 * 5. Effective Unit Cost = (Net Amount + gstAmount + deliveryCost) / receivedQty
 * 6. Trade Margin % = ((MRP - Effective Unit Cost) / MRP) * 100  (when MRP > 0 and PTR > 0 and GST >= 0)
 *
 * Missing fields are explicitly identified and never fabricated.
 */
class ProductQuotationUseCase(
    private val optimizeBasketUseCase: OptimizeBasketUseCase = OptimizeBasketUseCase()
) {

    fun calculateTradeMarginBreakdown(
        unitPricePtr: Double,
        mrp: Double,
        moq: Int,
        gstRatePercent: Double,
        schemeType: SchemeType,
        schemeBuyQty: Int,
        schemeFreeQty: Int,
        schemeDiscountPercent: Double,
        schemeMinQtyForDiscount: Int,
        supplierSettings: SupplierSettingsEntity?,
        evaluationQuantity: Int = maxOf(1, moq)
    ): TradeMarginBreakdown {
        val missingFields = mutableListOf<String>()
        if (unitPricePtr <= 0.0) missingFields.add("Unit Price (PTR)")
        if (mrp <= 0.0) missingFields.add("MRP")
        if (gstRatePercent < 0.0) missingFields.add("GST Rate (%)")
        if (supplierSettings == null) missingFields.add("Supplier delivery settings")

        val evalQty = maxOf(1, evaluationQuantity, moq)
        if (unitPricePtr <= 0.0 || gstRatePercent < 0.0) {
            return TradeMarginBreakdown(
                marginPercent = null,
                marginAmountPerUnit = null,
                effectiveUnitCost = null,
                unitPricePtr = unitPricePtr.takeIf { it > 0.0 },
                mrp = mrp.takeIf { it > 0.0 },
                netUnitPriceAfterScheme = null,
                gstPerUnit = null,
                gstRatePercent = gstRatePercent,
                deliveryCostApplied = null,
                deliveryPerUnit = null,
                freeDeliveryThreshold = supplierSettings?.freeDeliveryThreshold,
                freeQuantityBonus = 0,
                evaluationQuantity = evalQty,
                hasAdditionalCostData = supplierSettings != null,
                missingFields = missingFields
            )
        }

        val pricing = optimizeBasketUseCase.calculateSchemePrice(
            unitPrice = unitPricePtr,
            quantity = evalQty,
            schemeType = schemeType,
            buyQty = schemeBuyQty,
            freeQty = schemeFreeQty,
            discountPercent = schemeDiscountPercent,
            minQtyForDiscount = schemeMinQtyForDiscount,
            gstRatePercent = gstRatePercent
        )

        val receivedQty = pricing.totalReceivedQuantity.coerceAtLeast(1)
        val netUnitAfterScheme = pricing.netAmount / receivedQty
        val gstPerUnit = pricing.gstAmount / receivedQty

        val deliveryCostApplied: Double?
        val deliveryPerUnit: Double?
        if (supplierSettings != null) {
            val applied = if (
                pricing.netAmount < supplierSettings.freeDeliveryThreshold &&
                supplierSettings.deliveryCharge > 0.0
            ) {
                supplierSettings.deliveryCharge
            } else {
                0.0
            }
            deliveryCostApplied = applied
            deliveryPerUnit = applied / receivedQty
        } else {
            deliveryCostApplied = null
            deliveryPerUnit = null
        }

        val landedTotal = pricing.totalWithGst + (deliveryCostApplied ?: 0.0)
        val effectiveUnitCost = landedTotal / receivedQty

        val marginAmountPerUnit = if (mrp > 0.0) mrp - effectiveUnitCost else null
        val marginPercent = if (mrp > 0.0) ((mrp - effectiveUnitCost) / mrp) * 100.0 else null

        return TradeMarginBreakdown(
            marginPercent = marginPercent,
            marginAmountPerUnit = marginAmountPerUnit,
            effectiveUnitCost = effectiveUnitCost,
            unitPricePtr = unitPricePtr,
            mrp = mrp.takeIf { it > 0.0 },
            netUnitPriceAfterScheme = netUnitAfterScheme,
            gstPerUnit = gstPerUnit,
            gstRatePercent = gstRatePercent,
            deliveryCostApplied = deliveryCostApplied,
            deliveryPerUnit = deliveryPerUnit,
            freeDeliveryThreshold = supplierSettings?.freeDeliveryThreshold,
            freeQuantityBonus = pricing.freeQuantity,
            evaluationQuantity = evalQty,
            hasAdditionalCostData = supplierSettings != null,
            missingFields = missingFields
        )
    }

    fun buildSupplierQuotationItem(
        offer: OfferWithSupplier,
        gstRatePercent: Double,
        supplierSettings: SupplierSettingsEntity?,
        verifiedSupplierStock: Int?,
        hasVerifiedSupplierStockRecord: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): SupplierQuotationItem {
        val isExpired = offer.validTillEpochMillis in 1..nowMillis
        val unavailabilityReason = when {
            !offer.isActive -> "Inactive quote"
            isExpired -> "Quote expired"
            offer.unitPricePtr <= 0.0 -> "Missing valid PTR"
            offer.moq <= 0 -> "Invalid MOQ"
            offer.availableQuantity <= 0 -> "Out of stock"
            offer.availableQuantity < offer.moq -> "Quoted stock (${offer.availableQuantity}) below MOQ (${offer.moq})"
            else -> null
        }
        val isEligible = unavailabilityReason == null

        val breakdown = calculateTradeMarginBreakdown(
            unitPricePtr = offer.unitPricePtr,
            mrp = offer.mrp,
            moq = offer.moq,
            gstRatePercent = gstRatePercent,
            schemeType = offer.schemeType,
            schemeBuyQty = offer.schemeBuyQty,
            schemeFreeQty = offer.schemeFreeQty,
            schemeDiscountPercent = offer.schemeDiscountPercent,
            schemeMinQtyForDiscount = offer.schemeMinQtyForDiscount,
            supplierSettings = supplierSettings
        )

        return SupplierQuotationItem(
            offer = offer,
            effectiveUnitCost = breakdown.effectiveUnitCost,
            tradeMargin = breakdown,
            deliveryCharge = supplierSettings?.deliveryCharge,
            freeDeliveryThreshold = supplierSettings?.freeDeliveryThreshold,
            averageLeadTimeDays = supplierSettings?.averageLeadTimeDays,
            quotedStock = offer.availableQuantity.coerceAtLeast(0),
            verifiedStock = verifiedSupplierStock,
            hasVerifiedStockRecord = hasVerifiedSupplierStockRecord,
            isExpired = isExpired,
            isEligibleForPurchase = isEligible,
            unavailabilityReason = unavailabilityReason
        )
    }

    fun sortAndSummarizeQuotations(
        quotations: List<SupplierQuotationItem>,
        verifiedStockInfo: VerifiedStockInfo
    ): Pair<SupplierQuotationSummary, List<SupplierQuotationItem>> {
        val eligible = quotations
            .filter { it.isEligibleForPurchase }
            .sortedWith(
                compareBy<SupplierQuotationItem>(
                    { it.effectiveUnitCost ?: Double.MAX_VALUE },
                    { it.offer.unitPricePtr },
                    { it.offer.moq },
                    { -(it.offer.trustScore ?: -1) }
                )
            )
        val unavailable = quotations
            .filterNot { it.isEligibleForPurchase }
            .sortedWith(
                compareBy<SupplierQuotationItem>(
                    { it.offer.unitPricePtr.takeIf { p -> p > 0.0 } ?: Double.MAX_VALUE },
                    { it.offer.moq }
                )
            )

        val sorted = eligible + unavailable
        val bestOfferId = eligible.firstOrNull()?.offer?.offerId

        val missingFields = mutableListOf<String>()
        if (eligible.isEmpty()) {
            missingFields.add("No eligible supplier quotations available")
        }
        if (!verifiedStockInfo.hasVerifiedStockRecords) {
            missingFields.add("Verified batch/inventory stock records")
        }

        val summary = SupplierQuotationSummary(
            minMoq = eligible.minOfOrNull { it.offer.moq },
            maxMoq = eligible.maxOfOrNull { it.offer.moq },
            lowestUnitPricePtr = eligible.minOfOrNull { it.offer.unitPricePtr },
            lowestEffectiveUnitCost = eligible.firstOrNull()?.effectiveUnitCost,
            availableSuppliersCount = eligible.map { it.offer.supplierId }.distinct().size,
            totalOffersCount = quotations.size,
            totalQuotedAvailableStock = eligible.sumOf { it.quotedStock },
            verifiedAvailableStock = verifiedStockInfo.verifiedAvailableUnits,
            hasVerifiedStockRecords = verifiedStockInfo.hasVerifiedStockRecords,
            hasEligibleOffers = eligible.isNotEmpty(),
            bestOfferId = bestOfferId,
            missingFields = missingFields
        )
        return summary to sorted
    }

    fun buildAlternativeBrandQuotation(
        product: ProductEntity,
        offers: List<OfferWithSupplier>,
        settingsBySupplier: Map<Long, SupplierSettingsEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): AlternativeBrandQuotation {
        val evaluated = offers.map { offer ->
            buildSupplierQuotationItem(
                offer = offer,
                gstRatePercent = product.gstRatePercent,
                supplierSettings = settingsBySupplier[offer.supplierId],
                verifiedSupplierStock = null,
                hasVerifiedSupplierStockRecord = false,
                nowMillis = nowMillis
            )
        }
        val eligible = evaluated
            .filter { it.isEligibleForPurchase }
            .sortedWith(
                compareBy<SupplierQuotationItem>(
                    { it.effectiveUnitCost ?: Double.MAX_VALUE },
                    { it.offer.unitPricePtr },
                    { it.offer.moq }
                )
            )
        val best = eligible.firstOrNull()

        val missingFields = mutableListOf<String>()
        if (product.manufacturerName.isBlank()) missingFields.add("Manufacturer")
        if (best == null) {
            missingFields.addAll(listOf("Active supplier quote", "Unit price (PTR)", "MOQ", "Supplier rating", "Trade margin"))
            if (product.gstRatePercent < 0.0) missingFields.add("GST rate")
            return AlternativeBrandQuotation(
                product = product,
                manufacturerName = product.manufacturerName.ifBlank { "Manufacturer unavailable" },
                unitPricePtr = null,
                effectiveUnitCost = null,
                mrp = null,
                moq = null,
                supplierName = null,
                supplierRating = null,
                hasSufficientRatingData = false,
                gstRatePercent = product.gstRatePercent,
                deliveryCharge = null,
                freeDeliveryThreshold = null,
                tradeMargin = TradeMarginBreakdown(
                    marginPercent = null,
                    marginAmountPerUnit = null,
                    effectiveUnitCost = null,
                    unitPricePtr = null,
                    mrp = null,
                    netUnitPriceAfterScheme = null,
                    gstPerUnit = null,
                    gstRatePercent = product.gstRatePercent,
                    deliveryCostApplied = null,
                    deliveryPerUnit = null,
                    freeDeliveryThreshold = null,
                    hasAdditionalCostData = false,
                    missingFields = missingFields.distinct()
                ),
                availableSuppliersCount = 0,
                totalQuotedStock = 0,
                hasEligibleOffer = false,
                missingFields = missingFields.distinct()
            )
        }

        if (!best.offer.hasSufficientTrustData || best.offer.trustScore == null) {
            missingFields.add("Supplier rating (<5 completed orders)")
        }
        missingFields.addAll(best.tradeMargin.missingFields)

        return AlternativeBrandQuotation(
            product = product,
            manufacturerName = product.manufacturerName.ifBlank { "Manufacturer unavailable" },
            unitPricePtr = best.offer.unitPricePtr,
            effectiveUnitCost = best.effectiveUnitCost,
            mrp = best.offer.mrp.takeIf { it > 0.0 },
            moq = best.offer.moq,
            supplierName = best.offer.supplierBusinessName.ifBlank { best.offer.supplierName },
            supplierRating = if (best.offer.hasSufficientTrustData) best.offer.trustScore else null,
            hasSufficientRatingData = best.offer.hasSufficientTrustData && best.offer.trustScore != null,
            gstRatePercent = product.gstRatePercent,
            deliveryCharge = best.deliveryCharge,
            freeDeliveryThreshold = best.freeDeliveryThreshold,
            tradeMargin = best.tradeMargin,
            availableSuppliersCount = eligible.map { it.offer.supplierId }.distinct().size,
            totalQuotedStock = eligible.sumOf { it.quotedStock },
            hasEligibleOffer = true,
            missingFields = missingFields.distinct()
        )
    }
}

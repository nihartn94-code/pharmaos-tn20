package com.example.domain.usecase

import com.example.data.local.entity.SupplierOfferEntity
import com.example.data.local.entity.SupplierSettingsEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.EffectivePriceCalculation
import com.example.domain.model.OptimizationResult
import com.example.domain.model.OptimizedLineItem
import com.example.domain.model.SchemeType
import com.example.domain.repository.BasketItemDetail
import com.example.util.Formatters

class OptimizeBasketUseCase {

    data class SupplierSummary(
        val supplierId: Long,
        val deliveryCharge: Double,
        val freeDeliveryThreshold: Double,
        val trustScore: Int?
    )

    fun calculateSchemePrice(
        unitPrice: Double,
        quantity: Int,
        schemeType: SchemeType,
        buyQty: Int,
        freeQty: Int,
        discountPercent: Double,
        minQtyForDiscount: Int,
        gstRatePercent: Double
    ): EffectivePriceCalculation {
        val gross = unitPrice * quantity
        var freeQuantity = 0
        var discountAmount = 0.0

        when (schemeType) {
            SchemeType.BUY_X_GET_Y_FREE -> {
                if (buyQty > 0 && freeQty > 0 && quantity >= buyQty) {
                    val batches = quantity / buyQty
                    freeQuantity = batches * freeQty
                }
            }
            SchemeType.PERCENT_DISCOUNT_ABOVE_QTY -> {
                if (quantity >= minQtyForDiscount && discountPercent > 0) {
                    discountAmount = (gross * discountPercent) / 100.0
                }
            }
            SchemeType.NONE -> {}
        }

        val netAmount = Math.max(0.0, gross - discountAmount)
        val totalReceivedQty = quantity + freeQuantity
        val effectiveUnitPrice = if (totalReceivedQty > 0) netAmount / totalReceivedQty else unitPrice
        val gstAmount = (netAmount * gstRatePercent) / 100.0
        val totalWithGst = netAmount + gstAmount

        val standardGrossWithGst = gross + (gross * gstRatePercent / 100.0)
        val savingsAmount = Math.max(0.0, standardGrossWithGst - totalWithGst)
        val savingsPercent = if (standardGrossWithGst > 0) (savingsAmount / standardGrossWithGst) * 100.0 else 0.0

        return EffectivePriceCalculation(
            regularQuantity = quantity,
            freeQuantity = freeQuantity,
            totalReceivedQuantity = totalReceivedQty,
            grossAmount = gross,
            discountAmount = discountAmount,
            netAmount = netAmount,
            effectiveUnitPrice = effectiveUnitPrice,
            gstAmount = gstAmount,
            totalWithGst = totalWithGst,
            savingsAmount = savingsAmount,
            savingsPercent = savingsPercent
        )
    }

    fun optimize(
        currentItems: List<BasketItemDetail>,
        availableOffersByProduct: Map<Long, List<SupplierOfferEntity>>,
        suppliersById: Map<Long, UserEntity>,
        settingsBySupplier: Map<Long, SupplierSettingsEntity>,
        supplierTrustScores: Map<Long, Int?>,
        preferReliableSuppliers: Boolean = false
    ): OptimizationResult {
        if (currentItems.isEmpty()) {
            return OptimizationResult(
                isExact = true,
                currentTotal = 0.0,
                optimizedTotal = 0.0,
                savingsRupees = 0.0,
                savingsPercent = 0.0,
                lines = emptyList(),
                whySuggestions = listOf("Your basket is empty."),
                assumptions = listOf("No items to optimize.")
            )
        }

        val currentTotal = currentItems.sumOf { it.pricing.totalWithGst }

        // Filter valid candidate offers per item
        val candidateOffersPerItem = currentItems.map { item ->
            val allOffers = availableOffersByProduct[item.productId] ?: emptyList()
            var valid = allOffers.filter { it.isActive && it.availableQuantity >= item.quantity }
            if (preferReliableSuppliers) {
                // Filter or prefer suppliers with trust score >= 60 if available
                val highTrust = valid.filter { (supplierTrustScores[it.supplierId] ?: 0) >= 60 }
                if (highTrust.isNotEmpty()) {
                    valid = highTrust
                }
            }
            if (valid.isEmpty()) {
                // Keep original offer as fallback
                allOffers.filter { it.id == item.offerId }
            } else {
                valid
            }
        }

        // Calculate total combinations
        var combinationsCount: Long = 1
        for (offers in candidateOffersPerItem) {
            val count = Math.max(1, offers.size)
            combinationsCount = combinationsCount.saturatingMultiply(count.toLong())
        }

        val isExact = combinationsCount in 1..50000
        val chosenOffers: List<SupplierOfferEntity>

        if (isExact) {
            chosenOffers = findExactBestOffers(
                currentItems = currentItems,
                candidateOffersPerItem = candidateOffersPerItem,
                settingsBySupplier = settingsBySupplier
            )
        } else {
            chosenOffers = findHeuristicBestOffers(
                currentItems = currentItems,
                candidateOffersPerItem = candidateOffersPerItem,
                settingsBySupplier = settingsBySupplier
            )
        }

        // Build result lines
        val lines = mutableListOf<OptimizedLineItem>()
        val whySuggestions = mutableListOf<String>()
        var optimizedSubtotalWithGst = 0.0
        val supplierTotals = mutableMapOf<Long, Double>()

        for (i in currentItems.indices) {
            val orig = currentItems[i]
            val chosen = chosenOffers[i]
            val supplierUser = suppliersById[chosen.supplierId]
            val supplierName = supplierUser?.businessName ?: supplierUser?.fullName ?: "Supplier #${chosen.supplierId}"

            // MOQ adjustment check
            val targetQty = Math.max(orig.quantity, chosen.moq)
            val moqAdjusted = chosen.moq > orig.quantity

            val schemeType = try {
                SchemeType.valueOf(chosen.schemeType)
            } catch (_: Exception) {
                SchemeType.NONE
            }

            val pricing = calculateSchemePrice(
                unitPrice = chosen.unitPricePtr,
                quantity = targetQty,
                schemeType = schemeType,
                buyQty = chosen.schemeBuyQty,
                freeQty = chosen.schemeFreeQty,
                discountPercent = chosen.schemeDiscountPercent,
                minQtyForDiscount = chosen.schemeMinQtyForDiscount,
                gstRatePercent = orig.gstRatePercent
            )

            optimizedSubtotalWithGst += pricing.totalWithGst
            supplierTotals[chosen.supplierId] = (supplierTotals[chosen.supplierId] ?: 0.0) + pricing.netAmount

            val schemeApplied = when (schemeType) {
                SchemeType.BUY_X_GET_Y_FREE -> "Buy ${chosen.schemeBuyQty} Get ${chosen.schemeFreeQty} Free"
                SchemeType.PERCENT_DISCOUNT_ABOVE_QTY -> "${chosen.schemeDiscountPercent}% off (Min ${chosen.schemeMinQtyForDiscount})"
                SchemeType.NONE -> "Standard"
            }

            val reason = buildString {
                if (chosen.supplierId != orig.supplierId) {
                    append("Switched to $supplierName for lower unit rate (${Formatters.formatInr(chosen.unitPricePtr)} vs ${Formatters.formatInr(orig.unitPricePtr)}).")
                } else {
                    append("Kept current supplier.")
                }
                if (moqAdjusted) {
                    append(" Qty rounded up from ${orig.quantity} to ${chosen.moq} to meet MOQ.")
                }
                if (pricing.freeQuantity > 0) {
                    append(" Earned ${pricing.freeQuantity} free units via scheme.")
                }
                if (pricing.discountAmount > 0) {
                    append(" Saved ${Formatters.formatInr(pricing.discountAmount)} scheme discount.")
                }
            }

            if (chosen.supplierId != orig.supplierId || moqAdjusted || pricing.freeQuantity > 0) {
                whySuggestions.add("${orig.productName}: $reason")
            }

            lines.add(
                OptimizedLineItem(
                    productId = orig.productId,
                    productName = orig.productName,
                    brand = orig.brand,
                    originalSupplierId = orig.supplierId,
                    originalSupplierName = orig.supplierName,
                    originalQuantity = orig.quantity,
                    originalEffectivePrice = orig.pricing.effectiveUnitPrice,
                    suggestedSupplierId = chosen.supplierId,
                    suggestedSupplierName = supplierName,
                    suggestedQuantity = targetQty,
                    suggestedFreeQty = pricing.freeQuantity,
                    suggestedUnitPrice = chosen.unitPricePtr,
                    suggestedEffectivePrice = pricing.effectiveUnitPrice,
                    schemeApplied = schemeApplied,
                    trustScore = supplierTrustScores[chosen.supplierId],
                    moqAdjusted = moqAdjusted,
                    reason = reason
                )
            )
        }

        // Add delivery charges to optimized total
        var totalDeliveryCharge = 0.0
        for ((supId, subtotal) in supplierTotals) {
            val settings = settingsBySupplier[supId] ?: SupplierSettingsEntity(supplierId = supId)
            if (subtotal < settings.freeDeliveryThreshold && settings.deliveryCharge > 0) {
                totalDeliveryCharge += settings.deliveryCharge
            }
        }

        val finalOptimizedTotal = optimizedSubtotalWithGst + totalDeliveryCharge
        val savingsRupees = Math.max(0.0, currentTotal - finalOptimizedTotal)
        val savingsPercent = if (currentTotal > 0) (savingsRupees / currentTotal) * 100.0 else 0.0

        val assumptions = listOf(
            "Supplier quotes and active schemes as currently published in PharmaOS.",
            "MOQ constraints strictly satisfied by rounding required quantity where beneficial.",
            "Delivery costs computed based on each supplier's minimum threshold policies.",
            if (preferReliableSuppliers) "Suppliers filtered/weighted with Trust Score ≥ 60." else "Evaluated purely by lowest net procurement cost."
        )

        return OptimizationResult(
            isExact = isExact,
            currentTotal = currentTotal,
            optimizedTotal = finalOptimizedTotal,
            savingsRupees = savingsRupees,
            savingsPercent = savingsPercent,
            lines = lines,
            whySuggestions = if (whySuggestions.isEmpty()) listOf("Your current supplier selection is already optimal!") else whySuggestions,
            assumptions = assumptions
        )
    }

    private fun findExactBestOffers(
        currentItems: List<BasketItemDetail>,
        candidateOffersPerItem: List<List<SupplierOfferEntity>>,
        settingsBySupplier: Map<Long, SupplierSettingsEntity>
    ): List<SupplierOfferEntity> {
        var bestCost = Double.MAX_VALUE
        var bestCombination: List<SupplierOfferEntity> = candidateOffersPerItem.map { it.first() }

        fun backtrack(itemIndex: Int, currentChosen: MutableList<SupplierOfferEntity>) {
            if (itemIndex == currentItems.size) {
                val cost = evaluateTotalBasketCost(currentItems, currentChosen, settingsBySupplier)
                if (cost < bestCost) {
                    bestCost = cost
                    bestCombination = ArrayList(currentChosen)
                }
                return
            }

            for (offer in candidateOffersPerItem[itemIndex]) {
                currentChosen.add(offer)
                backtrack(itemIndex + 1, currentChosen)
                currentChosen.removeAt(currentChosen.size - 1)
            }
        }

        backtrack(0, mutableListOf())
        return bestCombination
    }

    private fun findHeuristicBestOffers(
        currentItems: List<BasketItemDetail>,
        candidateOffersPerItem: List<List<SupplierOfferEntity>>,
        settingsBySupplier: Map<Long, SupplierSettingsEntity>
    ): List<SupplierOfferEntity> {
        // Step 1: Greedy assignment based on effective unit price
        val chosen = ArrayList<SupplierOfferEntity>()
        for (i in currentItems.indices) {
            val item = currentItems[i]
            val best = candidateOffersPerItem[i].minByOrNull { offer ->
                val schemeType = try { SchemeType.valueOf(offer.schemeType) } catch (_: Exception) { SchemeType.NONE }
                val targetQty = Math.max(item.quantity, offer.moq)
                val p = calculateSchemePrice(
                    offer.unitPricePtr, targetQty, schemeType,
                    offer.schemeBuyQty, offer.schemeFreeQty, offer.schemeDiscountPercent,
                    offer.schemeMinQtyForDiscount, item.gstRatePercent
                )
                p.totalWithGst
            } ?: candidateOffersPerItem[i].first()
            chosen.add(best)
        }

        // Step 2: Supplier consolidation pass
        val dominantSupplierId = chosen.groupBy { it.supplierId }.maxByOrNull { it.value.size }?.key
        if (dominantSupplierId != null) {
            for (i in currentItems.indices) {
                val candidateDom = candidateOffersPerItem[i].find { it.supplierId == dominantSupplierId }
                if (candidateDom != null && candidateDom.id != chosen[i].id) {
                    val currentCost = evaluateTotalBasketCost(currentItems, chosen, settingsBySupplier)
                    val testList = ArrayList(chosen)
                    testList[i] = candidateDom
                    val newCost = evaluateTotalBasketCost(currentItems, testList, settingsBySupplier)
                    if (newCost < currentCost) {
                        chosen[i] = candidateDom
                    }
                }
            }
        }

        return chosen
    }

    private fun evaluateTotalBasketCost(
        items: List<BasketItemDetail>,
        offers: List<SupplierOfferEntity>,
        settings: Map<Long, SupplierSettingsEntity>
    ): Double {
        var total = 0.0
        val supplierSubtotals = mutableMapOf<Long, Double>()

        for (i in items.indices) {
            val item = items[i]
            val offer = offers[i]
            val schemeType = try { SchemeType.valueOf(offer.schemeType) } catch (_: Exception) { SchemeType.NONE }
            val targetQty = Math.max(item.quantity, offer.moq)
            val p = calculateSchemePrice(
                offer.unitPricePtr, targetQty, schemeType,
                offer.schemeBuyQty, offer.schemeFreeQty, offer.schemeDiscountPercent,
                offer.schemeMinQtyForDiscount, item.gstRatePercent
            )
            total += p.totalWithGst
            supplierSubtotals[offer.supplierId] = (supplierSubtotals[offer.supplierId] ?: 0.0) + p.netAmount
        }

        for ((supId, subtotal) in supplierSubtotals) {
            val conf = settings[supId] ?: SupplierSettingsEntity(supplierId = supId)
            if (subtotal < conf.freeDeliveryThreshold && conf.deliveryCharge > 0) {
                total += conf.deliveryCharge
            }
        }
        return total
    }

    private fun Long.saturatingMultiply(other: Long): Long {
        return try {
            Math.multiplyExact(this, other)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
    }
}

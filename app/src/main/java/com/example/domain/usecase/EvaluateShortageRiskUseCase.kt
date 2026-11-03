package com.example.domain.usecase

import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.StockRecordEntity
import com.example.data.local.entity.SupplierOfferEntity
import com.example.data.local.entity.SupplierSettingsEntity
import com.example.domain.model.ShortageEvaluationResult
import com.example.domain.model.ShortageRiskLevel

class EvaluateShortageRiskUseCase {

    /**
     * Rules per product evaluated across all suppliers:
     * - R1: 2 or more stockouts (quantity reached 0) in the last 30 days.
     * - R2: total available quantity dropped 50% or more across the last 3 snapshots.
     * - R3: fewer than 2 suppliers currently have stock.
     * - R4: average supplier lead time above 7 days.
     *
     * Risk levels:
     * - 0 rules = None
     * - 1 rule = Low
     * - 2 rules = Medium
     * - 3+ rules = High
     */
    fun evaluate(
        product: ProductEntity,
        activeOffers: List<SupplierOfferEntity>,
        recentSnapshots: List<StockRecordEntity>,
        supplierSettings: Map<Long, SupplierSettingsEntity>
    ): ShortageEvaluationResult {
        val triggered = mutableListOf<String>()

        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)

        // R1: 2 or more stockouts in the last 30 days
        val stockoutsIn30Days = recentSnapshots.count {
            it.availableQuantity == 0 && it.recordedAtEpochMillis >= thirtyDaysAgo
        }
        if (stockoutsIn30Days >= 2) {
            triggered.add("R1: High stockout frequency ($stockoutsIn30Days stockouts recorded in past 30 days)")
        }

        // R2: Total available quantity dropped 50% or more across the last 3 snapshots
        if (recentSnapshots.size >= 3) {
            val oldestOfThree = recentSnapshots[2].availableQuantity
            val latest = recentSnapshots[0].availableQuantity
            if (oldestOfThree > 0) {
                val dropPercent = ((oldestOfThree - latest).toDouble() / oldestOfThree.toDouble()) * 100.0
                if (dropPercent >= 50.0) {
                    triggered.add("R2: Rapid inventory depletion (${Math.round(dropPercent)}% drop across last 3 snapshots)")
                }
            }
        }

        // R3: Fewer than 2 suppliers currently have stock
        val inStockOffers = activeOffers.filter { it.isActive && it.availableQuantity > 0 }
        val suppliersWithStock = inStockOffers.map { it.supplierId }.distinct().size
        if (suppliersWithStock < 2) {
            triggered.add("R3: Single-source dependency (only $suppliersWithStock active supplier with available stock)")
        }

        // R4: Average supplier lead time above 7 days
        val supplierIds = activeOffers.map { it.supplierId }.distinct()
        val leadTimes = supplierIds.mapNotNull { supplierSettings[it]?.averageLeadTimeDays }
        val avgLeadTime = if (leadTimes.isNotEmpty()) leadTimes.average() else 3.0
        if (avgLeadTime > 7.0) {
            triggered.add("R4: Extended procurement lead time (average lead time is ${"%.1f".format(avgLeadTime)} days > 7 days)")
        }

        val riskLevel = when (triggered.size) {
            0 -> ShortageRiskLevel.NONE
            1 -> ShortageRiskLevel.LOW
            2 -> ShortageRiskLevel.MEDIUM
            else -> ShortageRiskLevel.HIGH
        }

        val totalAvailable = inStockOffers.sumOf { it.availableQuantity }

        return ShortageEvaluationResult(
            productId = product.id,
            productName = product.name,
            brand = product.brand,
            saltComposition = product.saltComposition,
            riskLevel = riskLevel,
            triggeredReasons = triggered,
            availableSuppliersCount = suppliersWithStock,
            totalAvailableQuantity = totalAvailable,
            avgLeadTimeDays = avgLeadTime,
            lastUpdatedEpochMillis = now
        )
    }
}

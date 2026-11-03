package com.example.domain.usecase

import com.example.data.local.entity.OrderEntity
import com.example.data.local.entity.OrderItemEntity
import com.example.data.local.entity.ProofOfDeliveryEntity
import com.example.data.local.entity.ReturnEntity
import com.example.domain.model.OrderStatus
import com.example.domain.model.ReturnReason
import com.example.domain.model.ReturnStatus
import com.example.domain.model.TrustScoreBreakdown

class CalculateTrustScoreUseCase {

    /**
     * Weights:
     * - Fulfilment rate (fulfilled quantity ÷ ordered quantity): 35%
     * - On-time delivery (delivered on or before expected date): 30%
     * - Cancellation rate (supplier-cancelled orders), inverted: 15%
     * - Delivery discrepancy rate from POD, inverted: 10%
     * - Return rate for Damaged or Wrong Item reasons, inverted: 10%
     *
     * If fewer than 5 completed orders, show Insufficient data.
     */
    fun calculate(
        supplierId: Long,
        supplierName: String,
        allOrders: List<OrderEntity>,
        orderItems: List<OrderItemEntity>,
        proofsOfDelivery: List<ProofOfDeliveryEntity>,
        returns: List<ReturnEntity>
    ): TrustScoreBreakdown {
        val completedOrders = allOrders.filter { it.status == OrderStatus.DELIVERED.name }
        val cancelledBySupplierOrders = allOrders.filter { it.status == OrderStatus.CANCELLED.name }
        val totalFinishedOrders = completedOrders.size + cancelledBySupplierOrders.size

        val hasSufficientData = completedOrders.size >= 5

        if (totalFinishedOrders == 0) {
            return TrustScoreBreakdown(
                supplierId = supplierId,
                supplierName = supplierName,
                overallScore = 0,
                hasSufficientData = false,
                completedOrdersCount = 0,
                fulfilmentRate = 1.0,
                onTimeDeliveryRate = 1.0,
                cancellationRate = 0.0,
                discrepancyRate = 0.0,
                returnRate = 0.0,
                fulfilmentScoreWeight = 35.0,
                onTimeScoreWeight = 30.0,
                cancellationScoreWeight = 15.0,
                discrepancyScoreWeight = 10.0,
                returnScoreWeight = 10.0,
                lastCalculatedEpochMillis = System.currentTimeMillis()
            )
        }

        // 1. Fulfilment rate (35%)
        val totalOrderedUnits = orderItems.sumOf { it.orderedQuantity }
        val totalFulfilledUnits = orderItems.sumOf { it.fulfilledQuantity }
        val fulfilmentRate = if (totalOrderedUnits > 0) {
            Math.min(1.0, totalFulfilledUnits.toDouble() / totalOrderedUnits.toDouble())
        } else 1.0

        // 2. On-time delivery (30%)
        var onTimeCount = 0
        for (order in completedOrders) {
            val deliveredDate = order.deliveredEpochMillis ?: order.createdAtEpochMillis
            val expectedDate = order.expectedDeliveryEpochMillis ?: Long.MAX_VALUE
            if (deliveredDate <= expectedDate) {
                onTimeCount++
            }
        }
        val onTimeRate = if (completedOrders.isNotEmpty()) {
            onTimeCount.toDouble() / completedOrders.size.toDouble()
        } else 1.0

        // 3. Cancellation rate (15%) - Inverted
        val cancellationRate = if (allOrders.isNotEmpty()) {
            cancelledBySupplierOrders.size.toDouble() / allOrders.size.toDouble()
        } else 0.0
        val cancellationScore = Math.max(0.0, 1.0 - cancellationRate)

        // 4. Delivery discrepancy rate (10%) - Inverted
        val totalShortOrDamagedUnits = proofsOfDelivery.sumOf { it.shortQuantity + it.damagedQuantity }
        val discrepancyRate = if (totalFulfilledUnits > 0) {
            Math.min(1.0, totalShortOrDamagedUnits.toDouble() / totalFulfilledUnits.toDouble())
        } else 0.0
        val discrepancyScore = Math.max(0.0, 1.0 - discrepancyRate)

        // 5. Return rate for Damaged or Wrong Item (10%) - Inverted
        val qualityReturns = returns.filter {
            it.status == ReturnStatus.APPROVED.name &&
                (it.reason == ReturnReason.DAMAGED.name || it.reason == ReturnReason.WRONG_ITEM.name)
        }
        val totalQualityReturnUnits = qualityReturns.sumOf { it.quantity }
        val returnRate = if (totalFulfilledUnits > 0) {
            Math.min(1.0, totalQualityReturnUnits.toDouble() / totalFulfilledUnits.toDouble())
        } else 0.0
        val returnScore = Math.max(0.0, 1.0 - returnRate)

        // Weighted total
        val wFulfilment = fulfilmentRate * 35.0
        val wOnTime = onTimeRate * 30.0
        val wCancellation = cancellationScore * 15.0
        val wDiscrepancy = discrepancyScore * 10.0
        val wReturn = returnScore * 10.0

        val totalRawScore = wFulfilment + wOnTime + wCancellation + wDiscrepancy + wReturn
        val finalScore = Math.min(100, Math.max(0, Math.round(totalRawScore).toInt()))

        return TrustScoreBreakdown(
            supplierId = supplierId,
            supplierName = supplierName,
            overallScore = finalScore,
            hasSufficientData = hasSufficientData,
            completedOrdersCount = completedOrders.size,
            fulfilmentRate = fulfilmentRate,
            onTimeDeliveryRate = onTimeRate,
            cancellationRate = cancellationRate,
            discrepancyRate = discrepancyRate,
            returnRate = returnRate,
            fulfilmentScoreWeight = wFulfilment,
            onTimeScoreWeight = wOnTime,
            cancellationScoreWeight = wCancellation,
            discrepancyScoreWeight = wDiscrepancy,
            returnScoreWeight = wReturn,
            lastCalculatedEpochMillis = System.currentTimeMillis()
        )
    }
}

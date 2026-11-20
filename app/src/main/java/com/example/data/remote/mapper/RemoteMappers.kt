package com.example.data.remote.mapper

import com.example.data.local.entity.BatchEntity
import com.example.data.local.entity.ComplianceVerificationEntity
import com.example.data.local.entity.CreditLedgerEntryEntity
import com.example.data.local.entity.CreditNoteEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.OrderEntity
import com.example.data.local.entity.OrderItemEntity
import com.example.data.local.entity.OrderStatusHistoryEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ProductInventoryEntity
import com.example.data.local.entity.ProofOfDeliveryEntity
import com.example.data.local.entity.RecallEntity
import com.example.data.local.entity.ReturnEntity
import com.example.data.local.entity.ShortageRiskEntity
import com.example.data.local.entity.SupplierOfferEntity
import com.example.data.local.entity.SupplierPerformanceEntity
import com.example.data.local.entity.SupplierSettingsEntity
import com.example.data.remote.dto.BatchDto
import com.example.data.remote.dto.ComplianceVerificationDto
import com.example.data.remote.dto.CreditLedgerEntryDto
import com.example.data.remote.dto.CreditNoteDto
import com.example.data.remote.dto.DashboardStatsDto
import com.example.data.remote.dto.NotificationDto
import com.example.data.remote.dto.OrderDto
import com.example.data.remote.dto.OrderItemDto
import com.example.data.remote.dto.OrderStatusHistoryDto
import com.example.data.remote.dto.ProductDto
import com.example.data.remote.dto.ProductInventoryDto
import com.example.data.remote.dto.ProofOfDeliveryDto
import com.example.data.remote.dto.RecallDto
import com.example.data.remote.dto.ReturnDto
import com.example.data.remote.dto.ShortageAlertDto
import com.example.data.remote.dto.SupplierOfferDto
import com.example.data.remote.dto.SupplierSettingsDto
import com.example.data.remote.dto.TaxInvoiceDto
import com.example.data.remote.dto.TrustScoreDto
import com.example.domain.model.OrderStatus
import com.example.domain.model.ShortageEvaluationResult
import com.example.domain.model.ShortageRiskLevel
import com.example.domain.model.TaxInvoiceData
import com.example.domain.model.TaxInvoiceLineItem
import com.example.domain.model.TaxInvoiceParty
import com.example.domain.model.TrustScoreBreakdown
import com.example.domain.model.UserRole
import com.example.domain.repository.DashboardKpis

fun ProductDto.toEntity(): ProductEntity = ProductEntity(
    id = id,
    name = name,
    brand = brand,
    saltComposition = saltComposition,
    strength = strength,
    dosageForm = dosageForm,
    therapeuticCategory = therapeuticCategory,
    manufacturerName = manufacturerName,
    packSize = packSize,
    hsnCode = hsnCode,
    gstRatePercent = gstRatePercent,
    storageCondition = storageCondition,
    isRx = isRx,
    isActive = isActive,
    createdBy = createdBy,
    createdAtEpochMillis = createdAtEpochMillis
)

fun ProductEntity.toDto(): ProductDto = ProductDto(
    id = id,
    name = name,
    brand = brand,
    saltComposition = saltComposition,
    strength = strength,
    dosageForm = dosageForm,
    therapeuticCategory = therapeuticCategory,
    manufacturerName = manufacturerName,
    packSize = packSize,
    hsnCode = hsnCode,
    gstRatePercent = gstRatePercent,
    storageCondition = storageCondition,
    isRx = isRx,
    isActive = isActive,
    createdBy = createdBy,
    createdAtEpochMillis = createdAtEpochMillis
)

fun ProductInventoryDto.toEntity(): ProductInventoryEntity = ProductInventoryEntity(
    id = id,
    productName = productName,
    sku = sku,
    currentStock = currentStock,
    reorderThreshold = reorderThreshold,
    productId = productId,
    saltComposition = saltComposition,
    dosageForm = dosageForm,
    packSize = packSize,
    batchNumber = batchNumber,
    expiryDateEpochMillis = expiryDateEpochMillis,
    unitPricePtr = unitPricePtr,
    mrp = mrp,
    storageCondition = storageCondition,
    locationRack = locationRack,
    supplierName = supplierName,
    isActive = isActive
)

fun ProductInventoryEntity.toDto(): ProductInventoryDto = ProductInventoryDto(
    id = id,
    productName = productName,
    sku = sku,
    currentStock = currentStock,
    reorderThreshold = reorderThreshold,
    productId = productId,
    saltComposition = saltComposition,
    dosageForm = dosageForm,
    packSize = packSize,
    batchNumber = batchNumber,
    expiryDateEpochMillis = expiryDateEpochMillis,
    unitPricePtr = unitPricePtr,
    mrp = mrp,
    storageCondition = storageCondition,
    locationRack = locationRack,
    supplierName = supplierName,
    isActive = isActive
)

fun SupplierOfferDto.toEntity(): SupplierOfferEntity = SupplierOfferEntity(
    id = id,
    productId = productId,
    supplierId = supplierId,
    unitPricePtr = unitPricePtr,
    mrp = mrp,
    moq = moq,
    availableQuantity = availableQuantity,
    schemeType = schemeType,
    schemeBuyQty = schemeBuyQty,
    schemeFreeQty = schemeFreeQty,
    schemeDiscountPercent = schemeDiscountPercent,
    schemeMinQtyForDiscount = schemeMinQtyForDiscount,
    validTillEpochMillis = validTillEpochMillis,
    isActive = isActive,
    lastUpdatedEpochMillis = lastUpdatedEpochMillis
)

fun SupplierOfferEntity.toDto(): SupplierOfferDto = SupplierOfferDto(
    id = id,
    productId = productId,
    supplierId = supplierId,
    unitPricePtr = unitPricePtr,
    mrp = mrp,
    moq = moq,
    availableQuantity = availableQuantity,
    schemeType = schemeType,
    schemeBuyQty = schemeBuyQty,
    schemeFreeQty = schemeFreeQty,
    schemeDiscountPercent = schemeDiscountPercent,
    schemeMinQtyForDiscount = schemeMinQtyForDiscount,
    validTillEpochMillis = validTillEpochMillis,
    isActive = isActive,
    lastUpdatedEpochMillis = lastUpdatedEpochMillis
)

fun SupplierSettingsDto.toEntity(): SupplierSettingsEntity = SupplierSettingsEntity(
    supplierId = supplierId,
    deliveryCharge = deliveryCharge,
    freeDeliveryThreshold = freeDeliveryThreshold,
    averageLeadTimeDays = averageLeadTimeDays
)

fun SupplierSettingsEntity.toDto(): SupplierSettingsDto = SupplierSettingsDto(
    supplierId = supplierId,
    deliveryCharge = deliveryCharge,
    freeDeliveryThreshold = freeDeliveryThreshold,
    averageLeadTimeDays = averageLeadTimeDays
)

fun BatchDto.toEntity(): BatchEntity = BatchEntity(
    id = id,
    productId = productId,
    supplierId = supplierId,
    batchNumber = batchNumber,
    mfgDateEpochMillis = mfgDateEpochMillis,
    expiryDateEpochMillis = expiryDateEpochMillis,
    initialQuantity = initialQuantity,
    remainingQuantity = remainingQuantity,
    storageCondition = storageCondition,
    mrp = mrp,
    ptr = ptr
)

fun BatchEntity.toDto(): BatchDto = BatchDto(
    id = id,
    productId = productId,
    supplierId = supplierId,
    batchNumber = batchNumber,
    mfgDateEpochMillis = mfgDateEpochMillis,
    expiryDateEpochMillis = expiryDateEpochMillis,
    initialQuantity = initialQuantity,
    remainingQuantity = remainingQuantity,
    storageCondition = storageCondition,
    mrp = mrp,
    ptr = ptr
)

fun OrderDto.toEntity(): OrderEntity = OrderEntity(
    id = id,
    orderNumber = orderNumber,
    buyerId = buyerId,
    supplierId = supplierId,
    status = status,
    subtotal = subtotal,
    gstAmount = gstAmount,
    deliveryCharge = deliveryCharge,
    totalAmount = totalAmount,
    deliveryAddress = deliveryAddress,
    notes = notes,
    isEwayBillGenerated = isEwayBillGenerated,
    ewayBillNumber = ewayBillNumber,
    vehicleNumber = vehicleNumber,
    transporterName = transporterName,
    expectedDeliveryEpochMillis = expectedDeliveryEpochMillis,
    dispatchEpochMillis = dispatchEpochMillis,
    deliveredEpochMillis = deliveredEpochMillis,
    invoiceNumber = invoiceNumber,
    invoiceIssuedAtEpochMillis = invoiceIssuedAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis
)

fun OrderItemDto.toEntity(): OrderItemEntity = OrderItemEntity(
    id = id,
    orderId = orderId,
    productId = productId,
    offerId = offerId,
    productName = productName,
    brand = brand,
    packSize = packSize,
    hsnCode = hsnCode,
    gstRatePercent = gstRatePercent,
    orderedQuantity = orderedQuantity,
    freeQuantity = freeQuantity,
    fulfilledQuantity = fulfilledQuantity,
    unitPricePtr = unitPricePtr,
    effectivePrice = effectivePrice,
    batchNumber = batchNumber,
    expiryDateEpochMillis = expiryDateEpochMillis
)

fun OrderStatusHistoryDto.toEntity(): OrderStatusHistoryEntity = OrderStatusHistoryEntity(
    id = id,
    orderId = orderId,
    status = status,
    note = note,
    changedAtEpochMillis = changedAtEpochMillis,
    changedByUserId = changedByUserId
)

fun ProofOfDeliveryDto.toEntity(): ProofOfDeliveryEntity = ProofOfDeliveryEntity(
    id = id,
    orderId = orderId,
    receivedDateEpochMillis = receivedDateEpochMillis,
    receivedByName = receivedByName,
    notes = notes,
    photoUri = photoUri,
    shortQuantity = shortQuantity,
    damagedQuantity = damagedQuantity
)

fun NotificationDto.toEntity(): NotificationEntity = NotificationEntity(
    id = id,
    recipientUserId = recipientUserId,
    type = type,
    title = title,
    message = message,
    referenceId = referenceId,
    isRead = isRead,
    createdAtEpochMillis = createdAtEpochMillis
)

fun RecallDto.toEntity(): RecallEntity = RecallEntity(
    id = id,
    productId = productId,
    productName = productName,
    batchNumbersCsv = batchNumbersCsv,
    reason = reason,
    severity = severity,
    createdBySupplierId = createdBySupplierId,
    recallDateEpochMillis = recallDateEpochMillis,
    isActive = isActive
)

fun ReturnDto.toEntity(): ReturnEntity = ReturnEntity(
    id = id,
    orderId = orderId,
    orderItemId = orderItemId,
    buyerId = buyerId,
    supplierId = supplierId,
    productId = productId,
    batchNumber = batchNumber,
    quantity = quantity,
    reason = reason,
    status = status,
    requestedAtEpochMillis = requestedAtEpochMillis,
    decisionAtEpochMillis = decisionAtEpochMillis,
    decisionNotes = decisionNotes
)

fun CreditNoteDto.toEntity(): CreditNoteEntity = CreditNoteEntity(
    id = id,
    creditNoteNumber = creditNoteNumber,
    returnId = returnId,
    orderId = orderId,
    buyerId = buyerId,
    supplierId = supplierId,
    amount = amount,
    gstAmount = gstAmount,
    totalAmount = totalAmount,
    issuedAtEpochMillis = issuedAtEpochMillis
)

fun CreditLedgerEntryDto.toEntity(): CreditLedgerEntryEntity = CreditLedgerEntryEntity(
    id = id,
    buyerId = buyerId,
    supplierId = supplierId,
    entryType = entryType,
    amount = amount,
    balanceAfter = balanceAfter,
    referenceType = referenceType,
    referenceId = referenceId,
    description = description,
    dateEpochMillis = dateEpochMillis
)

fun TrustScoreDto.toEntity(): SupplierPerformanceEntity = SupplierPerformanceEntity(
    supplierId = supplierId,
    overallScore = overallScore,
    completedOrdersCount = completedOrdersCount,
    fulfilmentRate = fulfilmentRate,
    onTimeRate = onTimeDeliveryRate,
    cancellationRate = cancellationRate,
    discrepancyRate = discrepancyRate,
    returnRate = returnRate,
    calculatedAtEpochMillis = lastCalculatedEpochMillis
)

fun TrustScoreDto.toDomain(): TrustScoreBreakdown = TrustScoreBreakdown(
    supplierId = supplierId,
    supplierName = supplierName,
    overallScore = overallScore,
    hasSufficientData = hasSufficientData,
    completedOrdersCount = completedOrdersCount,
    fulfilmentRate = fulfilmentRate,
    onTimeDeliveryRate = onTimeDeliveryRate,
    cancellationRate = cancellationRate,
    discrepancyRate = discrepancyRate,
    returnRate = returnRate,
    fulfilmentScoreWeight = 0.30,
    onTimeScoreWeight = 0.25,
    cancellationScoreWeight = 0.15,
    discrepancyScoreWeight = 0.15,
    returnScoreWeight = 0.15,
    lastCalculatedEpochMillis = lastCalculatedEpochMillis
)

fun ShortageAlertDto.toEntity(): ShortageRiskEntity = ShortageRiskEntity(
    productId = productId,
    riskLevel = riskLevel,
    triggeredReasonsCsv = triggeredReasons.joinToString(";;"),
    totalAvailableQuantity = totalAvailableQuantity,
    availableSuppliersCount = availableSuppliersCount,
    avgLeadTimeDays = avgLeadTimeDays,
    evaluatedAtEpochMillis = lastUpdatedEpochMillis
)

fun ShortageAlertDto.toDomain(): ShortageEvaluationResult = ShortageEvaluationResult(
    productId = productId,
    productName = productName,
    brand = brand,
    saltComposition = saltComposition,
    riskLevel = try { ShortageRiskLevel.valueOf(riskLevel.uppercase()) } catch (_: Exception) { ShortageRiskLevel.NONE },
    triggeredReasons = triggeredReasons,
    availableSuppliersCount = availableSuppliersCount,
    totalAvailableQuantity = totalAvailableQuantity,
    avgLeadTimeDays = avgLeadTimeDays,
    lastUpdatedEpochMillis = lastUpdatedEpochMillis
)

fun DashboardStatsDto.toDomain(): DashboardKpis {
    val parsedRole = try { UserRole.valueOf(role.uppercase()) } catch (_: Exception) { UserRole.STOCKIST }
    return DashboardKpis(
        role = parsedRole,
        openOrdersCount = openOrdersCount,
        basketItemsCount = basketItemsCount,
        nearExpiryBatchesCount = nearExpiryBatchesCount,
        shortageAlertsCount = shortageAlertsCount,
        ledgerBalance = ledgerBalance,
        activeOffersCount = activeOffersCount,
        pendingOrdersCount = pendingOrdersCount,
        lowStockCount = lowStockCount,
        trustScore = trustScore,
        totalUsersCount = totalUsersCount,
        manufacturersCount = manufacturersCount,
        pharmaCompaniesCount = pharmaCompaniesCount,
        stockistsCount = stockistsCount,
        totalProductsCount = totalProductsCount,
        totalOrdersCount = totalOrdersCount,
        activeRecallsCount = activeRecallsCount
    )
}

fun ComplianceVerificationDto.toEntity(): ComplianceVerificationEntity = ComplianceVerificationEntity(
    id = id ?: 0L,
    userId = userId,
    gstin = gstin,
    isGstinVerified = isGstinVerified,
    drugLicenseNumber = drugLicenseNumber,
    drugLicenseIssueDate = drugLicenseIssueDate,
    drugLicenseExpiryDate = drugLicenseExpiryDate,
    drugLicenseDocUri = drugLicenseDocUri,
    whoGmpCertificateNumber = whoGmpCertificateNumber,
    whoGmpIssueDate = whoGmpIssueDate,
    whoGmpExpiryDate = whoGmpExpiryDate,
    whoGmpDocUri = whoGmpDocUri,
    isWhoGmpApplicable = isWhoGmpApplicable,
    status = status,
    verifiedAtEpochMillis = verifiedAtEpochMillis ?: System.currentTimeMillis()
)

fun TaxInvoiceDto.toDomain(): TaxInvoiceData = TaxInvoiceData(
    orderId = orderId,
    orderNumber = orderNumber,
    orderStatus = try { OrderStatus.valueOf(orderStatus.uppercase()) } catch (_: Exception) { OrderStatus.PLACED },
    orderDateEpochMillis = orderDateEpochMillis,
    invoiceNumber = invoiceNumber?.takeIf { it.isNotBlank() },
    invoiceDateEpochMillis = invoiceDateEpochMillis,
    isEligible = isEligible,
    eligibilityReason = eligibilityReason,
    isBackendAuthoritative = true,
    seller = TaxInvoiceParty(
        legalName = seller.legalName?.takeIf { it.isNotBlank() },
        contactName = seller.contactName?.takeIf { it.isNotBlank() },
        address = seller.address?.takeIf { it.isNotBlank() },
        shippingAddress = seller.shippingAddress?.takeIf { it.isNotBlank() },
        gstin = seller.gstin?.takeIf { it.isNotBlank() },
        phone = seller.phone?.takeIf { it.isNotBlank() },
        email = seller.email?.takeIf { it.isNotBlank() }
    ),
    buyer = TaxInvoiceParty(
        legalName = buyer.legalName?.takeIf { it.isNotBlank() },
        contactName = buyer.contactName?.takeIf { it.isNotBlank() },
        address = buyer.address?.takeIf { it.isNotBlank() },
        shippingAddress = buyer.shippingAddress?.takeIf { it.isNotBlank() },
        gstin = buyer.gstin?.takeIf { it.isNotBlank() },
        phone = buyer.phone?.takeIf { it.isNotBlank() },
        email = buyer.email?.takeIf { it.isNotBlank() }
    ),
    placeOfSupply = placeOfSupply?.takeIf { it.isNotBlank() },
    gstTaxType = gstTaxType?.takeIf { it.isNotBlank() },
    items = items.map { item ->
        TaxInvoiceLineItem(
            productName = item.productName,
            brand = item.brand,
            packSize = item.packSize,
            hsnCode = item.hsnCode,
            batchNumber = item.batchNumber,
            quantity = item.quantity,
            freeQuantity = item.freeQuantity,
            unitPricePtr = item.unitPricePtr,
            effectiveUnitPrice = item.effectiveUnitPrice,
            taxableValue = item.taxableValue,
            gstRatePercent = item.gstRatePercent,
            gstAmount = item.gstAmount,
            cgstAmount = item.cgstAmount,
            sgstAmount = item.sgstAmount,
            igstAmount = item.igstAmount,
            lineTotal = item.lineTotal
        )
    },
    subtotalTaxable = subtotalTaxable,
    cgstTotal = cgstTotal,
    sgstTotal = sgstTotal,
    igstTotal = igstTotal,
    totalGstAmount = totalGstAmount,
    deliveryCharge = deliveryCharge,
    grandTotal = grandTotal,
    ewayBillNumber = ewayBillNumber?.takeIf { it.isNotBlank() },
    vehicleNumber = vehicleNumber?.takeIf { it.isNotBlank() },
    transporterName = transporterName?.takeIf { it.isNotBlank() },
    missingFields = missingFields
)


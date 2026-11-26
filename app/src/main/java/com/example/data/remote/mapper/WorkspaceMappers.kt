package com.example.data.remote.mapper

import com.example.data.local.entity.ApprovalAuditLogEntity
import com.example.data.local.entity.MasterDataEntity
import com.example.data.local.entity.MedicineAnnouncementEntity
import com.example.data.local.entity.MedicineSubmissionEntity
import com.example.data.local.entity.OfferBannerEntity
import com.example.data.local.entity.OrganizationMemberEntity
import com.example.data.local.entity.PromotionalCampaignEntity
import com.example.data.local.entity.StockistPurchasedInventoryEntity
import com.example.data.remote.dto.ApprovalAuditEntryDto
import com.example.data.remote.dto.MasterDataDto
import com.example.data.remote.dto.MedicineAnnouncementDto
import com.example.data.remote.dto.MedicineSubmissionDto
import com.example.data.remote.dto.OfferBannerDto
import com.example.data.remote.dto.OrganizationMemberDto
import com.example.data.remote.dto.PromotionalCampaignDto
import com.example.data.remote.dto.StockistPurchasedInventoryDto
import com.example.domain.model.AnnouncementCategory
import com.example.domain.model.AnnouncementStatus
import com.example.domain.model.ApprovalAuditEntry
import com.example.domain.model.BannerOfferType
import com.example.domain.model.BannerStatus
import com.example.domain.model.BannerTargetAudience
import com.example.domain.model.CampaignStatus
import com.example.domain.model.CataloguePublicationStatus
import com.example.domain.model.MasterDataItem
import com.example.domain.model.MasterDataType
import com.example.domain.model.MedicineAnnouncement
import com.example.domain.model.MedicineSubmission
import com.example.domain.model.MedicineSubmissionStatus
import com.example.domain.model.OfferBanner
import com.example.domain.model.OrganizationMember
import com.example.domain.model.PharmaPermission
import com.example.domain.model.PromotionalCampaign
import com.example.domain.model.RolePermissionMatrix
import com.example.domain.model.StockistPurchasedInventoryItem
import com.example.domain.model.UserRole

fun OrganizationMemberEntity.toDomain(): OrganizationMember {
    val parsedRoles = RolePermissionMatrix.parseRolesCsv(rolesCsv, UserRole.MANUFACTURER)
    val explicit = PharmaPermission.parseCsv(permissionsCsv)
    return OrganizationMember(
        id = id,
        organizationId = organizationId,
        userId = userId,
        fullName = fullName,
        email = email,
        roles = parsedRoles,
        permissions = RolePermissionMatrix.resolvePermissions(parsedRoles, explicit),
        isActive = isActive,
        joinedAtEpochMillis = joinedAtEpochMillis
    )
}

fun OrganizationMemberDto.toEntity(): OrganizationMemberEntity {
    val parsedRoles = roles.map { UserRole.fromString(it) }.toSet().ifEmpty { setOf(UserRole.MANUFACTURER) }
    val explicit = permissions.mapNotNull { raw ->
        PharmaPermission.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }.toSet()
    val resolved = RolePermissionMatrix.resolvePermissions(parsedRoles, explicit)
    return OrganizationMemberEntity(
        id = id,
        organizationId = organizationId,
        userId = userId,
        fullName = fullName,
        email = email,
        rolesCsv = RolePermissionMatrix.rolesToCsv(parsedRoles),
        permissionsCsv = PharmaPermission.toCsv(resolved),
        isActive = isActive,
        joinedAtEpochMillis = joinedAtEpochMillis
    )
}

fun MedicineSubmissionEntity.toDomain(): MedicineSubmission = MedicineSubmission(
    id = id,
    organizationId = organizationId,
    organizationName = organizationName,
    submittedByUserId = submittedByUserId,
    submittedByName = submittedByName,
    submittedByRole = UserRole.fromString(submittedByRole),
    medicineName = medicineName,
    brandName = brandName,
    genericName = genericName,
    saltComposition = saltComposition,
    dosageForm = dosageForm,
    strength = strength,
    routeOfAdministration = routeOfAdministration,
    packSize = packSize,
    packagingType = packagingType,
    therapeuticCategory = therapeuticCategory,
    storageCondition = storageCondition,
    isRx = isRx,
    mrp = mrp,
    wholesaleRatePtr = wholesaleRatePtr,
    hsnCode = hsnCode,
    gstRatePercent = gstRatePercent,
    skuCode = skuCode,
    description = description,
    imageUrl = imageUrl,
    regulatoryIdentifier = regulatoryIdentifier,
    supportingDocUri = supportingDocUri,
    approvalStatus = MedicineSubmissionStatus.fromString(approvalStatus),
    catalogueStatus = CataloguePublicationStatus.fromString(catalogueStatus),
    approvedProductId = approvedProductId,
    reviewedByUserId = reviewedByUserId,
    reviewedByName = reviewedByName,
    rejectionReason = rejectionReason,
    reviewNotes = reviewNotes,
    submittedAtEpochMillis = submittedAtEpochMillis,
    reviewedAtEpochMillis = reviewedAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun MedicineSubmission.toEntity(): MedicineSubmissionEntity = MedicineSubmissionEntity(
    id = id,
    organizationId = organizationId,
    organizationName = organizationName,
    submittedByUserId = submittedByUserId,
    submittedByName = submittedByName,
    submittedByRole = submittedByRole.name,
    medicineName = medicineName.trim(),
    brandName = brandName.trim(),
    genericName = genericName.trim(),
    saltComposition = saltComposition.trim(),
    dosageForm = dosageForm.trim(),
    strength = strength.trim(),
    routeOfAdministration = routeOfAdministration.trim(),
    packSize = packSize.trim(),
    packagingType = packagingType.trim(),
    therapeuticCategory = therapeuticCategory.trim(),
    storageCondition = storageCondition,
    isRx = isRx,
    mrp = mrp,
    wholesaleRatePtr = wholesaleRatePtr,
    hsnCode = hsnCode.trim(),
    gstRatePercent = gstRatePercent,
    skuCode = skuCode.trim(),
    description = description.trim(),
    imageUrl = imageUrl,
    regulatoryIdentifier = regulatoryIdentifier?.trim()?.takeIf { it.isNotBlank() },
    supportingDocUri = supportingDocUri,
    approvalStatus = approvalStatus.name,
    catalogueStatus = catalogueStatus.name,
    approvedProductId = approvedProductId,
    reviewedByUserId = reviewedByUserId,
    reviewedByName = reviewedByName,
    rejectionReason = rejectionReason,
    reviewNotes = reviewNotes,
    submittedAtEpochMillis = submittedAtEpochMillis,
    reviewedAtEpochMillis = reviewedAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun MedicineSubmission.toDto(): MedicineSubmissionDto = MedicineSubmissionDto(
    id = id,
    organizationId = organizationId,
    organizationName = organizationName,
    submittedByUserId = submittedByUserId,
    submittedByName = submittedByName,
    submittedByRole = submittedByRole.name,
    medicineName = medicineName,
    brandName = brandName,
    genericName = genericName,
    saltComposition = saltComposition,
    dosageForm = dosageForm,
    strength = strength,
    routeOfAdministration = routeOfAdministration,
    packSize = packSize,
    packagingType = packagingType,
    therapeuticCategory = therapeuticCategory,
    storageCondition = storageCondition,
    isRx = isRx,
    mrp = mrp,
    wholesaleRatePtr = wholesaleRatePtr,
    hsnCode = hsnCode,
    gstRatePercent = gstRatePercent,
    skuCode = skuCode,
    description = description,
    imageUrl = imageUrl,
    regulatoryIdentifier = regulatoryIdentifier,
    supportingDocUri = supportingDocUri,
    approvalStatus = approvalStatus.name,
    catalogueStatus = catalogueStatus.name,
    approvedProductId = approvedProductId,
    reviewedByUserId = reviewedByUserId,
    reviewedByName = reviewedByName,
    rejectionReason = rejectionReason,
    reviewNotes = reviewNotes,
    submittedAtEpochMillis = submittedAtEpochMillis,
    reviewedAtEpochMillis = reviewedAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun MedicineSubmissionDto.toEntity(): MedicineSubmissionEntity = MedicineSubmissionEntity(
    id = id,
    organizationId = organizationId,
    organizationName = organizationName,
    submittedByUserId = submittedByUserId,
    submittedByName = submittedByName,
    submittedByRole = submittedByRole,
    medicineName = medicineName,
    brandName = brandName,
    genericName = genericName,
    saltComposition = saltComposition,
    dosageForm = dosageForm,
    strength = strength,
    routeOfAdministration = routeOfAdministration,
    packSize = packSize,
    packagingType = packagingType,
    therapeuticCategory = therapeuticCategory,
    storageCondition = storageCondition,
    isRx = isRx,
    mrp = mrp,
    wholesaleRatePtr = wholesaleRatePtr,
    hsnCode = hsnCode,
    gstRatePercent = gstRatePercent,
    skuCode = skuCode,
    description = description,
    imageUrl = imageUrl,
    regulatoryIdentifier = regulatoryIdentifier,
    supportingDocUri = supportingDocUri,
    approvalStatus = approvalStatus,
    catalogueStatus = catalogueStatus,
    approvedProductId = approvedProductId,
    reviewedByUserId = reviewedByUserId,
    reviewedByName = reviewedByName,
    rejectionReason = rejectionReason,
    reviewNotes = reviewNotes,
    submittedAtEpochMillis = submittedAtEpochMillis,
    reviewedAtEpochMillis = reviewedAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun ApprovalAuditLogEntity.toDomain(): ApprovalAuditEntry = ApprovalAuditEntry(
    id = id,
    submissionId = submissionId,
    organizationId = organizationId,
    action = action,
    fromStatus = fromStatus,
    toStatus = toStatus,
    actorUserId = actorUserId,
    actorName = actorName,
    notes = notes,
    timestampEpochMillis = timestampEpochMillis
)

fun ApprovalAuditEntryDto.toEntity(): ApprovalAuditLogEntity = ApprovalAuditLogEntity(
    id = id,
    submissionId = submissionId,
    organizationId = organizationId,
    action = action,
    fromStatus = fromStatus,
    toStatus = toStatus,
    actorUserId = actorUserId,
    actorName = actorName,
    notes = notes,
    timestampEpochMillis = timestampEpochMillis
)

fun MasterDataEntity.toDomain(): MasterDataItem = MasterDataItem(
    id = id,
    organizationId = organizationId.takeIf { it > 0L },
    type = MasterDataType.fromString(type),
    name = name,
    code = code,
    description = description,
    isOrganizationScoped = isOrganizationScoped,
    isActive = isActive,
    createdAtEpochMillis = createdAtEpochMillis
)

fun MasterDataDto.toEntity(): MasterDataEntity = MasterDataEntity(
    id = id,
    organizationId = organizationId ?: 0L,
    type = type,
    name = name,
    normalizedName = name.trim().lowercase(),
    code = code,
    description = description,
    isOrganizationScoped = isOrganizationScoped,
    isActive = isActive,
    createdAtEpochMillis = createdAtEpochMillis
)

fun OfferBannerEntity.toDomain(): OfferBanner = OfferBanner(
    id = id,
    organizationId = organizationId,
    organizationName = organizationName,
    title = title,
    subtitle = subtitle,
    imageUrl = imageUrl,
    offerType = BannerOfferType.fromString(offerType),
    discountOrOfferSummary = discountOrOfferSummary,
    linkedProductId = linkedProductId,
    linkedProductName = linkedProductName,
    linkedCategory = linkedCategory,
    linkedCampaignId = linkedCampaignId,
    startDateEpochMillis = startDateEpochMillis,
    endDateEpochMillis = endDateEpochMillis,
    priorityOrder = priorityOrder,
    status = BannerStatus.fromString(status),
    targetAudience = BannerTargetAudience.fromString(targetAudience),
    ctaText = ctaText,
    ctaDestinationRoute = ctaDestinationRoute,
    createdByUserId = createdByUserId,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun OfferBanner.toEntity(): OfferBannerEntity = OfferBannerEntity(
    id = id,
    organizationId = organizationId,
    organizationName = organizationName,
    title = title.trim(),
    subtitle = subtitle.trim(),
    imageUrl = imageUrl,
    offerType = offerType.name,
    discountOrOfferSummary = discountOrOfferSummary.trim(),
    linkedProductId = linkedProductId,
    linkedProductName = linkedProductName,
    linkedCategory = linkedCategory?.trim()?.takeIf { it.isNotBlank() },
    linkedCampaignId = linkedCampaignId,
    startDateEpochMillis = startDateEpochMillis,
    endDateEpochMillis = endDateEpochMillis,
    priorityOrder = priorityOrder.coerceIn(1, 99),
    status = status.name,
    targetAudience = targetAudience.name,
    ctaText = ctaText.trim().ifBlank { "View Offer" },
    ctaDestinationRoute = ctaDestinationRoute.trim(),
    createdByUserId = createdByUserId,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun OfferBanner.toDto(): OfferBannerDto = OfferBannerDto(
    id = id,
    organizationId = organizationId,
    organizationName = organizationName,
    title = title,
    subtitle = subtitle,
    imageUrl = imageUrl,
    offerType = offerType.name,
    discountOrOfferSummary = discountOrOfferSummary,
    linkedProductId = linkedProductId,
    linkedProductName = linkedProductName,
    linkedCategory = linkedCategory,
    linkedCampaignId = linkedCampaignId,
    startDateEpochMillis = startDateEpochMillis,
    endDateEpochMillis = endDateEpochMillis,
    priorityOrder = priorityOrder,
    status = status.name,
    targetAudience = targetAudience.name,
    ctaText = ctaText,
    ctaDestinationRoute = ctaDestinationRoute,
    createdByUserId = createdByUserId,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun OfferBannerDto.toEntity(): OfferBannerEntity = OfferBannerEntity(
    id = id,
    organizationId = organizationId,
    organizationName = organizationName,
    title = title,
    subtitle = subtitle,
    imageUrl = imageUrl,
    offerType = offerType,
    discountOrOfferSummary = discountOrOfferSummary,
    linkedProductId = linkedProductId,
    linkedProductName = linkedProductName,
    linkedCategory = linkedCategory,
    linkedCampaignId = linkedCampaignId,
    startDateEpochMillis = startDateEpochMillis,
    endDateEpochMillis = endDateEpochMillis,
    priorityOrder = priorityOrder,
    status = status,
    targetAudience = targetAudience,
    ctaText = ctaText,
    ctaDestinationRoute = ctaDestinationRoute,
    createdByUserId = createdByUserId,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun PromotionalCampaignEntity.toDomain(): PromotionalCampaign = PromotionalCampaign(
    id = id,
    organizationId = organizationId,
    organizationName = organizationName,
    name = name,
    schemeSummary = schemeSummary,
    discountPercent = discountPercent,
    minOrderQuantity = minOrderQuantity,
    linkedProductIdsCsv = linkedProductIdsCsv,
    linkedProductNamesSummary = linkedProductNamesSummary,
    termsAndConditions = termsAndConditions,
    startDateEpochMillis = startDateEpochMillis,
    endDateEpochMillis = endDateEpochMillis,
    status = CampaignStatus.fromString(status),
    redemptionsCount = redemptionsCount,
    createdByUserId = createdByUserId,
    createdAtEpochMillis = createdAtEpochMillis
)

fun PromotionalCampaign.toEntity(): PromotionalCampaignEntity = PromotionalCampaignEntity(
    id = id,
    organizationId = organizationId,
    organizationName = organizationName,
    name = name.trim(),
    schemeSummary = schemeSummary.trim(),
    discountPercent = discountPercent,
    minOrderQuantity = minOrderQuantity.coerceAtLeast(1),
    linkedProductIdsCsv = linkedProductIdsCsv,
    linkedProductNamesSummary = linkedProductNamesSummary,
    termsAndConditions = termsAndConditions.trim(),
    startDateEpochMillis = startDateEpochMillis,
    endDateEpochMillis = endDateEpochMillis,
    status = status.name,
    redemptionsCount = redemptionsCount,
    createdByUserId = createdByUserId,
    createdAtEpochMillis = createdAtEpochMillis
)

fun PromotionalCampaign.toDto(): PromotionalCampaignDto = PromotionalCampaignDto(
    id = id,
    organizationId = organizationId,
    organizationName = organizationName,
    name = name,
    schemeSummary = schemeSummary,
    discountPercent = discountPercent,
    minOrderQuantity = minOrderQuantity,
    linkedProductIdsCsv = linkedProductIdsCsv,
    linkedProductNamesSummary = linkedProductNamesSummary,
    termsAndConditions = termsAndConditions,
    startDateEpochMillis = startDateEpochMillis,
    endDateEpochMillis = endDateEpochMillis,
    status = status.name,
    redemptionsCount = redemptionsCount,
    createdByUserId = createdByUserId,
    createdAtEpochMillis = createdAtEpochMillis
)

fun PromotionalCampaignDto.toEntity(): PromotionalCampaignEntity = PromotionalCampaignEntity(
    id = id,
    organizationId = organizationId,
    organizationName = organizationName,
    name = name,
    schemeSummary = schemeSummary,
    discountPercent = discountPercent,
    minOrderQuantity = minOrderQuantity,
    linkedProductIdsCsv = linkedProductIdsCsv,
    linkedProductNamesSummary = linkedProductNamesSummary,
    termsAndConditions = termsAndConditions,
    startDateEpochMillis = startDateEpochMillis,
    endDateEpochMillis = endDateEpochMillis,
    status = status,
    redemptionsCount = redemptionsCount,
    createdByUserId = createdByUserId,
    createdAtEpochMillis = createdAtEpochMillis
)

fun MedicineAnnouncementEntity.toDomain(): MedicineAnnouncement = MedicineAnnouncement(
    id = id,
    organizationId = organizationId,
    organizationName = organizationName,
    linkedProductId = linkedProductId,
    linkedProductName = linkedProductName,
    title = title,
    body = body,
    category = AnnouncementCategory.fromString(category),
    imageUrl = imageUrl,
    status = AnnouncementStatus.fromString(status),
    createdByUserId = createdByUserId,
    publishedAtEpochMillis = publishedAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis
)

fun MedicineAnnouncement.toEntity(): MedicineAnnouncementEntity = MedicineAnnouncementEntity(
    id = id,
    organizationId = organizationId,
    organizationName = organizationName,
    linkedProductId = linkedProductId,
    linkedProductName = linkedProductName,
    title = title.trim(),
    body = body.trim(),
    category = category.name,
    imageUrl = imageUrl,
    status = status.name,
    createdByUserId = createdByUserId,
    publishedAtEpochMillis = publishedAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis
)

fun MedicineAnnouncement.toDto(): MedicineAnnouncementDto = MedicineAnnouncementDto(
    id = id,
    organizationId = organizationId,
    organizationName = organizationName,
    linkedProductId = linkedProductId,
    linkedProductName = linkedProductName,
    title = title,
    body = body,
    category = category.name,
    imageUrl = imageUrl,
    status = status.name,
    createdByUserId = createdByUserId,
    publishedAtEpochMillis = publishedAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis
)

fun MedicineAnnouncementDto.toEntity(): MedicineAnnouncementEntity = MedicineAnnouncementEntity(
    id = id,
    organizationId = organizationId,
    organizationName = organizationName,
    linkedProductId = linkedProductId,
    linkedProductName = linkedProductName,
    title = title,
    body = body,
    category = category,
    imageUrl = imageUrl,
    status = status,
    createdByUserId = createdByUserId,
    publishedAtEpochMillis = publishedAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis
)

fun StockistPurchasedInventoryEntity.toDomain(): StockistPurchasedInventoryItem =
    StockistPurchasedInventoryItem(
        id = id,
        stockistOrganizationId = stockistOrganizationId,
        stockistUserId = stockistUserId,
        productId = productId,
        productName = productName,
        brand = brand,
        saltComposition = saltComposition,
        batchNumber = batchNumber,
        expiryDateEpochMillis = expiryDateEpochMillis,
        purchasedQuantity = purchasedQuantity,
        remainingQuantity = remainingQuantity,
        reorderLevel = reorderLevel,
        purchaseUnitPrice = purchaseUnitPrice,
        sourceOrderNumber = sourceOrderNumber,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

fun StockistPurchasedInventoryItem.toEntity(): StockistPurchasedInventoryEntity =
    StockistPurchasedInventoryEntity(
        id = id,
        stockistOrganizationId = stockistOrganizationId,
        stockistUserId = stockistUserId,
        productId = productId,
        productName = productName.trim(),
        brand = brand.trim(),
        saltComposition = saltComposition.trim(),
        batchNumber = batchNumber.trim(),
        expiryDateEpochMillis = expiryDateEpochMillis,
        purchasedQuantity = purchasedQuantity.coerceAtLeast(0),
        remainingQuantity = remainingQuantity.coerceAtLeast(0),
        reorderLevel = reorderLevel.coerceAtLeast(0),
        purchaseUnitPrice = purchaseUnitPrice.coerceAtLeast(0.0),
        sourceOrderNumber = sourceOrderNumber?.trim()?.takeIf { it.isNotBlank() },
        updatedAtEpochMillis = updatedAtEpochMillis
    )

fun StockistPurchasedInventoryItem.toDto(): StockistPurchasedInventoryDto =
    StockistPurchasedInventoryDto(
        id = id,
        stockistOrganizationId = stockistOrganizationId,
        stockistUserId = stockistUserId,
        productId = productId,
        productName = productName,
        brand = brand,
        saltComposition = saltComposition,
        batchNumber = batchNumber,
        expiryDateEpochMillis = expiryDateEpochMillis,
        purchasedQuantity = purchasedQuantity,
        remainingQuantity = remainingQuantity,
        reorderLevel = reorderLevel,
        purchaseUnitPrice = purchaseUnitPrice,
        sourceOrderNumber = sourceOrderNumber,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

fun StockistPurchasedInventoryDto.toEntity(): StockistPurchasedInventoryEntity =
    StockistPurchasedInventoryEntity(
        id = id,
        stockistOrganizationId = stockistOrganizationId,
        stockistUserId = stockistUserId,
        productId = productId,
        productName = productName,
        brand = brand,
        saltComposition = saltComposition,
        batchNumber = batchNumber,
        expiryDateEpochMillis = expiryDateEpochMillis,
        purchasedQuantity = purchasedQuantity,
        remainingQuantity = remainingQuantity,
        reorderLevel = reorderLevel,
        purchaseUnitPrice = purchaseUnitPrice,
        sourceOrderNumber = sourceOrderNumber,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

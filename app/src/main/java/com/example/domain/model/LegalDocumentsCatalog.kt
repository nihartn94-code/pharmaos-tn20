package com.example.domain.model

import com.example.data.remote.config.ApiConfig

/**
 * Canonical registry of PharmaOS legal document entries.
 *
 * URLs are resolved exclusively from backend-provided legal document links or a configured
 * organization backend host. When no live host is configured, [LegalDocument.configuredUrl]
 * remains null — no fictional legal approvals or fabricated regulatory endorsements are generated.
 */
object LegalDocumentsCatalog {

    private data class LegalDocumentTemplate(
        val id: String,
        val title: String,
        val subtitle: String,
        val slug: String
    )

    private val templates = listOf(
        LegalDocumentTemplate(
            id = "terms_and_conditions",
            title = "Terms and Conditions",
            subtitle = "B2B trading terms, account responsibilities, and order governance",
            slug = "terms-and-conditions"
        ),
        LegalDocumentTemplate(
            id = "privacy_policy",
            title = "Privacy Policy",
            subtitle = "Collection, storage, and protection of account and transaction data",
            slug = "privacy-policy"
        ),
        LegalDocumentTemplate(
            id = "refund_cancellation",
            title = "Refund and Cancellation Policy",
            subtitle = "Order cancellations, damaged/expired stock returns, and credit notes",
            slug = "refund-and-cancellation-policy"
        ),
        LegalDocumentTemplate(
            id = "shipping_delivery",
            title = "Shipping and Delivery Policy",
            subtitle = "Dispatch SLAs, cold-chain transit requirements, and proof of delivery",
            slug = "shipping-and-delivery-policy"
        ),
        LegalDocumentTemplate(
            id = "disclaimer",
            title = "Disclaimer",
            subtitle = "Platform intermediary scope and non-clinical information notice",
            slug = "disclaimer"
        ),
        LegalDocumentTemplate(
            id = "regulatory_compliance",
            title = "Pharmaceutical Compliance & Licensing Policy",
            subtitle = "Drug license, GSTIN verification, and batch recall compliance duties",
            slug = "regulatory-compliance-policy"
        )
    )

    fun resolveDocuments(
        backendBaseUrl: String?,
        remoteOverrides: Map<String, Pair<String?, String?>> = emptyMap()
    ): List<LegalDocument> {
        val baseUrl = backendBaseUrl?.takeIf { ApiConfig.isBackendUrlConfigured(it) }
            ?.let { ApiConfig.normalizeBaseUrl(it) }

        return templates.map { template ->
            val override = remoteOverrides[template.id] ?: remoteOverrides[template.slug]
            val resolvedUrl = override?.first?.takeIf { it.isNotBlank() }
                ?: baseUrl?.let { "${it}legal/${template.slug}" }
            val resolvedVersion = override?.second?.takeIf { it.isNotBlank() }

            LegalDocument(
                id = template.id,
                title = template.title,
                subtitle = template.subtitle,
                slug = template.slug,
                configuredUrl = resolvedUrl,
                versionLabel = resolvedVersion
            )
        }
    }
}

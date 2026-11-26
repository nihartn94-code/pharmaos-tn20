package com.example.ui.navigation

object NavRoutes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val COMPLIANCE_VERIFICATION = "compliance_verification"
    const val DASHBOARD = "dashboard"
    const val MEDICINE_CATALOGUE = "medicine_catalogue"
    const val MEDICINE_CATALOGUE_ROUTE = "medicine_catalogue?focus={focus}&manufacturer={manufacturer}&query={query}"
    const val SEARCH_MOLECULES = "search_molecules"
    const val ADD_EDIT_PRODUCT = "add_edit_product"
    const val MEDICINE_DETAILS = "medicine_details/{productId}"
    const val SUPPLIER_OFFERS = "supplier_offers"
    const val ADD_EDIT_OFFER = "add_edit_offer?productId={productId}"
    const val BASKET = "basket"
    const val SELECTED_PRODUCTS_SUMMARY = "selected_products_summary"
    const val BEST_BUY_OPTIMIZER = "best_buy_optimizer"
    const val OCR_SCANNER = "ocr_scanner"
    const val OCR_REVIEW = "ocr_review"
    const val CHECKOUT = "checkout"
    const val PAYMENT = "payment?address={address}&notes={notes}"
    const val ORDER_CONFIRMATION = "order_confirmation/{orderIds}?paymentMethod={paymentMethod}&txnId={txnId}"
    const val MY_ORDERS = "my_orders"
    const val ORDER_DETAILS = "order_details/{orderId}"
    const val BATCH_EXPIRY = "batch_expiry"
    const val INVENTORY_DASHBOARD = "inventory_dashboard"
    const val PRODUCT_INVENTORY_LIST = "product_inventory_list"
    const val RECALLS = "recalls"
    const val RETURNS_CREDIT_NOTES = "returns_credit_notes"
    const val CREDIT_LEDGER = "credit_ledger"
    const val PHARMA_TRUST_SCORE = "pharma_trust_score"
    const val SUPPLIER_COMPARISON = "supplier_comparison"
    const val SHORTAGE_ALERTS = "shortage_alerts"
    const val SUPPLIER_SETTINGS = "supplier_settings"
    const val CSV_IMPORT_EXPORT = "csv_import_export"
    const val ADMIN_USER_MANAGEMENT = "admin_user_management"
    const val PROFILE_SETTINGS = "profile_settings"
    const val NOTIFICATIONS = "notifications"

    // Stockist Isolated Purchased Inventory
    const val STOCKIST_PURCHASED_INVENTORY = "stockist_purchased_inventory"

    // Dedicated Manufacturer Workspace Routes
    const val MANUFACTURER_DASHBOARD = "manufacturer_dashboard"
    const val MANUFACTURER_BANNERS = "manufacturer_banners"
    const val MANUFACTURER_PROMOTIONS = "manufacturer_promotions"
    const val MANUFACTURER_UPDATES = "manufacturer_updates"

    // Dedicated Company Workspace Routes
    const val COMPANY_DASHBOARD = "company_dashboard"
    const val COMPANY_CATALOGUE = "company_catalogue"
    const val COMPANY_ADD_MEDICINE = "company_add_medicine"
    const val COMPANY_APPROVALS = "company_approvals"
    const val COMPANY_MASTER_DATA = "company_master_data"

    fun rootRouteForWorkspace(workspace: com.example.domain.model.WorkspaceType): String = when (workspace) {
        com.example.domain.model.WorkspaceType.STOCKIST -> DASHBOARD
        com.example.domain.model.WorkspaceType.MANUFACTURER -> MANUFACTURER_DASHBOARD
        com.example.domain.model.WorkspaceType.COMPANY -> COMPANY_DASHBOARD
        com.example.domain.model.WorkspaceType.ADMIN -> DASHBOARD
    }

    fun medicineCatalogue(
        focus: String = "products",
        manufacturer: String = "",
        query: String = ""
    ): String {
        val encFocus = java.net.URLEncoder.encode(focus, "UTF-8")
        val encMfg = java.net.URLEncoder.encode(manufacturer, "UTF-8")
        val encQuery = java.net.URLEncoder.encode(query, "UTF-8")
        return "medicine_catalogue?focus=$encFocus&manufacturer=$encMfg&query=$encQuery"
    }
    fun medicineDetails(productId: Long) = "medicine_details/$productId"
    fun addEditOffer(productId: Long? = null) = if (productId != null) "add_edit_offer?productId=$productId" else "add_edit_offer"
    fun payment(address: String = "", notes: String = ""): String {
        val encAddr = java.net.URLEncoder.encode(address, "UTF-8")
        val encNotes = java.net.URLEncoder.encode(notes, "UTF-8")
        return "payment?address=$encAddr&notes=$encNotes"
    }
    fun orderConfirmation(
        orderIds: List<Long>,
        paymentMethod: String? = null,
        txnId: String? = null
    ): String {
        val ids = orderIds.joinToString(",")
        val encMethod = paymentMethod?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
        val encTxn = txnId?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
        return "order_confirmation/$ids?paymentMethod=$encMethod&txnId=$encTxn"
    }
    fun orderDetails(orderId: Long) = "order_details/$orderId"
}

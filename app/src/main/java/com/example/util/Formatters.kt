package com.example.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object Formatters {
    private val dateOnlyFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

    /**
     * Formats currency in Indian numbering system: ₹X,XX,XXX.XX
     */
    fun formatInr(amount: Double): String {
        if (amount.isNaN()) return "₹0.00"
        val isNegative = amount < 0
        val absAmount = Math.abs(amount)
        val wholePart = absAmount.toLong()
        val decimalPart = Math.round((absAmount - wholePart) * 100).toInt()
        val decStr = if (decimalPart < 10) "0$decimalPart" else "$decimalPart".take(2)

        val wholeStr = wholePart.toString()
        val formattedWhole = if (wholeStr.length <= 3) {
            wholeStr
        } else {
            val lastThree = wholeStr.substring(wholeStr.length - 3)
            val rest = wholeStr.substring(0, wholeStr.length - 3)
            val groupedRest = rest.reversed().chunked(2).joinToString(",").reversed()
            "$groupedRest,$lastThree"
        }

        return (if (isNegative) "-₹" else "₹") + "$formattedWhole.$decStr"
    }

    fun formatDate(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0) return "N/A"
        return dateOnlyFormat.format(Date(epochMillis))
    }

    fun formatDateTime(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0) return "N/A"
        return dateTimeFormat.format(Date(epochMillis))
    }

    fun daysUntil(epochMillis: Long?): Long {
        if (epochMillis == null || epochMillis <= 0) return Long.MAX_VALUE
        val now = System.currentTimeMillis()
        val diff = epochMillis - now
        return diff / (1000 * 60 * 60 * 24)
    }

    fun formatPercent(value: Double): String {
        val df = DecimalFormat("#,##0.0")
        return "${df.format(value)}%"
    }

    fun generateOrderNumber(): String {
        val randomPart = UUID.randomUUID().toString().substring(0, 6).uppercase()
        val year = SimpleDateFormat("yy", Locale.ENGLISH).format(Date())
        return "ORD-$year-$randomPart"
    }

    fun generateInvoiceNumber(): String {
        val randomPart = UUID.randomUUID().toString().substring(0, 6).uppercase()
        val year = SimpleDateFormat("yy", Locale.ENGLISH).format(Date())
        return "INV-$year-$randomPart"
    }

    fun generateCreditNoteNumber(): String {
        val randomPart = UUID.randomUUID().toString().substring(0, 6).uppercase()
        val year = SimpleDateFormat("yy", Locale.ENGLISH).format(Date())
        return "CN-$year-$randomPart"
    }
}

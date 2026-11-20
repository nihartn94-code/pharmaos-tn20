package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.domain.model.TaxInvoiceData
import java.io.File
import java.io.FileOutputStream

data class SavedPdfResult(
    val internalFile: File,
    val contentUri: Uri,
    val displayLocation: String
)

/**
 * Generates A4 formatted GST Tax Invoice PDFs using Android's native [PdfDocument] API,
 * persists them safely without requiring dangerous storage permissions, and provides
 * zero-permission FileProvider sharing intents.
 */
object TaxInvoicePdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f
    private const val CONTENT_RIGHT = PAGE_WIDTH - MARGIN

    fun sanitizeFileName(invoice: TaxInvoiceData): String {
        val rawId = invoice.invoiceNumber?.takeIf { it.isNotBlank() } ?: invoice.orderNumber
        val safeId = rawId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "TaxInvoice_${safeId}.pdf"
    }

    /**
     * Generates a valid PDF file inside app-private `filesDir/invoices/` for sharing or viewing.
     * If [backendPdfBytes] is supplied, writes the authoritative backend PDF stream directly.
     */
    fun generatePdfFile(
        context: Context,
        invoice: TaxInvoiceData,
        backendPdfBytes: ByteArray? = null
    ): File {
        require(invoice.isEligible) {
            invoice.eligibilityReason ?: "Order is not eligible for Tax Invoice generation."
        }
        val invoicesDir = File(context.filesDir, "invoices").apply { mkdirs() }
        val targetFile = File(invoicesDir, sanitizeFileName(invoice))

        if (backendPdfBytes != null && backendPdfBytes.isNotEmpty()) {
            FileOutputStream(targetFile).use { it.write(backendPdfBytes) }
            return targetFile
        }

        val document = PdfDocument()
        try {
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(29, 100, 236)
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(51, 65, 85)
                textSize = 9.2f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val boldBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 8.2f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val rightBodyPaint = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
            val rightBoldPaint = Paint(boldBodyPaint).apply { textAlign = Paint.Align.RIGHT }
            val linePaint = Paint().apply {
                color = Color.rgb(203, 213, 225)
                strokeWidth = 1f
            }
            val headerFillPaint = Paint().apply {
                color = Color.rgb(241, 245, 249)
                style = Paint.Style.FILL
            }
            val boxStrokePaint = Paint().apply {
                color = Color.rgb(203, 213, 225)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            var canvas = page.canvas
            var y = MARGIN

            fun drawFooter(pCanvas: android.graphics.Canvas, pageNum: Int) {
                pCanvas.drawLine(MARGIN, PAGE_HEIGHT - 42f, CONTENT_RIGHT, PAGE_HEIGHT - 42f, linePaint)
                val sourceLabel = if (invoice.isBackendAuthoritative) {
                    "Issued per PharmaOS Backend Billing Rules"
                } else {
                    "Generated from verified order records on PharmaOS"
                }
                pCanvas.drawText(sourceLabel, MARGIN, PAGE_HEIGHT - 26f, mutedPaint)
                val rightMuted = Paint(mutedPaint).apply { textAlign = Paint.Align.RIGHT }
                pCanvas.drawText("Page $pageNum", CONTENT_RIGHT, PAGE_HEIGHT - 26f, rightMuted)
            }

            fun startNextPage() {
                drawFooter(canvas, pageNumber)
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN
                canvas.drawText(
                    "TAX INVOICE (Continued) - Ref: ${invoice.invoiceNumber ?: invoice.orderNumber}",
                    MARGIN,
                    y + 12f,
                    sectionPaint
                )
                y += 24f
            }

            // Header Row
            canvas.drawText("TAX INVOICE", MARGIN, y + 16f, titlePaint)
            val rightBrand = Paint(brandPaint).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText("PharmaOS B2B", CONTENT_RIGHT, y + 16f, rightBrand)
            y += 26f

            val invNoDisplay = invoice.invoiceNumber ?: "Not Issued"
            val invDateDisplay = Formatters.formatDate(invoice.invoiceDateEpochMillis)
            val poDateDisplay = Formatters.formatDate(invoice.orderDateEpochMillis)

            canvas.drawText("Invoice No: $invNoDisplay", MARGIN, y + 10f, boldBodyPaint)
            canvas.drawText("Invoice Date: $invDateDisplay", CONTENT_RIGHT, y + 10f, rightBodyPaint)
            y += 14f
            canvas.drawText("Order / PO No: ${invoice.orderNumber}", MARGIN, y + 10f, bodyPaint)
            canvas.drawText("Order Date: $poDateDisplay", CONTENT_RIGHT, y + 10f, rightBodyPaint)
            y += 18f

            canvas.drawLine(MARGIN, y, CONTENT_RIGHT, y, linePaint)
            y += 12f

            // Seller & Buyer Boxes Side by Side
            val colGap = 14f
            val colWidth = (CONTENT_RIGHT - MARGIN - colGap) / 2f
            val sellerLeft = MARGIN
            val buyerLeft = MARGIN + colWidth + colGap
            val boxTop = y
            val partyBoxHeight = 96f

            canvas.drawRect(sellerLeft, boxTop, sellerLeft + colWidth, boxTop + partyBoxHeight, boxStrokePaint)
            canvas.drawRect(buyerLeft, boxTop, buyerLeft + colWidth, boxTop + partyBoxHeight, boxStrokePaint)

            var sy = boxTop + 14f
            canvas.drawText("SELLER / SUPPLIER", sellerLeft + 8f, sy, sectionPaint)
            sy += 14f
            canvas.drawText(
                ellipsize(invoice.seller.legalName ?: "Seller Unavailable", 36),
                sellerLeft + 8f,
                sy,
                boldBodyPaint
            )
            sy += 12f
            canvas.drawText(
                "GSTIN: ${invoice.seller.gstin ?: "Not Registered"}",
                sellerLeft + 8f,
                sy,
                bodyPaint
            )
            sy += 12f
            for (line in wrapText(invoice.seller.address ?: "Address not available", 38, 2)) {
                canvas.drawText(line, sellerLeft + 8f, sy, mutedPaint)
                sy += 11f
            }
            invoice.seller.phone?.let {
                canvas.drawText("Ph: ${ellipsize(it, 32)}", sellerLeft + 8f, sy, mutedPaint)
            }

            var by = boxTop + 14f
            canvas.drawText("BUYER / BILL TO", buyerLeft + 8f, by, sectionPaint)
            by += 14f
            canvas.drawText(
                ellipsize(invoice.buyer.legalName ?: "Buyer Unavailable", 36),
                buyerLeft + 8f,
                by,
                boldBodyPaint
            )
            by += 12f
            canvas.drawText(
                "GSTIN: ${invoice.buyer.gstin ?: "Not Registered"}",
                buyerLeft + 8f,
                by,
                bodyPaint
            )
            by += 12f
            val shipAddr = invoice.buyer.shippingAddress ?: invoice.buyer.address ?: "Address not available"
            for (line in wrapText("Ship To: $shipAddr", 38, 2)) {
                canvas.drawText(line, buyerLeft + 8f, by, mutedPaint)
                by += 11f
            }
            invoice.placeOfSupply?.let {
                canvas.drawText("Place of Supply: $it", buyerLeft + 8f, by, mutedPaint)
            }

            y = boxTop + partyBoxHeight + 12f

            // Logistics & Tax Regime Strip
            if (!invoice.ewayBillNumber.isNullOrBlank() || !invoice.transporterName.isNullOrBlank() || !invoice.gstTaxType.isNullOrBlank()) {
                val metaParts = buildList {
                    invoice.gstTaxType?.let { add("Tax Regime: $it") }
                    invoice.ewayBillNumber?.let { add("E-Way Bill: $it") }
                    invoice.transporterName?.let { add("Transporter: $it") }
                    invoice.vehicleNumber?.let { add("Vehicle: $it") }
                }.joinToString("  |  ")
                canvas.drawRect(MARGIN, y, CONTENT_RIGHT, y + 22f, headerFillPaint)
                canvas.drawText(ellipsize(metaParts, 90), MARGIN + 8f, y + 14f, bodyPaint)
                y += 30f
            }

            // Itemized Table Header
            fun drawTableHeader() {
                canvas.drawRect(MARGIN, y, CONTENT_RIGHT, y + 22f, headerFillPaint)
                canvas.drawLine(MARGIN, y, CONTENT_RIGHT, y, linePaint)
                canvas.drawLine(MARGIN, y + 22f, CONTENT_RIGHT, y + 22f, linePaint)
                val ty = y + 14f
                canvas.drawText("#", MARGIN + 4f, ty, sectionPaint)
                canvas.drawText("Description", MARGIN + 22f, ty, sectionPaint)
                canvas.drawText("HSN", MARGIN + 205f, ty, sectionPaint)
                canvas.drawText("Qty", MARGIN + 275f, ty, rightBoldPaint)
                canvas.drawText("Rate", MARGIN + 332f, ty, rightBoldPaint)
                canvas.drawText("Taxable", MARGIN + 395f, ty, rightBoldPaint)
                canvas.drawText("GST%", MARGIN + 442f, ty, rightBoldPaint)
                canvas.drawText("Total", CONTENT_RIGHT - 4f, ty, rightBoldPaint)
                y += 26f
            }

            drawTableHeader()

            invoice.items.forEachIndexed { idx, item ->
                if (y > PAGE_HEIGHT - 165f) {
                    startNextPage()
                    drawTableHeader()
                }
                val rowStartY = y + 10f
                val desc = buildString {
                    append(item.productName)
                    if (item.brand.isNotBlank()) append(" (${item.brand})")
                }
                canvas.drawText("${idx + 1}", MARGIN + 4f, rowStartY, bodyPaint)
                canvas.drawText(ellipsize(desc, 33), MARGIN + 22f, rowStartY, boldBodyPaint)
                canvas.drawText(item.hsnCode.ifBlank { "-" }, MARGIN + 205f, rowStartY, bodyPaint)
                val qtyStr = if (item.freeQuantity > 0) "${item.quantity}+${item.freeQuantity}" else "${item.quantity}"
                canvas.drawText(qtyStr, MARGIN + 275f, rowStartY, rightBodyPaint)
                canvas.drawText(Formatters.formatInr(item.effectiveUnitPrice), MARGIN + 332f, rowStartY, rightBodyPaint)
                canvas.drawText(Formatters.formatInr(item.taxableValue), MARGIN + 395f, rowStartY, rightBodyPaint)
                canvas.drawText("${item.gstRatePercent}%", MARGIN + 442f, rowStartY, rightBodyPaint)
                canvas.drawText(Formatters.formatInr(item.lineTotal), CONTENT_RIGHT - 4f, rowStartY, rightBoldPaint)

                val subLine = buildList {
                    if (item.packSize.isNotBlank()) add("Pack: ${item.packSize}")
                    if (!item.batchNumber.isNullOrBlank()) add("Batch: ${item.batchNumber}")
                    item.gstAmount?.let { add("GST: ${Formatters.formatInr(it)}") }
                }.joinToString(" • ")
                if (subLine.isNotBlank()) {
                    canvas.drawText(ellipsize(subLine, 60), MARGIN + 22f, rowStartY + 11f, mutedPaint)
                    y += 26f
                } else {
                    y += 18f
                }
                canvas.drawLine(MARGIN, y, CONTENT_RIGHT, y, linePaint)
                y += 4f
            }

            if (y > PAGE_HEIGHT - 160f) {
                startNextPage()
            }

            y += 10f
            // Summary Totals Box (Right aligned)
            val summaryLeft = MARGIN + 260f
            canvas.drawRect(summaryLeft, y, CONTENT_RIGHT, y + 104f, boxStrokePaint)
            var syBox = y + 16f

            fun drawSummaryRow(label: String, value: String, bold: Boolean = false) {
                val lp = if (bold) boldBodyPaint else bodyPaint
                val rp = if (bold) rightBoldPaint else rightBodyPaint
                canvas.drawText(label, summaryLeft + 10f, syBox, lp)
                canvas.drawText(value, CONTENT_RIGHT - 10f, syBox, rp)
                syBox += 15f
            }

            drawSummaryRow("Taxable Subtotal", Formatters.formatInr(invoice.subtotalTaxable))
            if (invoice.cgstTotal != null && invoice.sgstTotal != null) {
                drawSummaryRow("CGST Total", Formatters.formatInr(invoice.cgstTotal))
                drawSummaryRow("SGST Total", Formatters.formatInr(invoice.sgstTotal))
            } else if (invoice.igstTotal != null) {
                drawSummaryRow("IGST Total", Formatters.formatInr(invoice.igstTotal))
            } else {
                val gstLabel = invoice.totalGstAmount?.let { Formatters.formatInr(it) } ?: "Unavailable"
                drawSummaryRow("Total GST", gstLabel)
            }
            drawSummaryRow(
                "Delivery Charges",
                if (invoice.deliveryCharge > 0.0) Formatters.formatInr(invoice.deliveryCharge) else "FREE"
            )
            canvas.drawLine(summaryLeft, syBox - 8f, CONTENT_RIGHT, syBox - 8f, linePaint)
            syBox += 4f
            drawSummaryRow("Grand Total (INR)", Formatters.formatInr(invoice.grandTotal), bold = true)

            if (invoice.missingFields.isNotEmpty()) {
                var my = y + 14f
                canvas.drawText("Missing Billing Metadata:", MARGIN, my, sectionPaint)
                my += 12f
                for (field in invoice.missingFields.take(4)) {
                    canvas.drawText("• ${ellipsize(field, 42)}", MARGIN, my, mutedPaint)
                    my += 11f
                }
            }

            drawFooter(canvas, pageNumber)
            document.finishPage(page)

            FileOutputStream(targetFile).use { output ->
                document.writeTo(output)
            }
        } catch (e: Exception) {
            if (e is IllegalStateException || e is UnsatisfiedLinkError) {
                writeStandardPdfFallback(targetFile, invoice)
            } else {
                throw e
            }
        } finally {
            runCatching { document.close() }
        }

        return targetFile
    }

    /**
     * Pure-Kotlin PDF 1.4 writer used as a resilient fallback when Android's native Skia
     * [PdfDocument] is unavailable (such as headless JVM test environments).
     */
    private fun writeStandardPdfFallback(targetFile: File, invoice: TaxInvoiceData) {
        val lines = buildList {
            add("TAX INVOICE - PharmaOS B2B")
            add("Invoice No: ${invoice.invoiceNumber ?: "Not Issued"}   |   Order No: ${invoice.orderNumber}")
            add("Invoice Date: ${Formatters.formatDate(invoice.invoiceDateEpochMillis)}   |   Order Date: ${Formatters.formatDate(invoice.orderDateEpochMillis)}")
            add("Seller: ${invoice.seller.legalName ?: "Unavailable"} (GSTIN: ${invoice.seller.gstin ?: "Not Registered"})")
            add("Buyer: ${invoice.buyer.legalName ?: "Unavailable"} (GSTIN: ${invoice.buyer.gstin ?: "Not Registered"})")
            invoice.gstTaxType?.let { add("Tax Regime: $it   |   Place of Supply: ${invoice.placeOfSupply ?: "-"}") }
            add("----------------------------------------------------------------------")
            invoice.items.forEachIndexed { idx, item ->
                add(
                    "${idx + 1}. ${item.productName} (${item.brand}) | HSN: ${item.hsnCode.ifBlank { "-" }} | " +
                        "Qty: ${item.quantity} | Taxable: ${Formatters.formatInr(item.taxableValue)} | " +
                        "GST ${item.gstRatePercent}% | Total: ${Formatters.formatInr(item.lineTotal)}"
                )
            }
            add("----------------------------------------------------------------------")
            add("Taxable Subtotal: ${Formatters.formatInr(invoice.subtotalTaxable)}")
            if (invoice.cgstTotal != null && invoice.sgstTotal != null) {
                add("CGST: ${Formatters.formatInr(invoice.cgstTotal)}   |   SGST: ${Formatters.formatInr(invoice.sgstTotal)}")
            } else if (invoice.igstTotal != null) {
                add("IGST: ${Formatters.formatInr(invoice.igstTotal)}")
            } else {
                add("Total GST: ${invoice.totalGstAmount?.let { Formatters.formatInr(it) } ?: "Unavailable"}")
            }
            add("Delivery Charges: ${if (invoice.deliveryCharge > 0.0) Formatters.formatInr(invoice.deliveryCharge) else "FREE"}")
            add("Grand Total (INR): ${Formatters.formatInr(invoice.grandTotal)}")
        }

        fun escapePdfText(raw: String): String {
            val ascii = raw.replace("₹", "INR ").replace("•", "-")
            return buildString(ascii.length) {
                for (ch in ascii) {
                    when {
                        ch == '\\' || ch == '(' || ch == ')' -> append('\\').append(ch)
                        ch.code in 32..126 -> append(ch)
                        else -> append(' ')
                    }
                }
            }
        }

        val contentStream = buildString {
            append("BT\n/F1 10 Tf\n36 800 Td\n15 TL\n")
            lines.forEachIndexed { index, line ->
                val escaped = escapePdfText(line)
                if (index == 0) {
                    append("($escaped) Tj\n")
                } else {
                    append("T* ($escaped) Tj\n")
                }
            }
            append("ET\n")
        }
        val streamBytes = contentStream.toByteArray(Charsets.US_ASCII)

        val objects = listOf(
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n",
            "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n",
            "5 0 obj\n<< /Length ${streamBytes.size} >>\nstream\n$contentStream\nendstream\nendobj\n"
        )

        val out = StringBuilder()
        out.append("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        for (obj in objects) {
            offsets.add(out.length)
            out.append(obj)
        }
        val xrefStart = out.length
        out.append("xref\n0 ${objects.size + 1}\n")
        out.append("0000000000 65535 f \n")
        for (offset in offsets) {
            out.append(String.format(java.util.Locale.US, "%010d 00000 n \n", offset))
        }
        out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
        out.append("startxref\n$xrefStart\n%%EOF\n")

        FileOutputStream(targetFile).use { fos ->
            fos.write(out.toString().toByteArray(Charsets.US_ASCII))
        }
    }

    /**
     * Saves the Tax Invoice PDF to the public Downloads collection via [MediaStore] on Android 10+
     * (zero storage permissions required), or app-scoped External Downloads directory on older versions.
     */
    fun saveToDownloads(
        context: Context,
        invoice: TaxInvoiceData,
        backendPdfBytes: ByteArray? = null
    ): SavedPdfResult {
        val internalFile = generatePdfFile(context, invoice, backendPdfBytes)
        val fileName = internalFile.name

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/PharmaOS")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    val outStream = resolver.openOutputStream(uri)
                    if (outStream != null) {
                        outStream.use { out ->
                            internalFile.inputStream().use { input -> input.copyTo(out) }
                        }
                        val doneValues = ContentValues().apply {
                            put(MediaStore.Downloads.IS_PENDING, 0)
                        }
                        resolver.update(uri, doneValues, null, null)
                        return SavedPdfResult(
                            internalFile = internalFile,
                            contentUri = uri,
                            displayLocation = "Downloads/PharmaOS/$fileName"
                        )
                    }
                }
            } catch (_: Exception) {
                // Fall back to app-scoped external downloads directory below
            }
        }

        // Fallback for API < 29 or Robolectric if MediaStore insert isn't backed
        val extDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.apply { mkdirs() }
        val exportFile = if (extDownloads != null) {
            val dest = File(extDownloads, fileName)
            internalFile.copyTo(dest, overwrite = true)
            dest
        } else {
            internalFile
        }
        val providerUri = getShareUri(context, internalFile)
        return SavedPdfResult(
            internalFile = exportFile,
            contentUri = providerUri,
            displayLocation = exportFile.absolutePath
        )
    }

    fun getShareUri(context: Context, pdfFile: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
    }

    fun createShareIntent(
        context: Context,
        pdfFile: File,
        invoice: TaxInvoiceData
    ): Intent {
        val uri = getShareUri(context, pdfFile)
        val subject = "Tax Invoice ${invoice.invoiceNumber ?: invoice.orderNumber} (${invoice.seller.legalName ?: "PharmaOS"})"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(
                Intent.EXTRA_TEXT,
                "Tax Invoice for Order ${invoice.orderNumber} - Total: ${Formatters.formatInr(invoice.grandTotal)}"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "Share Tax Invoice PDF")
    }

    private fun ellipsize(text: String, maxChars: Int): String {
        return if (text.length <= maxChars) text else text.take(maxChars - 1) + "…"
    }

    private fun wrapText(text: String, maxCharsPerLine: Int, maxLines: Int): List<String> {
        val words = text.trim().split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.isEmpty()) {
                current.append(word)
            } else if (current.length + 1 + word.length <= maxCharsPerLine) {
                current.append(' ').append(word)
            } else {
                lines.add(current.toString())
                if (lines.size >= maxLines) break
                current = StringBuilder(word)
            }
        }
        if (lines.size < maxLines && current.isNotEmpty()) {
            lines.add(current.toString())
        }
        return lines.take(maxLines).mapIndexed { index, line ->
            if (index == maxLines - 1 && line.length > maxCharsPerLine) ellipsize(line, maxCharsPerLine) else line
        }
    }
}

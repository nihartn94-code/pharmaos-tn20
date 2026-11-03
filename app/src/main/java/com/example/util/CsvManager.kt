package com.example.util

import com.example.domain.model.DosageForm
import com.example.domain.model.StorageCondition

object CsvManager {

    data class ProductCsvRow(
        val name: String,
        val brand: String,
        val saltComposition: String,
        val strength: String,
        val dosageForm: DosageForm,
        val therapeuticCategory: String,
        val manufacturerName: String,
        val packSize: String,
        val hsnCode: String,
        val gstRatePercent: Double,
        val storageCondition: StorageCondition,
        val isRx: Boolean
    )

    data class OfferCsvRow(
        val productName: String,
        val unitPricePtr: Double,
        val mrp: Double,
        val moq: Int,
        val availableQuantity: Int
    )

    data class CsvParseResult<T>(
        val successfulRows: List<T>,
        val errorLines: List<String>
    )

    fun parseProductsCsv(csvContent: String): CsvParseResult<ProductCsvRow> {
        val lines = csvContent.lines().filter { it.isNotBlank() }
        val success = mutableListOf<ProductCsvRow>()
        val errors = mutableListOf<String>()

        if (lines.isEmpty()) {
            return CsvParseResult(emptyList(), listOf("File is empty"))
        }

        val header = lines.first().split(",").map { it.trim().lowercase() }
        val requiredHeaders = listOf("name", "brand", "salt", "strength", "form", "category", "manufacturer", "packsize", "hsn", "gst", "storage", "rx")
        val missing = requiredHeaders.filter { req -> header.none { it.contains(req) } }
        if (missing.isNotEmpty()) {
            errors.add("Missing required columns: ${missing.joinToString(", ")}")
        }

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) continue
            val cols = parseCsvLine(line)
            if (cols.size < 12) {
                errors.add("Row $i: Expected 12 columns, got ${cols.size} (Data: $line)")
                continue
            }
            try {
                val name = cols[0].trim()
                val brand = cols[1].trim()
                val salt = cols[2].trim()
                val strength = cols[3].trim()
                val formStr = cols[4].trim().uppercase()
                val category = cols[5].trim()
                val manufacturer = cols[6].trim()
                val packSize = cols[7].trim()
                val hsn = cols[8].trim()
                val gst = cols[9].trim().toDoubleOrNull() ?: 12.0
                val storageStr = cols[10].trim().uppercase()
                val rx = cols[11].trim().equals("true", ignoreCase = true) || cols[11].trim() == "1"

                val dosageForm = try {
                    DosageForm.valueOf(formStr)
                } catch (_: Exception) {
                    DosageForm.values().find { it.displayName.equals(formStr, ignoreCase = true) } ?: DosageForm.TABLET
                }

                val storage = try {
                    StorageCondition.valueOf(storageStr)
                } catch (_: Exception) {
                    StorageCondition.ROOM_TEMPERATURE
                }

                if (name.isBlank()) {
                    errors.add("Row $i: Product name cannot be blank")
                    continue
                }

                success.add(
                    ProductCsvRow(
                        name = name,
                        brand = brand,
                        saltComposition = salt,
                        strength = strength,
                        dosageForm = dosageForm,
                        therapeuticCategory = category,
                        manufacturerName = manufacturer,
                        packSize = packSize,
                        hsnCode = hsn,
                        gstRatePercent = gst,
                        storageCondition = storage,
                        isRx = rx
                    )
                )
            } catch (e: Exception) {
                errors.add("Row $i: Failed to parse (${e.message ?: "Invalid data"})")
            }
        }
        return CsvParseResult(success, errors)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '\"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString().trim())
        return result
    }

    fun exportProductsToCsv(products: List<ProductCsvRow>): String {
        val sb = StringBuilder()
        sb.append("Name,Brand,SaltComposition,Strength,DosageForm,Category,Manufacturer,PackSize,HSN,GSTRate,Storage,Rx\n")
        for (p in products) {
            sb.append("\"${p.name}\",\"${p.brand}\",\"${p.saltComposition}\",\"${p.strength}\",\"${p.dosageForm.name}\",\"${p.therapeuticCategory}\",\"${p.manufacturerName}\",\"${p.packSize}\",\"${p.hsnCode}\",${p.gstRatePercent},\"${p.storageCondition.name}\",${p.isRx}\n")
        }
        return sb.toString()
    }
}

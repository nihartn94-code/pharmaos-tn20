package com.example.domain.usecase

import com.example.data.local.entity.ProductEntity
import com.example.domain.model.ParsedOcrItem
import java.util.Locale
import java.util.regex.Pattern

class ParsePurchaseListUseCase {

    private val patternWithQtyAtEnd = Pattern.compile(
        """^(.*?)(?:\s+(\d+\s*(?:mg|ml|mcg|gm|g|iu|%)))?(?:\s*(?:[-–xX*]|qty|strips|boxes|packs|tab|tabs|units|bottles|vials)?\s*[:=]?\s*(\d+))\s*(?:strips|boxes|packs|tab|tabs|units|bottles|vials)?$""",
        Pattern.CASE_INSENSITIVE
    )

    private val patternWithStrengthInName = Pattern.compile(
        """^(.*?)\s*[-–xX*]\s*(\d+)\s*(?:strips|boxes|packs|tab|tabs|units|bottles|vials)?$""",
        Pattern.CASE_INSENSITIVE
    )

    fun parse(rawOcrText: String, catalogueProducts: List<ProductEntity>): List<ParsedOcrItem> {
        val lines = rawOcrText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length >= 3 }

        val parsedItems = mutableListOf<ParsedOcrItem>()

        for (line in lines) {
            val item = parseLine(line)
            matchWithCatalogue(item, catalogueProducts)
            parsedItems.add(item)
        }

        return parsedItems
    }

    fun parseLine(line: String): ParsedOcrItem {
        val trimmed = line.trim()
        val matcher1 = patternWithQtyAtEnd.matcher(trimmed)
        if (matcher1.matches()) {
            val rawName = matcher1.group(1)?.trim() ?: trimmed
            val strength = matcher1.group(2)?.trim() ?: extractStrengthFromText(rawName)
            val qtyStr = matcher1.group(3)?.trim() ?: "1"
            val qty = qtyStr.toIntOrNull() ?: 1
            val cleanName = cleanMedicineName(rawName)
            return ParsedOcrItem(
                rawText = trimmed,
                medicineName = cleanName,
                strength = strength,
                quantity = Math.max(1, qty)
            )
        }

        val matcher2 = patternWithStrengthInName.matcher(trimmed)
        if (matcher2.matches()) {
            val rawName = matcher2.group(1)?.trim() ?: trimmed
            val qty = matcher2.group(2)?.toIntOrNull() ?: 1
            val strength = extractStrengthFromText(rawName)
            val cleanName = cleanMedicineName(rawName)
            return ParsedOcrItem(
                rawText = trimmed,
                medicineName = cleanName,
                strength = strength,
                quantity = Math.max(1, qty)
            )
        }

        // Fallback: search for last standalone number as quantity
        val tokens = trimmed.split("\\s+".toRegex())
        val lastToken = tokens.lastOrNull()
        val lastNumber = lastToken?.toIntOrNull()
        if (lastNumber != null && tokens.size > 1) {
            val nameTokens = tokens.dropLast(1).joinToString(" ")
            val strength = extractStrengthFromText(nameTokens)
            return ParsedOcrItem(
                rawText = trimmed,
                medicineName = cleanMedicineName(nameTokens),
                strength = strength,
                quantity = Math.max(1, lastNumber)
            )
        }

        return ParsedOcrItem(
            rawText = trimmed,
            medicineName = cleanMedicineName(trimmed),
            strength = extractStrengthFromText(trimmed),
            quantity = 1
        )
    }

    private fun extractStrengthFromText(text: String): String {
        val pattern = Pattern.compile("""(\d+\s*(?:mg|ml|mcg|gm|g|iu|%))""", Pattern.CASE_INSENSITIVE)
        val m = pattern.matcher(text)
        if (m.find()) return m.group(1) ?: ""
        val numPattern = Pattern.compile("""\b(\d{2,4})\b""")
        val mNum = numPattern.matcher(text)
        return if (mNum.find()) mNum.group(1) ?: "" else ""
    }

    private fun cleanMedicineName(name: String): String {
        return name
            .replace("""(?i)\b(\d+\s*(?:mg|ml|mcg|gm|g|iu|%))\b""".toRegex(), "")
            .replace("""\b\d{2,4}\b""".toRegex(), "")
            .replace("""[^\w\s-]""".toRegex(), " ")
            .replace("""\s+""".toRegex(), " ")
            .trim()
    }

    fun matchWithCatalogue(item: ParsedOcrItem, catalogueProducts: List<ProductEntity>) {
        if (catalogueProducts.isEmpty()) {
            item.matchedProductId = null
            item.matchedProductName = null
            item.matchConfidence = 0.0
            return
        }

        var bestMatch: ProductEntity? = null
        var highestScore = 0.0

        val queryName = item.medicineName.lowercase(Locale.ENGLISH)

        for (product in catalogueProducts) {
            val pName = product.name.lowercase(Locale.ENGLISH)
            val pBrand = product.brand.lowercase(Locale.ENGLISH)
            val pSalt = product.saltComposition.lowercase(Locale.ENGLISH)

            // Direct exact match
            if (pName == queryName || pBrand == queryName) {
                bestMatch = product
                highestScore = 1.0
                break
            }

            // Token overlap & Levenshtein score
            val nameScore = computeSimilarity(queryName, pName)
            val brandScore = computeSimilarity(queryName, pBrand)
            val saltScore = computeSimilarity(queryName, pSalt)

            val maxScore = Math.max(nameScore, Math.max(brandScore, saltScore))

            // Bonus if strength matches
            var totalScore = maxScore
            if (item.strength.isNotBlank() && product.strength.contains(item.strength, ignoreCase = true)) {
                totalScore = Math.min(1.0, totalScore + 0.15)
            }

            if (totalScore > highestScore) {
                highestScore = totalScore
                bestMatch = product
            }
        }

        if (bestMatch != null && highestScore >= 0.40) {
            item.matchedProductId = bestMatch.id
            item.matchedProductName = "${bestMatch.name} (${bestMatch.brand})"
            item.matchConfidence = highestScore
            item.isConfirmed = highestScore >= 0.75
        } else {
            item.matchedProductId = null
            item.matchedProductName = null
            item.matchConfidence = 0.0
            item.isConfirmed = false
        }
    }

    fun computeSimilarity(s1: String, s2: String): Double {
        if (s1.isBlank() || s2.isBlank()) return 0.0
        if (s1 == s2) return 1.0

        // Jaccard similarity on character bigrams or words
        val tokens1 = s1.split("\\s+".toRegex()).toSet()
        val tokens2 = s2.split("\\s+".toRegex()).toSet()
        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        val jaccard = if (union > 0) intersection.toDouble() / union.toDouble() else 0.0

        // Levenshtein ratio
        val dist = levenshteinDistance(s1, s2)
        val maxLen = Math.max(s1.length, s2.length)
        val levRatio = 1.0 - (dist.toDouble() / maxLen.toDouble())

        return (jaccard * 0.6) + (levRatio * 0.4)
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        var cost = IntArray(lhs.length + 1) { it }
        var newCost = IntArray(lhs.length + 1) { 0 }

        for (i in 1..rhs.length) {
            newCost[0] = i
            for (j in 1..lhs.length) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = Math.min(Math.min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhs.length]
    }
}

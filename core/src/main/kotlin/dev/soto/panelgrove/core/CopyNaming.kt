package dev.soto.panelgrove.core

import java.security.MessageDigest
import java.text.Normalizer

object CopyNaming {
    fun inferredSeries(filename: String): String = filename.substringBeforeLast('.')
        .replace(Regex("(?i)\\s*[-–—]?\\s*(vol(?:ume)?\\.?|ch(?:apter)?\\.?)\\s*\\d+.*$"), "").trim()
        .takeUnless { it.isBlank() || it.all { c -> c.isDigit() || c in " ._-" } }.orEmpty()

    fun seriesFolder(series: String): String = safeName(series.ifBlank { "Unsorted" }, 100)
    fun filename(name: String): String {
        val extension = name.substringAfterLast('.', "cbz").lowercase().filter { it.isLetterOrDigit() }.take(8).ifEmpty { "cbz" }
        return "${safeName(name.substringBeforeLast('.').ifBlank { "Book" }, 140)}.$extension"
    }
    private fun safeName(value: String, limit: Int): String {
        val normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC)
        val cleaned = normalized.replace(Regex("[\\p{Cntrl}\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ").trim().trim('.', ' ')
        var bytes = 0
        val kept = mutableListOf<Int>()
        for (point in cleaned.codePoints().limit(limit.toLong()).toArray()) {
            val size = String(Character.toChars(point)).toByteArray(Charsets.UTF_8).size
            if (bytes + size > 180) break
            bytes += size; kept += point
        }
        val points = kept.toIntArray()
        val shortened = String(points, 0, points.size).ifBlank { "Unsorted" }
        if (shortened == normalized && shortened != "." && shortened != "..") return shortened
        val suffix = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8)).take(4).joinToString("") { "%02x".format(it) }
        return "$shortened-$suffix"
    }
}

package dev.soto.panelgrove.core

object ArchiveRules {
    const val MAX_ENTRIES = 20_000
    const val MAX_PAGE_BYTES = 128L * 1024 * 1024
    const val MAX_METADATA_BYTES = 4L * 1024 * 1024
    const val MAX_TOTAL_EXPANDED = 8L * 1024 * 1024 * 1024
    private val images = setOf("png", "jpg", "jpeg", "webp")
    /** Comic filename extensions are frequently wrong; decode the actual container. */
    fun detectFormat(header: ByteArray): BookFormat? {
        fun starts(vararg bytes: Int) = header.size >= bytes.size && bytes.indices.all { (header[it].toInt() and 255) == bytes[it] }
        return when {
            starts(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x00) || starts(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x01, 0x00) -> BookFormat.RAR
            starts(0x50, 0x4b, 0x03, 0x04) || starts(0x50, 0x4b, 0x05, 0x06) || starts(0x50, 0x4b, 0x07, 0x08) -> BookFormat.ZIP
            starts(0x25, 0x50, 0x44, 0x46, 0x2d) -> BookFormat.PDF
            else -> null
        }
    }
    fun safePath(path: String): String {
        val normalized = path.replace('\\', '/')
        require(normalized.isNotBlank() && normalized.length <= 2048 && !normalized.startsWith('/') &&
            '\u0000' !in normalized && ':' !in normalized && normalized.split('/').none { it == ".." }) {
            "Archive contains an unsafe page path."
        }
        return normalized
    }
    fun isImage(path: String): Boolean {
        val parts = path.replace('\\', '/').split('/')
        return parts.none { it.startsWith('.') || it == "__MACOSX" } &&
            parts.last().substringAfterLast('.', "").lowercase() in images
    }
    fun format(name: String): BookFormat? = when (name.substringAfterLast('.', "").lowercase()) {
        "cbz", "zip" -> BookFormat.ZIP
        "cbr", "rar" -> BookFormat.RAR
        "pdf" -> BookFormat.PDF
        else -> null
    }
}

/** Numeric chunks are compared by magnitude without parsing into a fixed-size integer. */
object NaturalOrder : Comparator<String> {
    private val chunks = Regex("[0-9]+|[^0-9]+")
    override fun compare(a: String, b: String): Int {
        val left = chunks.findAll(a).map { it.value }.toList()
        val right = chunks.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(left.size, right.size)) {
            val x = left[i]; val y = right[i]
            val comparison = if (x.first().isDigit() && y.first().isDigit()) {
                val nx = x.trimStart('0').ifEmpty { "0" }; val ny = y.trimStart('0').ifEmpty { "0" }
                nx.length.compareTo(ny.length).takeIf { it != 0 } ?: nx.compareTo(ny)
            } else x.compareTo(y, ignoreCase = true)
            if (comparison != 0) return comparison
        }
        return left.size.compareTo(right.size).takeIf { it != 0 } ?: a.compareTo(b)
    }
}

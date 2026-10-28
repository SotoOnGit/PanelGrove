package dev.soto.panelgrove.core

import java.util.Locale

/** Compare language tags consistently, including metadata using ISO 639-2 codes. */
fun normalizeLanguage(value: String): String {
    val tag = value.trim().replace('_', '-').lowercase(Locale.ROOT)
    if (tag.isEmpty()) return ""
    val parts = tag.split('-', limit = 2)
    val language = isoLanguages[parts[0]] ?: parts[0]
    return language + if (parts.size == 2) "-${parts[1]}" else ""
}

private val isoLanguages by lazy {
    Locale.getISOLanguages().mapNotNull { code ->
        runCatching { Locale.forLanguageTag(code).isO3Language to code }.getOrNull()
    }.toMap() + mapOf("fre" to "fr", "ger" to "de", "chi" to "zh", "dut" to "nl", "rum" to "ro")
}

val Book.editionLanguage: String get() = normalizeLanguage(language ?: metadata.language.orEmpty())

data class EditionGroup(val key: String, val editions: List<Book>) {
    /** Stable on an unopened library; thereafter the last edition opened supplies the cover. */
    val representative: Book get() = editions.minWith(
        compareByDescending<Book> { it.lastOpenedAt }.thenBy { it.addedAt }.thenBy { it.id })
}

/** Editions share organization only. Keys do not depend on the representative cover. */
fun groupLanguageEditions(books: List<Book>): List<EditionGroup> {
    fun kind(book: Book) = book.metadata.kind.orEmpty().trim().lowercase(Locale.ROOT)
    val groups = mutableListOf<EditionGroup>()
    books.groupBy { it.series to it.number.trim().lowercase(Locale.ROOT) }.forEach { (identity, candidates) ->
        val (series, number) = identity
        if (series.isBlank() || number.isBlank()) {
            groups += candidates.map { EditionGroup("book:${it.id}", listOf(it)) }
        } else {
            val knownKinds = candidates.map(::kind).filter { it.isNotBlank() }.distinct()
            // Untyped PDFs/folders can join one known sequence, but never bridge a volume and chapter.
            candidates.groupBy { kind(it).ifBlank { knownKinds.singleOrNull().orEmpty() } }.forEach { (kind, editions) ->
                val key = listOf(series, number, kind).joinToString(":") { "${it.length}:$it" }
                groups += EditionGroup("editions:$key", editions)
            }
        }
    }
    return groups
}

fun languageEditions(current: Book, books: List<Book>): List<Book> =
    groupLanguageEditions(if (books.any { it.id == current.id }) books else books + current)
        .first { group -> group.editions.any { it.id == current.id } }.editions
        .sortedWith(compareBy<Book> { it.editionLanguage }.thenBy { it.title }.thenBy { it.id })

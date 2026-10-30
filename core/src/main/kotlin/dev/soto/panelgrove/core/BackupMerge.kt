package dev.soto.panelgrove.core

import java.util.UUID

/** Incoming organization wins for matching documents. Unrelated live IDs are never overwritten.
 * Restore keys survive relinking, so retrying an older backup does not recreate relocated entries. */
fun mergeBackup(incoming: LibraryBackup, live: LibraryBackup, identity: (String) -> String = { it }): LibraryBackup {
    incoming.validate()
    fun key(kind: String, id: String, location: String) = UUID.nameUUIDFromBytes("$kind:$id:${identity(location)}".toByteArray(Charsets.UTF_8)).toString()
    fun freeId(preferred: String, seed: String, used: Set<String>): String {
        if (preferred !in used) return preferred
        var candidate = seed; var attempt = 0
        while (candidate in used) candidate = key("collision", seed, (++attempt).toString())
        return candidate
    }
    val roots = live.sources.associateBy { it.id }.toMutableMap()
    val sourceIds = mutableMapOf<String, String>()
    incoming.sources.forEach { source ->
        val token = source.restoredKey ?: key("source", source.id, source.uri)
        val match = roots.values.find { identity(it.uri) == identity(source.uri) || it.restoredKey == token }
        val id = match?.id ?: freeId(source.id, token, roots.keys)
        sourceIds[source.id] = id
        roots[id] = source.copy(id = id, uri = match?.uri ?: source.uri, restoredKey = token)
    }
    val books = live.books.associateBy { it.id }.toMutableMap()
    val byLocation = mutableMapOf<String, MutableSet<String>>()
    val byRestore = mutableMapOf<String, String>()
    val byUri = mutableMapOf<String, String>()
    fun register(book: Book) {
        listOfNotNull(book.uri, book.originUri).forEach { byLocation.getOrPut(identity(it)) { mutableSetOf() }.add(book.id) }
        book.restoredKey?.let { byRestore[it] = book.id }
        byUri[book.uri] = book.id
    }
    live.books.forEach(::register)
    val bookIds = mutableMapOf<String, String>()
    val editionIds = mutableMapOf<String, String>()
    incoming.books.forEach { book ->
        val token = book.restoredKey ?: key("book", book.id, book.uri)
        val locations = listOfNotNull(book.uri, book.originUri).map(identity).toSet()
        val match = byRestore[token]?.let(books::get) ?: locations.asSequence().flatMap { byLocation[it].orEmpty().asSequence() }
            .mapNotNull(books::get).firstOrNull { candidate ->
                val sameOriginal = candidate.originUri != null && book.originUri != null && identity(candidate.originUri) == identity(book.originUri)
                sameOriginal || !candidate.managed || !book.managed || candidate.contentHash == book.contentHash
            }
        val id = match?.id ?: freeId(book.id, token, books.keys)
        bookIds[book.id] = id
        editionIds[book.statisticsId] = match?.statisticsId ?: book.statisticsId
        // A different managed file at the same private filename must never be mistaken for the backup's bytes.
        val uri = match?.uri ?: if (book.uri in byUri) {
            require(book.managed) { "Conflicting source locations in backup." }
            "panelgrove://managed/restore-$token.${book.displayName.substringAfterLast('.', "cbz").filter { it.isLetterOrDigit() }.take(8)}"
        } else book.uri
        books[id] = book.copy(id = id, uri = uri, managed = match?.managed ?: book.managed,
            originUri = match?.originUri ?: book.originUri, sourceId = match?.sourceId ?: book.sourceId?.let(sourceIds::getValue),
            relativePath = match?.relativePath ?: book.relativePath, restoredKey = token,
            // Retain the live cache revision only when it describes the same live bytes.
            revision = match?.revision ?: book.revision, providerRevision = match?.providerRevision ?: book.providerRevision,
            contentHash = match?.contentHash ?: book.contentHash)
            .let { merged ->
                // A restored filename collision is an unavailable private placeholder, never ownership of the live file.
                merged.copy(copyRootUri = if (uri.startsWith("panelgrove:")) null else if (match != null) match.copyRootUri else book.copyRootUri,
                    copyRevision = if (match != null) match.copyRevision else book.copyRevision,
                    // Cover files are local to this installation and are not part of JSON backups.
                    customCover = match?.customCover, statisticsId = match?.statisticsId ?: book.statisticsId)
            }
        register(books.getValue(id))
    }
    val marks = live.bookmarks.associateBy { it.id }.toMutableMap()
    val byPage = live.bookmarks.associate { (it.bookId to it.pageId) to it.id }.toMutableMap()
    incoming.bookmarks.forEach { mark ->
        val bookId = bookIds.getValue(mark.bookId)
        val same = byPage[bookId to mark.pageId]?.let(marks::get)
        val id = same?.id ?: freeId(mark.id, key("bookmark", mark.id, "$bookId:${mark.pageId}"), marks.keys)
        marks[id] = mark.copy(id = id, bookId = bookId)
        byPage[bookId to mark.pageId] = id
    }
    val history = live.history.associateBy { it.id }.toMutableMap()
    val byVisit = live.history.associate { (it.bookId to it.openedAt) to it.id }.toMutableMap()
    incoming.history.forEach { entry ->
        val bookId = bookIds.getValue(entry.bookId)
        val same = byVisit[bookId to entry.openedAt]?.let(history::get)
        val id = same?.id ?: freeId(entry.id, key("history", entry.id, "$bookId:${entry.openedAt}"), history.keys)
        history[id] = entry.copy(id = id, bookId = bookId)
        byVisit[bookId to entry.openedAt] = id
    }
    return incoming.copy(version = 2, sources = roots.values.toList(), books = books.values.toList(), bookmarks = marks.values.toList(),
        history = history.values.sortedByDescending { it.openedAt }.take(500),
        statistics = mergeStatistics(incoming.statistics, live.statistics, bookIds, editionIds))
}

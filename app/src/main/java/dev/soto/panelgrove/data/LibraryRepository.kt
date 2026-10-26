package dev.soto.panelgrove.data

import dev.soto.panelgrove.R
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.withTransaction
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.engine.DocumentEngine
import dev.soto.panelgrove.engine.DocumentSession
import dev.soto.panelgrove.storage.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

class RelinkConflictException(message: String) : IllegalArgumentException(message)
data class FolderRecovery(val restored: Int, val failed: Int)

class LibraryRepository(private val context: Context, private val database: LibraryDatabase,
    val preferences: PreferencesStore, private val sources: SourceAccess, val engine: DocumentEngine) {
    private val strings get() = dev.soto.panelgrove.ui.StringDictionary(context.resources)
    val dao = database.library()
    private val importMutex = Mutex()
    val books = dao.observeBooks().map { rows -> rows.map { it.book() } }
    val roots = dao.observeSources().map { rows -> rows.map { it.source() } }
    val bookmarks = dao.observeBookmarks().map { rows -> rows.map { it.bookmark() } }
    val history = dao.observeHistory().map { rows -> rows.map { it.entry() } }
    val coverDirectory = File(context.filesDir, "covers").apply { mkdirs() }
    fun cover(book: Book) = File(coverDirectory, "${book.id}.png")
    suspend fun book(id: String) = dao.book(id)?.book()
    suspend fun retainsCopyFor(uri: Uri, replaceId: String?): Boolean {
        val identity = sources.identity(uri.toString())
        val existing = replaceId?.let { book(it) } ?: dao.books().map { it.book() }.find {
            it.originUri?.let(sources::identity) == identity || sources.identity(it.uri) == identity
        }
        return existing?.managed == true && sources.identity(existing.uri) != identity
    }
    suspend fun change(id: String, transform: (Book) -> Book) = database.withTransaction {
        dao.book(id)?.book()?.let { dao.save(transform(it).row()) }
    }
    private suspend fun remapBookmarks(id: String, pages: List<PageRef>) {
        val indices = pages.withIndex().associate { it.value.id to it.index }
        dao.bookmarksForBook(id).forEach { row ->
            val mark = row.bookmark(); val matched = indices[mark.pageId]
            dao.save(mark.copy(pageIndex = matched ?: mark.pageIndex.coerceIn(pages.indices), missing = matched == null).row())
        }
    }
    suspend fun importFile(uri: Uri, copy: Boolean = false, root: LibrarySource? = null,
        relativePath: String = "", folder: Boolean = false, replaceId: String? = null,
        revision: String? = null, targetCopyRoot: Uri? = null, linkOnly: Boolean = false,
        recoveredCopyRoot: Uri? = null, progress: suspend (String) -> Unit = {}): Book = importMutex.withLock { withContext(Dispatchers.IO) {
        val details = sources.details(uri)
        val format = if (folder) BookFormat.FOLDER else sources.format(uri) ?: ArchiveRules.format(details.name)
            ?: error(strings[R.string.supported_files_are_cbz_zip_cbr_rar_and_pdf, details.name])
        require(!Regex("(?i)\\.part\\d+\\.rar$|\\.r\\d\\d$").containsMatchIn(details.name)) { strings[R.string.multipart_archives_are_not_supported] }
        val matches = dao.books().map { it.book() }.filter {
            sources.identity(it.uri) == sources.identity(uri.toString()) || it.originUri?.let(sources::identity) == sources.identity(uri.toString()) ||
                (root != null && it.sourceId == root.id && it.relativePath == relativePath)
        }
        val sameLocation = matches.find { it.id == replaceId } ?: matches.firstOrNull()
        val conflicting = matches.find { it.id != replaceId && sources.identity(it.uri) == sources.identity(uri.toString()) }
            ?: sameLocation?.takeIf { it.id != replaceId }
        var duplicate: Book? = null
        if (replaceId != null && conflicting != null) {
            val target = book(replaceId) ?: error(strings[R.string.this_book_was_removed])
            // Only consolidate an existing entry when the selected bytes prove it is
            // the restored book. A filename alone cannot establish that identity.
            if (linkOnly && target.contentHash != null && target.contentHash == sources.contentHash(uri)) duplicate = conflicting
            else throw RelinkConflictException(strings[R.string.relink_file_already_linked, conflicting.title, target.title])
        }
        val existing = replaceId?.let { book(it) ?: error(strings[R.string.this_book_was_removed]) } ?: sameLocation
        val relocated = existing != null && replaceId != null && listOfNotNull(existing.uri, existing.originUri)
            .none { sources.identity(it) == sources.identity(uri.toString()) }
        val sourceId = root?.id ?: existing?.sourceId?.takeUnless { relocated }
        val path = relativePath.ifEmpty { if (relocated && root == null) "" else existing?.relativePath.orEmpty() }
        val sourceRevision = revision ?: details.revision
        val atManagedCopy = existing?.managed == true && sources.identity(existing.uri) == sources.identity(uri.toString())
        val copying = !folder && !linkOnly && (targetCopyRoot != null || (!atManagedCopy && (copy || existing?.managed == true)))
        val copyRoot = if (copying) targetCopyRoot ?: preferences.current().copyFolderUri?.let(Uri::parse) ?: throw CopyFolderRequiredException() else null
        copyRoot?.let { sources.copies.checkWritable(it) }
        val displayName = if (atManagedCopy) existing!!.displayName else details.name
        // Explicit selection/rescan is a refresh boundary, even for same-size bytes and broken timestamps.
        var destination = uri; var hash: String? = null; var createdManaged = false
        var published: PublishedCopy? = null
        if (copying) {
            progress(strings[R.string.copying, details.name])
            val copied = sources.stageCopy(uri, details.name, progress)
            destination = copied.first; hash = copied.second; createdManaged = true
            if (existing?.managed == true && hash == existing.contentHash && sources.available(existing) &&
                existing.copyRootUri?.let(sources::identity) == copyRoot?.toString()?.let(sources::identity) &&
                (targetCopyRoot == null || sources.copies.organized(existing, targetCopyRoot)) &&
                hash == sources.contentHash(Uri.parse(existing.uri))) {
                sources.managedFile(destination).delete()
                val retained = existing.copy(access = AccessState.AVAILABLE, providerRevision = sourceRevision,
                    sourceId = sourceId, relativePath = path, originUri = if (atManagedCopy) existing.originUri else uri.toString())
                dao.save(retained.row())
                return@withContext retained
            }
        }
        if ((atManagedCopy || recoveredCopyRoot != null) && !copying && uri.scheme == "content") hash = sources.contentHash(uri)
        val shell = Book(id = existing?.id ?: UUID.randomUUID().toString(), uri = destination.toString(),
            displayName = displayName, format = format, sourceId = sourceId,
            relativePath = path, managed = destination.scheme == "panelgrove" || atManagedCopy || recoveredCopyRoot != null,
            originUri = if (atManagedCopy) existing?.originUri else if (destination != uri && uri.scheme == "content") uri.toString() else existing?.originUri,
            revision = UUID.randomUUID().toString(), providerRevision = if (atManagedCopy) existing!!.providerRevision else sourceRevision, contentHash = hash,
            restoredKey = existing?.restoredKey, copyRootUri = recoveredCopyRoot?.toString() ?: if (!copying && atManagedCopy) existing?.copyRootUri else null,
            copyRevision = if (!copying && atManagedCopy) sourceRevision else null)
        try {
            progress(strings[R.string.indexing, details.name])
            engine.open(shell, progress).use { session ->
                val metadata = session.index.metadata
                val inferredSeries = CopyNaming.inferredSeries(displayName)
                val defaultSeries = metadata.series?.takeIf { it.isNotBlank() }
                    ?: if (copying) inferredSeries.ifBlank { root?.name.orEmpty() } else if (atManagedCopy) existing!!.series else root?.name ?: inferredSeries
                if (copyRoot != null) {
                    val current = book(shell.id) ?: existing
                    val series = if (current?.metadataEdited == true) current.series else defaultSeries
                    published = sources.copies.publish(destination, copyRoot, series, displayName, progress)
                }
                val copyRevision = published?.let { sources.details(it.uri).revision } ?: shell.copyRevision
                val imported = database.withTransaction {
                    val target = dao.book(shell.id)?.book() ?: existing
                    val other = duplicate?.let { dao.book(it.id)?.book() }
                    val current = if (target != null && other != null) target.copy(
                        position = target.position ?: other.position,
                        favorite = target.favorite || other.favorite,
                        collections = (target.collections + other.collections).distinct(),
                        status = if (target.status == ReadingStatus.UNREAD) other.status else target.status,
                        lastOpenedAt = maxOf(target.lastOpenedAt, other.lastOpenedAt),
                    ) else target
                    if (other != null) {
                        val marks = dao.bookmarksForBook(shell.id).map { it.bookmark() }.associateBy { it.pageId }.toMutableMap()
                        dao.bookmarksForBook(other.id).map { it.bookmark() }.forEach { mark ->
                            val retained = marks[mark.pageId]
                            if (retained == null) { val moved = mark.copy(bookId = shell.id); dao.save(moved.row()); marks[mark.pageId] = moved }
                            else {
                                if (retained.label.isBlank() && mark.label.isNotBlank()) dao.save(retained.copy(label = mark.label).row())
                                dao.deleteBookmark(mark.id)
                            }
                        }
                        dao.history().map { it.entry() }.filter { it.bookId == other.id }.forEach { dao.save(it.copy(bookId = shell.id).row()) }
                        dao.deleteBook(other.id)
                    }
                    shell.copy(
                        uri = published?.uri?.toString() ?: shell.uri,
                        managed = published != null || shell.managed,
                        copyRootUri = published?.root?.toString() ?: shell.copyRootUri,
                        copyRevision = copyRevision,
                        title = if (current?.metadataEdited == true) current.title else metadata.title ?: shell.title,
                        series = if (current?.metadataEdited == true) current.series else defaultSeries,
                        number = if (current?.metadataEdited == true) current.number else metadata.number ?: Regex("\\d+(?:\\.\\d+)?").findAll(shell.displayName).lastOrNull()?.value.orEmpty(),
                        metadataEdited = current?.metadataEdited ?: false, metadata = metadata,
                        pageCount = session.index.pages.size, position = remapPosition(current?.position, session.index.pages),
                        favorite = current?.favorite ?: false, collections = current?.collections ?: emptyList(),
                        status = current?.status ?: ReadingStatus.UNREAD, settings = current?.settings ?: ReaderOverrides(),
                        addedAt = current?.addedAt ?: shell.addedAt, lastOpenedAt = current?.lastOpenedAt ?: 0,
                    ).also {
                        currentCoroutineContext().ensureActive()
                        dao.save(it.row())
                        remapBookmarks(it.id, session.index.pages)
                    }
                }
                published?.let(sources.copies::committed)
                duplicate?.let { cover(it).delete() }
                if (published != null && existing?.managed == true && existing.uri != imported.uri)
                    runCatching { sources.deleteCopy(existing) }
                runCatching {
                    val page = metadata.coverIndex.coerceIn(session.index.pages.indices)
                    val bitmap = engine.render(session, page, 360, 520)
                    val partial = File(coverDirectory, "${imported.id}.part")
                    try {
                        partial.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                        check(partial.renameTo(cover(imported)))
                    } finally { partial.delete() }
                }.onFailure { if (it is CancellationException) throw it }
                return@withContext imported
            }
        } catch (error: Exception) {
            withContext(kotlinx.coroutines.NonCancellable) {
                published?.let { result ->
                    if (dao.book(shell.id)?.uri != result.uri.toString()) sources.copies.discard(result)
                    else sources.copies.committed(result)
                }
            }
            throw error
        } finally {
            if (createdManaged) sources.managedFile(destination).delete()
        }
    } }
    suspend fun scan(rootId: String, progress: suspend (String) -> Unit = {}): List<String> = withContext(Dispatchers.IO) {
        val root = dao.sources().map { it.source() }.find { it.id == rootId } ?: error(strings[R.string.library_folder_was_removed])
        val tree = TreeAccess(context); val errors = mutableListOf<String>(); val seen = mutableSetOf<String>(); var visited = 0
        val copyFolderIdentity = preferences.current().copyFolderUri?.let(sources::identity)
        suspend fun visit(folder: Uri, relative: String, depth: Int) {
            currentCoroutineContext().ensureActive()
            if (relative.isNotEmpty() && copyFolderIdentity == sources.identity(folder.toString())) return
            require(depth <= 40 && ++visited <= 10_000) { strings[R.string.library_folder_exceeds_scanning_limits] }
            progress(strings[R.string.scanning, relative.ifEmpty { root.name }])
            val children = tree.children(folder)
            val images = children.filter { !it.directory && ArchiveRules.isImage(it.name) }
            if (images.isNotEmpty()) {
                val revision = digest(children.filter { !it.directory && (ArchiveRules.isImage(it.name) || it.name == "translation-manifest.json" || it.name.equals("ComicInfo.xml", true)) }
                    .sortedBy { it.name }.joinToString { "${it.name}:${it.size}:${it.modified}" })
                seen += relative
                try { importFile(folder, root = root, relativePath = relative, folder = true, revision = revision, progress = progress) }
                catch (e: Exception) { if (e is CancellationException) throw e; errors += "${relative.ifEmpty { root.name }}: ${e.message}" }
            }
            for (child in children.sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }) {
                if (child.name.startsWith('.') || child.name == "__MACOSX") continue
                val path = if (relative.isEmpty()) child.name else "$relative/${child.name}"
                if (child.directory) visit(child.uri, path, depth + 1)
                else if (ArchiveRules.format(child.name) != null) {
                    seen += path
                    try { importFile(child.uri, root = root, relativePath = path, progress = progress) }
                    catch (e: Exception) { if (e is CancellationException) throw e; errors += "$path: ${e.message}" }
                }
            }
        }
        try {
            visit(tree.root(Uri.parse(root.uri)), "", 0)
            database.withTransaction {
                dao.save(root.copy(access = AccessState.AVAILABLE).row())
                dao.books().map { it.book() }.filter { it.sourceId == rootId && it.relativePath !in seen }.forEach {
                    dao.save(it.copy(access = if (sources.available(it)) AccessState.AVAILABLE else AccessState.MISSING).row())
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            database.withTransaction {
                dao.save(root.copy(access = AccessState.NEEDS_PERMISSION).row())
                dao.books().map { it.book() }.filter { it.sourceId == rootId }.forEach {
                    dao.save(it.copy(access = if (sources.available(it)) AccessState.AVAILABLE else AccessState.NEEDS_PERMISSION).row())
                }
            }
            throw error
        }
        errors
    }
    suspend fun addRoot(uri: Uri, replaceId: String? = null): LibrarySource = withContext(Dispatchers.IO) {
        val name = sources.details(TreeAccess(context).root(uri)).name
        val existing = dao.sources().map { it.source() }.find { sources.identity(it.uri) == sources.identity(uri.toString()) }
        require(replaceId == null || existing == null || existing.id == replaceId) { strings[R.string.this_folder_is_already_linked] }
        val previous = replaceId?.let { id -> dao.sources().find { it.id == id }?.source() } ?: existing
        LibrarySource(replaceId ?: existing?.id ?: UUID.randomUUID().toString(), uri.toString(), name,
            restoredKey = previous?.restoredKey).also { dao.save(it.row()) }
    }
    suspend fun detachRoot(id: String) = database.withTransaction {
        dao.books().map { it.book() }.filter { it.sourceId == id }.forEach { dao.save(it.copy(sourceId = null).row()) }
        dao.deleteSource(id)
    }
    suspend fun openSession(id: String, progress: suspend (String) -> Unit): DocumentSession {
        val old = book(id) ?: error(strings[R.string.this_book_is_no_longer_in_your_library])
        val refreshed = if (old.uri.startsWith("content://") && old.format != BookFormat.FOLDER) runCatching {
            val details = withContext(Dispatchers.IO) { sources.details(Uri.parse(old.uri)) }
            val previousRevision = if (old.managed) old.copyRevision else old.providerRevision.ifEmpty { old.revision }
            if (details.revision != previousRevision) importFile(Uri.parse(old.uri), replaceId = id, progress = progress) else old
        }.getOrElse { if (it is CancellationException) throw it; old } else old
        val session = try { engine.open(refreshed, progress) } catch (error: Exception) {
            if (error is CancellationException) throw error
            val access = withContext(Dispatchers.IO) {
                if (sources.available(refreshed)) AccessState.AVAILABLE else if (error is SecurityException) AccessState.NEEDS_PERMISSION else AccessState.MISSING
            }
            change(id) { it.copy(access = access) }
            throw error
        }
        try {
        currentCoroutineContext().ensureActive()
        database.withTransaction {
        change(id) { it.copy(position = remapPosition(it.position, session.index.pages), pageCount = session.index.pages.size,
            access = AccessState.AVAILABLE, lastOpenedAt = System.currentTimeMillis(),
            status = if (it.status == ReadingStatus.UNREAD) ReadingStatus.READING else it.status) }
        dao.save(HistoryEntry(UUID.randomUUID().toString(), id).row()); dao.trimHistory()
        remapBookmarks(id, session.index.pages)
        }
        return session
        } catch (error: Exception) { engine.release(session); throw error }
    }
    suspend fun position(id: String, page: PageRef, index: Int, offset: Float) = change(id) {
        it.copy(position = ReadingPosition(page.id, index, offset.coerceIn(0f, 1f)))
    }
    suspend fun toggleBookmark(book: Book, page: PageRef, index: Int) = database.withTransaction {
        val existing = dao.bookmarksForBook(book.id).map { it.bookmark() }.find { it.pageId == page.id }
        if (existing != null) dao.deleteBookmark(existing.id)
        else dao.save(Bookmark(UUID.randomUUID().toString(), book.id, page.id, index).row())
    }
    suspend fun remove(id: String, deleteCopy: Boolean) = withContext(Dispatchers.IO) {
        val book = book(id) ?: return@withContext
        database.withTransaction { dao.deleteBookmarks(id); dao.deleteHistory(id); dao.deleteBook(id) }
        cover(book).delete()
        if (deleteCopy && book.managed) check(sources.deleteCopy(book)) { strings[R.string.the_library_entry_was_removed_but_its_file_could] }
    }
    suspend fun exportBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val backup = database.withTransaction { LibraryBackup(preferences = preferences.current(),
            sources = dao.sources().map { it.source() }, books = dao.books().map { it.book() },
            bookmarks = dao.bookmarks().map { it.bookmark() }, history = dao.history().map { it.entry() }) }
        val bytes = PanelgroveJson.encodeToString(backup).toByteArray()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error(strings[R.string.cannot_write_the_backup])
    }
    suspend fun recoverRestore() = importMutex.withLock {
        finishPendingRestore()
        sources.copies.recover(dao.books().map { it.uri }.toSet())
    }
    /** Reads granted documents in place. Recovery never recopies or deletes user files. */
    suspend fun recoverImportFolder(folder: Uri, progress: suspend (String) -> Unit = {}): FolderRecovery = withContext(Dispatchers.IO) {
        val tree = TreeAccess(context)
        val visited = mutableSetOf<String>()
        var restored = 0; var failed = 0
        suspend fun recover(uri: Uri, images: Boolean = false) {
            currentCoroutineContext().ensureActive()
            try {
                val books = dao.books().map { it.book() }
                val identity = sources.identity(uri.toString())
                val direct = books.filter { sources.identity(it.uri) == identity || it.originUri?.let(sources::identity) == identity }
                    .sortedByDescending { it.restoredKey != null }.firstOrNull()
                val missingCopies = if (direct == null && !images) books.filter { it.managed && it.contentHash != null && !sources.available(it) } else emptyList()
                val hash = if (missingCopies.isNotEmpty()) sources.contentHash(uri) else null
                val matched = direct ?: missingCopies.filter { it.contentHash == hash }.singleOrNull()
                progress(strings[R.string.restoring_found_book, sources.details(uri).name])
                importFile(uri, folder = images, replaceId = matched?.id, linkOnly = true,
                    recoveredCopyRoot = folder.takeIf { matched?.managed == true }, progress = progress)
                restored++
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                dev.soto.panelgrove.FailureMessages.recoverable(error, "Restore import-folder document: $uri")
                failed++
            }
        }
        suspend fun visit(directory: Uri, depth: Int) {
            currentCoroutineContext().ensureActive()
            if (!visited.add(sources.identity(directory.toString()))) return
            require(depth <= 40 && visited.size <= 10_000) { strings[R.string.library_folder_exceeds_scanning_limits] }
            val children = tree.children(directory)
            if (children.any { !it.directory && ArchiveRules.isImage(it.name) }) recover(directory, images = true)
            for (child in children.sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }) {
                if (child.name.startsWith('.') || child.name == "__MACOSX") continue
                if (child.directory) visit(child.uri, depth + 1)
                else if (ArchiveRules.format(child.name) != null) recover(child.uri)
            }
        }
        visit(tree.root(folder), 0)
        FolderRecovery(restored, failed)
    }
    suspend fun moveCopies(progress: suspend (String) -> Unit = {}) {
        val folder = preferences.current().copyFolderUri?.let(Uri::parse) ?: throw CopyFolderRequiredException()
        sources.copies.checkWritable(folder)
        dao.books().map { it.book() }.filter { it.managed }.forEach { old ->
            currentCoroutineContext().ensureActive()
            progress(strings[R.string.moving, old.title])
            importFile(Uri.parse(old.uri), copy = true, replaceId = old.id, targetCopyRoot = folder, progress = progress)
        }
    }
    suspend fun editMetadata(id: String, transform: (Book) -> Book) {
        change(id, transform)
        val current = book(id) ?: return
        current.copyRootUri?.let { root ->
            if (!sources.copies.organized(current, Uri.parse(root)))
                importFile(Uri.parse(current.uri), copy = true, replaceId = id, targetCopyRoot = Uri.parse(root))
        }
    }
    private suspend fun finishPendingRestore() {
        val pending = dao.pendingRestore() ?: return
        preferences.setRestored(PanelgroveJson.decodeFromString<AppPreferences>(pending.preferences))
        dao.clearRestore()
    }
    suspend fun restoreBackup(uri: Uri) = importMutex.withLock { withContext(Dispatchers.IO) {
        val output = java.io.ByteArrayOutputStream()
        sources.input(uri).use { copyBounded(it, output, 32L * 1024 * 1024) }
        val backup = PanelgroveJson.decodeFromString<LibraryBackup>(output.toString("UTF-8")).withCurrentLocations(); backup.validate()
        finishPendingRestore()
        database.withTransaction {
            val live = LibraryBackup(sources = dao.sources().map { it.source() }, books = dao.books().map { it.book() },
                bookmarks = dao.bookmarks().map { it.bookmark() }, history = dao.history().map { it.entry() })
            val merged = mergeBackup(backup, live, sources::identity)
            merged.sources.forEach { source ->
                val accessible = runCatching { TreeAccess(context).children(TreeAccess(context).root(Uri.parse(source.uri))) }.isSuccess
                dao.save(source.copy(access = if (accessible) AccessState.AVAILABLE else AccessState.NEEDS_PERMISSION).row())
            }
            merged.books.forEach { incoming ->
                dao.save(incoming.copy(access = if (sources.available(incoming)) AccessState.AVAILABLE else AccessState.NEEDS_PERMISSION).row())
            }
            merged.bookmarks.forEach { dao.save(it.row()) }
            merged.history.forEach { dao.save(it.row()) }
            dao.trimHistory()
            dao.save(RestoreJournal(preferences = PanelgroveJson.encodeToString(backup.preferences)))
        }
        finishPendingRestore()
    } }
}

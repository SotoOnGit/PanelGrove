package dev.soto.panelgrove.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val PanelgroveJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

@Serializable enum class Direction { RTL, LTR }
@Serializable enum class ReadingMode { PAGED, VERTICAL }
@Serializable enum class FitMode { PAGE, WIDTH }
@Serializable enum class AppTheme { SYSTEM, LIGHT, DARK }
@Serializable enum class ReadingStatus { UNREAD, READING, READ }
@Serializable enum class BookFormat { ZIP, RAR, PDF, FOLDER, SEVEN_Z }
@Serializable enum class AccessState { AVAILABLE, MISSING, NEEDS_PERMISSION }

@Serializable data class ReaderSettings(
    val direction: Direction = Direction.RTL,
    val mode: ReadingMode = ReadingMode.PAGED,
    val fit: FitMode = FitMode.PAGE,
    val spreads: Boolean = false,
    val background: Long = 0xFF0B0D10,
    val brightness: Float = -1f,
    val volumeKeys: Boolean = false,
    val keepAwake: Boolean = true,
    val rotationLock: Boolean = false,
    val contrast: Float = 1f,
    val gamma: Float = 1f,
)

@Serializable data class ReaderOverrides(
    val direction: Direction? = null, val mode: ReadingMode? = null,
    val fit: FitMode? = null, val spreads: Boolean? = null,
    val background: Long? = null, val brightness: Float? = null,
    val volumeKeys: Boolean? = null, val keepAwake: Boolean? = null,
    val rotationLock: Boolean? = null,
    val contrast: Float? = null, val gamma: Float? = null,
) {
    fun applyTo(base: ReaderSettings) = ReaderSettings(
        direction ?: base.direction, mode ?: base.mode, fit ?: base.fit,
        spreads ?: base.spreads, background ?: base.background, brightness ?: base.brightness,
        volumeKeys ?: base.volumeKeys, keepAwake ?: base.keepAwake, rotationLock ?: base.rotationLock,
        contrast ?: base.contrast, gamma ?: base.gamma,
    )
    companion object {
        fun from(settings: ReaderSettings) = ReaderOverrides(
            settings.direction, settings.mode, settings.fit, settings.spreads, settings.background,
            settings.brightness, settings.volumeKeys, settings.keepAwake, settings.rotationLock,
            settings.contrast, settings.gamma,
        )
    }
}

@Serializable data class AppPreferences(
    val reader: ReaderSettings = ReaderSettings(), val theme: AppTheme = AppTheme.DARK,
    val grid: Boolean = true, val copyImports: Boolean = false,
    val seriesSettings: Map<String, ReaderOverrides> = emptyMap(),
    val copyFolderUri: String? = null, val copyFolderName: String? = null,
    val trueBlack: Boolean = false,
)

@Serializable data class LibrarySource(
    val id: String, val uri: String, val name: String,
    val access: AccessState = AccessState.AVAILABLE,
    val restoredKey: String? = null,
)

@Serializable data class PageRef(
    val id: String, val locator: String, val width: Int = 0, val height: Int = 0,
    val chapter: String? = null, val doublePage: Boolean = false,
)

@Serializable data class BookMetadata(
    val title: String? = null, val series: String? = null, val number: String? = null,
    val direction: Direction? = null, val completeness: String? = null,
    val kind: String? = null, val language: String? = null,
    val missingMaterial: List<String> = emptyList(), val coverIndex: Int = 0,
)

@Serializable data class DocumentIndex(
    val pages: List<PageRef>, val metadata: BookMetadata = BookMetadata(),
    val warnings: List<String> = emptyList(),
) { init { require(pages.isNotEmpty()) { "No readable pages were found." } } }

@Serializable data class ReadingPosition(val pageId: String, val index: Int, val offset: Float = 0f)

@Serializable data class Book(
    val id: String, val uri: String, val displayName: String, val format: BookFormat,
    val sourceId: String? = null, val relativePath: String = "", val managed: Boolean = false,
    val originUri: String? = null,
    val revision: String = "", val title: String = displayName.substringBeforeLast('.'),
    val series: String = "", val number: String = "", val metadataEdited: Boolean = false,
    val metadata: BookMetadata = BookMetadata(), val pageCount: Int = 0,
    val access: AccessState = AccessState.AVAILABLE,
    val favorite: Boolean = false, val collections: List<String> = emptyList(),
    val status: ReadingStatus = ReadingStatus.UNREAD, val position: ReadingPosition? = null,
    val settings: ReaderOverrides = ReaderOverrides(),
    val addedAt: Long = System.currentTimeMillis(), val lastOpenedAt: Long = 0,
    val contentHash: String? = null,
    val providerRevision: String = "",
    val restoredKey: String? = null,
    val copyRootUri: String? = null,
    val copyRevision: String? = null,
    val customCover: String? = null,
    // Null follows document metadata; an empty string explicitly means unspecified.
    val language: String? = null,
)

@Serializable data class Bookmark(
    val id: String, val bookId: String, val pageId: String, val pageIndex: Int,
    val label: String = "", val createdAt: Long = System.currentTimeMillis(),
    val missing: Boolean = false,
)
@Serializable data class HistoryEntry(
    val id: String, val bookId: String, val openedAt: Long = System.currentTimeMillis(),
)
@Serializable data class LibraryBackup(
    val version: Int = 1, val exportedAt: Long = System.currentTimeMillis(),
    val preferences: AppPreferences = AppPreferences(),
    val sources: List<LibrarySource> = emptyList(), val books: List<Book> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(), val history: List<HistoryEntry> = emptyList(),
) {
    /** Only the previous preview's private managed-file scheme is migrated; unsafe paths still fail validation. */
    fun withCurrentLocations(): LibraryBackup = copy(books = books.map { book ->
        if (book.uri.startsWith("readly://managed/"))
            book.copy(uri = "panelgrove://managed/" + book.uri.removePrefix("readly://managed/"))
        else book
    })

    fun validate() {
        require(version == 1) { "This backup version is not supported." }
        require(books.size <= 100_000 && bookmarks.size <= 500_000 && sources.size <= 1_000 && history.size <= 500) { "Backup exceeds library limits." }
        require(books.map { it.id }.distinct().size == books.size) { "Backup contains duplicate book IDs." }
        require(books.map { it.uri }.distinct().size == books.size) { "Backup contains duplicate source locations." }
        require(sources.map { it.id }.distinct().size == sources.size) { "Backup contains duplicate source IDs." }
        require(bookmarks.map { it.id }.distinct().size == bookmarks.size) { "Backup contains duplicate bookmark IDs." }
        require(history.map { it.id }.distinct().size == history.size) { "Backup contains duplicate history IDs." }
        val bookIds = books.map { it.id }.toSet()
        require(bookmarks.all { it.bookId in bookIds } && history.all { it.bookId in bookIds }) { "Backup contains orphaned reading data." }
        require(books.all { it.id.isNotBlank() && it.uri.isNotBlank() && it.pageCount >= 0 }) { "Backup contains an invalid book." }
        val safeId = Regex("[A-Za-z0-9_-]{1,100}")
        require(books.all { safeId.matches(it.id) } && sources.all { safeId.matches(it.id) }) { "Backup contains an unsafe identifier." }
        require(books.all { it.customCover == null || safeId.matches(it.customCover) }) { "Backup contains an unsafe cover identifier." }
        require(bookmarks.all { safeId.matches(it.id) } && history.all { safeId.matches(it.id) }) { "Backup contains an unsafe reading identifier." }
        val sourceIds = sources.map { it.id }.toSet()
        require(books.all { it.sourceId == null || it.sourceId in sourceIds }) { "Backup contains an orphaned folder reference." }
        require(books.all { it.uri.startsWith("content://") || it.uri.startsWith("panelgrove://managed/") }) { "Backup contains an unsupported source location." }
        val managedLocation = Regex("panelgrove://managed/[A-Za-z0-9_-][A-Za-z0-9._-]*")
        require(books.all {
            if (it.uri.startsWith("panelgrove:")) it.managed && it.copyRootUri == null && managedLocation.matches(it.uri) && it.format != BookFormat.FOLDER
            else if (it.managed) it.copyRootUri?.startsWith("content://") == true && it.format != BookFormat.FOLDER
            else it.copyRootUri == null
        }) { "Backup contains an invalid managed location." }
        require(preferences.copyFolderUri == null || preferences.copyFolderUri.startsWith("content://")) { "Backup contains an invalid copy folder." }
        require(sources.all { it.uri.startsWith("content://") } && books.all { it.originUri == null || it.originUri.startsWith("content://") }) { "Backup contains an invalid original location." }
        require(books.all { it.position == null || (it.position.pageId.isNotBlank() && it.position.index >= 0 && it.position.offset in 0f..1f) } &&
            bookmarks.all { it.pageId.isNotBlank() && it.pageIndex >= 0 }) { "Backup contains invalid reading positions." }
        require(preferences.reader.brightness in -1f..1f) { "Backup contains invalid brightness." }
        require(books.all { it.settings.brightness == null || it.settings.brightness in -1f..1f } &&
            preferences.seriesSettings.values.all { it.brightness == null || it.brightness in -1f..1f }) { "Backup contains invalid reader overrides." }
        require(preferences.reader.contrast in .5f..2f && preferences.reader.gamma in .5f..2f) { "Backup contains invalid display adjustments." }
        require((books.map { it.settings } + preferences.seriesSettings.values).all {
            (it.contrast == null || it.contrast in .5f..2f) && (it.gamma == null || it.gamma in .5f..2f)
        }) { "Backup contains invalid display overrides." }
    }
}

fun resolveSettings(preferences: AppPreferences, book: Book): ReaderSettings {
    val base = book.metadata.direction?.let { preferences.reader.copy(direction = it) } ?: preferences.reader
    val resolved = book.settings.applyTo(preferences.seriesSettings[book.series]?.applyTo(base) ?: base)
    return resolved.copy(background = when (resolved.background) {
        0xFF101210L -> 0xFF0B0D10L
        0xFFF5F1E8L -> 0xFFF4EFE6L
        else -> resolved.background
    })
}

fun remapPosition(position: ReadingPosition?, pages: List<PageRef>): ReadingPosition? = position?.let {
    val matched = pages.indexOfFirst { page -> page.id == it.pageId }
    val index = if (matched >= 0) matched else it.index.coerceIn(pages.indices)
    ReadingPosition(pages[index].id, index, if (matched >= 0) it.offset.coerceIn(0f, 1f) else 0f)
}

/** Cover stays alone; all following pages are grouped in reading order, never reversed in storage. */
fun pageGroups(pages: List<PageRef>, spreads: Boolean): List<List<Int>> {
    if (!spreads) return pages.indices.map { listOf(it) }
    val result = mutableListOf<List<Int>>()
    var i = 0
    while (i < pages.size) {
        val page = pages[i]
        val wide = page.doublePage || (page.width > 0 && page.width > page.height)
        if (i == 0 || wide || i + 1 == pages.size || pages[i + 1].doublePage ||
            (pages[i + 1].width > 0 && pages[i + 1].width > pages[i + 1].height)) result += listOf(i++)
        else { result += listOf(i, i + 1); i += 2 }
    }
    return result
}

fun nextUnread(current: Book, books: List<Book>): Book? = books.filter {
    current.series.isNotBlank() && it.id != current.id && it.series == current.series && it.status == ReadingStatus.UNREAD &&
        it.editionLanguage == current.editionLanguage &&
        (current.metadata.kind == null || it.metadata.kind == null || it.metadata.kind == current.metadata.kind) &&
        NaturalOrder.compare(it.number, current.number) > 0
}.minWithOrNull { a, b -> NaturalOrder.compare(a.number, b.number) }

package dev.soto.panelgrove.core

import kotlinx.serialization.json.*
import org.w3c.dom.Element
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

object MetadataReader {
    fun index(entries: List<PageRef>, translation: String? = null, comicInfo: String? = null): DocumentIndex {
        require(entries.size <= ArchiveRules.MAX_ENTRIES) { "This book contains too many pages." }
        require(entries.map { it.locator }.distinct().size == entries.size) { "Archive contains duplicate page names." }
        val fallback = entries.sortedWith { a, b -> NaturalOrder.compare(a.locator, b.locator) }
        val warnings = mutableListOf<String>()
        var metadata = BookMetadata()
        var pages = fallback
        if (comicInfo != null) runCatching {
            val parsed = comic(comicInfo, fallback)
            metadata = parsed.first; pages = parsed.second
        }.onFailure { warnings += "ComicInfo.xml could not be read; filename order is used." }
        if (translation != null) runCatching {
            val parsed = translation(translation, pages)
            val t = parsed.first
            metadata = t.copy(
                title = t.title ?: metadata.title, series = t.series ?: metadata.series,
                number = t.number ?: metadata.number, direction = t.direction ?: metadata.direction,
                language = t.language ?: metadata.language,
                coverIndex = metadata.coverIndex,
            )
            pages = parsed.second
        }.onFailure { warnings += "Translation metadata is invalid; ordinary comic metadata and filename order are used." }
        return DocumentIndex(pages, metadata, warnings)
    }

    private fun translation(text: String, entries: List<PageRef>): Pair<BookMetadata, List<PageRef>> {
        require(text.length <= ArchiveRules.MAX_METADATA_BYTES)
        val root = PanelgroveJson.parseToJsonElement(text).jsonObject
        fun value(key: String) = (root[key] as? JsonPrimitive)?.contentOrNull
        val byName = entries.associateBy { it.locator }
        val rawPages = root["pages"]?.jsonArray ?: error("Missing pages")
        require(rawPages.size == entries.size)
        val pages = rawPages.map { element ->
            val p = element.jsonObject
            fun string(key: String) = (p[key] as? JsonPrimitive)?.contentOrNull
            val name = ArchiveRules.safePath(string("archive_name") ?: string("path") ?: error("Missing page name"))
            val original = byName[name] ?: error("Missing page")
            val dimensions = p["dimensions"] as? JsonArray
            original.copy(
                id = string("source_id")?.takeIf { it.isNotBlank() } ?: original.id,
                width = dimensions?.getOrNull(0)?.jsonPrimitive?.intOrNull?.coerceIn(0, 100_000) ?: original.width,
                height = dimensions?.getOrNull(1)?.jsonPrimitive?.intOrNull?.coerceIn(0, 100_000) ?: original.height,
                chapter = string("chapter"),
            )
        }
        require(pages.map { it.locator }.toSet() == byName.keys)
        require(pages.map { it.id }.distinct().size == pages.size)
        val direction = when (value("reading_direction")?.lowercase()) {
            "right-to-left", "rtl" -> Direction.RTL
            "left-to-right", "ltr" -> Direction.LTR
            else -> null
        }
        val missing = (root["missing_material"] as? JsonArray)?.map { it.toString().trim('"') } ?: emptyList()
        return BookMetadata(value("title"), value("series"), value("number"), direction,
            value("completeness"), value("kind"), value("language"), missing) to pages
    }

    private fun comic(text: String, entries: List<PageRef>): Pair<BookMetadata, List<PageRef>> {
        require(text.length <= ArchiveRules.MAX_METADATA_BYTES)
        require(!text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true)) { "External XML entities are not allowed." }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(text)))
        fun value(key: String): String? = doc.getElementsByTagName(key).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
        val pages = entries.toMutableList()
        var cover = 0
        val nodes = doc.getElementsByTagName("Page")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            val index = node.getAttribute("Image").toIntOrNull() ?: continue
            if (index !in pages.indices) continue
            if (node.getAttribute("Type").equals("FrontCover", true)) cover = index
            pages[index] = pages[index].copy(doublePage = node.getAttribute("DoublePage").equals("true", true),
                chapter = node.getAttribute("Bookmark").takeIf { it.isNotBlank() })
        }
        return BookMetadata(title = value("Title"), series = value("Series"), number = value("Number"),
            direction = if (value("Manga") == "YesAndRightToLeft") Direction.RTL else null,
            language = value("LanguageISO"), coverIndex = cover) to pages
    }
}

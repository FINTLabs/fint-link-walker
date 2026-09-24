package no.novari.linkwalker.index

import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import no.novari.linkwalker.config.IndexProperties
import org.springframework.stereotype.Component
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.inputStream

@Component
class RecordExtractor(
    private val mapper: ObjectMapper,
    config: IndexProperties,
) {

    private val excludedRelations: Set<String> =
        config.excludeRelations.mapTo(mutableSetOf()) { it.lowercase() }

    fun extractFromFile(path: Path, component: String, resourceName: String): PageExtraction {
        val records = mutableListOf<MinimalRecord>()
        var totalItems: Long? = null
        var nextHref: String? = null
        var entryCount = 0
        var malformedSelfCount = 0
        path.inputStream().use { input ->
            mapper.createParser(input).use { parser ->
                if (parser.nextToken() != JsonToken.START_OBJECT) {
                    return PageExtraction(emptyList(), totalItems = null)
                }
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    val name = parser.currentName() ?: break
                    parser.nextToken()
                    when (name) {
                        "total_items" -> if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                            totalItems = parser.longValue
                        }
                        "_links" -> nextHref = readNextHref(parser)
                        "_embedded" -> {
                            val counts = readEntries(parser, records, component, resourceName)
                            entryCount = counts.entries
                            malformedSelfCount = counts.malformedSelf
                        }
                        else -> parser.skipChildren()
                    }
                }
            }
        }
        return PageExtraction(records, totalItems, nextHref, entryCount, malformedSelfCount)
    }

    private fun readNextHref(parser: JsonParser): String? {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            parser.skipChildren()
            return null
        }
        var next: String? = null
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val rel = parser.currentName() ?: return next
            parser.nextToken()
            if (rel == "next") {
                next = hrefOf(parser.readValueAsTree())
            } else {
                parser.skipChildren()
            }
        }
        return next
    }

    private fun hrefOf(node: JsonNode): String? {
        val link = if (node.isArray) node.firstOrNull() else node
        return link?.get("href")?.asString()?.takeIf { it.isNotBlank() }
    }

    private fun readEntries(
        parser: JsonParser,
        records: MutableList<MinimalRecord>,
        component: String,
        resourceName: String,
    ): EntryCounts {
        if (parser.currentToken() != JsonToken.START_OBJECT) return EntryCounts(0, 0)
        var count = 0
        var malformedSelf = 0
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val embName = parser.currentName() ?: return EntryCounts(count, malformedSelf)
            parser.nextToken()
            if (embName == "_entries" && parser.currentToken() == JsonToken.START_ARRAY) {
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    val node: JsonNode = parser.readValueAsTree()
                    count++
                    when (val outcome = extractEntry(node, component, resourceName)) {
                        is EntryOutcome.Indexed -> {
                            records += outcome.record
                            if (outcome.selfDropped) malformedSelf++
                        }
                        EntryOutcome.MalformedSelf -> malformedSelf++
                        EntryOutcome.NoSelf -> Unit
                    }
                }
            } else {
                parser.skipChildren()
            }
        }
        return EntryCounts(count, malformedSelf)
    }

    /**
     * Returns null when the entry has no self href that can anchor back-link validation: either
     * `_links.self` is missing, or every self href is malformed. Malformed self hrefs are damage or
     * garbage and never become canonical keys.
     */
    fun extract(node: JsonNode, component: String, resourceName: String): MinimalRecord? =
        (extractEntry(node, component, resourceName) as? EntryOutcome.Indexed)?.record

    private fun extractEntry(node: JsonNode, component: String, resourceName: String): EntryOutcome {
        val links = node["_links"]
        if (links == null || !links.isObject) return EntryOutcome.NoSelf

        val selfHrefs = links["self"]?.mapNotNull { it["href"]?.asString() }.orEmpty()
        if (selfHrefs.isEmpty()) return EntryOutcome.NoSelf
        val canonicalKeys = selfHrefs.mapNotNull(::canonicalSelf)
        if (canonicalKeys.isEmpty()) return EntryOutcome.MalformedSelf

        val outboundRefs = mutableListOf<OutboundRef>()
        val malformedHrefs = mutableListOf<String>()

        links.properties().forEach { (relName, rels) ->
            if (relName.equals("self", ignoreCase = true)) return@forEach
            if (isExcluded(relName)) return@forEach
            rels.forEach { linkNode ->
                val href = linkNode["href"]?.asString() ?: return@forEach
                if (HREF_REGEX.matches(href)) {
                    outboundRefs.add(OutboundRef(relName, canonicalize(href)))
                } else {
                    malformedHrefs.add(href)
                }
            }
        }

        val record = MinimalRecord(
            component = component,
            resourceName = resourceName,
            canonicalKeys = canonicalKeys,
            outboundRefs = outboundRefs,
            malformedHrefs = malformedHrefs,
        )
        return EntryOutcome.Indexed(record, selfDropped = canonicalKeys.size < selfHrefs.size)
    }

    private fun canonicalSelf(href: String): String? =
        canonicalize(href).takeIf { HREF_REGEX.matches(it) }

    fun canonicalize(href: String): String {
        val trimmed = href.trim().trimEnd('/')
        val decoded = runCatching { UriUtils.decode(trimmed, StandardCharsets.UTF_8) }
            .getOrDefault(trimmed)
        val match = HREF_REGEX.matchEntire(decoded)
        return if (match != null) {
            val (prefix, field, value) = match.destructured
            "${prefix.lowercase()}/${field.lowercase()}/$value"
        } else {
            decoded.lowercase()
        }
    }

    private fun isExcluded(relationName: String): Boolean {
        val lower = relationName.lowercase()
        return excludedRelations.any { lower.contains(it) }
    }

    private data class EntryCounts(
        val entries: Int,
        val malformedSelf: Int,
    )

    private sealed interface EntryOutcome {
        data class Indexed(val record: MinimalRecord, val selfDropped: Boolean) : EntryOutcome
        data object MalformedSelf : EntryOutcome
        data object NoSelf : EntryOutcome
    }
}

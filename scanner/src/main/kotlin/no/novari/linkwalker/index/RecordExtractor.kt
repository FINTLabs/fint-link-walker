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
                        "_embedded" -> entryCount = readEntries(parser, records, component, resourceName)
                        else -> parser.skipChildren()
                    }
                }
            }
        }
        return PageExtraction(records, totalItems, nextHref, entryCount)
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
    ): Int {
        if (parser.currentToken() != JsonToken.START_OBJECT) return 0
        var count = 0
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val embName = parser.currentName() ?: return count
            parser.nextToken()
            if (embName == "_entries" && parser.currentToken() == JsonToken.START_ARRAY) {
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    val node: JsonNode = parser.readValueAsTree()
                    count++
                    extract(node, component, resourceName)?.let { records += it }
                }
            } else {
                parser.skipChildren()
            }
        }
        return count
    }

    /**
     * Returns null when the entry has no usable `_links.self` href. Those records
     * can't anchor back-link validation and are dropped at extraction.
     */
    fun extract(node: JsonNode, component: String, resourceName: String): MinimalRecord? {
        val links = node["_links"]
        if (links == null || !links.isObject) return null

        val canonicalKeys = links["self"]?.mapNotNull { it["href"]?.asString()?.let(::canonicalize) }
            ?: emptyList()
        if (canonicalKeys.isEmpty()) return null

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

        return MinimalRecord(
            component = component,
            resourceName = resourceName,
            canonicalKeys = canonicalKeys,
            outboundRefs = outboundRefs,
            malformedHrefs = malformedHrefs,
        )
    }

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
}

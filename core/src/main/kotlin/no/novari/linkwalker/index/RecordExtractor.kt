package no.novari.linkwalker.index

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import no.novari.linkwalker.config.LinkWalkerConfig
import org.springframework.stereotype.Component
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.inputStream

@Component
class RecordExtractor(
    private val mapper: ObjectMapper,
    config: LinkWalkerConfig,
) {

    private val excludedRelations: Set<String> =
        config.excludeRelations.mapTo(mutableSetOf()) { it.lowercase() }

    fun extractFromFile(path: Path, component: String, resourceName: String): List<MinimalRecord> {
        val records = mutableListOf<MinimalRecord>()
        path.inputStream().use { input ->
            mapper.factory.createParser(input).use { parser ->
                advanceToEntries(parser)
                if (parser.currentToken != JsonToken.START_ARRAY) return emptyList()
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    val node: JsonNode = mapper.readTree(parser)
                    records += extract(node, component, resourceName)
                }
            }
        }
        return records
    }

    fun extract(node: JsonNode, component: String, resourceName: String): MinimalRecord {
        val links = node["_links"]
        if (links == null || !links.isObject) {
            return MinimalRecord(
                component = component,
                resourceName = resourceName,
                canonicalKeys = emptyList(),
                outboundRefs = emptyList(),
                malformedHrefs = emptyList(),
            )
        }

        val canonicalKeys = links["self"]?.mapNotNull { it["href"]?.asText()?.let(::canonicalize) }
            ?: emptyList()
        val outboundRefs = mutableListOf<OutboundRef>()
        val malformedHrefs = mutableListOf<String>()

        links.fields().forEach { (relName, rels) ->
            if (relName.equals("self", ignoreCase = true)) return@forEach
            if (isExcluded(relName)) return@forEach
            rels.forEach { linkNode ->
                val href = linkNode["href"]?.asText() ?: return@forEach
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

    private fun advanceToEntries(parser: JsonParser) {
        if (parser.nextToken() != JsonToken.START_OBJECT) return
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val name = parser.currentName ?: return
            parser.nextToken()
            if (name == "_embedded") {
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    val embName = parser.currentName ?: return
                    parser.nextToken()
                    if (embName == "_entries") return
                    parser.skipChildren()
                }
                return
            } else {
                parser.skipChildren()
            }
        }
    }

    companion object {
        /**
         * Matches a Fint resource href and captures three groups:
         *   1. URL prefix up to and including the resource name
         *      (host + at least three path segments — domain/pkg/.../resource).
         *   2. Identifier field (e.g. `systemid`, `fodselsnummer`).
         *   3. Identifier value (stops at `?` or `#` to avoid query/fragment).
         *
         * Allows variable depth so sub-namespaced resources match too,
         * e.g. `https://host/felles/kodeverk/iso/kjonn/systemid/2`.
         *
         * Examples that match (host + 5 or 6 path segments):
         *   https://api.f.no/utdanning/elev/elev/systemid/abc
         *   https://api.f.no/felles/kodeverk/iso/kjonn/systemid/2
         *
         * Doesn't match: anything fewer than 5 path segments, malformed
         * placeholders like `${...}/systemid/x`, or non-http(s) schemes.
         */
        private val HREF_REGEX =
            Regex("""(https?://[^/]+/(?:[^/]+/){2,}[^/]+)/([^/]+)/([^/?#]+)""")
    }
}

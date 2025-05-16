package no.fintlabs.linkwalker

import com.fasterxml.jackson.databind.JsonNode
import no.fintlabs.linkwalker.model.LinkInfo
import no.fintlabs.linkwalker.model.RelationError
import no.fintlabs.linkwalker.model.RelationReport
import org.springframework.stereotype.Service

@Service
class LinkParserService {

    fun parseRelations(info: LinkInfo, entries: Collection<JsonNode>): RelationReport {
        val seen = collectSeen(entries, info.ids.keys)
        val selfLink = seen["<self>"]?.firstOrNull() ?: "<no-self-link>"

        val errors = mutableListOf<RelationError>()
        info.ids.forEach { (field, wanted) ->
            val found = seen[field].orEmpty()
            (wanted - found).forEach { missing ->
                val relation = "${info.url}/$field/$missing"
                errors.add(RelationError(relation = relation, selfLink = selfLink))
            }
        }

        return RelationReport(info.url, errors)
    }

    private fun collectSeen(
        entries: Collection<JsonNode>,
        wantedFields: Set<String>
    ): Map<String, MutableSet<String>> = buildMap {
        entries.forEach { entry ->
            entry["_links"]?.get("self")?.firstOrNull()
                ?.get("href")?.asText()
                ?.let { getOrPut("<self>") { mutableSetOf() } += it }

            wantedFields.forEach { field ->
                entry.findCaseInsensitive(field)
                    ?.get("identifikatorverdi")
                    ?.takeIf(JsonNode::isTextual)
                    ?.asText()
                    ?.let { getOrPut(field) { mutableSetOf() } += it }
            }
        }
    }

    private fun JsonNode.findCaseInsensitive(key: String): JsonNode? =
        fields().asSequence().firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

}
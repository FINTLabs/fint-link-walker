package no.fintlabs.linkwalker

import com.fasterxml.jackson.databind.JsonNode
import no.fintlabs.linkwalker.model.*
import org.springframework.stereotype.Service

@Service
class LinkParserService {

    /**  Create a RelationReport that lists EVERY id (field/value) that
     *  was expected (i.e. present in LinkInfo.entries) but NOT found
     *  inside any of the provided `entries` resources.                                               */
    fun parseRelations(info: LinkInfo, entries: Collection<JsonNode>): RelationReport {

        /* 1) Which fields do we have to look for?  */
        val wantedFields: Set<String> =
            info.entries.flatMap { it.ids.keys }.toSet()

        /* 2) Scan through all JsonNode resources once and collect every
              identifikatorverdi we actually see, grouped by field name.  */
        val seen: Map<String, Set<String>> = collectSeen(entries, wantedFields)

        /* 3) Build the list of errors: missing field/value pairs          */
        val relationErrors = mutableListOf<RelationError>()

        info.entries.forEach { entry ->
            entry.ids.forEach { (field, values) ->
                values.forEach { value ->
                    if (seen[field]?.contains(value) != true) {
                        relationErrors += RelationError(
                            relation = "$field/$value",
                            selfLink = entry.selfLink
                        )
                    }
                }
            }
        }

        return RelationReport(baseUrl = info.url, relationErrors = relationErrors)
    }

    /** Traverse every resource and store the identifikatorverdi for the
     *  fields we care about, using case-insensitive key matching.        */
    private fun collectSeen(
        resources: Collection<JsonNode>,
        wantedFields: Set<String>
    ): Map<String, MutableSet<String>> = buildMap {

        resources.forEach { node ->
            wantedFields.forEach { field ->
                node.findCaseInsensitive(field)              // the object { identifikatorverdi = "…" }
                    ?.path("identifikatorverdi")
                    ?.takeIf(JsonNode::isTextual)
                    ?.asText()
                    ?.let { idValue ->
                        getOrPut(field) { mutableSetOf() }.add(idValue)
                    }
            }
        }
    }

    /** Utility: find a child property ignoring upper/lower case           */
    private fun JsonNode.findCaseInsensitive(key: String): JsonNode? =
        fields().asSequence().firstOrNull { it.key.equals(key, true) }?.value
}

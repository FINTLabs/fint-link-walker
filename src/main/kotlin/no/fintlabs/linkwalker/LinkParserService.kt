package no.fintlabs.linkwalker

import com.fasterxml.jackson.databind.JsonNode
import no.fintlabs.linkwalker.model.Entry
import no.fintlabs.linkwalker.model.LinkInfo
import no.fintlabs.linkwalker.model.RelationEntry
import no.fintlabs.linkwalker.model.RelationReport
import org.springframework.stereotype.Service

@Service
class LinkParserService {

    fun parseRelations(info: LinkInfo, entries: Collection<JsonNode>): RelationReport {
        val wantedFields = info.entries.flatMap { it.ids.keys }.toSet()
        val seenIds = seenIdentificators(entries, wantedFields)

        val relationErrors = linkedSetOf<RelationEntry>()
        val unknownLinks = linkedSetOf<RelationEntry>()

        info.entries.forEach { entry ->
            collectRelationErrors(entry, seenIds, relationErrors)
            collectUnknownLinks(entry, unknownLinks)
        }

        println(relationErrors.size)

        return RelationReport(
            info.url,
            relationErrors.toMutableList(),
            unknownLinks.toMutableList()
        )
    }

    private fun collectRelationErrors(
        entry: Entry,
        seen: Map<String, MutableSet<String>>,
        relationErrors: LinkedHashSet<RelationEntry>
    ) = entry.ids.forEach { (field, values) ->
        values.forEach { value ->
            if (seen[field]?.contains(value) != true) {
                relationErrors += RelationEntry("$field/$value", entry.selfLink)
            }
        }
    }

    private fun collectUnknownLinks(entry: Entry, unknownLinks: LinkedHashSet<RelationEntry>) =
        entry.malformedHrefs.forEach { badHref ->
            unknownLinks.add(RelationEntry(badHref, entry.selfLink))
        }

    private fun seenIdentificators(
        resources: Collection<JsonNode>,
        wantedFields: Set<String>
    ): Map<String, MutableSet<String>> = buildMap {
        resources.forEach { node ->
            wantedFields.forEach { field ->
                node.findCaseInsensitive(field)
                    ?.path("identifikatorverdi")
                    ?.takeIf(JsonNode::isTextual)
                    ?.asText()
                    ?.let { idValue ->
                        getOrPut(field) { mutableSetOf() }.add(idValue)
                    }
            }
        }
    }

    private fun JsonNode.findCaseInsensitive(key: String): JsonNode? =
        fields().asSequence().firstOrNull { it.key.equals(key, true) }?.value
}

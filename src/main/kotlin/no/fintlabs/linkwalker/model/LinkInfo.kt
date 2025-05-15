package no.fintlabs.linkwalker.model

import com.fasterxml.jackson.databind.JsonNode

data class LinkInfo(
    val url: String,
    val ids: MutableMap<String, MutableSet<String>>,
    var relationError: Boolean = false
) {
    val idCount: Int
        get() = ids.values.sumOf { it.size }

    fun validateAgainst(entries: List<JsonNode>) {
        entries.forEach { entry ->
            ids.forEach { (field, wantedIds) ->
                entry.getIgnoreCase(field)
                    ?.get("identifikatorverdi")
                    ?.takeIf(JsonNode::isTextual)
                    ?.asText()
                    ?.let(wantedIds::remove)
            }
        }
        relationError = ids.values.any { it.isNotEmpty() }
    }

    private fun JsonNode.getIgnoreCase(key: String): JsonNode? =
        fields().asSequence()
            .firstOrNull { (jsonKey, _) -> jsonKey.equals(key, ignoreCase = true) }
            ?.value
}
package no.fintlabs.linkwalker.model

import com.fasterxml.jackson.databind.JsonNode

data class LinkInfo(
    val url: String,
    val ids: MutableMap<String, MutableSet<String>>,
    val relationErrors: MutableMap<String, String> = mutableMapOf(),
) {
    val errorCount get() = ids.values.sumOf { it.size }
    val hasRelationError get() = relationErrors.isNotEmpty()

    fun validateAgainst(entries: List<JsonNode>) {
        entries.forEach { entry ->
            val selfLink = entry["_links"]?.get("self")?.firstOrNull()?.get("href")?.asText()
            ids.forEach { (field, wanted) ->
                entry.getIgnoreCase(field)
                    ?.get("identifikatorverdi")
                    ?.takeIf(JsonNode::isTextual)
                    ?.asText()
                    ?.let { id ->
                        if (id in wanted && selfLink != null)
                            relationErrors.putIfAbsent("$field/$id", selfLink)
                    }
            }
        }
    }

    private fun JsonNode.getIgnoreCase(key: String): JsonNode? =
        fields().asSequence()
            .firstOrNull { (jsonKey, _) -> jsonKey.equals(key, ignoreCase = true) }
            ?.value
}
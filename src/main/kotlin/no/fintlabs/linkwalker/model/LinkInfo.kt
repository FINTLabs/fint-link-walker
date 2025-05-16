package no.fintlabs.linkwalker.model

import com.fasterxml.jackson.databind.JsonNode

data class LinkInfo(
    val url: String,
    val ids: Map<String, Set<String>>
) {
    val idCount: Int
        get() = ids.values.sumOf { it.size }

    companion object {
        private val hrefRegex =
            Regex("""(https?://[^/]+/(?:[^/]+/)*[^/]+)/([^/]+)/([^/?#]+)""")

        fun fromEntries(entries: List<JsonNode>): MutableList<LinkInfo> =
            getLinkParts(entries)
                .groupBy { it.baseUrl }
                .map { (url, parts) ->
                    val ids = parts
                        .groupBy { it.field }
                        .mapValues { (_, list) -> list.map { it.value }.toSet() }
                    LinkInfo(url, ids)
                }.toMutableList()

        private fun getLinkParts(entries: List<JsonNode>) =
            entries.asSequence()
                .flatMap { entry ->
                    entry.path("_links").fields().asSequence()
                        .flatMap { (field, arrNode) ->
                            arrNode.asSequence()
                                .mapNotNull { linkNode ->
                                    linkNode["href"]?.asText()
                                        ?.let(hrefRegex::find)
                                        ?.destructured
                                        ?.let { (baseUrl, f, v) ->
                                            LinkPart(baseUrl, f, v)
                                        }
                                }
                        }
                }

        private data class LinkPart(
            val baseUrl: String,
            val field: String,
            val value: String
        )
    }
}

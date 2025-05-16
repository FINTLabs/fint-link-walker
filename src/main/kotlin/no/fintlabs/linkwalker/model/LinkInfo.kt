package no.fintlabs.linkwalker.model

import com.fasterxml.jackson.databind.JsonNode

data class LinkInfo(
    val url: String,
    val entries: MutableList<Entry>
) {
    companion object {
        private val hrefRegex =
            Regex("""(https?://[^/]+/(?:[^/]+/){2}[^/]+)/([^/]+)/([^/?#]+)""")

        fun fromEntries(resources: List<JsonNode>): MutableList<LinkInfo> =
            resources.fold(mutableMapOf<String, LinkInfo>()) { acc, resource ->
                val links = resource["_links"]
                val self  = links["self"]?.elements()?.asSequence()
                    ?.mapNotNull { it["href"]?.asText() }
                    ?.firstOrNull() ?: "<no-self-link>"

                val idsByUrl = mutableMapOf<String, MutableMap<String, MutableSet<String>>>()
                val malformed = mutableListOf<String>()

                links.fields().forEach { (_, rels) ->
                    rels.forEach { node ->
                        val href = node["href"]?.asText() ?: return@forEach
                        val m = hrefRegex.matchEntire(href)
                        if (m == null) {
                            malformed += href
                            return@forEach
                        }
                        val (urlKey, field, value) = m.destructured
                        idsByUrl
                            .getOrPut(urlKey) { mutableMapOf() }
                            .getOrPut(field) { mutableSetOf() }
                            .add(value)
                    }
                }

                if (idsByUrl.isEmpty() && malformed.isNotEmpty()) {
                    acc.getOrPut(self) { LinkInfo(self, mutableListOf()) }
                        .entries += Entry(self, emptyMap(), malformed)
                } else {
                    idsByUrl.forEach { (urlKey, fields) ->
                        acc.getOrPut(urlKey) { LinkInfo(urlKey, mutableListOf()) }
                            .entries += Entry(self, fields, malformed.toMutableList())
                    }
                }
                acc
            }.values.toMutableList()
    }
}

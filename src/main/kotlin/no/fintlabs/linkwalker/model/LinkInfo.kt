package no.fintlabs.linkwalker.model

import com.fasterxml.jackson.databind.JsonNode

data class LinkInfo(
    val url: String,
    val entries: MutableList<Entry>
) {
    companion object {
        private val hrefRegex =
            Regex("""(https?://[^/]+/(?:[^/]+/){2}[^/]+)/([^/]+)/([^/?#]+)""")

        fun fromEntries(entries: List<JsonNode>): MutableList<LinkInfo> {

            val linkInfoByUrl = mutableMapOf<String, LinkInfo>()

            entries.forEach { entry ->
                val linksNode = entry.path("_links")

                // grab the *first* self link safely
                val selfLink = linksNode.path("self")
                    .elements()
                    .asSequence()
                    .mapNotNull { it.get("href")?.asText() }
                    .firstOrNull() ?: "<no-self-link>"

                /*  Gather the ids we found in this resource,
                    grouped by the urlKey they belong to.           */
                val tmpIdsByUrl =
                    mutableMapOf<String, MutableMap<String, MutableSet<String>>>()

                linksNode.fields().forEach { (_, relArray) ->
                    relArray.elements().forEach { linkNode ->
                        val href = linkNode.path("href").asText(null) ?: return@forEach
                        val m = hrefRegex.matchEntire(href) ?: return@forEach

                        val urlKey = m.groupValues[1]   // https://…/seg1/seg2/seg3
                        val field = m.groupValues[2]   // systemid / personid / …
                        val value = m.groupValues[3]   // 246167480 / …

                        val fieldMap = tmpIdsByUrl.getOrPut(urlKey) { mutableMapOf() }
                        fieldMap.getOrPut(field) { mutableSetOf() }.add(value)
                    }
                }

                // convert that tmp structure into real Entry objects
                tmpIdsByUrl.forEach { (urlKey, fieldMap) ->
                    val linkInfo = linkInfoByUrl.getOrPut(urlKey) {
                        LinkInfo(urlKey, mutableListOf())
                    }
                    linkInfo.entries += Entry(selfLink, fieldMap)
                }
            }

            return linkInfoByUrl.values.toMutableList()
        }
    }
}

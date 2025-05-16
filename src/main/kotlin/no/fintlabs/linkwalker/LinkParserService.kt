package no.fintlabs.linkwalker

import com.fasterxml.jackson.databind.JsonNode
import no.fintlabs.linkwalker.model.LinkInfo
import org.springframework.stereotype.Service

@Service
class LinkParserService {

    private val hrefRegex =
        Regex("""(https?://[^/]+/(?:[^/]+/)*[^/]+)/([^/]+)/([^/?#]+)""")

    fun collectLinkInfos(entries: List<JsonNode>): MutableList<LinkInfo> {
        val temp = mutableMapOf<String, LinkInfo>()

        entries.forEach { entry ->
            entry.path("_links").fields().forEach { (_, arrNode) ->
                arrNode.forEach { link ->
                    val href = link["href"]?.asText() ?: return@forEach
                    val m = hrefRegex.find(href) ?: return@forEach

                    val url = m.groupValues[1]
                    val field = m.groupValues[2]
                    val value = m.groupValues[3]

                    val info = temp.getOrPut(url) {
                        LinkInfo(url, mutableMapOf())
                    }

                    val set = info.ids.getOrPut(field) { mutableSetOf() }
                    set.add(value)
                }
            }
        }

        return temp.values.toMutableList()
    }
}
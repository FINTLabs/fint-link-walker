package no.novari.linkwalker.reader

import no.novari.linkwalker.config.JacksonConfig
import no.novari.linkwalker.report.LinkGroup
import no.novari.linkwalker.report.LinkScope
import no.novari.linkwalker.report.ResourceSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScanSummaryJsonTest {

    private val mapper = JacksonConfig().objectMapper()

    @Test
    fun `links survive a round trip with scope in its wire form`() {
        val resource = ResourceSummary(
            resource = "person",
            totalRecords = 1,
            totalRefs = 2,
            brokenLinkCount = 1,
            integrityPercent = 50.0,
            byProblemType = mapOf("missing-resource" to 1L),
            links = listOf(
                LinkGroup(LinkScope.WithinDomain, "utdanning_elev", 2, mapOf("missing-resource" to 1L)),
                LinkGroup(LinkScope.NotCovered, "administrasjon_personal", 7),
            ),
        )

        val json = mapper.writeValueAsString(resource)

        assertTrue("\"scope\":\"not_covered\"" in json, json)
        assertEquals(resource, mapper.readValue(json, ResourceSummary::class.java))
    }

    @Test
    fun `summaries written before links existed still load`() {
        val json = """
            {"resource":"elev","totalRecords":1,"totalRefs":2,"brokenLinkCount":0,
             "integrityPercent":100.0,"byProblemType":{}}
        """.trimIndent()

        assertEquals(emptyList<LinkGroup>(), mapper.readValue(json, ResourceSummary::class.java).links)
    }
}

package no.fintlabs.linkwalker.model

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.fintlabs.linkwalker.model.LinkInfo.Companion.fromEntries
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LinkInfoFromEntriesTest {

    private val mapper = jacksonObjectMapper()

    private val r1 = """
        {
          "systemId": { "identifikatorverdi": "42" },
          "_links": {
            "self": [{ "href": "https://api.f.no/a/b/c/systemid/42" }],
            "foo" : [{ "href": "https://api.f.no/a/b/c/systemid/42" }],
            "bar" : [{ "href": "https://api.f.no/a/d/e/systemid/99" }]
          }
        }
    """.trimIndent()

    private val r2 = """
        {
          "systemId": { "identifikatorverdi": "43" },
          "_links": {
            "self": [{ "href": "https://api.f.no/a/b/c/systemid/43" }],
            "foo" : [{ "href": "https://api.f.no/a/b/c/systemid/43" }]
          }
        }
    """.trimIndent()

    private val r3 = """
        {
          "_links": {
            "self" : [{ "href": "https://api.f.no/x/y/z/personid/7" }],
            "baz"  : [{ "href": "https://api.f.no/x/y/z/personid/7" }]
          }
        }
    """.trimIndent()

    /** Resource 4: no `self` link – exercise the “\<no-self-link\>” fallback */
    private val r4 = """
        {
          "_links": {
            "baz": [{ "href": "https://api.f.no/a/d/e/systemid/100" }]
          }
        }
    """.trimIndent()

    @Test
    fun `fromEntries creates one LinkInfo per distinct baseUrl`() {
        val infos = fromEntries(
            listOf(r1, r2, r3, r4).map { mapper.readTree(it) }
        )

        assertEquals(3, infos.size)
        assertTrue(infos.any { it.url.endsWith("/a/b/c") })
        assertTrue(infos.any { it.url.endsWith("/a/d/e") })
        assertTrue(infos.any { it.url.endsWith("/x/y/z") })
    }

    @Test
    fun `resources with same baseUrl are merged into one LinkInfo with multiple entries`() {
        val infos = fromEntries(listOf(mapper.readTree(r1), mapper.readTree(r2)))

        val abc = infos.single { it.url.endsWith("/a/b/c") }
        assertEquals(2, abc.entries.size)

        val idsR1 = abc.entries.first { it.selfLink.endsWith("42") }.ids["systemid"]
        val idsR2 = abc.entries.first { it.selfLink.endsWith("43") }.ids["systemid"]

        assertEquals(setOf("42"), idsR1)
        assertEquals(setOf("43"), idsR2)
    }

    @Test
    fun `idCount reflects the number of id values in an entry`() {
        val json = """
            {
              "_links": {
                "self"     : [{ "href": "https://api.f.no/a/b/c/systemid/55" }],
                "systemid" : [{ "href": "https://api.f.no/a/b/c/systemid/55" }],
                "personid" : [{ "href": "https://api.f.no/a/b/c/personid/99" }]
              }
            }
        """.trimIndent()

        val info = fromEntries(listOf(mapper.readTree(json))).single()
        val entry = info.entries.single()

        assertEquals(2, entry.idCount)          // 55 + 99
        assertEquals(setOf("systemid", "personid"), entry.ids.keys)
    }

    @Test
    fun `missing self link results in placeholder selfLink`() {
        val info = fromEntries(listOf(mapper.readTree(r4))).single()
        val entry = info.entries.single()

        assertEquals("<no-self-link>", entry.selfLink)
    }
}

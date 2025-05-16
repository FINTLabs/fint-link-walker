package no.fintlabs.linkwalker

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.test.runTest
import no.fintlabs.linkwalker.model.Entry
import no.fintlabs.linkwalker.model.LinkInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LinkParserServiceTest {

    private val parser = LinkParserService()
    private val mapper = jacksonObjectMapper()

    @Test
    fun `all ids present gives empty error list`() = runTest {
        val resources = listOf(
            node("""{ "systemId": { "identifikatorverdi": "123" } }""")
        )

        val info = linkInfoFor("systemId" to "123")

        val report = parser.parseRelations(info, resources)

        assertTrue(report.relationErrors.isEmpty())
        assertEquals(0, report.errorCount)
        assertFalse(report.hasErrors)
    }

    @Test
    fun `missing id (case-insensitive field) is reported`() = runTest {
        val resources = listOf(
            node("""{ "SYSTEMID": { "identifikatorverdi": "ABC" } }""")
        )

        val info = linkInfoFor("systemId" to "XYZ")

        val report = parser.parseRelations(info, resources)

        assertEquals(1, report.errorCount)
        assertEquals("systemId/XYZ", report.relationErrors.single().relation)
        assertTrue(report.hasErrors)
    }

    @Test
    fun `value comparison is case-sensitive`() = runTest {
        val resources = listOf(
            node("""{ "systemId": { "identifikatorverdi": "ABC" } }""")
        )

        val info = linkInfoFor("systemId" to "abc")

        val report = parser.parseRelations(info, resources)

        assertEquals(1, report.errorCount, "Different letter-case should count as missing")
    }

    @Test
    fun `multiple expected ids → correct errorCount when some are missing`() = runTest {
        val resources = listOf(
            node("""{ 
                   "systemId": { "identifikatorverdi": "111" },
                   "personId": { "identifikatorverdi": "222" }
            }""")
        )

        val info = linkInfoFor(
            "systemId" to "111",
            "personId" to "222",
            "fooId"    to "BAR"
        )

        val report = parser.parseRelations(info, resources)

        assertEquals(1, report.errorCount)
        assertEquals("fooId/BAR", report.relationErrors.single().relation)
    }

    @Test
    fun `non-textual identifikatorverdi is ignored and treated as missing`() = runTest {
        val resources = listOf(
            node("""{ "systemId": { "identifikatorverdi": 42 } }""")
        )

        val info = linkInfoFor("systemId" to "42")

        val report = parser.parseRelations(info, resources)

        assertEquals(1, report.errorCount)
        assertEquals("systemId/42", report.relationErrors.single().relation)
    }

    @Test
    fun `ids may be spread across several resources`() = runTest {
        val resources = listOf(
            node("""{ "systemId": { "identifikatorverdi": "AAA" } }"""),
            node("""{ "personId": { "identifikatorverdi": "BBB" } }""")
        )

        val info = linkInfoFor(
            "systemId" to "AAA",
            "personId" to "BBB",
            "extraId"  to "CCC"
        )

        val report = parser.parseRelations(info, resources)

        assertEquals(1, report.errorCount)
        assertEquals(listOf("extraId/CCC"), report.relationErrors.map { it.relation })
    }

    private fun linkInfoFor(vararg pairs: Pair<String, String>) =
        LinkInfo(
            url = "https://host/a/b/c",
            entries = mutableListOf(
                Entry(
                    selfLink = "https://host/a/b/c/self",
                    ids = pairs.groupBy({ it.first }) { it.second }.mapValues { it.value.toSet() }
                )
            )
        )

    private fun node(json: String) = mapper.readTree(json)

}

package no.novari.linkwalker.index

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.novari.linkwalker.config.LinkWalkerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class RecordExtractorTest {

    private val mapper = jacksonObjectMapper().findAndRegisterModules()

    @Test
    fun `extracts canonical keys, outbound refs, and malformed hrefs`() {
        val json = """
            {
              "_links": {
                "self": [{ "href": "https://api.f.no/utdanning/elev/elev/systemid/abc" }],
                "person": [{ "href": "https://api.f.no/utdanning/elev/person/systemid/p-1" }],
                "klasse": [{ "href": "https://api.f.no/utdanning/elev/klasse/systemid/k-1" }],
                "broken": [{ "href": "garbage-href" }]
              }
            }
        """.trimIndent()

        val record = extractor().extract(mapper.readTree(json), "utdanning_elev", "elev")

        assertEquals(
            listOf("https://api.f.no/utdanning/elev/elev/systemid/abc"),
            record.canonicalKeys,
        )
        assertEquals(2, record.outboundRefs.size)
        assertEquals(setOf("person", "klasse"), record.outboundRefs.map { it.relationName }.toSet())
        assertEquals(listOf("garbage-href"), record.malformedHrefs)
    }

    @Test
    fun `excludes configured relations entirely`() {
        val json = """
            {
              "_links": {
                "self": [{ "href": "https://api.f.no/utdanning/elev/elev/systemid/abc" }],
                "vigoreferanse": [{ "href": "https://api.f.no/utdanning/elev/elev/systemid/x" }]
              }
            }
        """.trimIndent()

        val record = extractor(excludeRelations = listOf("vigoreferanse"))
            .extract(mapper.readTree(json), "utdanning_elev", "elev")

        assertTrue(record.outboundRefs.isEmpty())
        assertTrue(record.malformedHrefs.isEmpty())
    }

    @Test
    fun `canonicalize URL-decodes percent-encoded characters`() {
        val canonical = extractor().canonicalize(
            "https://api.f.no/utdanning/vurdering/karakterverdi/systemid/ap%3a%3aapproved"
        )
        assertEquals(
            "https://api.f.no/utdanning/vurdering/karakterverdi/systemid/ap::approved",
            canonical,
        )
    }

    @Test
    fun `canonicalize lowercases path but preserves identifier value case`() {
        val canonical = extractor().canonicalize(
            "https://API.felleskomponent.no/Utdanning/Timeplan/Fag/SystemId/00HH12A"
        )
        assertEquals(
            "https://api.felleskomponent.no/utdanning/timeplan/fag/systemid/00HH12A",
            canonical,
        )
    }

    @Test
    fun `canonicalize handles 6-segment URLs (sub-namespaced resources)`() {
        val canonical = extractor().canonicalize(
            "https://api.f.no/felles/kodeverk/iso/kjonn/systemid/2"
        )
        assertEquals(
            "https://api.f.no/felles/kodeverk/iso/kjonn/systemid/2",
            canonical,
        )
    }

    @Test
    fun `record with no _links returns empty fields`() {
        val record = extractor().extract(mapper.readTree("{}"), "any", "any")
        assertTrue(record.canonicalKeys.isEmpty())
        assertTrue(record.outboundRefs.isEmpty())
        assertTrue(record.malformedHrefs.isEmpty())
    }

    @Test
    fun `multiple self hrefs all become canonical keys`() {
        val json = """
            {
              "_links": {
                "self": [
                  { "href": "https://api.f.no/utdanning/elev/person/systemid/p-1" },
                  { "href": "https://api.f.no/utdanning/elev/person/fodselsnummer/12345678901" }
                ]
              }
            }
        """.trimIndent()
        val record = extractor().extract(mapper.readTree(json), "utdanning_elev", "person")

        assertEquals(2, record.canonicalKeys.size)
        assertTrue(record.canonicalKeys.any { it.endsWith("systemid/p-1") })
        assertTrue(record.canonicalKeys.any { it.endsWith("fodselsnummer/12345678901") })
    }

    @Test
    fun `self relation is not treated as an outbound ref`() {
        val json = """
            {
              "_links": {
                "self": [{ "href": "https://api.f.no/utdanning/elev/elev/systemid/abc" }]
              }
            }
        """.trimIndent()
        val record = extractor().extract(mapper.readTree(json), "utdanning_elev", "elev")
        assertTrue(record.outboundRefs.isEmpty())
    }

    @Test
    fun `extractFromFile streams every entry from _embedded _entries array`(@TempDir tmp: Path) {
        val file = tmp.resolve("page.json")
        // Three entries: regression test for Jackson 3's FAIL_ON_TRAILING_TOKENS,
        // which broke iteration after the first element when we used
        // mapper.readTree(parser) instead of parser.readValueAsTree().
        file.writeText(
            """
            {
              "_embedded": {
                "_entries": [
                  {
                    "_links": {
                      "self": [{ "href": "https://api.f.no/utdanning/elev/elev/systemid/a" }],
                      "person": [{ "href": "https://api.f.no/utdanning/elev/person/systemid/p-1" }]
                    }
                  },
                  {
                    "_links": {
                      "self": [{ "href": "https://api.f.no/utdanning/elev/elev/systemid/b" }],
                      "person": [{ "href": "https://api.f.no/utdanning/elev/person/systemid/p-2" }]
                    }
                  },
                  {
                    "_links": {
                      "self": [{ "href": "https://api.f.no/utdanning/elev/elev/systemid/c" }],
                      "klasse": [{ "href": "https://api.f.no/utdanning/elev/klasse/systemid/k-1" }]
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val records = extractor().extractFromFile(file, "utdanning_elev", "elev")

        assertEquals(3, records.size, "All three entries should have been extracted")
        assertEquals(
            listOf("systemid/a", "systemid/b", "systemid/c"),
            records.map { it.canonicalKeys.single().substringAfterLast('/').let { id -> "systemid/$id" } },
        )
        assertEquals(setOf("person", "klasse"), records.flatMap { it.outboundRefs.map { ref -> ref.relationName } }.toSet())
    }

    @Test
    fun `extractFromFile returns empty when _embedded _entries is empty`(@TempDir tmp: Path) {
        val file = tmp.resolve("empty.json")
        file.writeText("""{"_embedded":{"_entries":[]}}""")

        val records = extractor().extractFromFile(file, "utdanning_elev", "elev")

        assertTrue(records.isEmpty())
    }

    @Test
    fun `extractFromFile returns empty when _embedded _entries is missing`(@TempDir tmp: Path) {
        val file = tmp.resolve("no-entries.json")
        file.writeText("""{"_embedded":{"otherField":[]}}""")

        val records = extractor().extractFromFile(file, "utdanning_elev", "elev")

        assertTrue(records.isEmpty())
    }

    private fun extractor(excludeRelations: List<String> = emptyList()) =
        RecordExtractor(mapper, LinkWalkerConfig(excludeRelations = excludeRelations))
}

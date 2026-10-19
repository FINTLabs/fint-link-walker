package no.novari.linkwalker.report

import no.novari.linkwalker.OrgId
import no.novari.linkwalker.index.MinimalRecord
import no.novari.linkwalker.index.OutboundRef
import no.novari.linkwalker.index.ResourceKey
import no.novari.linkwalker.index.TenantIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SummaryBuilderTest {

    private val builder = SummaryBuilder()

    @Test
    fun `aggregates totals across records and problems`() {
        val records = listOf(
            recordOf("utdanning_elev", "elev", refs = 5),
            recordOf("utdanning_elev", "elev", refs = 3),
            recordOf("utdanning_elev", "person", refs = 2, malformed = 1),
        )
        val problems = listOf(
            problem("utdanning_elev", "elev", "missing-resource"),
            problem("utdanning_elev", "elev", "missing-resource"),
            problem("utdanning_elev", "person", "unknown-link"),
        )

        val summary = builder.build(indexOf(records), problems)

        assertEquals(3L, summary.totalRecords)
        assertEquals(11L, summary.totalRefs) // 5 + 3 + (2 + 1 malformed) = 11
        assertEquals(3L, summary.brokenLinkCount)
        assertEquals(
            mapOf("missing-resource" to 2L, "unknown-link" to 1L),
            summary.byProblemType,
        )
    }

    @Test
    fun `groups counts per component`() {
        val records = listOf(
            recordOf("utdanning_elev", "elev", refs = 10),
            recordOf("utdanning_vurdering", "fag", refs = 5),
        )
        val problems = listOf(
            problem("utdanning_elev", "elev", "missing-resource"),
            problem("utdanning_vurdering", "fag", "missing-back-link-adapter"),
            problem("utdanning_vurdering", "fag", "missing-back-link-adapter"),
        )

        val summary = builder.build(indexOf(records), problems)
        val byComp = summary.components.associateBy { it.component }

        assertEquals(1L, byComp["utdanning_elev"]?.brokenLinkCount)
        assertEquals(2L, byComp["utdanning_vurdering"]?.brokenLinkCount)
        assertEquals(10L, byComp["utdanning_elev"]?.totalRefs)
        assertEquals(5L, byComp["utdanning_vurdering"]?.totalRefs)
    }

    @Test
    fun `100 percent integrity when no broken problems`() {
        val records = listOf(recordOf("c", "r", refs = 5))
        val summary = builder.build(indexOf(records), emptyList())
        assertEquals(100.0, summary.integrityPercent)
    }

    @Test
    fun `null integrity when there are no refs to measure`() {
        val records = listOf(recordOf("c", "r", refs = 0))
        val summary = builder.build(indexOf(records), emptyList())
        assertNull(summary.integrityPercent, "An empty / failed scan must not claim 100% integrity")
    }

    @Test
    fun `links to resources that were not fetched are left out of integrity`() {
        val record = MinimalRecord(
            component = "utdanning_elev",
            resourceName = "person",
            canonicalKeys = listOf("https://host/utdanning/elev/person/systemid/p-1"),
            outboundRefs = listOf(
                OutboundRef("elev", "$TARGET/e-1"),
                OutboundRef("kjonn", "https://host/felles/kodeverk/iso/kjonn/systemid/1"),
                OutboundRef("personalressurs", "https://host/administrasjon/personal/personalressurs/ansattnummer/9"),
            ),
            malformedHrefs = emptyList(),
        )

        val summary = builder.build(indexOf(listOf(record)), emptyList())
        val person = summary.components.single().resources.single()

        assertEquals(1L, summary.totalRefs)
        assertEquals(100.0, summary.integrityPercent)
        assertEquals(
            listOf(
                LinkGroup(LinkScope.WithinDomain, "utdanning_elev", 1),
                LinkGroup(LinkScope.NotCovered, "administrasjon_personal", 1),
                LinkGroup(LinkScope.NotCovered, "felles_kodeverk", 1),
            ),
            person.links,
        )
    }

    @Test
    fun `links and errors are grouped by scope and target component`() {
        val record = MinimalRecord(
            component = "utdanning_elev",
            resourceName = "person",
            canonicalKeys = listOf("https://host/utdanning/elev/person/systemid/p-1"),
            outboundRefs = listOf(
                OutboundRef("elev", "$TARGET/e-1"),
                OutboundRef("elev", "$TARGET/e-2"),
                OutboundRef("kjonn", "https://host/felles/kodeverk/iso/kjonn/systemid/1"),
            ),
            malformedHrefs = listOf("garbage"),
        )
        val problems = listOf(
            problem("utdanning_elev", "person", "missing-resource"),
            problem("utdanning_elev", "person", "unknown-link").copy(targetHref = "garbage"),
            problem("utdanning_elev", "person", "missing-resource")
                .copy(targetHref = "https://host/felles/kodeverk/iso/kjonn/systemid/1"),
        )
        val fetched = setOf(
            ResourceKey.of("utdanning_elev", "elev"),
            ResourceKey.of("felles_kodeverk", "kjonn"),
        )

        val person = builder.build(indexOf(listOf(record), fetched), problems)
            .components.single().resources.single()

        assertEquals(
            listOf(
                LinkGroup(LinkScope.WithinDomain, "unknown", 1, mapOf("unknown-link" to 1L)),
                LinkGroup(LinkScope.WithinDomain, "utdanning_elev", 2, mapOf("missing-resource" to 1L)),
                LinkGroup(LinkScope.CrossDomain, "felles_kodeverk", 1, mapOf("missing-resource" to 1L)),
            ),
            person.links,
        )
        assertEquals(4L, person.totalRefs)
        assertEquals(25.0, person.integrityPercent)
    }

    @Test
    fun `components sorted by brokenLinkCount descending`() {
        val records = listOf(
            recordOf("low", "r", refs = 1),
            recordOf("mid", "r", refs = 1),
            recordOf("high", "r", refs = 1),
        )
        val problems = listOf(
            problem("high", "r", "missing-resource"),
            problem("high", "r", "missing-resource"),
            problem("high", "r", "missing-resource"),
            problem("mid", "r", "missing-resource"),
            problem("mid", "r", "missing-resource"),
            problem("low", "r", "missing-resource"),
        )

        val summary = builder.build(indexOf(records), problems)

        assertEquals(listOf("high", "mid", "low"), summary.components.map { it.component })
    }

    private var idCounter = 0

    private fun recordOf(
        component: String,
        resource: String,
        refs: Int,
        malformed: Int = 0,
    ) = MinimalRecord(
        component = component,
        resourceName = resource,
        canonicalKeys = listOf("https://host/$component/$resource/systemid/r-${idCounter++}"),
        outboundRefs = (1..refs).map { OutboundRef("rel-$it", "$TARGET/v-$it") },
        malformedHrefs = (1..malformed).map { "garbage-$it" },
    )

    private fun problem(component: String, resource: String, problemType: String) = ReportProblem(
        orgId = OrgId("test"),
        component = component,
        resource = resource,
        problemType = ProblemType.parseOrNull(problemType) ?: error("unknown wire: $problemType"),
        sourceSelf = "https://host/$component/$resource/systemid/x",
        targetHref = "$TARGET/y",
    )

    private fun indexOf(
        records: List<MinimalRecord>,
        fetched: Set<ResourceKey> = setOf(ResourceKey.of("utdanning_elev", "elev")),
    ) = TenantIndex(records = records, byKey = emptyMap(), fetchedResources = fetched)

    private companion object {
        const val TARGET = "https://host/utdanning/elev/elev/systemid"
    }
}

package no.novari.linkwalker.report

import no.novari.linkwalker.index.MinimalRecord
import no.novari.linkwalker.index.OutboundRef
import no.novari.linkwalker.index.TenantIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SummaryBuilderTest {

    private val builder = SummaryBuilder()

    @Test
    fun `aggregates totals across records and rows`() {
        val records = listOf(
            recordOf("utdanning_elev", "elev", refs = 5),
            recordOf("utdanning_elev", "elev", refs = 3),
            recordOf("utdanning_elev", "person", refs = 2, malformed = 1),
        )
        val rows = listOf(
            row("utdanning_elev", "elev", "missing-resource"),
            row("utdanning_elev", "elev", "missing-resource"),
            row("utdanning_elev", "person", "unknown-link"),
        )

        val summary = builder.build(indexOf(records), rows)

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
        val rows = listOf(
            row("utdanning_elev", "elev", "missing-resource"),
            row("utdanning_vurdering", "fag", "missing-back-link-provider"),
            row("utdanning_vurdering", "fag", "missing-back-link-provider"),
        )

        val summary = builder.build(indexOf(records), rows)
        val byComp = summary.components.associateBy { it.component }

        assertEquals(1L, byComp["utdanning_elev"]?.brokenLinkCount)
        assertEquals(2L, byComp["utdanning_vurdering"]?.brokenLinkCount)
        assertEquals(10L, byComp["utdanning_elev"]?.totalRefs)
        assertEquals(5L, byComp["utdanning_vurdering"]?.totalRefs)
    }

    @Test
    fun `100 percent integrity when no broken rows`() {
        val records = listOf(recordOf("c", "r", refs = 5))
        val summary = builder.build(indexOf(records), emptyList())
        assertEquals(100.0, summary.integrityPercent)
    }

    @Test
    fun `100 percent integrity when no refs`() {
        val records = listOf(recordOf("c", "r", refs = 0))
        val summary = builder.build(indexOf(records), emptyList())
        assertEquals(100.0, summary.integrityPercent)
    }

    @Test
    fun `components sorted by brokenLinkCount descending`() {
        val records = listOf(
            recordOf("low", "r", refs = 1),
            recordOf("mid", "r", refs = 1),
            recordOf("high", "r", refs = 1),
        )
        val rows = listOf(
            row("high", "r", "missing-resource"),
            row("high", "r", "missing-resource"),
            row("high", "r", "missing-resource"),
            row("mid", "r", "missing-resource"),
            row("mid", "r", "missing-resource"),
            row("low", "r", "missing-resource"),
        )

        val summary = builder.build(indexOf(records), rows)

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
        outboundRefs = (1..refs).map { OutboundRef("rel-$it", "https://host/target/systemid/v-$it") },
        malformedHrefs = (1..malformed).map { "garbage-$it" },
    )

    private fun row(component: String, resource: String, problemType: String) = ReportRow(
        tenant = "test",
        component = component,
        resource = resource,
        problemType = problemType,
        sourceSelf = "https://host/$component/$resource/systemid/x",
        targetHref = "https://host/target/systemid/y",
    )

    private fun indexOf(records: List<MinimalRecord>) =
        TenantIndex(records = records, byKey = emptyMap())
}

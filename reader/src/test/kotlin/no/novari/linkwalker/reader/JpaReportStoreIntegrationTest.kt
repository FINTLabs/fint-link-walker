package no.novari.linkwalker.reader

import no.novari.linkwalker.report.ComponentSummary
import no.novari.linkwalker.report.LatestReport
import no.novari.linkwalker.report.ReportRow
import no.novari.linkwalker.report.ReportStore
import no.novari.linkwalker.report.ResourceSummary
import no.novari.linkwalker.report.RowFilter
import no.novari.linkwalker.report.ScanSummary
import no.novari.linkwalker.report.jpa.ReportRowRepository
import no.novari.linkwalker.report.jpa.ReportSummaryRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "link-walker.storage.type=jpa",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/linkwalker",
        "spring.datasource.username=linkwalker",
        "spring.datasource.password=linkwalker",
        "spring.jpa.hibernate.ddl-auto=update",
    ],
)
class JpaReportStoreIntegrationTest @Autowired constructor(
    private val reportStore: ReportStore,
    private val summaryRepo: ReportSummaryRepository,
    private val rowRepo: ReportRowRepository,
) {

    @BeforeEach
    fun cleanup() {
        // deleteAllInBatch issues a single `DELETE FROM …` — deleteAll() fetches every
        // row first and OOMs against tables with real-scanner-sized data left behind.
        rowRepo.deleteAllInBatch()
        summaryRepo.deleteAllInBatch()
    }

    @Test
    fun `publish then read summary + paginated filtered rows`() {
        val orgId = "test_org"
        val rows = buildRows(orgId, components = listOf("utdanning_elev", "utdanning_vurdering"))
        val report = LatestReport(
            scanCompletedAt = Instant.now(),
            orgId = orgId,
            components = listOf("utdanning_elev", "utdanning_vurdering"),
            summary = sampleSummary(rows),
            rows = rows,
        )

        reportStore.publish(report)

        val summary = reportStore.getSummary(orgId)
        assertNotNull(summary)
        assertEquals(orgId, summary.orgId)
        assertEquals(rows.size.toLong(), summary.summary.brokenLinkCount)

        val firstPage = reportStore.findRows(orgId, RowFilter(), page = 0, size = 100)!!
        assertEquals(100, firstPage.rows.size)
        assertEquals(250L, firstPage.totalRows)
        assertEquals(3, firstPage.totalPages)

        val secondPage = reportStore.findRows(orgId, RowFilter(), page = 1, size = 100)!!
        assertEquals(100, secondPage.rows.size)

        val thirdPage = reportStore.findRows(orgId, RowFilter(), page = 2, size = 100)!!
        assertEquals(50, thirdPage.rows.size)

        // pages share no rows
        val firstIds = firstPage.rows.map { it.targetHref }.toSet()
        val secondIds = secondPage.rows.map { it.targetHref }.toSet()
        assertTrue(firstIds.intersect(secondIds).isEmpty())

        val onlyVurdering = reportStore.findRows(
            orgId,
            RowFilter(component = "utdanning_vurdering"),
            page = 0,
            size = 1000,
        )!!
        assertTrue(onlyVurdering.rows.all { it.component == "utdanning_vurdering" })
        assertEquals(125L, onlyVurdering.totalRows)

        val onlyMissing = reportStore.findRows(
            orgId,
            RowFilter(problemType = "missing-resource"),
            page = 0,
            size = 1000,
        )!!
        assertTrue(onlyMissing.rows.all { it.problemType == "missing-resource" })

        val unknownOrg = reportStore.findRows("does_not_exist", RowFilter(), 0, 10)
        assertNull(unknownOrg)
    }

    @Test
    fun `latest scan wins for same org-id`() {
        val orgId = "test_org"
        val older = LatestReport(
            scanCompletedAt = Instant.parse("2026-04-01T00:00:00Z"),
            orgId = orgId,
            components = listOf("utdanning_elev"),
            summary = ScanSummary(10, 100, 1, 99.0, mapOf("missing-resource" to 1L), emptyList()),
            rows = listOf(sampleRow(orgId, 0, "utdanning_elev")),
        )
        val newer = LatestReport(
            scanCompletedAt = Instant.parse("2026-05-01T00:00:00Z"),
            orgId = orgId,
            components = listOf("utdanning_elev"),
            summary = ScanSummary(20, 200, 5, 97.5, mapOf("missing-resource" to 5L), emptyList()),
            rows = (0..4).map { sampleRow(orgId, it, "utdanning_elev") },
        )

        reportStore.publish(older)
        reportStore.publish(newer)

        val summary = reportStore.getSummary(orgId)!!
        assertEquals(20L, summary.summary.totalRecords)
        assertEquals(5L, summary.summary.brokenLinkCount)

        val rows = reportStore.findRows(orgId, RowFilter(), 0, 100)!!
        assertEquals(5L, rows.totalRows)

        // Summaries kept for cross-scan history; rows pruned to just the latest scan.
        assertEquals(2, summaryRepo.findAll().count())
        assertEquals(5L, rowRepo.count())
    }

    @Test
    fun `publishing for one org does not retire another org's rows`() {
        val keepOrg = "keep_org"
        val replaceOrg = "replace_org"
        val keepRow = sampleRow(keepOrg, 0, "utdanning_elev")
        val older = LatestReport(
            scanCompletedAt = Instant.parse("2026-04-01T00:00:00Z"),
            orgId = replaceOrg,
            components = listOf("utdanning_elev"),
            summary = ScanSummary(10, 100, 1, 99.0, mapOf("missing-resource" to 1L), emptyList()),
            rows = listOf(sampleRow(replaceOrg, 0, "utdanning_elev")),
        )
        val keep = LatestReport(
            scanCompletedAt = Instant.parse("2026-04-15T00:00:00Z"),
            orgId = keepOrg,
            components = listOf("utdanning_elev"),
            summary = ScanSummary(10, 100, 1, 99.0, mapOf("missing-resource" to 1L), emptyList()),
            rows = listOf(keepRow),
        )
        val newer = LatestReport(
            scanCompletedAt = Instant.parse("2026-05-01T00:00:00Z"),
            orgId = replaceOrg,
            components = listOf("utdanning_elev"),
            summary = ScanSummary(20, 200, 2, 99.0, mapOf("missing-resource" to 2L), emptyList()),
            rows = (0..1).map { sampleRow(replaceOrg, it, "utdanning_elev") },
        )

        reportStore.publish(older)
        reportStore.publish(keep)
        reportStore.publish(newer)

        // keep_org's row survives; replace_org has only its newer scan's rows.
        assertEquals(3L, rowRepo.count())
        val keepRows = reportStore.findRows(keepOrg, RowFilter(), 0, 10)!!
        assertEquals(1L, keepRows.totalRows)
        val replaceRows = reportStore.findRows(replaceOrg, RowFilter(), 0, 10)!!
        assertEquals(2L, replaceRows.totalRows)
    }

    private fun buildRows(orgId: String, components: List<String>): List<ReportRow> {
        val problemTypes = listOf("missing-resource", "unknown-link", "missing-back-link-adapter")
        val resources = listOf("elevforhold", "vurdering")
        return (0 until 250).map { i ->
            ReportRow(
                orgId = orgId,
                component = components[i % components.size],
                resource = resources[i % resources.size],
                problemType = problemTypes[i % problemTypes.size],
                sourceSelf = "https://api.felleskomponent.no/source/$i",
                targetHref = "https://api.felleskomponent.no/target/$i",
                relationName = "rel-${i % 5}",
                expectedInverseName = null,
            )
        }
    }

    private fun sampleSummary(rows: List<ReportRow>): ScanSummary = ScanSummary(
        totalRecords = 1000L,
        totalRefs = 5000L,
        brokenLinkCount = rows.size.toLong(),
        integrityPercent = 95.0,
        byProblemType = rows.groupingBy { it.problemType }.eachCount().mapValues { it.value.toLong() },
        components = rows.groupBy { it.component }.map { (compName, compRows) ->
            ComponentSummary(
                component = compName,
                totalRecords = 500L,
                totalRefs = 2500L,
                brokenLinkCount = compRows.size.toLong(),
                integrityPercent = 95.0,
                byProblemType = compRows.groupingBy { it.problemType }.eachCount().mapValues { it.value.toLong() },
                resources = compRows.groupBy { it.resource }.map { (resName, resRows) ->
                    ResourceSummary(
                        resource = resName,
                        totalRecords = 250L,
                        totalRefs = 1250L,
                        brokenLinkCount = resRows.size.toLong(),
                        integrityPercent = 95.0,
                        byProblemType = resRows.groupingBy { it.problemType }.eachCount()
                            .mapValues { it.value.toLong() },
                    )
                },
            )
        },
    )

    private fun sampleRow(orgId: String, idx: Int, component: String): ReportRow = ReportRow(
        orgId = orgId,
        component = component,
        resource = "elevforhold",
        problemType = "missing-resource",
        sourceSelf = "https://api.felleskomponent.no/source/$idx",
        targetHref = "https://api.felleskomponent.no/target/$idx",
    )
}

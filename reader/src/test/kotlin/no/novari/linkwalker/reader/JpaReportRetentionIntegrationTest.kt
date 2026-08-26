package no.novari.linkwalker.reader

import no.novari.linkwalker.OrgId
import no.novari.linkwalker.report.LatestReport
import no.novari.linkwalker.report.ProblemFilter
import no.novari.linkwalker.report.ProblemType
import no.novari.linkwalker.report.PurgedScans
import no.novari.linkwalker.report.ReportProblem
import no.novari.linkwalker.report.ReportRetention
import no.novari.linkwalker.report.ReportStore
import no.novari.linkwalker.report.ScanSummary
import no.novari.linkwalker.report.jpa.ReportProblemRepository
import no.novari.linkwalker.report.jpa.ReportSummaryRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
class JpaReportRetentionIntegrationTest @Autowired constructor(
    private val reportStore: ReportStore,
    private val reportRetention: ReportRetention,
    private val summaryRepo: ReportSummaryRepository,
    private val problemRepo: ReportProblemRepository,
) {

    @BeforeEach
    fun cleanup() {
        problemRepo.deleteAllInBatch()
        summaryRepo.deleteAllInBatch()
    }

    @Test
    fun `purge keeps only the latest scan and leaves other orgs untouched`() {
        val orgA = OrgId("org_a")
        val orgB = OrgId("org_b")
        reportStore.publish(report(orgA, "2026-04-01T00:00:00Z", problemCount = 2))
        reportStore.publish(report(orgA, "2026-05-01T00:00:00Z", problemCount = 3))
        reportStore.publish(report(orgB, "2026-05-01T00:00:00Z", problemCount = 1))

        val purged = reportRetention.purgeOldScans(orgA)

        assertEquals(PurgedScans(summariesDeleted = 1, problemsDeleted = 2), purged)
        assertEquals(2L, summaryRepo.count())
        assertEquals(4L, problemRepo.count())

        val summaryA = reportStore.getSummary(orgA)
        assertNotNull(summaryA)
        assertEquals(Instant.parse("2026-05-01T00:00:00Z"), summaryA.scanCompletedAt)
        assertEquals(3L, reportStore.findProblems(orgA, ProblemFilter(), 0, 100)!!.totalProblems)
        assertEquals(1L, reportStore.findProblems(orgB, ProblemFilter(), 0, 100)!!.totalProblems)
    }

    @Test
    fun `purge with a single scan deletes nothing`() {
        val orgId = OrgId("org_a")
        reportStore.publish(report(orgId, "2026-05-01T00:00:00Z", problemCount = 2))

        val purged = reportRetention.purgeOldScans(orgId)

        assertEquals(PurgedScans.NOTHING, purged)
        assertEquals(1L, summaryRepo.count())
        assertEquals(2L, problemRepo.count())
    }

    @Test
    fun `purge for unknown org is a no-op`() {
        assertEquals(PurgedScans.NOTHING, reportRetention.purgeOldScans(OrgId("does_not_exist")))
    }

    @Test
    fun `second purge deletes nothing further`() {
        val orgId = OrgId("org_a")
        reportStore.publish(report(orgId, "2026-04-01T00:00:00Z", problemCount = 2))
        reportStore.publish(report(orgId, "2026-05-01T00:00:00Z", problemCount = 3))

        reportRetention.purgeOldScans(orgId)
        val second = reportRetention.purgeOldScans(orgId)

        assertEquals(PurgedScans.NOTHING, second)
        assertEquals(1L, summaryRepo.count())
        assertEquals(3L, problemRepo.count())
    }

    private fun report(orgId: OrgId, completedAt: String, problemCount: Int) = LatestReport(
        scanCompletedAt = Instant.parse(completedAt),
        orgId = orgId,
        components = listOf("utdanning_elev"),
        summary = ScanSummary(
            totalRecords = 100,
            totalRefs = 500,
            brokenLinkCount = problemCount.toLong(),
            integrityPercent = 99.0,
            byProblemType = mapOf("missing-resource" to problemCount.toLong()),
            components = emptyList(),
        ),
        problems = (0 until problemCount).map { i ->
            ReportProblem(
                orgId = orgId,
                component = "utdanning_elev",
                resource = "elevforhold",
                problemType = ProblemType.MissingResource,
                sourceSelf = "https://api.felleskomponent.no/source/$i",
                targetHref = "https://api.felleskomponent.no/target/$i",
            )
        },
    )
}

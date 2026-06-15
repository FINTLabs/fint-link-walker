package no.novari.linkwalker.reader

import no.novari.linkwalker.OrgId
import no.novari.linkwalker.report.ComponentSummary
import no.novari.linkwalker.report.LatestReport
import no.novari.linkwalker.report.ProblemFilter
import no.novari.linkwalker.report.ProblemType
import no.novari.linkwalker.report.ReportProblem
import no.novari.linkwalker.report.ReportStore
import no.novari.linkwalker.report.ResourceSummary
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
    private val problemRepo: ReportProblemRepository,
) {

    @BeforeEach
    fun cleanup() {
        // deleteAllInBatch issues a single `DELETE FROM …` — deleteAll() fetches every
        // row first and OOMs against tables with real-scanner-sized data left behind.
        problemRepo.deleteAllInBatch()
        summaryRepo.deleteAllInBatch()
    }

    @Test
    fun `publish then read summary + paginated filtered problems`() {
        val orgId = OrgId("test_org")
        val problems = buildProblems(orgId, components = listOf("utdanning_elev", "utdanning_vurdering"))
        val report = LatestReport(
            scanCompletedAt = Instant.now(),
            orgId = orgId,
            components = listOf("utdanning_elev", "utdanning_vurdering"),
            summary = sampleSummary(problems),
            problems = problems,
        )

        reportStore.publish(report)

        val summary = reportStore.getSummary(orgId)
        assertNotNull(summary)
        assertEquals(orgId, summary.orgId)
        assertEquals(problems.size.toLong(), summary.summary.brokenLinkCount)

        val firstPage = reportStore.findProblems(orgId, ProblemFilter(), page = 0, size = 100)!!
        assertEquals(100, firstPage.problems.size)
        assertEquals(250L, firstPage.totalProblems)
        assertEquals(3, firstPage.totalPages)

        val secondPage = reportStore.findProblems(orgId, ProblemFilter(), page = 1, size = 100)!!
        assertEquals(100, secondPage.problems.size)

        val thirdPage = reportStore.findProblems(orgId, ProblemFilter(), page = 2, size = 100)!!
        assertEquals(50, thirdPage.problems.size)

        // pages share no problems
        val firstIds = firstPage.problems.map { it.targetHref }.toSet()
        val secondIds = secondPage.problems.map { it.targetHref }.toSet()
        assertTrue(firstIds.intersect(secondIds).isEmpty())

        val onlyVurdering = reportStore.findProblems(
            orgId,
            ProblemFilter(component = "utdanning_vurdering"),
            page = 0,
            size = 1000,
        )!!
        assertTrue(onlyVurdering.problems.all { it.component == "utdanning_vurdering" })
        assertEquals(125L, onlyVurdering.totalProblems)

        val onlyMissing = reportStore.findProblems(
            orgId,
            ProblemFilter(problemType = ProblemType.MissingResource),
            page = 0,
            size = 1000,
        )!!
        assertTrue(onlyMissing.problems.all { it.problemType == ProblemType.MissingResource })

        val unknownOrg = reportStore.findProblems(OrgId("does_not_exist"), ProblemFilter(), 0, 10)
        assertNull(unknownOrg)
    }

    @Test
    fun `latest scan wins for same org-id`() {
        val orgId = OrgId("test_org")
        val older = LatestReport(
            scanCompletedAt = Instant.parse("2026-04-01T00:00:00Z"),
            orgId = orgId,
            components = listOf("utdanning_elev"),
            summary = ScanSummary(10, 100, 1, 99.0, mapOf("missing-resource" to 1L), emptyList()),
            problems = listOf(sampleProblem(orgId, 0, "utdanning_elev")),
        )
        val newer = LatestReport(
            scanCompletedAt = Instant.parse("2026-05-01T00:00:00Z"),
            orgId = orgId,
            components = listOf("utdanning_elev"),
            summary = ScanSummary(20, 200, 5, 97.5, mapOf("missing-resource" to 5L), emptyList()),
            problems = (0..4).map { sampleProblem(orgId, it, "utdanning_elev") },
        )

        reportStore.publish(older)
        reportStore.publish(newer)

        val summary = reportStore.getSummary(orgId)!!
        assertEquals(20L, summary.summary.totalRecords)
        assertEquals(5L, summary.summary.brokenLinkCount)

        val problems = reportStore.findProblems(orgId, ProblemFilter(), 0, 100)!!
        assertEquals(5L, problems.totalProblems)

        // Both scans persisted (history kept), but reader sees only the latest.
        assertEquals(2, summaryRepo.findAll().count())
    }

    private fun buildProblems(orgId: OrgId, components: List<String>): List<ReportProblem> {
        val problemTypes = listOf(
            ProblemType.MissingResource,
            ProblemType.UnknownLink,
            ProblemType.MissingBackLinkAdapter,
        )
        val resources = listOf("elevforhold", "vurdering")
        return (0 until 250).map { i ->
            ReportProblem(
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

    private fun sampleSummary(problems: List<ReportProblem>): ScanSummary = ScanSummary(
        totalRecords = 1000L,
        totalRefs = 5000L,
        brokenLinkCount = problems.size.toLong(),
        integrityPercent = 95.0,
        byProblemType = problems.groupingBy { it.problemType.wire }.eachCount().mapValues { it.value.toLong() },
        components = problems.groupBy { it.component }.map { (compName, compProblems) ->
            ComponentSummary(
                component = compName,
                totalRecords = 500L,
                totalRefs = 2500L,
                brokenLinkCount = compProblems.size.toLong(),
                integrityPercent = 95.0,
                byProblemType = compProblems.groupingBy { it.problemType.wire }.eachCount().mapValues { it.value.toLong() },
                resources = compProblems.groupBy { it.resource }.map { (resName, resProblems) ->
                    ResourceSummary(
                        resource = resName,
                        totalRecords = 250L,
                        totalRefs = 1250L,
                        brokenLinkCount = resProblems.size.toLong(),
                        integrityPercent = 95.0,
                        byProblemType = resProblems.groupingBy { it.problemType.wire }.eachCount()
                            .mapValues { it.value.toLong() },
                    )
                },
            )
        },
    )

    private fun sampleProblem(orgId: OrgId, idx: Int, component: String): ReportProblem = ReportProblem(
        orgId = orgId,
        component = component,
        resource = "elevforhold",
        problemType = ProblemType.MissingResource,
        sourceSelf = "https://api.felleskomponent.no/source/$idx",
        targetHref = "https://api.felleskomponent.no/target/$idx",
    )
}

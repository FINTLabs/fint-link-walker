package no.novari.linkwalker.reader

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.novari.linkwalker.OrgId
import no.novari.linkwalker.report.LatestReportSummary
import no.novari.linkwalker.report.PagedProblems
import no.novari.linkwalker.report.ProblemFilter
import no.novari.linkwalker.report.ProblemType
import no.novari.linkwalker.report.ReportProblem
import no.novari.linkwalker.report.ReportStore
import no.novari.linkwalker.report.ScanSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant

class ReportControllerTest {

    private val store = mockk<ReportStore>()
    private val controller = ReportController(store)

    private val afk = OrgId("afk_no")

    @Test
    fun `summary returns 200 with summary when tenant exists`() {
        every { store.getSummary(afk) } returns summaryDoc(afk, integrity = 99.5)

        val response = controller.summary(afk)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(99.5, response.body!!.summary.integrityPercent)
    }

    @Test
    fun `summary returns 404 when tenant has no summary`() {
        val missing = OrgId("missing")
        every { store.getSummary(missing) } returns null

        val response = controller.summary(missing)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `problems returns 404 when store has nothing for that org`() {
        val missing = OrgId("missing")
        every { store.findProblems(missing, any(), any(), any()) } returns null

        val response = controller.problems(missing, null, null, null, 0, 100)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `problems passes through the store's pagination response`() {
        val expected = PagedProblems(
            problems = listOf(problem(afk, "comp", "res", ProblemType.MissingResource)),
            page = 0,
            size = 100,
            totalProblems = 1L,
            totalPages = 1,
            scanCompletedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        every { store.findProblems(afk, any(), 0, 100) } returns expected

        val response = controller.problems(afk, null, null, null, page = 0, size = 100)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(expected, response.body)
    }

    @Test
    fun `problems forwards filter params to the store`() {
        val filterSlot = slot<ProblemFilter>()
        every { store.findProblems(afk, capture(filterSlot), 2, 50) } returns
            PagedProblems(emptyList(), 2, 50, 0, 0, Instant.parse("2026-01-01T00:00:00Z"))

        controller.problems(
            orgId = afk,
            component = "utdanning_elev",
            resource = "elev",
            problemType = ProblemType.MissingResource,
            page = 2,
            size = 50,
        )

        assertEquals(
            ProblemFilter(
                component = "utdanning_elev",
                resource = "elev",
                problemType = ProblemType.MissingResource,
            ),
            filterSlot.captured,
        )
        verify(exactly = 1) { store.findProblems(afk, any(), 2, 50) }
    }

    private fun summaryDoc(tenant: OrgId, integrity: Double) = LatestReportSummary(
        scanCompletedAt = Instant.parse("2026-01-01T00:00:00Z"),
        orgId = tenant,
        components = listOf("comp_x"),
        summary = ScanSummary(
            totalRecords = 0,
            totalRefs = 0,
            brokenLinkCount = 0,
            integrityPercent = integrity,
            byProblemType = emptyMap(),
            components = emptyList(),
        ),
    )

    private fun problem(tenant: OrgId, component: String, resource: String, problemType: ProblemType) =
        ReportProblem(
            orgId = tenant,
            component = component,
            resource = resource,
            problemType = problemType,
            sourceSelf = "https://host/$component/$resource/systemid/x",
            targetHref = "https://host/$component/$resource/systemid/x-target",
        )
}

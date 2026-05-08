package no.novari.linkwalker.reader

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.novari.linkwalker.report.LatestReportSummary
import no.novari.linkwalker.report.PagedRows
import no.novari.linkwalker.report.ReportRow
import no.novari.linkwalker.report.ReportStore
import no.novari.linkwalker.report.RowFilter
import no.novari.linkwalker.report.ScanSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant

class ReportControllerTest {

    private val store = mockk<ReportStore>()
    private val controller = ReportController(store)

    @Test
    fun `summary returns 200 with summary when tenant exists`() {
        every { store.getSummary("afk-no") } returns summaryDoc("afk-no", integrity = 99.5)

        val response = controller.summary("afk-no")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(99.5, response.body!!.summary.integrityPercent)
    }

    @Test
    fun `summary returns 404 when tenant has no summary`() {
        every { store.getSummary("missing") } returns null

        val response = controller.summary("missing")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `rows returns 404 when store has nothing for that org`() {
        every { store.findRows(any(), any(), any(), any()) } returns null

        val response = controller.rows("missing", null, null, null, 0, 100)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `rows passes through the store's pagination response`() {
        val expected = PagedRows(
            rows = listOf(row("afk-no", "comp", "res", "missing-resource")),
            page = 0,
            size = 100,
            totalRows = 1L,
            totalPages = 1,
            scanCompletedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        every { store.findRows("afk-no", any(), 0, 100) } returns expected

        val response = controller.rows("afk-no", null, null, null, page = 0, size = 100)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(expected, response.body)
    }

    @Test
    fun `rows forwards filter params to the store`() {
        val filterSlot = slot<RowFilter>()
        every { store.findRows("afk-no", capture(filterSlot), 2, 50) } returns
            PagedRows(emptyList(), 2, 50, 0, 0, Instant.parse("2026-01-01T00:00:00Z"))

        controller.rows(
            orgId = "afk-no",
            component = "utdanning_elev",
            resource = "elev",
            problemType = "missing-resource",
            page = 2,
            size = 50,
        )

        assertEquals(
            RowFilter(component = "utdanning_elev", resource = "elev", problemType = "missing-resource"),
            filterSlot.captured,
        )
        verify(exactly = 1) { store.findRows("afk-no", any(), 2, 50) }
    }

    private fun summaryDoc(tenant: String, integrity: Double) = LatestReportSummary(
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

    private fun row(tenant: String, component: String, resource: String, problemType: String) =
        ReportRow(
            orgId = tenant,
            component = component,
            resource = resource,
            problemType = problemType,
            sourceSelf = "https://host/$component/$resource/systemid/x",
            targetHref = "https://host/$component/$resource/systemid/x-target",
        )
}

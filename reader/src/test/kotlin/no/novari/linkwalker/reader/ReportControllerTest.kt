package no.novari.linkwalker.reader

import io.mockk.every
import io.mockk.mockk
import no.novari.linkwalker.report.LatestReportRows
import no.novari.linkwalker.report.LatestReportSummary
import no.novari.linkwalker.report.ReportRow
import no.novari.linkwalker.report.ReportStore
import no.novari.linkwalker.report.ScanSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `rows returns 404 when tenant has no rows blob`() {
        every { store.getRows("missing") } returns null

        val response = controller.rows("missing", null, null, null, 0, 100)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `rows returns paginated rows`() {
        every { store.getRows("afk-no") } returns rowsDoc("afk-no", count = 250)

        val response = controller.rows("afk-no", null, null, null, page = 0, size = 100)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(100, body.rows.size)
        assertEquals(250, body.totalRows)
        assertEquals(0, body.page)
        assertEquals(100, body.size)
        assertEquals(3, body.totalPages, "ceil(250/100) = 3 pages")
    }

    @Test
    fun `rows applies component filter`() {
        every { store.getRows("afk-no") } returns LatestReportRows(
            scanCompletedAt = Instant.parse("2026-01-01T00:00:00Z"),
            orgId = "afk-no",
            rows = listOf(
                row("afk-no", "utdanning_elev", "elev", "missing-resource"),
                row("afk-no", "utdanning_vurdering", "elevvurdering", "missing-resource"),
                row("afk-no", "utdanning_elev", "person", "unknown-link"),
            ),
        )

        val response = controller.rows("afk-no", component = "utdanning_elev", null, null, 0, 100)

        val body = response.body!!
        assertEquals(2, body.totalRows)
        assertTrue(body.rows.all { it.component == "utdanning_elev" })
    }

    @Test
    fun `rows applies problemType filter`() {
        every { store.getRows("afk-no") } returns LatestReportRows(
            scanCompletedAt = Instant.parse("2026-01-01T00:00:00Z"),
            orgId = "afk-no",
            rows = listOf(
                row("afk-no", "utdanning_elev", "elev", "missing-resource"),
                row("afk-no", "utdanning_elev", "elev", "unknown-link"),
                row("afk-no", "utdanning_elev", "person", "missing-resource"),
            ),
        )

        val response = controller.rows("afk-no", null, null, problemType = "missing-resource", 0, 100)

        assertEquals(2, response.body!!.totalRows)
        assertTrue(response.body!!.rows.all { it.problemType == "missing-resource" })
    }

    @Test
    fun `rows handles page beyond total gracefully`() {
        every { store.getRows("afk-no") } returns rowsDoc("afk-no", count = 5)

        val response = controller.rows("afk-no", null, null, null, page = 10, size = 100)

        val body = response.body!!
        assertEquals(0, body.rows.size)
        assertEquals(5, body.totalRows)
    }

    @Test
    fun `rows clamps oversized page size to MAX_PAGE_SIZE`() {
        every { store.getRows("afk-no") } returns rowsDoc("afk-no", count = 5)

        val response = controller.rows("afk-no", null, null, null, page = 0, size = 999_999)

        assertTrue(response.body!!.size <= 1000)
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

    private fun rowsDoc(tenant: String, count: Int) = LatestReportRows(
        scanCompletedAt = Instant.parse("2026-01-01T00:00:00Z"),
        orgId = tenant,
        rows = (1..count).map { row(tenant, "comp_x", "res", "missing-resource", suffix = it.toString()) },
    )

    private fun row(tenant: String, component: String, resource: String, problemType: String, suffix: String = "x") =
        ReportRow(
            orgId = tenant,
            component = component,
            resource = resource,
            problemType = problemType,
            sourceSelf = "https://host/$component/$resource/systemid/$suffix",
            targetHref = "https://host/$component/$resource/systemid/$suffix-target",
        )
}

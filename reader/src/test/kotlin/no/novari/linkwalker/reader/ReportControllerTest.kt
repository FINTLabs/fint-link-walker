package no.novari.linkwalker.reader

import io.mockk.every
import io.mockk.mockk
import no.novari.linkwalker.report.LatestReport
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
    fun `latest returns map keyed by tenant`() {
        every { store.list() } returns listOf(
            report("afk-no"),
            report("vlfk-no"),
        )

        val result = controller.latest()

        assertEquals(setOf("afk-no", "vlfk-no"), result.keys)
        assertEquals(listOf("afk-no"), result["afk-no"]?.tenants)
        assertEquals(listOf("vlfk-no"), result["vlfk-no"]?.tenants)
    }

    @Test
    fun `latest returns empty map when no reports`() {
        every { store.list() } returns emptyList()

        val result = controller.latest()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `summary returns map of tenant to ScanSummary`() {
        every { store.list() } returns listOf(
            report("afk-no", integrity = 99.0),
            report("vlfk-no", integrity = 95.0),
        )

        val result = controller.summary()

        assertEquals(99.0, result["afk-no"]?.integrityPercent)
        assertEquals(95.0, result["vlfk-no"]?.integrityPercent)
    }

    @Test
    fun `latestForTenant returns 200 with report when tenant exists`() {
        every { store.list() } returns listOf(report("afk-no", integrity = 99.5))

        val response = controller.latestForTenant("afk-no")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(99.5, response.body!!.summary.integrityPercent)
    }

    @Test
    fun `latestForTenant returns 404 when tenant missing`() {
        every { store.list() } returns listOf(report("afk-no"))

        val response = controller.latestForTenant("vlfk-no")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `latestForTenant returns 404 when no reports at all`() {
        every { store.list() } returns emptyList()

        val response = controller.latestForTenant("afk-no")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    private fun report(tenant: String, integrity: Double = 100.0) = LatestReport(
        scanCompletedAt = Instant.parse("2026-01-01T00:00:00Z"),
        tenants = listOf(tenant),
        components = listOf("comp_x"),
        summary = ScanSummary(
            totalRecords = 0,
            totalRefs = 0,
            brokenLinkCount = 0,
            integrityPercent = integrity,
            byProblemType = emptyMap(),
            components = emptyList(),
        ),
        rows = emptyList(),
    )
}

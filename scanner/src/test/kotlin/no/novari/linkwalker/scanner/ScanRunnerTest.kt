package no.novari.linkwalker.scanner

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.novari.linkwalker.auth.AuthService
import no.novari.linkwalker.config.LinkWalkerConfig
import no.novari.linkwalker.index.IndexBuilder
import no.novari.linkwalker.index.IndexValidator
import no.novari.linkwalker.index.TenantIndex
import no.novari.linkwalker.report.LatestReport
import no.novari.linkwalker.report.ReportRow
import no.novari.linkwalker.report.ReportStore
import no.novari.linkwalker.report.ScanSummary
import no.novari.linkwalker.report.SummaryBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.boot.ApplicationArguments

class ScanRunnerTest {

    private val authService = mockk<AuthService>()
    private val indexBuilder = mockk<IndexBuilder>()
    private val indexValidator = mockk<IndexValidator>()
    private val summaryBuilder = mockk<SummaryBuilder>()
    private val reportStore = mockk<ReportStore>(relaxed = true)
    private val args = mockk<ApplicationArguments>(relaxed = true)

    @Test
    fun `happy path publishes report with summary and rows`() {
        val runner = runner(tenant = "afk-no", components = listOf("utdanning_elev"))
        val index = emptyIndex()
        val rows = listOf(reportRow("afk-no"))
        val summary = summary(integrity = 99.5)

        coEvery { authService.getBearerToken("afk-no") } returns "bearer-x"
        coEvery { indexBuilder.buildIndex(listOf("utdanning_elev"), "bearer-x", any()) } returns index
        every { indexValidator.validate("afk-no", index) } returns rows
        every { summaryBuilder.build(index, rows) } returns summary

        val captured = slot<LatestReport>()
        every { reportStore.publish(capture(captured)) } returns Unit

        runner.run(args)

        coVerify { reportStore.publish(any()) }
        assertEquals(listOf("afk-no"), captured.captured.tenants)
        assertEquals(listOf("utdanning_elev"), captured.captured.components)
        assertEquals(rows, captured.captured.rows)
        assertEquals(summary, captured.captured.summary)
    }

    @Test
    fun `null tenant throws and never publishes`() {
        val runner = runner(tenant = null)

        assertThrows(IllegalArgumentException::class.java) { runner.run(args) }
        coVerify(exactly = 0) { reportStore.publish(any()) }
    }

    @Test
    fun `blank tenant throws and never publishes`() {
        val runner = runner(tenant = "   ")

        assertThrows(IllegalArgumentException::class.java) { runner.run(args) }
        coVerify(exactly = 0) { reportStore.publish(any()) }
    }

    @Test
    fun `null bearer token throws and never publishes`() {
        val runner = runner(tenant = "afk-no")
        coEvery { authService.getBearerToken("afk-no") } returns null

        assertThrows(IllegalStateException::class.java) { runner.run(args) }
        coVerify(exactly = 0) { reportStore.publish(any()) }
    }

    @Test
    fun `partial component failure still publishes report`() {
        val runner = runner(tenant = "afk-no", components = listOf("good", "broken"))
        val index = emptyIndex()
        val rows = emptyList<ReportRow>()

        coEvery { authService.getBearerToken("afk-no") } returns "bearer"
        coEvery {
            indexBuilder.buildIndex(any(), any(), any())
        } answers {
            // Simulate a per-component failure being reported back via the callback.
            val onError = thirdArg<(String) -> Unit>()
            onError("broken")
            index
        }
        every { indexValidator.validate("afk-no", index) } returns rows
        every { summaryBuilder.build(index, rows) } returns summary(integrity = 100.0)

        runner.run(args)

        coVerify(exactly = 1) { reportStore.publish(any()) }
    }

    private fun runner(tenant: String?, components: List<String> = emptyList()): ScanRunner =
        ScanRunner(
            config = LinkWalkerConfig(tenant = tenant, components = components),
            authService = authService,
            indexBuilder = indexBuilder,
            indexValidator = indexValidator,
            summaryBuilder = summaryBuilder,
            reportStore = reportStore,
        )

    private fun emptyIndex(): TenantIndex = TenantIndex(records = emptyList(), byKey = emptyMap())

    private fun summary(integrity: Double) = ScanSummary(
        totalRecords = 0,
        totalRefs = 0,
        brokenLinkCount = 0,
        integrityPercent = integrity,
        byProblemType = emptyMap(),
        components = emptyList(),
    )

    private fun reportRow(tenant: String) = ReportRow(
        tenant = tenant,
        component = "utdanning_elev",
        resource = "elev",
        problemType = "missing-resource",
        sourceSelf = "https://host/utdanning/elev/elev/systemid/x",
        targetHref = "https://host/utdanning/elev/elev/systemid/y",
    )
}

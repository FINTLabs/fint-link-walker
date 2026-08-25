package no.novari.linkwalker.scanner

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.novari.linkwalker.OrgId
import no.novari.linkwalker.auth.AuthService
import no.novari.linkwalker.config.ScannerProperties
import no.novari.linkwalker.report.ProblemType
import no.novari.linkwalker.index.IndexBuilder
import no.novari.linkwalker.index.IndexValidator
import no.novari.linkwalker.index.TenantIndex
import no.novari.linkwalker.report.LatestReport
import no.novari.linkwalker.report.ReportProblem
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
    fun `happy path publishes report with summary and problems`() {
        val runner = runner(orgId = "afk_no", components = listOf("utdanning_elev"))
        val index = emptyIndex()
        val problems = listOf(reportProblem("afk_no"))
        val summary = summary(integrity = 99.5)

        coEvery { authService.getBearerToken(OrgId("afk_no")) } returns "bearer-x"
        coEvery { indexBuilder.buildIndex(listOf("utdanning_elev"), "bearer-x") } returns index
        every { indexValidator.validate(OrgId("afk_no"), index) } returns problems
        every { summaryBuilder.build(index, problems) } returns summary

        val captured = slot<LatestReport>()
        every { reportStore.publish(capture(captured)) } returns Unit

        runner.run(args)

        coVerify { reportStore.publish(any()) }
        assertEquals(OrgId("afk_no"), captured.captured.orgId)
        assertEquals(listOf("utdanning_elev"), captured.captured.components)
        assertEquals(problems, captured.captured.problems)
        assertEquals(summary, captured.captured.summary)
    }

    @Test
    fun `null bearer token throws and never publishes`() {
        val runner = runner(orgId = "afk_no")
        coEvery { authService.getBearerToken(OrgId("afk_no")) } returns null

        assertThrows(IllegalStateException::class.java) { runner.run(args) }
        coVerify(exactly = 0) { reportStore.publish(any()) }
    }

    @Test
    fun `index-builder failure fails the scan and never publishes (fail-fast)`() {
        val runner = runner(orgId = "afk_no", components = listOf("utdanning_elev"))
        coEvery { authService.getBearerToken(OrgId("afk_no")) } returns "bearer"
        coEvery { indexBuilder.buildIndex(any(), any()) } throws RuntimeException("fetch blew up")

        assertThrows(RuntimeException::class.java) { runner.run(args) }
        coVerify(exactly = 0) { reportStore.publish(any()) }
    }

    private fun runner(orgId: String, components: List<String> = emptyList()): ScanRunner =
        ScanRunner(
            config = ScannerProperties(orgId = orgId, components = components),
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

    private fun reportProblem(orgId: String) = ReportProblem(
        orgId = OrgId(orgId),
        component = "utdanning_elev",
        resource = "elev",
        problemType = ProblemType.MissingResource,
        sourceSelf = "https://host/utdanning/elev/elev/systemid/x",
        targetHref = "https://host/utdanning/elev/elev/systemid/y",
    )
}

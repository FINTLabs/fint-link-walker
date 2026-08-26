package no.novari.linkwalker.reader

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.novari.linkwalker.OrgId
import no.novari.linkwalker.report.PurgedScans
import no.novari.linkwalker.report.jpa.JpaReportRetention
import no.novari.linkwalker.report.jpa.ReportProblemRepository
import no.novari.linkwalker.report.jpa.ReportSummaryEntity
import no.novari.linkwalker.report.jpa.ReportSummaryRepository
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class JpaReportRetentionTest {

    private val summaryRepo = mockk<ReportSummaryRepository>()
    private val problemRepo = mockk<ReportProblemRepository>()

    @Test
    fun `deletes problems in batches until a batch comes up short`() {
        val keepScanId = UUID.randomUUID()
        val latest = mockk<ReportSummaryEntity>()
        every { latest.scanId } returns keepScanId
        every { summaryRepo.findFirstByOrgIdOrderByScanCompletedAtDesc("org_a") } returns latest
        every { problemRepo.deleteBatchForOtherScans("org_a", keepScanId, 2) } returnsMany listOf(2, 2, 1)
        every { summaryRepo.deleteForOtherScans("org_a", keepScanId) } returns 3

        val purged = JpaReportRetention(summaryRepo, problemRepo, batchSize = 2).purgeOldScans(OrgId("org_a"))

        assertEquals(PurgedScans(summariesDeleted = 3, problemsDeleted = 5), purged)
        verify(exactly = 3) { problemRepo.deleteBatchForOtherScans("org_a", keepScanId, 2) }
    }

    @Test
    fun `org without summaries purges nothing`() {
        every { summaryRepo.findFirstByOrgIdOrderByScanCompletedAtDesc("org_a") } returns null

        val purged = JpaReportRetention(summaryRepo, problemRepo).purgeOldScans(OrgId("org_a"))

        assertEquals(PurgedScans.NOTHING, purged)
        verify(exactly = 0) { problemRepo.deleteBatchForOtherScans(any(), any(), any()) }
        verify(exactly = 0) { summaryRepo.deleteForOtherScans(any(), any()) }
    }
}

package no.novari.linkwalker.report.jpa

import no.novari.linkwalker.OrgId
import no.novari.linkwalker.report.PurgedScans
import no.novari.linkwalker.report.ReportRetention
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class JpaReportRetention(
    private val summaryRepo: ReportSummaryRepository,
    private val problemRepo: ReportProblemRepository,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) : ReportRetention {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun purgeOldScans(orgId: OrgId): PurgedScans {
        val latest = summaryRepo.findFirstByOrgIdOrderByScanCompletedAtDesc(orgId.value)
            ?: return PurgedScans.NOTHING

        var problemsDeleted = 0L
        do {
            val deleted = problemRepo.deleteBatchForOtherScans(orgId.value, latest.scanId, batchSize)
            problemsDeleted += deleted
        } while (deleted == batchSize)

        val summariesDeleted = summaryRepo.deleteForOtherScans(orgId.value, latest.scanId)

        if (summariesDeleted > 0 || problemsDeleted > 0) {
            logger.info(
                "Purged old scans: org-id={} kept-scan-id={} summaries-deleted={} problems-deleted={}",
                orgId.value, latest.scanId, summariesDeleted, problemsDeleted,
            )
        }
        return PurgedScans(summariesDeleted, problemsDeleted)
    }

    private companion object {
        const val DEFAULT_BATCH_SIZE = 10_000
    }
}

package no.novari.linkwalker.report.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ReportSummaryRepository : JpaRepository<ReportSummaryEntity, UUID> {

    fun findFirstByOrgIdOrderByScanCompletedAtDesc(orgId: String): ReportSummaryEntity?

    /** Latest summary per org, used by SummaryMetrics for cross-org dashboards. */
    @Query(
        """
        select s from ReportSummaryEntity s
        where s.scanCompletedAt = (
            select max(s2.scanCompletedAt) from ReportSummaryEntity s2 where s2.orgId = s.orgId
        )
        """,
    )
    fun findLatestPerOrg(): List<ReportSummaryEntity>
}

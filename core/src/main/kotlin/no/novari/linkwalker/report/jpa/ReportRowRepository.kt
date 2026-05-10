package no.novari.linkwalker.report.jpa

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ReportRowRepository : JpaRepository<ReportRowEntity, Long> {

    /**
     * Returns one page of rows as a [Slice] (no implicit count query).
     *
     * The WHERE clause leads with `org_id` so the composite index
     * `idx_row_filter (org_id, scan_id, component, resource, problem_type)` is a
     * prefix match — without `org_id` Postgres has to seq-scan the whole
     * `report_row` table since no index leads with `scan_id`.
     */
    @Query(
        """
        select r from ReportRowEntity r
        where r.orgId = :orgId
          and r.scanId = :scanId
          and (:component is null or r.component = :component)
          and (:resource is null or r.resource = :resource)
          and (:problemType is null or r.problemType = :problemType)
        """,
    )
    fun findFiltered(
        @Param("orgId") orgId: String,
        @Param("scanId") scanId: UUID,
        @Param("component") component: String?,
        @Param("resource") resource: String?,
        @Param("problemType") problemType: String?,
        pageable: Pageable,
    ): Slice<ReportRowEntity>

    /**
     * Counts rows matching the filter. Only call this when the filter is non-empty;
     * for an empty filter use `LatestReportSummary.summary.brokenLinkCount` from the
     * summary doc — it's already written at scan time and avoids a `count(*)` over
     * millions of rows.
     */
    @Query(
        """
        select count(r) from ReportRowEntity r
        where r.orgId = :orgId
          and r.scanId = :scanId
          and (:component is null or r.component = :component)
          and (:resource is null or r.resource = :resource)
          and (:problemType is null or r.problemType = :problemType)
        """,
    )
    fun countFiltered(
        @Param("orgId") orgId: String,
        @Param("scanId") scanId: UUID,
        @Param("component") component: String?,
        @Param("resource") resource: String?,
        @Param("problemType") problemType: String?,
    ): Long
}

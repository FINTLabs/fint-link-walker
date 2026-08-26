package no.novari.linkwalker.report.jpa

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ReportProblemRepository : JpaRepository<ReportProblemEntity, Long> {

    @Query(
        """
        select p from ReportProblemEntity p
        where p.scanId = :scanId
          and (:component is null or p.component = :component)
          and (:resource is null or p.resource = :resource)
          and (:problemType is null or p.problemType = :problemType)
        """,
        countQuery = """
        select count(p) from ReportProblemEntity p
        where p.scanId = :scanId
          and (:component is null or p.component = :component)
          and (:resource is null or p.resource = :resource)
          and (:problemType is null or p.problemType = :problemType)
        """,
    )
    fun findFiltered(
        @Param("scanId") scanId: UUID,
        @Param("component") component: String?,
        @Param("resource") resource: String?,
        @Param("problemType") problemType: String?,
        pageable: Pageable,
    ): Page<ReportProblemEntity>

    @Modifying
    @Transactional
    @Query(
        value = """
        delete from report_row where id in (
            select id from report_row
            where org_id = :orgId and scan_id <> :keepScanId
            limit :limit
        )
        """,
        nativeQuery = true,
    )
    fun deleteBatchForOtherScans(
        @Param("orgId") orgId: String,
        @Param("keepScanId") keepScanId: UUID,
        @Param("limit") limit: Int,
    ): Int
}

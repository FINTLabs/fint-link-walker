package no.novari.linkwalker.report.jpa

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ReportRowRepository : JpaRepository<ReportRowEntity, Long> {

    @Query(
        """
        select r from ReportRowEntity r
        where r.scanId = :scanId
          and (:component is null or r.component = :component)
          and (:resource is null or r.resource = :resource)
          and (:problemType is null or r.problemType = :problemType)
        """,
        countQuery = """
        select count(r) from ReportRowEntity r
        where r.scanId = :scanId
          and (:component is null or r.component = :component)
          and (:resource is null or r.resource = :resource)
          and (:problemType is null or r.problemType = :problemType)
        """,
    )
    fun findFiltered(
        @Param("scanId") scanId: UUID,
        @Param("component") component: String?,
        @Param("resource") resource: String?,
        @Param("problemType") problemType: String?,
        pageable: Pageable,
    ): Page<ReportRowEntity>
}

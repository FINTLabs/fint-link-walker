package no.novari.linkwalker.report.jpa

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import no.novari.linkwalker.OrgId
import no.novari.linkwalker.report.LatestReport
import no.novari.linkwalker.report.LatestReportSummary
import no.novari.linkwalker.report.PagedRows
import no.novari.linkwalker.report.ProblemType
import no.novari.linkwalker.report.ReportRow
import no.novari.linkwalker.report.ReportStore
import no.novari.linkwalker.report.RowFilter
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Service
@Transactional(readOnly = true)
class JpaReportStore(
    private val mapper: ObjectMapper,
    private val summaryRepo: ReportSummaryRepository,
    private val rowRepo: ReportRowRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : ReportStore {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun publish(report: LatestReport) {
        val scanId = UUID.randomUUID()
        summaryRepo.saveAndFlush(summaryEntity(scanId, report))
        saveRowsInChunks(scanId, report)

        logger.info(
            "Persisted scan org-id={} scan-id={} rows={}",
            report.orgId, scanId, report.rows.size,
        )
    }

    // Chunk to bound the persistence context: saveAll on millions of rows in one go
    // would hold every entity in the session until commit. Flushing and clearing
    // every CHUNK_SIZE rows keeps the heap footprint flat; Hibernate's
    // jdbc.batch_size setting controls how many INSERTs go in each network round-trip.
    private fun saveRowsInChunks(scanId: UUID, report: LatestReport) {
        report.rows.chunked(CHUNK_SIZE).forEach { chunk ->
            rowRepo.saveAll(chunk.map { rowEntity(scanId, report, it) })
            entityManager.flush()
            entityManager.clear()
        }
    }

    override fun getSummary(orgId: OrgId): LatestReportSummary? =
        summaryRepo.findFirstByOrgIdOrderByScanCompletedAtDesc(orgId.value)
            ?.let { mapper.readValue(it.summaryJson, LatestReportSummary::class.java) }

    override fun findRows(orgId: OrgId, filter: RowFilter, page: Int, size: Int): PagedRows? {
        val latest = summaryRepo.findFirstByOrgIdOrderByScanCompletedAtDesc(orgId.value) ?: return null
        val pageSize = size.coerceIn(1, ReportStore.MAX_PAGE_SIZE)
        val pageIndex = page.coerceAtLeast(0)
        val result = rowRepo.findFiltered(
            scanId = latest.scanId,
            component = filter.component,
            resource = filter.resource,
            problemType = filter.problemType?.wire,
            pageable = PageRequest.of(pageIndex, pageSize, Sort.by("id")),
        )
        return PagedRows(
            rows = result.content.map { it.toRow() },
            page = pageIndex,
            size = pageSize,
            totalRows = result.totalElements,
            totalPages = result.totalPages,
            scanCompletedAt = latest.scanCompletedAt,
        )
    }

    override fun listSummaries(): List<LatestReportSummary> =
        summaryRepo.findLatestPerOrg()
            .mapNotNull { entity ->
                runCatching { mapper.readValue(entity.summaryJson, LatestReportSummary::class.java) }
                    .onFailure { logger.warn("Skipping summary for org={}: {}", entity.orgId, it.message) }
                    .getOrNull()
            }

    private fun summaryEntity(scanId: UUID, report: LatestReport): ReportSummaryEntity {
        val doc = LatestReportSummary(
            scanCompletedAt = report.scanCompletedAt,
            orgId = report.orgId,
            components = report.components,
            summary = report.summary,
        )
        return ReportSummaryEntity(
            id = UUID.randomUUID(),
            scanId = scanId,
            orgId = report.orgId.value,
            scanCompletedAt = report.scanCompletedAt,
            summaryJson = mapper.writeValueAsString(doc),
        )
    }

    private fun rowEntity(scanId: UUID, report: LatestReport, row: ReportRow) = ReportRowEntity(
        scanId = scanId,
        orgId = report.orgId.value,
        scanCompletedAt = report.scanCompletedAt,
        component = row.component,
        resource = row.resource,
        problemType = row.problemType.wire,
        sourceSelf = row.sourceSelf,
        targetHref = row.targetHref,
        relationName = row.relationName,
        expectedInverseName = row.expectedInverseName,
    )

    private fun ReportRowEntity.toRow(): ReportRow = ReportRow(
        orgId = OrgId(orgId),
        component = component,
        resource = resource,
        problemType = ProblemType.parseOrNull(problemType)
            ?: error("Persisted row has unknown problemType: '$problemType'"),
        sourceSelf = sourceSelf,
        targetHref = targetHref,
        relationName = relationName,
        expectedInverseName = expectedInverseName,
    )

    private companion object {
        const val CHUNK_SIZE = 1000
    }
}

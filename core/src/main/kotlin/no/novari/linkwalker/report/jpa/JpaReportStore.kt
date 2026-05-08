package no.novari.linkwalker.report.jpa

import no.novari.linkwalker.report.LatestReport
import no.novari.linkwalker.report.LatestReportSummary
import no.novari.linkwalker.report.PagedRows
import no.novari.linkwalker.report.ReportRow
import no.novari.linkwalker.report.ReportStore
import no.novari.linkwalker.report.RowFilter
import org.postgresql.PGConnection
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.JdbcTemplate
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
    private val jdbcTemplate: JdbcTemplate,
) : ReportStore {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun publish(report: LatestReport) {
        val scanId = UUID.randomUUID()
        val summaryDoc = LatestReportSummary(
            scanCompletedAt = report.scanCompletedAt,
            orgId = report.orgId,
            components = report.components,
            summary = report.summary,
        )
        summaryRepo.saveAndFlush(
            ReportSummaryEntity(
                id = UUID.randomUUID(),
                scanId = scanId,
                orgId = report.orgId,
                scanCompletedAt = report.scanCompletedAt,
                summaryJson = mapper.writeValueAsString(summaryDoc),
            ),
        )

        if (report.rows.isNotEmpty()) {
            bulkCopyRows(scanId, report)
        }

        logger.info(
            "Persisted scan org-id={} scan-id={} rows={}",
            report.orgId, scanId, report.rows.size,
        )
    }

    /**
     * Postgres COPY ... FROM STDIN bypasses the Hibernate persistence context for the
     * row insert path. Hibernate can't batch INSERTs against an IDENTITY-keyed table —
     * 2M rows took ~9.5 min via saveAll. COPY streams the same volume in seconds and
     * keeps the publish step's heap footprint tiny since rows go down the wire one at
     * a time without per-entity session bookkeeping.
     */
    private fun bulkCopyRows(scanId: UUID, report: LatestReport) {
        val orgId = report.orgId
        val completedAt = report.scanCompletedAt.toString()
        val scanIdStr = scanId.toString()

        jdbcTemplate.execute(ConnectionCallback { connection ->
            val pg = connection.unwrap(PGConnection::class.java)
            val copyIn = pg.copyAPI.copyIn(COPY_SQL)
            try {
                report.rows.forEach { row ->
                    val line = buildCopyLine(scanIdStr, orgId, completedAt, row)
                    val bytes = line.toByteArray(Charsets.UTF_8)
                    copyIn.writeToCopy(bytes, 0, bytes.size)
                }
                copyIn.endCopy()
            } catch (ex: Exception) {
                runCatching { copyIn.cancelCopy() }
                throw ex
            }
        })
    }

    private fun buildCopyLine(
        scanId: String,
        orgId: String,
        completedAt: String,
        row: ReportRow,
    ): String = buildString {
        append(scanId); append('\t')
        append(escape(orgId)); append('\t')
        append(completedAt); append('\t')
        append(escape(row.component)); append('\t')
        append(escape(row.resource)); append('\t')
        append(escape(row.problemType)); append('\t')
        append(escape(row.sourceSelf)); append('\t')
        append(escape(row.targetHref)); append('\t')
        append(row.relationName?.let { escape(it) } ?: NULL_TOKEN); append('\t')
        append(row.expectedInverseName?.let { escape(it) } ?: NULL_TOKEN); append('\n')
    }

    /** COPY text format reserves \, tab, newline, CR. Everything else passes through. */
    private fun escape(s: String): String {
        if (s.indexOfAny(SPECIAL_CHARS) < 0) return s
        val sb = StringBuilder(s.length + 8)
        for (c in s) when (c) {
            '\\' -> sb.append("\\\\")
            '\t' -> sb.append("\\t")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            else -> sb.append(c)
        }
        return sb.toString()
    }

    override fun getSummary(orgId: String): LatestReportSummary? =
        summaryRepo.findFirstByOrgIdOrderByScanCompletedAtDesc(orgId)
            ?.let { mapper.readValue(it.summaryJson, LatestReportSummary::class.java) }

    override fun findRows(orgId: String, filter: RowFilter, page: Int, size: Int): PagedRows? {
        val latest = summaryRepo.findFirstByOrgIdOrderByScanCompletedAtDesc(orgId) ?: return null
        val pageSize = size.coerceIn(1, ReportStore.MAX_PAGE_SIZE)
        val pageIndex = page.coerceAtLeast(0)
        val pageable = PageRequest.of(pageIndex, pageSize, Sort.by("id"))
        val result = rowRepo.findFiltered(
            scanId = latest.scanId,
            component = filter.component,
            resource = filter.resource,
            problemType = filter.problemType,
            pageable = pageable,
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

    private fun ReportRowEntity.toRow(): ReportRow = ReportRow(
        orgId = orgId,
        component = component,
        resource = resource,
        problemType = problemType,
        sourceSelf = sourceSelf,
        targetHref = targetHref,
        relationName = relationName,
        expectedInverseName = expectedInverseName,
    )

    private companion object {
        const val NULL_TOKEN = "\\N"
        val SPECIAL_CHARS = charArrayOf('\\', '\t', '\n', '\r')
        val COPY_SQL = """
            COPY report_row(
              scan_id, org_id, scan_completed_at, component, resource,
              problem_type, source_self, target_href, relation_name, expected_inverse_name
            ) FROM STDIN WITH (FORMAT text, NULL '\N')
        """.trimIndent()
    }
}

package no.novari.linkwalker.scanner

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import no.novari.linkwalker.OrgId
import no.novari.linkwalker.auth.AuthService
import no.novari.linkwalker.config.ScannerProperties
import no.novari.linkwalker.index.IndexBuilder
import no.novari.linkwalker.index.IndexValidator
import no.novari.linkwalker.index.TenantIndex
import no.novari.linkwalker.report.LatestReport
import no.novari.linkwalker.report.ReportRow
import no.novari.linkwalker.report.ReportStore
import no.novari.linkwalker.report.ScanSummary
import no.novari.linkwalker.report.SummaryBuilder
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Component
class ScanRunner(
    private val config: ScannerProperties,
    private val authService: AuthService,
    private val indexBuilder: IndexBuilder,
    private val indexValidator: IndexValidator,
    private val summaryBuilder: SummaryBuilder,
    private val reportStore: ReportStore,
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val orgId = requiredOrgId()
        MDC.put("scanId", UUID.randomUUID().toString())
        MDC.put("orgId", orgId.value)
        try {
            runBlocking(MDCContext()) { runScan(orgId) }
        } finally {
            MDC.clear()
        }
    }

    private suspend fun runScan(orgId: OrgId) {
        logger.info("Scan starting: components={}", config.components.size)

        val started = Instant.now()
        val result = scan(orgId)
        val completedAt = Instant.now()
        val summary = summaryBuilder.build(result.index, result.rows)
        publish(orgId, summary, result.rows, completedAt)
        logFinished(summary, result.rows, started, completedAt)
    }

    private fun requiredOrgId(): OrgId {
        val raw = requireNotNull(config.orgId?.takeIf { it.isNotBlank() }) {
            "fint.link-walker.scanner.org-id must be set (e.g. --fint.link-walker.scanner.org-id=afk_no)"
        }
        return OrgId.parseOrNull(raw)
            ?: error("fint.link-walker.scanner.org-id '$raw' is invalid — expected ${OrgId.REGEX.pattern}")
    }

    private suspend fun scan(orgId: OrgId): ScanResult {
        val bearer = authService.getBearerToken(orgId)
            ?: error("No bearer token for org-id $orgId — aborting")

        val index = indexBuilder.buildIndex(components = config.components, bearer = bearer)
        val rows = indexValidator.validate(orgId, index)
        logger.info("Indexed records={} broken-link rows={}", index.records.size, rows.size)
        return ScanResult(index, rows)
    }

    private fun publish(orgId: OrgId, summary: ScanSummary, rows: List<ReportRow>, completedAt: Instant) {
        reportStore.publish(
            LatestReport(
                scanCompletedAt = completedAt,
                orgId = orgId,
                components = config.components,
                summary = summary,
                rows = rows,
            )
        )
    }

    private fun logFinished(summary: ScanSummary, rows: List<ReportRow>, started: Instant, completedAt: Instant) {
        val duration = Duration.between(started, completedAt)
        logger.info(
            "Scan finished: integrity={}% rows={} duration={}s",
            summary.integrityPercent, rows.size, duration.toSeconds(),
        )
    }

    private data class ScanResult(val index: TenantIndex, val rows: List<ReportRow>)
}

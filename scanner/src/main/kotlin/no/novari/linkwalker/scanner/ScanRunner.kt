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
import no.novari.linkwalker.report.ReportProblem
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
        val orgId = OrgId(config.orgId)
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
        val summary = summaryBuilder.build(result.index, result.problems)
        publish(orgId, summary, result.problems, completedAt)
        logFinished(summary, result.problems, started, completedAt)
    }

    private suspend fun scan(orgId: OrgId): ScanResult {
        val bearer = authService.getBearerToken(orgId)
            ?: error("No bearer token for org-id $orgId — aborting")

        val index = indexBuilder.buildIndex(components = config.components, bearer = bearer)
        val problems = indexValidator.validate(orgId, index)
        logger.info("Indexed records={} broken-link problems={}", index.records.size, problems.size)
        return ScanResult(index, problems)
    }

    private fun publish(orgId: OrgId, summary: ScanSummary, problems: List<ReportProblem>, completedAt: Instant) {
        reportStore.publish(
            LatestReport(
                scanCompletedAt = completedAt,
                orgId = orgId,
                components = config.components,
                summary = summary,
                problems = problems,
            )
        )
    }

    private fun logFinished(summary: ScanSummary, problems: List<ReportProblem>, started: Instant, completedAt: Instant) {
        val duration = Duration.between(started, completedAt)
        logger.info(
            "Scan finished: integrity={}% problems={} duration={}s",
            summary.integrityPercent, problems.size, duration.toSeconds(),
        )
    }

    private data class ScanResult(val index: TenantIndex, val problems: List<ReportProblem>)
}

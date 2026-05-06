package no.novari.linkwalker.scanner

import kotlinx.coroutines.runBlocking
import no.novari.linkwalker.auth.AuthService
import no.novari.linkwalker.config.LinkWalkerConfig
import no.novari.linkwalker.index.IndexBuilder
import no.novari.linkwalker.index.IndexValidator
import no.novari.linkwalker.index.TenantIndex
import no.novari.linkwalker.report.LatestReport
import no.novari.linkwalker.report.ReportRow
import no.novari.linkwalker.report.ReportStore
import no.novari.linkwalker.report.SummaryBuilder
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class ScanRunner(
    private val config: LinkWalkerConfig,
    private val authService: AuthService,
    private val indexBuilder: IndexBuilder,
    private val indexValidator: IndexValidator,
    private val summaryBuilder: SummaryBuilder,
    private val reportStore: ReportStore,
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) = runBlocking {
        val orgId = requireNotNull(config.orgId?.takeIf { it.isNotBlank() }) {
            "link-walker.org-id must be set (e.g. --link-walker.org-id=afk-no)"
        }

        logger.info("Scan starting: org-id={} components={}", orgId, config.components.size)
        val started = Instant.now()

        val (index, rows) = scan(orgId)
        val summary = summaryBuilder.build(index, rows)

        reportStore.publish(
            LatestReport(
                scanCompletedAt = Instant.now(),
                orgId = orgId,
                components = config.components,
                summary = summary,
                rows = rows,
            )
        )

        val duration = Duration.between(started, Instant.now())
        logger.info(
            "Scan finished: org-id={} integrity={}% rows={} duration={}s",
            orgId, summary.integrityPercent, rows.size, duration.toSeconds(),
        )
    }

    private suspend fun scan(orgId: String): Pair<TenantIndex, List<ReportRow>> {
        val bearer = authService.getBearerToken(orgId)
            ?: error("No bearer token for org-id $orgId — aborting")

        val index = indexBuilder.buildIndex(
            components = config.components,
            bearer = bearer,
            onComponentError = { component ->
                logger.error("Fetch failed for org-id={} component={}", orgId, component)
            },
        )

        val rows = indexValidator.validate(orgId, index)
        logger.info(
            "Indexed records={} broken-link rows={}",
            index.records.size, rows.size,
        )
        return index to rows
    }
}

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
        val tenant = requireNotNull(config.tenant?.takeIf { it.isNotBlank() }) {
            "link-walker.tenant must be set (e.g. --link-walker.tenant=afk-no)"
        }

        logger.info("Scan starting: tenant={} components={}", tenant, config.components.size)
        val started = Instant.now()

        val (index, rows) = scanTenant(tenant)
        val summary = summaryBuilder.build(index, rows)

        reportStore.publish(
            LatestReport(
                scanCompletedAt = Instant.now(),
                tenants = listOf(tenant),
                components = config.components,
                summary = summary,
                rows = rows,
            )
        )

        val duration = Duration.between(started, Instant.now())
        logger.info(
            "Scan finished: tenant={} integrity={}% rows={} duration={}s",
            tenant, summary.integrityPercent, rows.size, duration.toSeconds(),
        )
    }

    private suspend fun scanTenant(tenant: String): Pair<TenantIndex, List<ReportRow>> {
        val bearer = authService.getBearerToken(tenant)
            ?: error("No bearer token for tenant $tenant — aborting")

        val index = indexBuilder.buildIndex(
            components = config.components,
            bearer = bearer,
            onComponentError = { component ->
                logger.error("Fetch failed for tenant={} component={}", tenant, component)
            },
        )

        val rows = indexValidator.validate(tenant, index)
        logger.info(
            "Indexed records={} broken-link rows={}",
            index.records.size, rows.size,
        )
        return index to rows
    }
}

package no.novari.linkwalker.reader

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import no.novari.linkwalker.report.LatestReportSummary
import no.novari.linkwalker.report.ReportStore
import no.novari.linkwalker.report.ResourceSummary
import no.novari.linkwalker.report.ScanSummary
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SummaryMetrics(
    private val reportStore: ReportStore,
    registry: MeterRegistry,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val integrityPercent: MultiGauge =
        MultiGauge.builder("link_walker_integrity_percent")
            .description("Link integrity percent per (tenant, component, resource)")
            .register(registry)

    private val recordsTotal: MultiGauge =
        MultiGauge.builder("link_walker_records_count")
            .description("Records scanned per (tenant, component, resource)")
            .register(registry)

    private val refsTotal: MultiGauge =
        MultiGauge.builder("link_walker_refs_count")
            .description("Outbound refs per (tenant, component, resource)")
            .register(registry)

    private val brokenLinks: MultiGauge =
        MultiGauge.builder("link_walker_broken_links")
            .description("Broken-link counts per (tenant, component, resource, problem_type)")
            .register(registry)

    private val tenantIntegrity: MultiGauge =
        MultiGauge.builder("link_walker_tenant_integrity_percent")
            .description("Overall integrity percent per tenant")
            .register(registry)

    @Scheduled(fixedRate = 60_000, initialDelay = 5_000)
    fun refresh() {
        val summaries = reportStore.listSummaries()
        if (summaries.isEmpty()) {
            logger.debug("No reports available — skipping metrics refresh")
            return
        }
        publishAll(summaries)
    }

    private fun publishAll(summaries: List<LatestReportSummary>) {
        val perTenant: List<Pair<String, ScanSummary>> = summaries.map { doc ->
            val tenant = doc.tenants.firstOrNull() ?: "unknown"
            tenant to doc.summary
        }

        tenantIntegrity.register(
            perTenant.map { (tenant, summary) ->
                MultiGauge.Row.of(Tags.of("tenant", tenant), summary.integrityPercent)
            },
            true,
        )

        integrityPercent.register(perTenant.flatMap { (tenant, summary) -> resourceRows(tenant, summary) { it.integrityPercent } }, true)
        recordsTotal.register(perTenant.flatMap { (tenant, summary) -> resourceRows(tenant, summary) { it.totalRecords.toDouble() } }, true)
        refsTotal.register(perTenant.flatMap { (tenant, summary) -> resourceRows(tenant, summary) { it.totalRefs.toDouble() } }, true)
        brokenLinks.register(perTenant.flatMap { (tenant, summary) -> brokenRows(tenant, summary) }, true)

        logger.debug(
            "Refreshed metrics across {} tenants: {}",
            perTenant.size, perTenant.joinToString { "${it.first}=${it.second.integrityPercent}%" },
        )
    }

    private fun resourceRows(
        tenant: String,
        summary: ScanSummary,
        value: (ResourceSummary) -> Double,
    ): List<MultiGauge.Row<Number>> = summary.components.flatMap { comp ->
        comp.resources.map { res ->
            MultiGauge.Row.of(
                Tags.of("tenant", tenant, "component", comp.component, "resource", res.resource),
                value(res),
            )
        }
    }

    private fun brokenRows(tenant: String, summary: ScanSummary): List<MultiGauge.Row<Number>> =
        summary.components.flatMap { comp ->
            comp.resources.flatMap { res ->
                res.byProblemType.map { (problemType, count) ->
                    MultiGauge.Row.of(
                        Tags.of(
                            "tenant", tenant,
                            "component", comp.component,
                            "resource", res.resource,
                            "problem_type", problemType,
                        ),
                        count.toDouble(),
                    )
                }
            }
        }
}

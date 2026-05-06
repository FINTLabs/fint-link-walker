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
            .description("Link integrity percent per (orgId, component, resource)")
            .register(registry)

    private val recordsTotal: MultiGauge =
        MultiGauge.builder("link_walker_records_count")
            .description("Records scanned per (orgId, component, resource)")
            .register(registry)

    private val refsTotal: MultiGauge =
        MultiGauge.builder("link_walker_refs_count")
            .description("Outbound refs per (orgId, component, resource)")
            .register(registry)

    private val brokenLinks: MultiGauge =
        MultiGauge.builder("link_walker_broken_links")
            .description("Broken-link counts per (orgId, component, resource, problem_type)")
            .register(registry)

    private val orgIntegrity: MultiGauge =
        MultiGauge.builder("link_walker_org_integrity_percent")
            .description("Overall integrity percent per orgId")
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
        val perOrg: List<Pair<String, ScanSummary>> = summaries.map { doc ->
            doc.orgId to doc.summary
        }

        orgIntegrity.register(
            perOrg.mapNotNull { (orgId, summary) ->
                summary.integrityPercent?.let {
                    MultiGauge.Row.of(Tags.of("orgId", orgId), it)
                }
            },
            true,
        )

        integrityPercent.register(perOrg.flatMap { (orgId, summary) -> resourceRowsNullable(orgId, summary) { it.integrityPercent } }, true)
        recordsTotal.register(perOrg.flatMap { (orgId, summary) -> resourceRows(orgId, summary) { it.totalRecords.toDouble() } }, true)
        refsTotal.register(perOrg.flatMap { (orgId, summary) -> resourceRows(orgId, summary) { it.totalRefs.toDouble() } }, true)
        brokenLinks.register(perOrg.flatMap { (orgId, summary) -> brokenRows(orgId, summary) }, true)

        logger.debug(
            "Refreshed metrics across {} orgs: {}",
            perOrg.size, perOrg.joinToString { "${it.first}=${it.second.integrityPercent}%" },
        )
    }

    private fun resourceRows(
        orgId: String,
        summary: ScanSummary,
        value: (ResourceSummary) -> Double,
    ): List<MultiGauge.Row<Number>> = summary.components.flatMap { comp ->
        comp.resources.map { res ->
            MultiGauge.Row.of(
                Tags.of("orgId", orgId, "component", comp.component, "resource", res.resource),
                value(res),
            )
        }
    }

    private fun resourceRowsNullable(
        orgId: String,
        summary: ScanSummary,
        value: (ResourceSummary) -> Double?,
    ): List<MultiGauge.Row<Number>> = summary.components.flatMap { comp ->
        comp.resources.mapNotNull { res ->
            value(res)?.let {
                MultiGauge.Row.of(
                    Tags.of("orgId", orgId, "component", comp.component, "resource", res.resource),
                    it,
                )
            }
        }
    }

    private fun brokenRows(orgId: String, summary: ScanSummary): List<MultiGauge.Row<Number>> =
        summary.components.flatMap { comp ->
            comp.resources.flatMap { res ->
                res.byProblemType.map { (problemType, count) ->
                    MultiGauge.Row.of(
                        Tags.of(
                            "orgId", orgId,
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

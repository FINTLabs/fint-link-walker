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

    private val integrityPercent: MultiGauge = gauge(registry, "link_walker_integrity_percent",
        "Link integrity percent per (orgId, component, resource)")

    private val recordsTotal: MultiGauge = gauge(registry, "link_walker_records_count",
        "Records scanned per (orgId, component, resource)")

    private val refsTotal: MultiGauge = gauge(registry, "link_walker_refs_count",
        "Outbound refs per (orgId, component, resource)")

    private val brokenLinks: MultiGauge = gauge(registry, "link_walker_broken_links",
        "Broken-link counts per (orgId, component, resource, problem_type)")

    private val orgIntegrity: MultiGauge = gauge(registry, "link_walker_org_integrity_percent",
        "Overall integrity percent per orgId")

    private val lastScanTimestamp: MultiGauge = gauge(registry, "link_walker_last_scan_timestamp_seconds",
        "Epoch seconds of the last successful scan per orgId")

    @Scheduled(fixedDelay = 60_000, initialDelay = 5_000)
    fun refresh() {
        // A transient DB error shouldn't take the scheduler down or
        // freeze the previous metrics — log and try again next tick.
        try {
            val summaries = reportStore.listSummaries()
            if (summaries.isEmpty()) {
                logger.debug("No reports available — skipping metrics refresh")
                return
            }
            publishAll(summaries)
        } catch (ex: Exception) {
            logger.warn("Metrics refresh failed; will retry next tick", ex)
        }
    }

    private fun publishAll(summaries: List<LatestReportSummary>) {
        // Tags are strings on the wire; unwrap OrgId once here.
        val perOrg = summaries.map { it.orgId.value to it.summary }

        orgIntegrity.register(orgIntegrityRows(perOrg), true)
        lastScanTimestamp.register(lastScanRows(summaries), true)
        integrityPercent.register(perOrg.flatMap { (org, s) -> resourceRows(org, s) { it.integrityPercent } }, true)
        recordsTotal.register(perOrg.flatMap { (org, s) -> resourceRows(org, s) { it.totalRecords.toDouble() } }, true)
        refsTotal.register(perOrg.flatMap { (org, s) -> resourceRows(org, s) { it.totalRefs.toDouble() } }, true)
        brokenLinks.register(perOrg.flatMap { (org, s) -> brokenRows(org, s) }, true)

        logger.debug(
            "Refreshed metrics across {} orgs: {}",
            perOrg.size, perOrg.joinToString { "${it.first}=${it.second.integrityPercent}%" },
        )
    }

    private fun lastScanRows(summaries: List<LatestReportSummary>): List<MultiGauge.Row<Number>> =
        summaries.map {
            MultiGauge.Row.of(Tags.of("orgId", it.orgId.value), it.scanCompletedAt.epochSecond.toDouble())
        }

    private fun orgIntegrityRows(
        perOrg: List<Pair<String, ScanSummary>>,
    ): List<MultiGauge.Row<Number>> =
        perOrg.mapNotNull { (orgId, summary) ->
            summary.integrityPercent?.let { MultiGauge.Row.of(Tags.of("orgId", orgId), it) }
        }

    private fun resourceRows(
        orgId: String,
        summary: ScanSummary,
        value: (ResourceSummary) -> Double?,
    ): List<MultiGauge.Row<Number>> =
        summary.components.flatMap { comp ->
            comp.resources.mapNotNull { res ->
                value(res)?.let { MultiGauge.Row.of(resourceTags(orgId, comp.component, res.resource), it) }
            }
        }

    private fun brokenRows(orgId: String, summary: ScanSummary): List<MultiGauge.Row<Number>> =
        summary.components.flatMap { comp ->
            comp.resources.flatMap { res ->
                res.byProblemType.map { (problemType, count) ->
                    MultiGauge.Row.of(
                        resourceTags(orgId, comp.component, res.resource).and("problem_type", problemType),
                        count.toDouble(),
                    )
                }
            }
        }

    private fun resourceTags(orgId: String, component: String, resource: String): Tags =
        Tags.of("orgId", orgId, "component", component, "resource", resource)

    private companion object {
        fun gauge(registry: MeterRegistry, name: String, description: String): MultiGauge =
            MultiGauge.builder(name).description(description).register(registry)
    }
}

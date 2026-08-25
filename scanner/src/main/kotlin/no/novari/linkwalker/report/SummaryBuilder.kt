package no.novari.linkwalker.report

import no.novari.linkwalker.index.MinimalRecord
import no.novari.linkwalker.index.TenantIndex
import org.springframework.stereotype.Component

@Component
class SummaryBuilder {

    fun build(index: TenantIndex, problems: List<ReportProblem>): ScanSummary {
        val problemsByComponent = problems.groupBy { it.component }
        val recordsByComponent = index.records.groupBy { it.component }

        val components = recordsByComponent
            .map { (component, records) ->
                buildComponent(component, records, problemsByComponent[component] ?: emptyList())
            }
            .sortedByDescending { it.brokenLinkCount }

        val agg = aggregate(index.records, problems)
        return ScanSummary(
            totalRecords = agg.totalRecords,
            totalRefs = agg.totalRefs,
            brokenLinkCount = agg.brokenLinkCount,
            integrityPercent = agg.integrityPercent,
            byProblemType = agg.byProblemType,
            components = components,
        )
    }

    private fun buildComponent(
        component: String,
        records: List<MinimalRecord>,
        problems: List<ReportProblem>,
    ): ComponentSummary {
        val problemsByResource = problems.groupBy { it.resource }
        val recordsByResource = records.groupBy { it.resourceName }

        val resources = recordsByResource
            .map { (resource, recs) ->
                buildResource(resource, recs, problemsByResource[resource] ?: emptyList())
            }
            .sortedByDescending { it.brokenLinkCount }

        val agg = aggregate(records, problems)
        return ComponentSummary(
            component = component,
            totalRecords = agg.totalRecords,
            totalRefs = agg.totalRefs,
            brokenLinkCount = agg.brokenLinkCount,
            integrityPercent = agg.integrityPercent,
            byProblemType = agg.byProblemType,
            resources = resources,
        )
    }

    private fun buildResource(
        resource: String,
        records: List<MinimalRecord>,
        problems: List<ReportProblem>,
    ): ResourceSummary {
        val agg = aggregate(records, problems)
        return ResourceSummary(
            resource = resource,
            totalRecords = agg.totalRecords,
            totalRefs = agg.totalRefs,
            brokenLinkCount = agg.brokenLinkCount,
            integrityPercent = agg.integrityPercent,
            byProblemType = agg.byProblemType,
        )
    }

    private fun aggregate(records: List<MinimalRecord>, problems: List<ReportProblem>): Aggregates {
        val totalRefs = records.sumOf { refCount(it) }
        val broken = problems.size.toLong()
        return Aggregates(
            totalRecords = records.size.toLong(),
            totalRefs = totalRefs,
            brokenLinkCount = broken,
            integrityPercent = integrity(totalRefs, broken),
            byProblemType = countByProblemType(problems),
        )
    }

    // Malformed hrefs count as both a ref (denominator of integrity) and a broken
    // ref (numerator) — they surface as `unknown-link` problems, so integrity drops
    // by their share just like missing-resource and missing-back-link findings.
    private fun refCount(record: MinimalRecord): Long =
        (record.outboundRefs.size + record.malformedHrefs.size).toLong()

    private fun countByProblemType(problems: List<ReportProblem>): Map<String, Long> =
        problems.groupingBy { it.problemType.wire }.eachCount().mapValues { it.value.toLong() }

    private fun integrity(totalRefs: Long, broken: Long): Double? {
        if (totalRefs == 0L) return null
        val pct = (1.0 - broken.toDouble() / totalRefs) * 100
        return truncateToTwoDecimals(pct).coerceIn(0.0, 100.0)
    }

    private fun truncateToTwoDecimals(value: Double): Double =
        (value * 100).toLong() / 100.0

    private data class Aggregates(
        val totalRecords: Long,
        val totalRefs: Long,
        val brokenLinkCount: Long,
        val integrityPercent: Double?,
        val byProblemType: Map<String, Long>,
    )
}

package no.novari.linkwalker.report

import no.novari.linkwalker.index.MinimalRecord
import no.novari.linkwalker.index.TenantIndex
import org.springframework.stereotype.Component

@Component
class SummaryBuilder {

    fun build(index: TenantIndex, rows: List<ReportRow>): ScanSummary {
        val rowsByComponent = rows.groupBy { it.component }
        val recordsByComponent = index.records.groupBy { it.component }

        val components = recordsByComponent
            .map { (component, records) ->
                buildComponent(component, records, rowsByComponent[component] ?: emptyList())
            }
            .sortedByDescending { it.brokenLinkCount }

        val totalRecords = index.records.size.toLong()
        val totalRefs = index.records.sumOf { refCount(it) }
        val brokenLinkCount = rows.size.toLong()

        return ScanSummary(
            totalRecords = totalRecords,
            totalRefs = totalRefs,
            brokenLinkCount = brokenLinkCount,
            integrityPercent = integrity(totalRefs, brokenLinkCount),
            byProblemType = countByProblemType(rows),
            components = components,
        )
    }

    private fun buildComponent(
        component: String,
        records: List<MinimalRecord>,
        rows: List<ReportRow>,
    ): ComponentSummary {
        val rowsByResource = rows.groupBy { it.resource }
        val recordsByResource = records.groupBy { it.resourceName }

        val resources = recordsByResource
            .map { (resource, recs) ->
                buildResource(resource, recs, rowsByResource[resource] ?: emptyList())
            }
            .sortedByDescending { it.brokenLinkCount }

        val totalRecords = records.size.toLong()
        val totalRefs = records.sumOf { refCount(it) }
        val brokenLinkCount = rows.size.toLong()

        return ComponentSummary(
            component = component,
            totalRecords = totalRecords,
            totalRefs = totalRefs,
            brokenLinkCount = brokenLinkCount,
            integrityPercent = integrity(totalRefs, brokenLinkCount),
            byProblemType = countByProblemType(rows),
            resources = resources,
        )
    }

    private fun buildResource(
        resource: String,
        records: List<MinimalRecord>,
        rows: List<ReportRow>,
    ): ResourceSummary {
        val totalRecords = records.size.toLong()
        val totalRefs = records.sumOf { refCount(it) }
        val brokenLinkCount = rows.size.toLong()

        return ResourceSummary(
            resource = resource,
            totalRecords = totalRecords,
            totalRefs = totalRefs,
            brokenLinkCount = brokenLinkCount,
            integrityPercent = integrity(totalRefs, brokenLinkCount),
            byProblemType = countByProblemType(rows),
        )
    }

    private fun refCount(record: MinimalRecord): Long =
        (record.outboundRefs.size + record.malformedHrefs.size).toLong()

    private fun countByProblemType(rows: List<ReportRow>): Map<String, Long> =
        rows.groupingBy { it.problemType }.eachCount().mapValues { it.value.toLong() }

    private fun integrity(totalRefs: Long, broken: Long): Double {
        if (totalRefs == 0L) return 100.0
        val pct = (1.0 - broken.toDouble() / totalRefs) * 100
        return ((pct * 100).toLong() / 100.0).coerceIn(0.0, 100.0)
    }
}

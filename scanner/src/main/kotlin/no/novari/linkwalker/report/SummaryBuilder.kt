package no.novari.linkwalker.report

import no.novari.linkwalker.index.LinkTarget
import no.novari.linkwalker.index.MinimalRecord
import no.novari.linkwalker.index.TenantIndex
import org.springframework.stereotype.Component

@Component
class SummaryBuilder {

    fun build(index: TenantIndex, problems: List<ReportProblem>): ScanSummary {
        val problemsByComponent = problems.groupBy { it.component }

        val components = index.records.groupBy { it.component }
            .map { (component, records) ->
                buildComponent(index, component, records, problemsByComponent[component].orEmpty())
            }
            .sortedByDescending { it.brokenLinkCount }

        val totalRefs = components.sumOf { it.totalRefs }
        val broken = components.sumOf { it.brokenLinkCount }
        return ScanSummary(
            totalRecords = components.sumOf { it.totalRecords },
            totalRefs = totalRefs,
            brokenLinkCount = broken,
            integrityPercent = integrity(totalRefs, broken),
            byProblemType = sumByProblemType(components.map { it.byProblemType }),
            components = components,
        )
    }

    private fun buildComponent(
        index: TenantIndex,
        component: String,
        records: List<MinimalRecord>,
        problems: List<ReportProblem>,
    ): ComponentSummary {
        val problemsByResource = problems.groupBy { it.resource }

        val resources = records.groupBy { it.resourceName }
            .map { (resource, recs) ->
                buildResource(index, resource, recs, problemsByResource[resource].orEmpty())
            }
            .sortedByDescending { it.brokenLinkCount }

        val totalRefs = resources.sumOf { it.totalRefs }
        val broken = resources.sumOf { it.brokenLinkCount }
        return ComponentSummary(
            component = component,
            totalRecords = resources.sumOf { it.totalRecords },
            totalRefs = totalRefs,
            brokenLinkCount = broken,
            integrityPercent = integrity(totalRefs, broken),
            byProblemType = sumByProblemType(resources.map { it.byProblemType }),
            resources = resources,
        )
    }

    private fun buildResource(
        index: TenantIndex,
        resource: String,
        records: List<MinimalRecord>,
        problems: List<ReportProblem>,
    ): ResourceSummary {
        val links = linkGroups(index, records, problems)
        val totalRefs = links.filter { it.scope != LinkScope.NotCovered }.sumOf { it.links }
        val broken = problems.size.toLong()
        return ResourceSummary(
            resource = resource,
            totalRecords = records.size.toLong(),
            totalRefs = totalRefs,
            brokenLinkCount = broken,
            integrityPercent = integrity(totalRefs, broken),
            byProblemType = countByProblemType(problems),
            links = links,
        )
    }

    /**
     * Malformed hrefs count as both a checked link and an error: they show up as `unknown-link`
     * problems, and are put within the source's own domain since its adapter sent them.
     */
    private fun linkGroups(
        index: TenantIndex,
        records: List<MinimalRecord>,
        problems: List<ReportProblem>,
    ): List<LinkGroup> {
        val links = HashMap<LinkTarget, Long>()
        records.forEach { record ->
            record.outboundRefs.forEach { ref ->
                links.merge(index.linkTarget(record.component, ref.targetCanonical), 1L, Long::plus)
            }
            if (record.malformedHrefs.isNotEmpty()) {
                links.merge(MALFORMED, record.malformedHrefs.size.toLong(), Long::plus)
            }
        }
        val errors = problems
            .groupBy { problem ->
                if (problem.problemType == ProblemType.UnknownLink) MALFORMED
                else index.linkTarget(problem.component, problem.targetHref)
            }
            .mapValues { (_, grouped) -> countByProblemType(grouped) }

        return (links.keys + errors.keys)
            .map { target -> LinkGroup(target.scope, target.component, links[target] ?: 0L, errors[target].orEmpty()) }
            .sortedWith(compareBy({ it.scope }, { it.targetComponent }))
    }

    private fun countByProblemType(problems: List<ReportProblem>): Map<String, Long> =
        problems.groupingBy { it.problemType.wire }.eachCount().mapValues { it.value.toLong() }

    private fun sumByProblemType(counts: List<Map<String, Long>>): Map<String, Long> =
        counts.flatMap { it.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }

    private fun integrity(totalRefs: Long, broken: Long): Double? {
        if (totalRefs == 0L) return null
        val pct = (1.0 - broken.toDouble() / totalRefs) * 100
        return truncateToTwoDecimals(pct).coerceIn(0.0, 100.0)
    }

    private fun truncateToTwoDecimals(value: Double): Double =
        (value * 100).toLong() / 100.0

    private companion object {
        val MALFORMED = LinkTarget(LinkScope.WithinDomain, LinkGroup.UNKNOWN_TARGET)
    }
}

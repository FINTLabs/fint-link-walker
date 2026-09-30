package no.novari.linkwalker.report

data class ScanSummary(
    val totalRecords: Long,
    val totalRefs: Long,
    val brokenLinkCount: Long,
    val integrityPercent: Double?,
    val byProblemType: Map<String, Long>,
    val components: List<ComponentSummary>,
    val gatewayCanary: GatewayCanaryResult? = null,
)

/**
 * The outcome of the one page fetched through the public host when the scan otherwise goes around
 * the Access Gateway. `ok` is false when the body carried a damage signature or the fetch failed.
 */
data class GatewayCanaryResult(
    val url: String,
    val ok: Boolean,
    val via: String?,
    val bytes: Long,
    val sha256: String?,
    val signature: String?,
    val byteOffset: Long?,
    val error: String?,
)

data class ComponentSummary(
    val component: String,
    val totalRecords: Long,
    val totalRefs: Long,
    val brokenLinkCount: Long,
    val integrityPercent: Double?,
    val byProblemType: Map<String, Long>,
    val resources: List<ResourceSummary>,
)

/**
 * [totalRefs] and [integrityPercent] only count links that were checked: [LinkScope.NotCovered]
 * links are left out of both. [links] breaks every link down by scope and target component, and
 * is empty for summaries written before it existed.
 */
data class ResourceSummary(
    val resource: String,
    val totalRecords: Long,
    val totalRefs: Long,
    val brokenLinkCount: Long,
    val integrityPercent: Double?,
    val byProblemType: Map<String, Long>,
    val links: List<LinkGroup> = emptyList(),
)

/**
 * The links from one resource that share a [scope] and a [targetComponent]. [errors] counts
 * them per problem type and is always empty for [LinkScope.NotCovered]. Hrefs with the wrong
 * format have [targetComponent] [UNKNOWN_TARGET].
 */
data class LinkGroup(
    val scope: LinkScope,
    val targetComponent: String,
    val links: Long,
    val errors: Map<String, Long> = emptyMap(),
) {
    companion object {
        const val UNKNOWN_TARGET = "unknown"
    }
}

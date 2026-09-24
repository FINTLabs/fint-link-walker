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

data class ResourceSummary(
    val resource: String,
    val totalRecords: Long,
    val totalRefs: Long,
    val brokenLinkCount: Long,
    val integrityPercent: Double?,
    val byProblemType: Map<String, Long>,
)

package no.novari.linkwalker.report

data class ScanSummary(
    val totalRecords: Long,
    val totalRefs: Long,
    val brokenLinkCount: Long,
    val integrityPercent: Double?,
    val byProblemType: Map<String, Long>,
    val components: List<ComponentSummary>,
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

package no.novari.linkwalker.report

import io.swagger.v3.oas.annotations.media.Schema

private const val PROBLEM_TYPE_BREAKDOWN = """
{
  "missing-resource": 12,
  "unknown-link": 3,
  "missing-back-link-adapter": 5,
  "missing-back-link-autorelation": 0
}
"""

@Schema(description = "Tenant-level scan aggregate, with nested per-component and per-resource breakdowns.")
data class ScanSummary(
    @field:Schema(description = "Total record count scanned across all components and resources.")
    val totalRecords: Long,
    @field:Schema(description = "Total outbound references found across all records.")
    val totalRefs: Long,
    @field:Schema(description = "Total broken-link findings (sum across all problem types).")
    val brokenLinkCount: Long,
    @field:Schema(
        description = "Link integrity as `(1 − brokenLinkCount / totalRefs) × 100`. " +
            "Null when there were no refs to measure (failed or empty scan).",
        example = "99.92",
        nullable = true,
    )
    val integrityPercent: Double?,
    @field:Schema(
        description = "Broken-link counts keyed by problem type.",
        example = PROBLEM_TYPE_BREAKDOWN,
    )
    val byProblemType: Map<String, Long>,
    val components: List<ComponentSummary>,
)

@Schema(description = "Per-component aggregate within a tenant scan.")
data class ComponentSummary(
    @field:Schema(description = "FINT component (`<domain>_<subdomain>`).", example = "utdanning_elev")
    val component: String,
    val totalRecords: Long,
    val totalRefs: Long,
    val brokenLinkCount: Long,
    @field:Schema(nullable = true)
    val integrityPercent: Double?,
    @field:Schema(
        description = "Broken-link counts within this component, keyed by problem type.",
        example = PROBLEM_TYPE_BREAKDOWN,
    )
    val byProblemType: Map<String, Long>,
    val resources: List<ResourceSummary>,
)

@Schema(description = "Per-resource aggregate within a component.")
data class ResourceSummary(
    @field:Schema(description = "Resource name within the component.", example = "elevforhold")
    val resource: String,
    val totalRecords: Long,
    val totalRefs: Long,
    val brokenLinkCount: Long,
    @field:Schema(nullable = true)
    val integrityPercent: Double?,
    @field:Schema(
        description = "Broken-link counts within this resource, keyed by problem type.",
        example = PROBLEM_TYPE_BREAKDOWN,
    )
    val byProblemType: Map<String, Long>,
)

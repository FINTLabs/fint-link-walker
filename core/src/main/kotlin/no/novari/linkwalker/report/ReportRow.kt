package no.novari.linkwalker.report

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "A single broken-link finding.")
data class ReportRow(
    @field:Schema(description = "Tenant org-id this row belongs to.", example = "agderfk-no")
    val tenant: String,
    @field:Schema(description = "FINT component (`<domain>_<subdomain>`).", example = "utdanning_elev")
    val component: String,
    @field:Schema(description = "Resource within the component.", example = "elevforhold")
    val resource: String,
    @field:Schema(
        description = "Classification of the broken link.",
        allowableValues = [
            "missing-resource",
            "unknown-link",
            "missing-back-link-adapter",
            "missing-back-link-autorelation",
        ],
    )
    val problemType: String,
    @field:Schema(description = "`self` href of the source record (PII identifier values masked).")
    val sourceSelf: String,
    @field:Schema(description = "Target href the source pointed at (PII identifier values masked).")
    val targetHref: String,
    @field:Schema(description = "Name of the `_links` relation on the source record. Null for `unknown-link`.")
    val relationName: String? = null,
    @field:Schema(description = "Name of the inverse relation that should have pointed back. Set only for `missing-back-link-*`.")
    val expectedInverseName: String? = null,
)

@Schema(description = "Full in-memory aggregate the scanner builds for one scan. Persisted as two split blobs.")
data class LatestReport(
    val scanCompletedAt: Instant,
    val tenants: List<String>,
    val components: List<String>,
    val summary: ScanSummary,
    val rows: List<ReportRow>,
)

@Schema(description = "Summary half of a scan report. Contains aggregate counts and integrity per (component, resource, problem-type).")
data class LatestReportSummary(
    @field:Schema(description = "When the scan finished, in UTC ISO-8601.")
    val scanCompletedAt: Instant,
    @field:Schema(description = "Tenants covered by this report (typically a single org-id).")
    val tenants: List<String>,
    @field:Schema(description = "Components included in the scan.")
    val components: List<String>,
    val summary: ScanSummary,
)

@Schema(description = "Rows half of a scan report. Contains the per-broken-link records (one entry per finding).")
data class LatestReportRows(
    val scanCompletedAt: Instant,
    val tenants: List<String>,
    val rows: List<ReportRow>,
)

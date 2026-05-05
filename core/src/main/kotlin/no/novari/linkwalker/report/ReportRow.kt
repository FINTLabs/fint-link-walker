package no.novari.linkwalker.report

import java.time.Instant

data class ReportRow(
    val tenant: String,
    val component: String,
    val resource: String,
    val problemType: String,
    val sourceSelf: String,
    val targetHref: String,
    val relationName: String? = null,
    val expectedInverseName: String? = null,
)

data class LatestReport(
    val scanCompletedAt: Instant,
    val tenants: List<String>,
    val components: List<String>,
    val summary: ScanSummary,
    val rows: List<ReportRow>,
)

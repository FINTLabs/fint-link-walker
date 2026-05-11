package no.novari.linkwalker.report

import no.novari.linkwalker.OrgId
import java.time.Instant

data class ReportRow(
    val orgId: OrgId,
    val component: String,
    val resource: String,
    val problemType: ProblemType,
    val sourceSelf: String,
    val targetHref: String,
    val relationName: String? = null,
    val expectedInverseName: String? = null,
)

data class LatestReport(
    val scanCompletedAt: Instant,
    val orgId: OrgId,
    val components: List<String>,
    val summary: ScanSummary,
    val rows: List<ReportRow>,
)

data class LatestReportSummary(
    val scanCompletedAt: Instant,
    val orgId: OrgId,
    val components: List<String>,
    val summary: ScanSummary,
)

package no.novari.linkwalker.report

import no.novari.linkwalker.OrgId
import java.time.Instant

data class ReportProblem(
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
    val problems: List<ReportProblem>,
)

data class LatestReportSummary(
    val scanCompletedAt: Instant,
    val orgId: OrgId,
    val components: List<String>,
    val summary: ScanSummary,
)

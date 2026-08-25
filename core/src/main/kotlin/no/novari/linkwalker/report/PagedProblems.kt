package no.novari.linkwalker.report

import java.time.Instant

data class ProblemFilter(
    val component: String? = null,
    val resource: String? = null,
    val problemType: ProblemType? = null,
)

data class PagedProblems(
    val problems: List<ReportProblem>,
    val page: Int,
    val size: Int,
    val totalProblems: Long,
    val totalPages: Int,
    val scanCompletedAt: Instant,
)

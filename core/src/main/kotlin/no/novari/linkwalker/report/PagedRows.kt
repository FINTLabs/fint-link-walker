package no.novari.linkwalker.report

import java.time.Instant

data class RowFilter(
    val component: String? = null,
    val resource: String? = null,
    val problemType: ProblemType? = null,
)

data class PagedRows(
    val rows: List<ReportRow>,
    val page: Int,
    val size: Int,
    val totalRows: Long,
    val totalPages: Int,
    val scanCompletedAt: Instant,
)

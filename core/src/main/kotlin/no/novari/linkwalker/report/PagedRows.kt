package no.novari.linkwalker.report

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Filter parameters for /rows. All fields AND-combined; nulls = no filter.")
data class RowFilter(
    val component: String? = null,
    val resource: String? = null,
    val problemType: String? = null,
)

@Schema(description = "A page of broken-link rows together with pagination metadata.")
data class PagedRows(
    @field:Schema(description = "Rows in this page (length ≤ `size`).")
    val rows: List<ReportRow>,
    @field:Schema(description = "Zero-based index of the page returned.")
    val page: Int,
    @field:Schema(description = "Effective page size used for this response (clamped to [1, 1000]).")
    val size: Int,
    @field:Schema(description = "Total number of rows matching the filter, across all pages.")
    val totalRows: Long,
    @field:Schema(description = "Total number of pages given the filter and `size`.")
    val totalPages: Int,
    @field:Schema(description = "When the scan that produced these rows finished.")
    val scanCompletedAt: Instant,
)

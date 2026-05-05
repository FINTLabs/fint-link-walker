package no.novari.linkwalker.reader

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import no.novari.linkwalker.report.LatestReportSummary
import no.novari.linkwalker.report.ReportRow
import no.novari.linkwalker.report.ReportStore
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/report")
@Tag(name = "Reports", description = "Per-tenant link-integrity scan reports")
class ReportController(
    private val reportStore: ReportStore,
) {

    @Operation(
        summary = "Per-tenant scan summary",
        description = "Returns the latest scan summary for the given tenant: aggregate counts and integrity nested by component → resource → problem-type. " +
            "Drives the dashboard's overview / drill-down views with a single fetch. 404 if no summary blob exists.",
    )
    @GetMapping("/{tenant}/summary")
    fun summary(
        @Parameter(description = "Tenant org-id, e.g. `agderfk-no`")
        @PathVariable tenant: String,
    ): ResponseEntity<LatestReportSummary> =
        reportStore.getSummary(tenant)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @Operation(
        summary = "Paginated, filterable broken-link rows for a tenant",
        description = "Returns one page of broken-link records for the given tenant. " +
            "Filters are AND-combined and applied before pagination, so `totalRows` reflects the filtered count. " +
            "404 if the tenant has no rows blob.",
    )
    @GetMapping("/{tenant}/rows")
    fun rows(
        @Parameter(description = "Tenant org-id, e.g. `agderfk-no`")
        @PathVariable tenant: String,
        @Parameter(description = "Filter by component (e.g. `utdanning_elev`)")
        @RequestParam(required = false) component: String?,
        @Parameter(description = "Filter by resource within the component (e.g. `elevforhold`)")
        @RequestParam(required = false) resource: String?,
        @Parameter(
            description = "Filter by problem type. One of: `missing-resource`, `unknown-link`, " +
                "`missing-back-link-adapter`, `missing-back-link-autorelation`."
        )
        @RequestParam(required = false, name = "problemType") problemType: String?,
        @Parameter(description = "Zero-based page index. Defaults to 0.")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size. Defaults to 100, capped at 1000.")
        @RequestParam(defaultValue = "100") size: Int,
    ): ResponseEntity<PagedRows> {
        val doc = reportStore.getRows(tenant) ?: return ResponseEntity.notFound().build()

        val filtered: List<ReportRow> = doc.rows
            .asSequence()
            .filter { component == null || it.component == component }
            .filter { resource == null || it.resource == resource }
            .filter { problemType == null || it.problemType == problemType }
            .toList()

        val total = filtered.size
        val pageSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val pageIndex = page.coerceAtLeast(0)
        val from = (pageIndex * pageSize).coerceAtMost(total)
        val to = (from + pageSize).coerceAtMost(total)

        return ResponseEntity.ok(
            PagedRows(
                rows = filtered.subList(from, to),
                page = pageIndex,
                size = pageSize,
                totalRows = total,
                totalPages = if (total == 0) 0 else (total + pageSize - 1) / pageSize,
            )
        )
    }

    companion object {
        private const val MAX_PAGE_SIZE = 1000
    }
}

@Schema(description = "A page of broken-link rows together with pagination metadata.")
data class PagedRows(
    @field:Schema(description = "Rows in this page (length ≤ `size`).")
    val rows: List<ReportRow>,
    @field:Schema(description = "Zero-based index of the page returned.")
    val page: Int,
    @field:Schema(description = "Effective page size used for this response (clamped to [1, 1000]).")
    val size: Int,
    @field:Schema(description = "Total number of rows matching the filter, across all pages.")
    val totalRows: Int,
    @field:Schema(description = "Total number of pages given the filter and `size`.")
    val totalPages: Int,
)

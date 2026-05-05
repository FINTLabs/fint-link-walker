package no.novari.linkwalker.reader

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
class ReportController(
    private val reportStore: ReportStore,
) {

    @GetMapping("/{tenant}/summary")
    fun summary(@PathVariable tenant: String): ResponseEntity<LatestReportSummary> =
        reportStore.getSummary(tenant)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @GetMapping("/{tenant}/rows")
    fun rows(
        @PathVariable tenant: String,
        @RequestParam(required = false) component: String?,
        @RequestParam(required = false) resource: String?,
        @RequestParam(required = false, name = "problemType") problemType: String?,
        @RequestParam(defaultValue = "0") page: Int,
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

data class PagedRows(
    val rows: List<ReportRow>,
    val page: Int,
    val size: Int,
    val totalRows: Int,
    val totalPages: Int,
)

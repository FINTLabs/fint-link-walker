package no.novari.linkwalker.reader

import jakarta.validation.constraints.Pattern
import no.novari.linkwalker.OrgId
import no.novari.linkwalker.report.LatestReportSummary
import no.novari.linkwalker.report.PagedRows
import no.novari.linkwalker.report.ProblemType
import no.novari.linkwalker.report.ReportStore
import no.novari.linkwalker.report.RowFilter
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus

private const val ORG_ID_PATTERN = "^[a-z0-9_]+$"
private const val ORG_ID_PATTERN_MESSAGE =
    "org-id must be lowercase alphanumeric with underscores only (e.g. agderfk_no)"

@RestController
@RequestMapping("/report")
@Validated
class ReportController(
    private val reportStore: ReportStore,
) {

    @GetMapping("/{orgId}/summary")
    fun summary(
        @PathVariable
        @Pattern(regexp = ORG_ID_PATTERN, message = ORG_ID_PATTERN_MESSAGE)
        orgId: String,
    ): ResponseEntity<LatestReportSummary> =
        reportStore.getSummary(OrgId(orgId))
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @GetMapping("/{orgId}/rows")
    fun rows(
        @PathVariable
        @Pattern(regexp = ORG_ID_PATTERN, message = ORG_ID_PATTERN_MESSAGE)
        orgId: String,
        @RequestParam(required = false) component: String?,
        @RequestParam(required = false) resource: String?,
        @RequestParam(required = false, name = "problemType") problemType: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
    ): ResponseEntity<PagedRows> {
        val problemTypeFilter = problemType?.let {
            ProblemType.parseOrNull(it) ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Unknown problemType: '$it'",
            )
        }
        val result = reportStore.findRows(
            orgId = OrgId(orgId),
            filter = RowFilter(component = component, resource = resource, problemType = problemTypeFilter),
            page = page,
            size = size,
        ) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }
}

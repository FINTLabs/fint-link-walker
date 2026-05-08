package no.novari.linkwalker.reader

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Pattern
import no.novari.linkwalker.report.LatestReportSummary
import no.novari.linkwalker.report.PagedRows
import no.novari.linkwalker.report.ReportStore
import no.novari.linkwalker.report.RowFilter
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val ORG_ID_PATTERN = "^[a-z0-9_]+$"
private const val ORG_ID_PATTERN_MESSAGE =
    "org-id must be lowercase alphanumeric with underscores only (e.g. agderfk_no)"

@RestController
@RequestMapping("/report")
@Validated
@Tag(name = "Reports", description = "Per-org link-integrity scan reports")
class ReportController(
    private val reportStore: ReportStore,
) {

    @Operation(
        summary = "Per-org scan summary",
        description = "Returns the latest scan summary for the given org: aggregate counts and integrity nested by component → resource → problem-type. " +
            "Drives the dashboard's overview / drill-down views with a single fetch.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Summary found and returned."),
        ApiResponse(
            responseCode = "404",
            description = "No summary exists for this org-id (scanner hasn't published yet, or wrong org-id format).",
            content = [Content()],
        ),
    )
    @GetMapping("/{orgId}/summary")
    fun summary(
        @Parameter(description = "Org id (lowercase, underscore-separated), e.g. `agderfk_no`")
        @PathVariable
        @Pattern(regexp = ORG_ID_PATTERN, message = ORG_ID_PATTERN_MESSAGE)
        orgId: String,
    ): ResponseEntity<LatestReportSummary> =
        reportStore.getSummary(orgId)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @Operation(
        summary = "Paginated, filterable broken-link rows for an org",
        description = "Returns one page of broken-link records for the given org. " +
            "Filters are AND-combined and applied before pagination, so `totalRows` reflects the filtered count.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Filtered, paginated rows returned."),
        ApiResponse(
            responseCode = "400",
            description = "Malformed query parameter (e.g. `page` or `size` not numeric).",
            content = [Content()],
        ),
        ApiResponse(
            responseCode = "404",
            description = "No rows exist for this org-id.",
            content = [Content()],
        ),
    )
    @GetMapping("/{orgId}/rows")
    fun rows(
        @Parameter(description = "Org id (lowercase, underscore-separated), e.g. `agderfk_no`")
        @PathVariable
        @Pattern(regexp = ORG_ID_PATTERN, message = ORG_ID_PATTERN_MESSAGE)
        orgId: String,
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
        val result = reportStore.findRows(
            orgId = orgId,
            filter = RowFilter(component = component, resource = resource, problemType = problemType),
            page = page,
            size = size,
        ) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }
}

package no.novari.linkwalker.reader

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Pattern
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import no.novari.linkwalker.report.ReportRow
import no.novari.linkwalker.report.ReportStore

private const val ORG_ID_PATTERN = "^[a-z0-9_]+$"
private const val ORG_ID_PATTERN_MESSAGE =
    "org-id must be lowercase alphanumeric with underscores only (e.g. agderfk_no)"

@Path("/report")
@Produces(MediaType.APPLICATION_JSON)
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
            description = "No summary blob exists for this org-id (scanner hasn't published yet, or wrong org-id format).",
            content = [Content()],
        ),
    )
    @GET
    @Path("/{orgId}/summary")
    fun summary(
        @Parameter(description = "Org id (lowercase, underscore-separated), e.g. `agderfk_no`")
        @PathParam("orgId")
        @Pattern(regexp = ORG_ID_PATTERN, message = ORG_ID_PATTERN_MESSAGE)
        orgId: String,
    ): Response =
        reportStore.getSummary(orgId)
            ?.let { Response.ok(it).build() }
            ?: Response.status(Response.Status.NOT_FOUND).build()

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
            description = "No rows blob exists for this org-id.",
            content = [Content()],
        ),
    )
    @GET
    @Path("/{orgId}/rows")
    fun rows(
        @Parameter(description = "Org id (lowercase, underscore-separated), e.g. `agderfk_no`")
        @PathParam("orgId")
        @Pattern(regexp = ORG_ID_PATTERN, message = ORG_ID_PATTERN_MESSAGE)
        orgId: String,
        @Parameter(description = "Filter by component (e.g. `utdanning_elev`)")
        @QueryParam("component") component: String?,
        @Parameter(description = "Filter by resource within the component (e.g. `elevforhold`)")
        @QueryParam("resource") resource: String?,
        @Parameter(
            description = "Filter by problem type. One of: `missing-resource`, `unknown-link`, " +
                "`missing-back-link-adapter`, `missing-back-link-autorelation`."
        )
        @QueryParam("problemType") problemType: String?,
        @Parameter(description = "Zero-based page index. Defaults to 0.")
        @QueryParam("page") @DefaultValue("0") page: Int,
        @Parameter(description = "Page size. Defaults to 100, capped at 1000.")
        @QueryParam("size") @DefaultValue("100") size: Int,
    ): Response {
        val doc = reportStore.getRows(orgId)
            ?: return Response.status(Response.Status.NOT_FOUND).build()

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

        return Response.ok(
            PagedRows(
                rows = filtered.subList(from, to),
                page = pageIndex,
                size = pageSize,
                totalRows = total,
                totalPages = if (total == 0) 0 else (total + pageSize - 1) / pageSize,
            )
        ).build()
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

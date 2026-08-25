package no.novari.linkwalker.reader

import no.novari.linkwalker.OrgId
import no.novari.linkwalker.report.LatestReportSummary
import no.novari.linkwalker.report.PagedProblems
import no.novari.linkwalker.report.ProblemFilter
import no.novari.linkwalker.report.ProblemType
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

    @GetMapping("/{orgId}/summary")
    fun summary(@PathVariable orgId: OrgId): ResponseEntity<LatestReportSummary> =
        reportStore.getSummary(orgId)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @GetMapping("/{orgId}/problems")
    fun problems(
        @PathVariable orgId: OrgId,
        @RequestParam(required = false) component: String?,
        @RequestParam(required = false) resource: String?,
        @RequestParam(required = false) problemType: ProblemType?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
    ): ResponseEntity<PagedProblems> =
        reportStore.findProblems(
            orgId = orgId,
            filter = ProblemFilter(component = component, resource = resource, problemType = problemType),
            page = page,
            size = size,
        )?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
}

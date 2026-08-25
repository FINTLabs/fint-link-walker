package no.novari.linkwalker.report

import no.novari.linkwalker.OrgId

interface ReportStore {
    fun publish(report: LatestReport)
    fun getSummary(orgId: OrgId): LatestReportSummary?
    fun listSummaries(): List<LatestReportSummary>

    /** Filtered, paginated problems query. Filters AND-combined; nulls = no filter. */
    fun findProblems(orgId: OrgId, filter: ProblemFilter, page: Int, size: Int): PagedProblems?

    companion object {
        const val MAX_PAGE_SIZE = 1000
    }
}

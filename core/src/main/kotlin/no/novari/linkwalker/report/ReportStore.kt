package no.novari.linkwalker.report

import no.novari.linkwalker.OrgId

interface ReportStore {
    fun publish(report: LatestReport)
    fun getSummary(orgId: OrgId): LatestReportSummary?
    fun listSummaries(): List<LatestReportSummary>

    /** Filtered, paginated rows query. Filters AND-combined; nulls = no filter. */
    fun findRows(orgId: OrgId, filter: RowFilter, page: Int, size: Int): PagedRows?

    companion object {
        const val MAX_PAGE_SIZE = 1000
    }
}

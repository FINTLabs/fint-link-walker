package no.novari.linkwalker.report

interface ReportStore {
    fun publish(report: LatestReport)
    fun getSummary(orgId: String): LatestReportSummary?
    fun getRows(orgId: String): LatestReportRows?
    fun listSummaries(): List<LatestReportSummary>
}

package no.novari.linkwalker.report

interface ReportStore {
    fun publish(report: LatestReport)
    fun getSummary(tenant: String): LatestReportSummary?
    fun getRows(tenant: String): LatestReportRows?
    fun listSummaries(): List<LatestReportSummary>
}

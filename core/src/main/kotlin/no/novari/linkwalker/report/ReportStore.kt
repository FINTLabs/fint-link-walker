package no.novari.linkwalker.report

interface ReportStore {
    fun publish(report: LatestReport)
    fun get(): LatestReport?
    fun list(): List<LatestReport>
}

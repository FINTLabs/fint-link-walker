package no.novari.linkwalker.reader

import no.novari.linkwalker.report.LatestReport
import no.novari.linkwalker.report.ReportStore
import no.novari.linkwalker.report.ScanSummary
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/report")
class ReportController(
    private val reportStore: ReportStore,
) {

    @GetMapping("/latest")
    fun latest(): Map<String, LatestReport> =
        reportStore.list().associateBy { it.tenantKey() }

    @GetMapping("/summary")
    fun summary(): Map<String, ScanSummary> =
        reportStore.list().associate { it.tenantKey() to it.summary }

    @GetMapping("/{tenant}/latest")
    fun latestForTenant(@PathVariable tenant: String): ResponseEntity<LatestReport> =
        reportStore.list().firstOrNull { it.tenantKey() == tenant }
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    private fun LatestReport.tenantKey(): String = tenants.firstOrNull() ?: "unknown"
}

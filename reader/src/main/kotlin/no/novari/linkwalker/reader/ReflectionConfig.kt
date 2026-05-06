package no.novari.linkwalker.reader

import io.quarkus.runtime.annotations.RegisterForReflection
import no.novari.linkwalker.report.ComponentSummary
import no.novari.linkwalker.report.LatestReport
import no.novari.linkwalker.report.LatestReportRows
import no.novari.linkwalker.report.LatestReportSummary
import no.novari.linkwalker.report.ReportRow
import no.novari.linkwalker.report.ResourceSummary
import no.novari.linkwalker.report.ScanSummary

/**
 * Storage classes load these via `mapper.readValue(stream, Class<T>)`, so the
 * native-image analyzer doesn't see them as deserialization targets and strips
 * their reflection metadata. Explicitly register them.
 */
@RegisterForReflection(
    targets = [
        LatestReport::class,
        LatestReportSummary::class,
        LatestReportRows::class,
        ReportRow::class,
        ScanSummary::class,
        ComponentSummary::class,
        ResourceSummary::class,
    ],
)
class ReflectionConfig

package no.novari.linkwalker.reader

import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import no.novari.linkwalker.OrgId
import no.novari.linkwalker.report.ComponentSummary
import no.novari.linkwalker.report.LatestReportSummary
import no.novari.linkwalker.report.ReportStore
import no.novari.linkwalker.report.ResourceSummary
import no.novari.linkwalker.report.ScanSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SummaryMetricsTest {

    private val store = mockk<ReportStore>()
    private lateinit var registry: SimpleMeterRegistry
    private lateinit var metrics: SummaryMetrics

    @BeforeEach
    fun setUp() {
        registry = SimpleMeterRegistry()
        metrics = SummaryMetrics(store, registry)
    }

    @Test
    fun `empty store publishes no metrics`() {
        every { store.listSummaries() } returns emptyList()

        metrics.refresh()

        assertNull(registry.find("link_walker_org_integrity_percent").gauge())
    }

    @Test
    fun `single tenant report publishes tagged metrics`() {
        every { store.listSummaries() } returns listOf(report("afk_no", integrity = 99.5))

        metrics.refresh()

        val gauge = registry.find("link_walker_org_integrity_percent")
            .tag("orgId", "afk_no")
            .gauge()
        assertNotNull(gauge)
        assertEquals(99.5, gauge!!.value())
    }

    @Test
    fun `two tenants both appear with correct values`() {
        every { store.listSummaries() } returns listOf(
            report("afk_no", integrity = 99.5),
            report("vlfk_no", integrity = 95.0),
        )

        metrics.refresh()

        assertEquals(
            99.5,
            registry.find("link_walker_org_integrity_percent").tag("orgId", "afk_no").gauge()?.value(),
        )
        assertEquals(
            95.0,
            registry.find("link_walker_org_integrity_percent").tag("orgId", "vlfk_no").gauge()?.value(),
        )
    }

    @Test
    fun `last scan timestamp is published per org`() {
        every { store.listSummaries() } returns listOf(report("afk_no", integrity = 99.5))

        metrics.refresh()

        assertEquals(
            Instant.parse("2026-01-01T00:00:00Z").epochSecond.toDouble(),
            registry.find("link_walker_last_scan_timestamp_seconds").tag("orgId", "afk_no").gauge()?.value(),
        )
    }

    @Test
    fun `stale tenant rows are dropped on subsequent refresh`() {
        every { store.listSummaries() } returns listOf(report("afk_no", integrity = 99.0))
        metrics.refresh()
        assertNotNull(
            registry.find("link_walker_org_integrity_percent").tag("orgId", "afk_no").gauge()
        )

        // afk-no removed; only vlfk-no remains
        every { store.listSummaries() } returns listOf(report("vlfk_no", integrity = 90.0))
        metrics.refresh()

        assertNull(
            registry.find("link_walker_org_integrity_percent").tag("orgId", "afk_no").gauge(),
            "afk-no row should be dropped after re-register with overwrite=true",
        )
        assertEquals(
            90.0,
            registry.find("link_walker_org_integrity_percent").tag("orgId", "vlfk_no").gauge()?.value(),
        )
    }

    @Test
    fun `per-resource metrics include component and resource tags`() {
        val report = report(
            orgId = "afk_no",
            integrity = 99.0,
            components = listOf(
                componentSummary(
                    "utdanning_elev",
                    resources = listOf(resourceSummary("elev", 99.5, records = 100, refs = 500))
                ),
            ),
        )
        every { store.listSummaries() } returns listOf(report)

        metrics.refresh()

        val resourceGauge = registry.find("link_walker_integrity_percent")
            .tag("orgId", "afk_no")
            .tag("component", "utdanning_elev")
            .tag("resource", "elev")
            .gauge()
        assertNotNull(resourceGauge)
        assertEquals(99.5, resourceGauge!!.value())

        val recordsGauge = registry.find("link_walker_records_count")
            .tag("orgId", "afk_no")
            .tag("component", "utdanning_elev")
            .tag("resource", "elev")
            .gauge()
        assertEquals(100.0, recordsGauge?.value())
    }

    @Test
    fun `broken-link rows are tagged with problem_type`() {
        val report = report(
            orgId = "afk_no",
            integrity = 90.0,
            components = listOf(
                componentSummary(
                    "utdanning_elev",
                    resources = listOf(
                        resourceSummary(
                            "elev",
                            integrity = 90.0,
                            byProblemType = mapOf("missing-resource" to 5L, "unknown-link" to 3L),
                        )
                    ),
                ),
            ),
        )
        every { store.listSummaries() } returns listOf(report)

        metrics.refresh()

        val missing = registry.find("link_walker_broken_links")
            .tag("orgId", "afk_no")
            .tag("component", "utdanning_elev")
            .tag("resource", "elev")
            .tag("problem_type", "missing-resource")
            .gauge()
        assertEquals(5.0, missing?.value())

        val unknown = registry.find("link_walker_broken_links")
            .tag("problem_type", "unknown-link")
            .gauge()
        assertEquals(3.0, unknown?.value())
    }

    @Test
    fun `metric tenant label uses the report's orgId verbatim`() {
        every { store.listSummaries() } returns listOf(report("agderfk_no", integrity = 99.0))

        metrics.refresh()

        val gauges = registry.find("link_walker_org_integrity_percent").gauges()
        assertTrue(gauges.any { it.id.tags.any { tag: Tag -> tag.key == "orgId" && tag.value == "agderfk_no" } })
    }

    private fun report(
        orgId: String,
        integrity: Double,
        components: List<ComponentSummary> = emptyList(),
    ): LatestReportSummary = LatestReportSummary(
        scanCompletedAt = Instant.parse("2026-01-01T00:00:00Z"),
        orgId = OrgId(orgId),
        components = components.map { it.component },
        summary = ScanSummary(
            totalRecords = 0,
            totalRefs = 0,
            brokenLinkCount = 0,
            integrityPercent = integrity,
            byProblemType = emptyMap(),
            components = components,
        ),
    )

    private fun componentSummary(
        component: String,
        resources: List<ResourceSummary>,
    ) = ComponentSummary(
        component = component,
        totalRecords = resources.sumOf { it.totalRecords },
        totalRefs = resources.sumOf { it.totalRefs },
        brokenLinkCount = resources.sumOf { it.brokenLinkCount },
        integrityPercent = resources.mapNotNull { it.integrityPercent }.average(),
        byProblemType = emptyMap(),
        resources = resources,
    )

    private fun resourceSummary(
        resource: String,
        integrity: Double,
        records: Long = 0,
        refs: Long = 0,
        byProblemType: Map<String, Long> = emptyMap(),
    ) = ResourceSummary(
        resource = resource,
        totalRecords = records,
        totalRefs = refs,
        brokenLinkCount = byProblemType.values.sum(),
        integrityPercent = integrity,
        byProblemType = byProblemType,
    )
}

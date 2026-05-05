package no.novari.linkwalker.report

import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.BlobContainerClient
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.novari.linkwalker.config.LinkWalkerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant
import java.util.zip.GZIPInputStream

class BlobReportStoreTest {

    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val container = mockk<BlobContainerClient>()

    @Test
    fun `publish uploads gzipped report under tenant-derived blob name`() {
        val blob = mockk<BlobClient>(relaxed = true)
        val nameSlot = slot<String>()
        val streamSlot = slot<InputStream>()
        val overwriteSlot = slot<Boolean>()

        every { container.getBlobClient(capture(nameSlot)) } returns blob
        every {
            blob.upload(capture(streamSlot), any<Long>(), capture(overwriteSlot))
        } returns mockk()

        val report = report("afk-no", listOf("missing-resource", "unknown-link"))
        storeFor("afk-no").publish(report)

        assertEquals("afk-no.json.gz", nameSlot.captured)
        assertTrue(overwriteSlot.captured, "publish must overwrite existing blob")

        val loaded: LatestReport = GZIPInputStream(streamSlot.captured).use { mapper.readValue(it) }
        assertEquals(report.tenants, loaded.tenants)
        assertEquals(report.rows.size, loaded.rows.size)
        assertEquals(report.summary.byProblemType, loaded.summary.byProblemType)
    }

    @Test
    fun `publish uses tenant value when blob path is constructed`() {
        val blob = mockk<BlobClient>(relaxed = true)
        val nameSlot = slot<String>()
        every { container.getBlobClient(capture(nameSlot)) } returns blob
        every { blob.upload(any<InputStream>(), any<Long>(), any<Boolean>()) } returns mockk()

        storeFor("vlfk-no").publish(report("vlfk-no", emptyList()))

        assertEquals("vlfk-no.json.gz", nameSlot.captured)
        verify { blob.upload(any<InputStream>(), any<Long>(), any<Boolean>()) }
    }

    @Test
    fun `tenant-less config throws on publish`() {
        val store = BlobReportStore(
            config = LinkWalkerConfig(tenant = null),
            mapper = mapper,
            container = container,
        )
        assertThrows(IllegalArgumentException::class.java) {
            store.publish(report("any", emptyList()))
        }
    }

    @Test
    fun `get returns null when blob does not exist`() {
        val blob = mockk<BlobClient>()
        every { container.getBlobClient(any<String>()) } returns blob
        every { blob.exists() } returns false

        val result = storeFor("afk-no").get()

        assertEquals(null, result)
    }

    private fun storeFor(tenant: String): BlobReportStore =
        BlobReportStore(
            config = LinkWalkerConfig(tenant = tenant),
            mapper = mapper,
            container = container,
        )

    private fun report(tenant: String, problemTypes: List<String>) = LatestReport(
        scanCompletedAt = Instant.parse("2026-01-01T00:00:00Z"),
        tenants = listOf(tenant),
        components = listOf("comp_x"),
        summary = ScanSummary(
            totalRecords = 100,
            totalRefs = 500,
            brokenLinkCount = problemTypes.size.toLong(),
            integrityPercent = 99.0,
            byProblemType = problemTypes
                .groupingBy { it }
                .eachCount()
                .mapValues { it.value.toLong() },
            components = emptyList(),
        ),
        rows = problemTypes.map { type ->
            ReportRow(
                tenant = tenant,
                component = "comp_x",
                resource = "res",
                problemType = type,
                sourceSelf = "https://host/comp/x/res/systemid/x",
                targetHref = "https://host/comp/x/res/systemid/y",
            )
        },
    )
}

package no.novari.linkwalker.report

import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.BlobContainerClient
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.novari.linkwalker.config.LinkWalkerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.time.Instant

class BlobReportStoreTest {

    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val container = mockk<BlobContainerClient>()

    @Test
    fun `publish uploads two gzipped blobs - summary and rows`() {
        val summaryBlob = mockk<BlobClient>(relaxed = true)
        val rowsBlob = mockk<BlobClient>(relaxed = true)

        every { container.getBlobClient("afk-no-summary.json.gz") } returns summaryBlob
        every { container.getBlobClient("afk-no-rows.json.gz") } returns rowsBlob
        every { summaryBlob.upload(any<InputStream>(), any<Long>(), any<Boolean>()) } returns mockk()
        every { rowsBlob.upload(any<InputStream>(), any<Long>(), any<Boolean>()) } returns mockk()

        storeFor("afk-no").publish(report("afk-no", listOf("missing-resource", "unknown-link")))

        verify { summaryBlob.upload(any<InputStream>(), any<Long>(), any<Boolean>()) }
        verify { rowsBlob.upload(any<InputStream>(), any<Long>(), any<Boolean>()) }
    }

    @Test
    fun `publish overwrites both blobs`() {
        val summaryBlob = mockk<BlobClient>(relaxed = true)
        val rowsBlob = mockk<BlobClient>(relaxed = true)
        val summaryOverwriteSlot = slot<Boolean>()
        val rowsOverwriteSlot = slot<Boolean>()

        every { container.getBlobClient("afk-no-summary.json.gz") } returns summaryBlob
        every { container.getBlobClient("afk-no-rows.json.gz") } returns rowsBlob
        every { summaryBlob.upload(any<InputStream>(), any<Long>(), capture(summaryOverwriteSlot)) } returns mockk()
        every { rowsBlob.upload(any<InputStream>(), any<Long>(), capture(rowsOverwriteSlot)) } returns mockk()

        storeFor("afk-no").publish(report("afk-no", emptyList()))

        assertTrue(summaryOverwriteSlot.captured)
        assertTrue(rowsOverwriteSlot.captured)
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
    fun `getSummary returns null when blob does not exist`() {
        val blob = mockk<BlobClient>()
        every { container.getBlobClient("afk-no-summary.json.gz") } returns blob
        every { blob.exists() } returns false

        assertEquals(null, storeFor("afk-no").getSummary("afk-no"))
    }

    @Test
    fun `getRows returns null when blob does not exist`() {
        val blob = mockk<BlobClient>()
        every { container.getBlobClient("afk-no-rows.json.gz") } returns blob
        every { blob.exists() } returns false

        assertEquals(null, storeFor("afk-no").getRows("afk-no"))
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

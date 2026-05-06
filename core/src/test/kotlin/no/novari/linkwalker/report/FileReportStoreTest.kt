package no.novari.linkwalker.report

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.novari.linkwalker.config.FileStorageConfig
import no.novari.linkwalker.config.LinkWalkerConfig
import no.novari.linkwalker.config.StorageConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.exists

class FileReportStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private val mapper = jacksonObjectMapper().findAndRegisterModules()

    @Test
    fun `publish writes summary and rows blobs separately`() {
        storeFor("afk-no").publish(report("afk-no", listOf("missing-resource", "unknown-link")))

        assertTrue(tempDir.resolve("afk-no-summary.json.gz").exists())
        assertTrue(tempDir.resolve("afk-no-rows.json.gz").exists())
    }

    @Test
    fun `getSummary returns parsed summary without rows`() {
        val store = storeFor("afk-no")
        store.publish(report("afk-no", listOf("missing-resource", "unknown-link")))

        val summary = store.getSummary("afk-no")

        assertNotNull(summary)
        assertEquals("afk-no", summary!!.orgId)
        assertEquals(2L, summary.summary.brokenLinkCount)
    }

    @Test
    fun `getRows returns parsed rows`() {
        val store = storeFor("afk-no")
        store.publish(report("afk-no", listOf("missing-resource", "unknown-link")))

        val rows = store.getRows("afk-no")

        assertNotNull(rows)
        assertEquals(2, rows!!.rows.size)
    }

    @Test
    fun `getSummary returns null when missing`() {
        assertNull(storeFor("nope").getSummary("nope"))
    }

    @Test
    fun `getRows returns null when missing`() {
        assertNull(storeFor("nope").getRows("nope"))
    }

    @Test
    fun `publish overwrites existing files`() {
        val store = storeFor("afk-no")
        store.publish(report("afk-no", listOf("missing-resource")))
        store.publish(report("afk-no", listOf("missing-resource", "unknown-link")))

        assertEquals(2, store.getRows("afk-no")?.rows?.size)
        assertEquals(2L, store.getSummary("afk-no")?.summary?.brokenLinkCount)
    }

    @Test
    fun `tenant-less config throws on publish`() {
        val store = FileReportStore(
            config = LinkWalkerConfig(
                orgId = null,
                storage = StorageConfig(
                    type = "file",
                    file = FileStorageConfig(directory = tempDir.toString()),
                ),
            ),
            mapper = mapper,
        )
        assertThrows(IllegalArgumentException::class.java) {
            store.publish(report("any", emptyList()))
        }
    }

    @Test
    fun `corrupted summary file returns null gracefully`() {
        val target = tempDir.resolve("afk-no-summary.json.gz")
        target.toFile().writeText("not gzip content")

        assertNull(storeFor("afk-no").getSummary("afk-no"))
    }

    @Test
    fun `listSummaries returns all tenant summaries in directory`() {
        storeFor("afk-no").publish(report("afk-no", listOf("missing-resource")))
        storeFor("vlfk-no").publish(report("vlfk-no", listOf("unknown-link", "missing-resource")))

        val all = storeFor("afk-no").listSummaries()

        assertEquals(2, all.size)
        assertEquals(setOf("afk-no", "vlfk-no"), all.map { it.orgId }.toSet())
    }

    @Test
    fun `listSummaries ignores rows blobs`() {
        storeFor("afk-no").publish(report("afk-no", emptyList()))

        val all = storeFor("afk-no").listSummaries()

        assertEquals(1, all.size, "Should not include the rows blob")
    }

    @Test
    fun `listSummaries returns empty when directory does not exist`() {
        val store = FileReportStore(
            config = LinkWalkerConfig(
                orgId = "afk-no",
                storage = StorageConfig(
                    type = "file",
                    file = FileStorageConfig(directory = tempDir.resolve("does-not-exist").toString()),
                ),
            ),
            mapper = mapper,
        )
        assertTrue(store.listSummaries().isEmpty())
    }

    @Test
    fun `listSummaries skips corrupted summary files`() {
        storeFor("afk-no").publish(report("afk-no", emptyList()))
        tempDir.resolve("broken-summary.json.gz").toFile().writeText("not gzip")

        val all = storeFor("afk-no").listSummaries()

        assertEquals(1, all.size)
        assertEquals("afk-no", all.single().orgId)
    }

    private fun storeFor(tenant: String, directory: Path = tempDir): FileReportStore =
        FileReportStore(
            config = LinkWalkerConfig(
                orgId = tenant,
                storage = StorageConfig(
                    type = "file",
                    file = FileStorageConfig(directory = directory.toString()),
                ),
            ),
            mapper = mapper,
        )

    private fun report(tenant: String, problemTypes: List<String>) = LatestReport(
        scanCompletedAt = Instant.parse("2026-01-01T00:00:00Z"),
        orgId = tenant,
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
                orgId = tenant,
                component = "comp_x",
                resource = "res",
                problemType = type,
                sourceSelf = "https://host/comp/x/res/systemid/x",
                targetHref = "https://host/comp/x/res/systemid/y",
            )
        },
    )
}

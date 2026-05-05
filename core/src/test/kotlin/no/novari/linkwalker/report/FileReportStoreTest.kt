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
    fun `publish then get returns equivalent report`() {
        val store = storeFor("afk-no")
        val report = report("afk-no", problemTypes = listOf("missing-resource", "unknown-link"))

        store.publish(report)
        val loaded = store.get()

        assertNotNull(loaded)
        assertEquals(report.tenants, loaded!!.tenants)
        assertEquals(report.components, loaded.components)
        assertEquals(report.rows.size, loaded.rows.size)
        assertEquals(report.summary.totalRecords, loaded.summary.totalRecords)
        assertEquals(report.summary.byProblemType, loaded.summary.byProblemType)
    }

    @Test
    fun `get returns null when no file exists`() {
        assertNull(storeFor("afk-no").get())
    }

    @Test
    fun `publish overwrites existing file atomically`() {
        val store = storeFor("afk-no")
        store.publish(report("afk-no", problemTypes = listOf("missing-resource")))
        store.publish(report("afk-no", problemTypes = listOf("missing-resource", "unknown-link")))

        assertEquals(2, store.get()?.rows?.size)
    }

    @Test
    fun `publish writes file at tenant-derived path`() {
        storeFor("afk-no").publish(report("afk-no", emptyList()))

        assertTrue(tempDir.resolve("afk-no.json.gz").exists())
    }

    @Test
    fun `publish creates parent directory if missing`() {
        val nested = tempDir.resolve("nested/deep")
        storeFor("afk-no", directory = nested).publish(report("afk-no", emptyList()))

        assertTrue(nested.resolve("afk-no.json.gz").exists())
    }

    @Test
    fun `tenant-less config throws on publish`() {
        val store = FileReportStore(
            config = LinkWalkerConfig(
                tenant = null,
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
    fun `corrupted file returns null gracefully`() {
        val target = tempDir.resolve("afk-no.json.gz")
        target.toFile().writeText("not gzip content")

        assertNull(storeFor("afk-no").get())
    }

    @Test
    fun `list returns all reports in directory`() {
        storeFor("afk-no").publish(report("afk-no", listOf("missing-resource")))
        storeFor("vlfk-no").publish(report("vlfk-no", listOf("unknown-link", "missing-resource")))

        val all = storeFor("afk-no").list()

        assertEquals(2, all.size)
        assertEquals(setOf("afk-no", "vlfk-no"), all.flatMap { it.tenants }.toSet())
    }

    @Test
    fun `list returns empty when directory does not exist`() {
        val store = FileReportStore(
            config = LinkWalkerConfig(
                tenant = "afk-no",
                storage = StorageConfig(
                    type = "file",
                    file = FileStorageConfig(directory = tempDir.resolve("does-not-exist").toString()),
                ),
            ),
            mapper = mapper,
        )
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `list skips corrupted files`() {
        storeFor("afk-no").publish(report("afk-no", emptyList()))
        tempDir.resolve("broken.json.gz").toFile().writeText("not gzip")

        val all = storeFor("afk-no").list()

        assertEquals(1, all.size)
        assertEquals(listOf("afk-no"), all.single().tenants)
    }

    private fun storeFor(tenant: String, directory: Path = tempDir): FileReportStore =
        FileReportStore(
            config = LinkWalkerConfig(
                tenant = tenant,
                storage = StorageConfig(
                    type = "file",
                    file = FileStorageConfig(directory = directory.toString()),
                ),
            ),
            mapper = mapper,
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

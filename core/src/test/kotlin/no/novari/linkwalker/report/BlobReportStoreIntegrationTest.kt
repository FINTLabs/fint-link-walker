package no.novari.linkwalker.report

import com.azure.storage.common.StorageSharedKeyCredential
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.novari.linkwalker.config.LinkWalkerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.UUID

@Testcontainers
class BlobReportStoreIntegrationTest {

    private val mapper = jacksonObjectMapper().findAndRegisterModules()

    @Test
    fun `publish then get returns equivalent report via real blob storage`() {
        val container = freshContainer()
        val store = storeFor("afk-no", container)
        val report = report("afk-no", listOf("missing-resource", "unknown-link"))

        store.publish(report)
        val loaded = store.get()

        assertNotNull(loaded)
        assertEquals(report.tenants, loaded!!.tenants)
        assertEquals(report.components, loaded.components)
        assertEquals(report.rows.size, loaded.rows.size)
        assertEquals(report.summary.byProblemType, loaded.summary.byProblemType)
        assertEquals(report.summary.totalRecords, loaded.summary.totalRecords)
    }

    @Test
    fun `get returns null when blob does not exist`() {
        val container = freshContainer()
        assertNull(storeFor("missing-tenant", container).get())
    }

    @Test
    fun `publish overwrites existing blob`() {
        val container = freshContainer()
        val store = storeFor("afk-no", container)

        store.publish(report("afk-no", listOf("missing-resource")))
        store.publish(report("afk-no", listOf("missing-resource", "unknown-link")))

        assertEquals(2, store.get()?.rows?.size)
    }

    @Test
    fun `publish writes blob at tenant-derived name`() {
        val container = freshContainer()
        storeFor("vlfk-no", container).publish(report("vlfk-no", emptyList()))

        assertTrue(container.getBlobClient("vlfk-no.json.gz").exists())
    }

    @Test
    fun `list returns all reports across tenants`() {
        val container = freshContainer()
        storeFor("afk-no", container).publish(report("afk-no", listOf("missing-resource")))
        storeFor("vlfk-no", container).publish(report("vlfk-no", listOf("unknown-link", "missing-resource")))

        val all = storeFor("afk-no", container).list()

        assertEquals(2, all.size)
        assertEquals(setOf("afk-no", "vlfk-no"), all.flatMap { it.tenants }.toSet())
    }

    @Test
    fun `list returns empty when container has no blobs`() {
        assertTrue(storeFor("afk-no", freshContainer()).list().isEmpty())
    }

    @Test
    fun `each tenant writes to its own blob without collision`() {
        val container = freshContainer()
        storeFor("afk-no", container).publish(report("afk-no", listOf("missing-resource")))
        storeFor("vlfk-no", container).publish(report("vlfk-no", listOf("unknown-link", "missing-resource")))

        assertEquals(1, storeFor("afk-no", container).get()?.rows?.size)
        assertEquals(2, storeFor("vlfk-no", container).get()?.rows?.size)
    }

    private fun storeFor(tenant: String, container: BlobContainerClient): BlobReportStore =
        BlobReportStore(
            config = LinkWalkerConfig(tenant = tenant),
            mapper = mapper,
            container = container,
        )

    private fun freshContainer(): BlobContainerClient {
        val mappedPort = azurite.getMappedPort(BLOB_PORT)
        val service = BlobServiceClientBuilder()
            .endpoint("http://127.0.0.1:$mappedPort/$ACCOUNT_NAME")
            .credential(StorageSharedKeyCredential(ACCOUNT_NAME, ACCOUNT_KEY))
            .buildClient()

        return service.getBlobContainerClient("test-${UUID.randomUUID()}")
            .also { it.createIfNotExists() }
    }

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

    companion object {
        private const val BLOB_PORT = 10000
        private const val ACCOUNT_NAME = "devstoreaccount1"
        private const val ACCOUNT_KEY =
            "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw=="

        @Container
        @JvmStatic
        val azurite: GenericContainer<*> = GenericContainer(
            DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:latest"),
        )
            .withExposedPorts(BLOB_PORT)
            .withCommand("azurite-blob", "--blobHost", "0.0.0.0", "--blobPort", BLOB_PORT.toString())
    }
}

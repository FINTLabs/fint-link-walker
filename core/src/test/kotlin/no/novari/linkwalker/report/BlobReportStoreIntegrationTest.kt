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
    fun `publish then getSummary returns equivalent summary via real blob storage`() {
        val container = freshContainer()
        val store = storeFor("afk-no", container)
        val report = report("afk-no", listOf("missing-resource", "unknown-link"))

        store.publish(report)
        val loaded = store.getSummary("afk-no")

        assertNotNull(loaded)
        assertEquals(report.tenants, loaded!!.tenants)
        assertEquals(report.components, loaded.components)
        assertEquals(report.summary.byProblemType, loaded.summary.byProblemType)
    }

    @Test
    fun `publish then getRows returns rows via real blob storage`() {
        val container = freshContainer()
        val store = storeFor("afk-no", container)
        val report = report("afk-no", listOf("missing-resource", "unknown-link"))

        store.publish(report)
        val rows = store.getRows("afk-no")

        assertNotNull(rows)
        assertEquals(2, rows!!.rows.size)
    }

    @Test
    fun `getSummary returns null when blob does not exist`() {
        val container = freshContainer()
        assertNull(storeFor("missing-tenant", container).getSummary("missing-tenant"))
    }

    @Test
    fun `getRows returns null when blob does not exist`() {
        val container = freshContainer()
        assertNull(storeFor("missing-tenant", container).getRows("missing-tenant"))
    }

    @Test
    fun `publish writes blobs at tenant-derived names`() {
        val container = freshContainer()
        storeFor("vlfk-no", container).publish(report("vlfk-no", emptyList()))

        assertTrue(container.getBlobClient("vlfk-no-summary.json.gz").exists())
        assertTrue(container.getBlobClient("vlfk-no-rows.json.gz").exists())
    }

    @Test
    fun `listSummaries returns all tenant summaries across container`() {
        val container = freshContainer()
        storeFor("afk-no", container).publish(report("afk-no", listOf("missing-resource")))
        storeFor("vlfk-no", container).publish(report("vlfk-no", listOf("unknown-link", "missing-resource")))

        val all = storeFor("afk-no", container).listSummaries()

        assertEquals(2, all.size)
        assertEquals(setOf("afk-no", "vlfk-no"), all.flatMap { it.tenants }.toSet())
    }

    @Test
    fun `listSummaries ignores rows blobs`() {
        val container = freshContainer()
        storeFor("afk-no", container).publish(report("afk-no", emptyList()))

        val all = storeFor("afk-no", container).listSummaries()

        assertEquals(1, all.size)
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

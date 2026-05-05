package no.novari.linkwalker.report

import com.azure.storage.blob.BlobContainerClient
import com.fasterxml.jackson.databind.ObjectMapper
import no.novari.linkwalker.config.LinkWalkerConfig
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class BlobReportStore(
    private val config: LinkWalkerConfig,
    private val mapper: ObjectMapper,
    private val container: BlobContainerClient,
) : ReportStore {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publish(report: LatestReport) {
        val name = blobName()
        val bytes = ByteArrayOutputStream().also { sink ->
            GZIPOutputStream(sink).use { gz -> mapper.writeValue(gz, report) }
        }.toByteArray()

        runCatching {
            container.getBlobClient(name).upload(
                ByteArrayInputStream(bytes),
                bytes.size.toLong(),
                /* overwrite = */ true,
            )
        }
            .onSuccess {
                logger.info("Wrote blob {} ({} rows, {} bytes)", name, report.rows.size, bytes.size)
            }
            .onFailure { logger.error("Failed to write blob {}: {}", name, it.message) }
    }

    override fun get(): LatestReport? {
        val name = blobName()
        val blob = container.getBlobClient(name)
        if (!blob.exists()) return null
        return readBlob(name)
    }

    override fun list(): List<LatestReport> =
        container.listBlobs()
            .map { it.name }
            .filter { it.endsWith(".json.gz") }
            .mapNotNull { readBlob(it) }

    private fun readBlob(name: String): LatestReport? = runCatching {
        container.getBlobClient(name).openInputStream().use { input ->
            GZIPInputStream(input).use { gz ->
                mapper.readValue(gz, LatestReport::class.java)
            }
        }
    }.getOrElse {
        logger.warn("Failed to read blob {}: {}", name, it.message)
        null
    }

    private fun blobName(): String {
        val tenant = requireNotNull(config.tenant?.takeIf { it.isNotBlank() }) {
            "link-walker.tenant must be set for blob storage"
        }
        return "${tenant}.json.gz"
    }
}

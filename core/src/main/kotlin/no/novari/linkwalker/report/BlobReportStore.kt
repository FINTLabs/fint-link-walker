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
        val tenant = requireTenant()
        write(summaryName(tenant), report.toSummary(), "${report.rows.size} rows")
        write(rowsName(tenant), report.toRows(), "${report.rows.size} rows")
    }

    override fun getSummary(tenant: String): LatestReportSummary? =
        read(summaryName(tenant), LatestReportSummary::class.java)

    override fun getRows(tenant: String): LatestReportRows? =
        read(rowsName(tenant), LatestReportRows::class.java)

    override fun listSummaries(): List<LatestReportSummary> =
        container.listBlobs()
            .map { it.name }
            .filter { it.endsWith(SUMMARY_SUFFIX) }
            .mapNotNull { read(it, LatestReportSummary::class.java) }

    private fun <T> write(name: String, payload: T, label: String) {
        val bytes = ByteArrayOutputStream().also { sink ->
            GZIPOutputStream(sink).use { gz -> mapper.writeValue(gz, payload) }
        }.toByteArray()

        runCatching {
            container.getBlobClient(name).upload(
                ByteArrayInputStream(bytes),
                bytes.size.toLong(),
                /* overwrite = */ true,
            )
        }
            .onSuccess { logger.info("Wrote blob {} ({} bytes, {})", name, bytes.size, label) }
            .onFailure { logger.error("Failed to write blob {}: {}", name, it.message) }
    }

    private fun <T> read(name: String, type: Class<T>): T? {
        val blob = container.getBlobClient(name)
        if (!blob.exists()) return null
        return runCatching {
            blob.openInputStream().use { input ->
                GZIPInputStream(input).use { gz ->
                    mapper.readValue(gz, type)
                }
            }
        }.getOrElse {
            logger.warn("Failed to read blob {}: {}", name, it.message)
            null
        }
    }

    private fun summaryName(tenant: String): String = "${tenant}${SUMMARY_SUFFIX}"
    private fun rowsName(tenant: String): String = "${tenant}${ROWS_SUFFIX}"

    private fun requireTenant(): String =
        requireNotNull(config.tenant?.takeIf { it.isNotBlank() }) {
            "link-walker.tenant must be set for blob storage"
        }

    companion object {
        const val SUMMARY_SUFFIX = "-summary.json.gz"
        const val ROWS_SUFFIX = "-rows.json.gz"
    }
}

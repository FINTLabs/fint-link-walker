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
        val orgId = requireOrgId()
        write(summaryName(orgId), report.toSummary(), "${report.rows.size} rows")
        write(rowsName(orgId), report.toRows(), "${report.rows.size} rows")
    }

    override fun getSummary(orgId: String): LatestReportSummary? =
        read(summaryName(orgId), LatestReportSummary::class.java)

    override fun getRows(orgId: String): LatestReportRows? =
        read(rowsName(orgId), LatestReportRows::class.java)

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

    private fun summaryName(orgId: String): String = "${orgId}${SUMMARY_SUFFIX}"
    private fun rowsName(orgId: String): String = "${orgId}${ROWS_SUFFIX}"

    private fun requireOrgId(): String =
        requireNotNull(config.orgId?.takeIf { it.isNotBlank() }) {
            "link-walker.org-id must be set for blob storage"
        }

    companion object {
        const val SUMMARY_SUFFIX = "-summary.json.gz"
        const val ROWS_SUFFIX = "-rows.json.gz"
    }
}

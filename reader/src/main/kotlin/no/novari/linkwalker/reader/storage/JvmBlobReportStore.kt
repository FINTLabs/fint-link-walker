package no.novari.linkwalker.reader.storage

import com.azure.storage.blob.BlobContainerClient
import com.fasterxml.jackson.databind.ObjectMapper
import no.novari.linkwalker.report.LatestReport
import no.novari.linkwalker.report.LatestReportRows
import no.novari.linkwalker.report.LatestReportSummary
import no.novari.linkwalker.report.ReportStore
import org.slf4j.LoggerFactory
import java.util.zip.GZIPInputStream

/** Jackson-2 fork of core.BlobReportStore for reader's Quarkus runtime. */
class JvmBlobReportStore(
    private val mapper: ObjectMapper,
    private val container: BlobContainerClient,
) : ReportStore {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publish(report: LatestReport) =
        error("reader is read-only; publish() is scanner-side")

    override fun getSummary(orgId: String): LatestReportSummary? =
        read("${orgId}${SUMMARY_SUFFIX}", LatestReportSummary::class.java)

    override fun getRows(orgId: String): LatestReportRows? =
        read("${orgId}${ROWS_SUFFIX}", LatestReportRows::class.java)

    override fun listSummaries(): List<LatestReportSummary> =
        container.listBlobs()
            .map { it.name }
            .filter { it.endsWith(SUMMARY_SUFFIX) }
            .mapNotNull { read(it, LatestReportSummary::class.java) }

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

    companion object {
        const val SUMMARY_SUFFIX = "-summary.json.gz"
        const val ROWS_SUFFIX = "-rows.json.gz"
    }
}

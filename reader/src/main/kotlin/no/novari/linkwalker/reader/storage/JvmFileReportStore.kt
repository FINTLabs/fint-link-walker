package no.novari.linkwalker.reader.storage

import com.fasterxml.jackson.databind.ObjectMapper
import no.novari.linkwalker.config.LinkWalkerConfig
import no.novari.linkwalker.report.LatestReport
import no.novari.linkwalker.report.LatestReportRows
import no.novari.linkwalker.report.LatestReportSummary
import no.novari.linkwalker.report.ReportStore
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream

/** Jackson-2 fork of core.FileReportStore for reader's Quarkus runtime. */
class JvmFileReportStore(
    private val config: LinkWalkerConfig,
    private val mapper: ObjectMapper,
) : ReportStore {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publish(report: LatestReport) =
        error("reader is read-only; publish() is scanner-side")

    override fun getSummary(orgId: String): LatestReportSummary? =
        read(summaryPath(orgId), LatestReportSummary::class.java)

    override fun getRows(orgId: String): LatestReportRows? =
        read(rowsPath(orgId), LatestReportRows::class.java)

    override fun listSummaries(): List<LatestReportSummary> {
        val dir = directory()
        if (!dir.exists()) return emptyList()
        return Files.list(dir).use { stream ->
            stream
                .filter { it.fileName.toString().endsWith(SUMMARY_SUFFIX) }
                .map { read(it, LatestReportSummary::class.java) }
                .toList()
        }.filterNotNull()
    }

    private fun <T> read(path: Path, type: Class<T>): T? {
        if (!path.exists()) return null
        return runCatching {
            GZIPInputStream(path.inputStream()).use { gz ->
                mapper.readValue(gz, type)
            }
        }.getOrElse {
            logger.warn("Failed to load {}: {}", path, it.message)
            null
        }
    }

    private fun directory(): Path = Path(config.storage.file.directory)
    private fun summaryPath(orgId: String): Path = directory().resolve("${orgId}${SUMMARY_SUFFIX}")
    private fun rowsPath(orgId: String): Path = directory().resolve("${orgId}${ROWS_SUFFIX}")

    companion object {
        const val SUMMARY_SUFFIX = "-summary.json.gz"
        const val ROWS_SUFFIX = "-rows.json.gz"
    }
}

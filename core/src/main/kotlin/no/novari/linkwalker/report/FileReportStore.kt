package no.novari.linkwalker.report

import tools.jackson.databind.ObjectMapper
import no.novari.linkwalker.config.LinkWalkerConfig
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class FileReportStore(
    private val config: LinkWalkerConfig,
    private val mapper: ObjectMapper,
) : ReportStore {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publish(report: LatestReport) {
        val tenant = requireTenant()
        write(summaryPath(tenant), report.toSummary(), "${report.rows.size} rows")
        write(rowsPath(tenant), report.toRows(), "${report.rows.size} rows")
    }

    override fun getSummary(tenant: String): LatestReportSummary? =
        read(summaryPath(tenant), LatestReportSummary::class.java)

    override fun getRows(tenant: String): LatestReportRows? =
        read(rowsPath(tenant), LatestReportRows::class.java)

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

    private fun <T> write(path: Path, payload: T, label: String) {
        val tmp = path.resolveSibling("${path.fileName}.tmp")
        runCatching {
            path.parent?.createDirectories()
            GZIPOutputStream(tmp.outputStream()).use { gz ->
                mapper.writeValue(gz, payload)
            }
            Files.move(
                tmp, path,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }
            .onSuccess { logger.info("Wrote {} ({})", path, label) }
            .onFailure {
                logger.error("Failed to write {}: {}", path, it.message)
                runCatching { tmp.deleteIfExists() }
            }
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

    private fun summaryPath(tenant: String): Path =
        directory().resolve("${tenant}${SUMMARY_SUFFIX}")

    private fun rowsPath(tenant: String): Path =
        directory().resolve("${tenant}${ROWS_SUFFIX}")

    private fun requireTenant(): String =
        requireNotNull(config.tenant?.takeIf { it.isNotBlank() }) {
            "link-walker.tenant must be set for file storage"
        }

    companion object {
        const val SUMMARY_SUFFIX = "-summary.json.gz"
        const val ROWS_SUFFIX = "-rows.json.gz"
    }
}

internal fun LatestReport.toSummary(): LatestReportSummary = LatestReportSummary(
    scanCompletedAt = scanCompletedAt,
    tenants = tenants,
    components = components,
    summary = summary,
)

internal fun LatestReport.toRows(): LatestReportRows = LatestReportRows(
    scanCompletedAt = scanCompletedAt,
    tenants = tenants,
    rows = rows,
)

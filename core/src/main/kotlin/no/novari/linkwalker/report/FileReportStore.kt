package no.novari.linkwalker.report

import com.fasterxml.jackson.databind.ObjectMapper
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
        val path = filePath()
        val tmp = path.resolveSibling("${path.fileName}.tmp")
        runCatching {
            path.parent?.createDirectories()
            GZIPOutputStream(tmp.outputStream()).use { gz ->
                mapper.writeValue(gz, report)
            }
            Files.move(
                tmp, path,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }
            .onSuccess { logger.info("Wrote report to {} ({} rows)", path, report.rows.size) }
            .onFailure {
                logger.error("Failed to write report to {}: {}", path, it.message)
                runCatching { tmp.deleteIfExists() }
            }
    }

    override fun get(): LatestReport? {
        val path = filePath()
        if (!path.exists()) return null
        return readReport(path)
    }

    override fun list(): List<LatestReport> {
        val dir = Path(config.storage.file.directory)
        if (!dir.exists()) return emptyList()
        return Files.list(dir).use { stream ->
            stream
                .filter { it.fileName.toString().endsWith(".json.gz") }
                .map { readReport(it) }
                .toList()
        }.filterNotNull()
    }

    private fun readReport(path: Path): LatestReport? = runCatching {
        GZIPInputStream(path.inputStream()).use { gz ->
            mapper.readValue(gz, LatestReport::class.java)
        }
    }.getOrElse {
        logger.warn("Failed to load report at {}: {}", path, it.message)
        null
    }

    private fun filePath(): Path {
        val tenant = requireNotNull(config.tenant?.takeIf { it.isNotBlank() }) {
            "link-walker.tenant must be set for file storage"
        }
        return Path(config.storage.file.directory).resolve("${tenant}.json.gz")
    }
}

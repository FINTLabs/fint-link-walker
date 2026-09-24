package no.novari.linkwalker.index

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.novari.linkwalker.FintClient
import no.novari.linkwalker.NoDataException
import no.novari.linkwalker.NoRouteException
import no.novari.linkwalker.config.HttpProperties
import no.novari.linkwalker.config.ScannerProperties
import no.novari.metamodel.MetamodelService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

class IncompleteIndexException(message: String) : RuntimeException(message)
class PageParseException(message: String, cause: Throwable) : RuntimeException(message, cause)
class PageCorruptException(message: String) : RuntimeException(message)

@Component
class IndexBuilder(
    scannerConfig: ScannerProperties,
    httpConfig: HttpProperties,
    private val fintClient: FintClient,
    private val metamodelService: MetamodelService,
    private val extractor: RecordExtractor,
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val baseUrl: String = scannerConfig.baseUrl.trimEnd('/')
    private val defaultPageSize: Int = scannerConfig.pageSize
    private val pageSizes: Map<String, Int> = scannerConfig.pageSizes.mapKeys { it.key.lowercase() }
    private val maxConcurrentFetches: Int = httpConfig.maxConcurrentFetches
    private val maxAttempts: Int = httpConfig.maxAttempts
    private val integrity = PageIntegrity(baseUrl)

    // Caps the number of in-flight HTTP fetches across the whole scan.
    // limitedParallelism wraps Dispatchers.IO so coroutines beyond the cap suspend
    // (they don't block IO threads) until a permit frees up.
    private val fetchDispatcher: CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(maxConcurrentFetches)

    suspend fun buildIndex(components: List<String>, bearer: String): TenantIndex = coroutineScope {
        val targets = resolveTargets(components)
        logger.info(
            "Indexing {} resources from {} components, {} at a time",
            targets.size, components.size, maxConcurrentFetches,
        )
        val records = targets
            .mapIndexed { i, target ->
                async(fetchDispatcher) { fetchAndExtract(target, "${i + 1}/${targets.size}", bearer) }
            }
            .awaitAll()
            .flatten()
        logger.info("Index built: {} records from {} resources", records.size, targets.size)
        records.toTenantIndex()
    }

    private fun resolveTargets(components: List<String>): List<FetchTarget> =
        components.flatMap { targetsFor(it) }

    private fun targetsFor(component: String): List<FetchTarget> {
        val id = ComponentId.parse(component) ?: run {
            logger.warn("Component '{}' is not in 'domain_pkg' form, skipping", component)
            return emptyList()
        }
        val resources = metamodelService.getResources(id.domain, id.pkg)
        if (resources.isEmpty()) {
            logger.warn("No resources in metamodel for {}/{}", id.domain, id.pkg)
            return emptyList()
        }
        return resources.map {
            FetchTarget(component, "${id.domain}/${id.pkg}/${it.name}", it.name, pageSizeFor(it.name))
        }
    }

    private fun pageSizeFor(resourceName: String): Int =
        pageSizes[resourceName.lowercase()] ?: defaultPageSize

    private fun List<MinimalRecord>.toTenantIndex(): TenantIndex {
        val byKey = HashMap<String, MinimalRecord>(size * 2)
        forEach { r -> r.canonicalKeys.forEach { key -> byKey[key] = r } }
        return TenantIndex(records = this, byKey = byKey)
    }

    private suspend fun fetchAndExtract(
        target: FetchTarget,
        position: String,
        bearer: String,
    ): List<MinimalRecord> =
        try {
            followPages(target, position, bearer)
        } catch (ex: NoRouteException) {
            logger.info("[{}] {}: no route, skipping ({})", position, target.label, ex.message)
            emptyList()
        } catch (ex: NoDataException) {
            logger.info("[{}] {}: no data, skipping ({})", position, target.label, ex.message)
            emptyList()
        }

    private suspend fun followPages(
        target: FetchTarget,
        position: String,
        bearer: String,
    ): List<MinimalRecord> {
        logger.info("[{}] {}: fetching with page size {}", position, target.label, target.pageSize)
        val records = mutableListOf<MinimalRecord>()
        val visited = HashSet<String>()
        var entries = 0L
        var pages = 0
        var totalItems: Long? = null
        var url: String? = firstPageUrl(target)
        while (url != null) {
            if (!visited.add(url)) {
                throw IncompleteIndexException("Paging loop for ${target.label}: $url was already fetched")
            }
            val page = fetchPage(target, position, url, bearer)
            pages++
            records += page.records
            entries += page.entryCount
            totalItems = page.totalItems ?: totalItems
            url = page.nextHref?.let { resolve(it) }
            logger.info(
                "[{}] {}: page {} had {} entries, {} of {} fetched, more={}",
                position, target.label, pages, page.entryCount, entries, totalItems ?: "?", url != null,
            )
        }
        if (totalItems != null && entries < totalItems) {
            throw IncompleteIndexException(
                "Paging for ${target.label} ended after $entries of $totalItems entries with no next link"
            )
        }
        logger.info("[{}] {}: done, {} pages, {} records", position, target.label, pages, records.size)
        return records
    }

    private suspend fun fetchPage(
        target: FetchTarget,
        position: String,
        url: String,
        bearer: String,
    ): PageExtraction {
        var attempt = 0
        while (true) {
            attempt++
            val started = System.nanoTime()
            @Suppress("BlockingMethodInNonBlockingContext")
            val tempFile: Path = Files.createTempFile("link-walker-", "-${target.resourceName}.json")
            try {
                val fetched = fintClient.streamToFile(url, bearer, tempFile)
                val report = integrity.inspect(tempFile)
                val finding = report.finding
                if (finding != null) {
                    logger.warn(
                        "[{}] {}: page is damaged ({} at byte {}) on attempt {}, bytes={} sha256={} route={} via={}, re-fetching {}",
                        position, target.label, finding.signature, finding.offset, attempt,
                        report.bytes, report.sha256, fetched.route, fetched.via, url,
                    )
                    if (attempt >= maxAttempts) {
                        throw PageCorruptException(
                            "Page $url for ${target.label} was damaged on $attempt attempts, " +
                                "last: ${finding.signature} at byte ${finding.offset} (via=${fetched.via})"
                        )
                    }
                    continue
                }
                val page = extractor.extractFromFile(tempFile, target.component, target.resourceName)
                logger.info(
                    "[{}] {}: page fetched, bytes={} sha256={} attempt={} elapsed={}ms route={} via={} entries={} malformedSelf={}",
                    position, target.label, report.bytes, report.sha256, attempt,
                    (System.nanoTime() - started) / 1_000_000, fetched.route, fetched.via, page.entryCount, page.malformedSelfCount,
                )
                if (page.malformedSelfCount > 0) {
                    logger.warn(
                        "[{}] {}: {} entries had a malformed self href and were not indexed ({})",
                        position, target.label, page.malformedSelfCount, url,
                    )
                }
                return page
            } catch (ex: JacksonException) {
                val diagnostic = PageDiagnostics.describe(tempFile, ex)
                if (attempt >= maxAttempts) {
                    throw PageParseException(
                        "Page $url for ${target.label} failed to parse on $attempt attempts, $diagnostic", ex
                    )
                }
                logger.warn(
                    "[{}] {}: page failed to parse on attempt {}, re-fetching {} ({})",
                    position, target.label, attempt, url, diagnostic,
                )
            } finally {
                runCatching { tempFile.deleteIfExists() }
                    .onFailure { logger.warn("Failed to delete {}: {}", tempFile, it.message) }
            }
        }
    }

    private fun firstPageUrl(target: FetchTarget): String =
        "$baseUrl/${target.path}?size=${target.pageSize}"

    private fun resolve(href: String): String =
        if (href.startsWith("http://") || href.startsWith("https://")) href
        else "$baseUrl/${href.trimStart('/')}"

    private data class FetchTarget(
        val component: String,
        val path: String,
        val resourceName: String,
        val pageSize: Int,
    ) {
        val label: String get() = "$component/$resourceName"
    }
}

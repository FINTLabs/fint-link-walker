package no.novari.linkwalker.index

import kotlinx.coroutines.CancellationException
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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

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
    private val publishOnError: Boolean = scannerConfig.publishOnError

    // Caps the number of in-flight HTTP fetches across the whole scan.
    // limitedParallelism wraps Dispatchers.IO so coroutines beyond the cap suspend
    // (they don't block IO threads) until a permit frees up.
    private val fetchDispatcher: CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(httpConfig.maxConcurrentFetches)

    suspend fun buildIndex(components: List<String>, bearer: String): TenantIndex = coroutineScope {
        resolveTargets(components)
            .map { async(fetchDispatcher) { fetchAndExtract(it, bearer) } }
            .awaitAll()
            .flatten()
            .toTenantIndex()
    }

    private fun resolveTargets(components: List<String>): List<FetchTarget> =
        components.flatMap { targetsFor(it) }

    private fun targetsFor(component: String): List<FetchTarget> {
        val id = ComponentId.parse(component) ?: run {
            logger.warn("Component '{}' is not in 'domain_pkg' form — skipping", component)
            return emptyList()
        }
        val resources = metamodelService.getResources(id.domain, id.pkg)
        if (resources.isEmpty()) {
            logger.warn("No resources in metamodel for {}/{}", id.domain, id.pkg)
            return emptyList()
        }
        return resources.map { FetchTarget(component, "${id.domain}/${id.pkg}/${it.name}", it.name) }
    }

    private fun List<MinimalRecord>.toTenantIndex(): TenantIndex {
        val byKey = HashMap<String, MinimalRecord>(size * 2)
        forEach { r -> r.canonicalKeys.forEach { key -> byKey[key] = r } }
        return TenantIndex(records = this, byKey = byKey)
    }

    // Returns empty for "this resource doesn't apply to this tenant" cases
    // (route absent / cache empty). A real fetch failure aborts the whole scan
    // (coroutineScope cancels siblings, buildIndex fails) unless publishOnError is
    // set, in which case the target is skipped and the partial index is published.
    private suspend fun fetchAndExtract(target: FetchTarget, bearer: String): List<MinimalRecord> =
        try {
            val first = fetchPage(target, offset = 0L, bearer)
            first.records + fetchTail(target, first, bearer)
        } catch (ex: NoRouteException) {
            logger.info("No route for {} — {}", target.label, ex.message)
            emptyList()
        } catch (ex: NoDataException) {
            logger.info("No data for {} — {}", target.label, ex.message)
            emptyList()
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            if (!publishOnError) throw ex
            logger.warn("Skipping {} after fetch failure (publish-on-error) — {}", target.label, ex.message)
            emptyList()
        }

    private suspend fun fetchTail(
        target: FetchTarget,
        first: PageExtraction,
        bearer: String,
    ): List<MinimalRecord> = coroutineScope {
        val total = first.totalItems
        when {
            total == null -> drainSequentially(target, first.records.size, bearer)
            total <= PAGE_SIZE -> emptyList()
            else -> tailOffsets(total)
                .map { offset -> async(fetchDispatcher) { fetchPage(target, offset, bearer).records } }
                .awaitAll()
                .flatten()
        }
    }

    private fun tailOffsets(total: Long): List<Long> =
        generateSequence(PAGE_SIZE) { it + PAGE_SIZE }.takeWhile { it < total }.toList()

    // Fallback for endpoints that don't surface total_items: walk offsets in series
    // until a short page signals the end.
    private suspend fun drainSequentially(
        target: FetchTarget,
        firstPageSize: Int,
        bearer: String,
    ): List<MinimalRecord> {
        if (firstPageSize.toLong() < PAGE_SIZE) return emptyList()
        val records = mutableListOf<MinimalRecord>()
        var offset = PAGE_SIZE
        while (true) {
            val page = fetchPage(target, offset, bearer)
            records += page.records
            if (page.records.size.toLong() < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        return records
    }

    // Runs on whatever dispatcher the caller is on — callers reach this via
    // async(fetchDispatcher), so the parallelism cap applies. Don't withContext
    // here: switching to plain Dispatchers.IO would defeat the cap.
    private suspend fun fetchPage(
        target: FetchTarget,
        offset: Long,
        bearer: String,
    ): PageExtraction {
        // IDE flags createTempFile as blocking, but we're already on Dispatchers.IO
        // via the caller's async(fetchDispatcher) — no thread starvation risk.
        @Suppress("BlockingMethodInNonBlockingContext")
        val tempFile: Path = Files.createTempFile("link-walker-", "-${target.resourceName}.json")
        return try {
            fintClient.streamToFile(pageUrl(target, offset), bearer, tempFile)
            extractor.extractFromFile(tempFile, target.component, target.resourceName)
        } finally {
            runCatching { tempFile.deleteIfExists() }
                .onFailure { logger.warn("Failed to delete {}: {}", tempFile, it.message) }
        }
    }

    private fun pageUrl(target: FetchTarget, offset: Long): String =
        "$baseUrl/${target.path}?size=$PAGE_SIZE&offset=$offset"

    private data class FetchTarget(
        val component: String,
        val path: String,
        val resourceName: String,
    ) {
        val label: String get() = "$component/$resourceName"
    }

    private companion object {
        const val PAGE_SIZE = 100_000L
    }
}

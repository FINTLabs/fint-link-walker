package no.novari.linkwalker.index

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.novari.linkwalker.FintClient
import no.novari.linkwalker.config.ScannerProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.deleteIfExists
import kotlin.io.path.inputStream

/**
 * Fetches again each record that a link points to but that did not point back when the scan
 * read it. The scan reads resources minutes apart, so a record created after its target was read
 * looks like a missing back-link even when FINT has added it. The re-fetch shows the target as it
 * is now, and validation runs on that.
 *
 * A record is fetched by its first key that is not a personal identifier, so no personal data ends
 * up in a URL or a log line; a record with only personal keys is left as it was read. So is a
 * record whose fetch fails, which keeps its problem. After [MAX_FAILURES] failed fetches the rest
 * are skipped, since every failure costs a full round of retries.
 */
@Component
class BackLinkRecheck(
    scannerConfig: ScannerProperties,
    private val validator: IndexValidator,
    private val fintClient: FintClient,
    private val extractor: RecordExtractor,
    private val sanitizer: HrefSanitizer,
    private val mapper: ObjectMapper,
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val maxRecords: Int = scannerConfig.backLinkRecheck.maxRecords
    private val fetchDispatcher: CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(scannerConfig.backLinkRecheck.concurrency.coerceAtLeast(1))

    suspend fun refresh(index: TenantIndex, bearer: String): TenantIndex {
        if (maxRecords <= 0) return index
        val targets = validator.backLinkTargets(index)
        if (targets.isEmpty()) return index

        val fetchable = targets.mapNotNull { target -> sanitizer.nonPiiKey(target)?.let { target to it } }
        val selected = fetchable.take(maxRecords)
        logger.info(
            "Re-fetching {} of {} records that do not point back ({} have only personal keys, {} over the limit of {})",
            selected.size, targets.size, targets.size - fetchable.size, fetchable.size - selected.size, maxRecords,
        )

        val failures = AtomicInteger()
        val fetched = coroutineScope {
            selected.map { (target, href) ->
                async(fetchDispatcher) {
                    target to if (failures.get() < MAX_FAILURES) refetch(target, href, bearer, failures) else null
                }
            }.awaitAll()
        }
        val fresh = IdentityHashMap<MinimalRecord, MinimalRecord>()
        fetched.forEach { (old, new) -> if (new != null) fresh[old] = new }
        logger.info("Re-fetched {} records, {} could not be fetched and keep the version the scan read", fresh.size, selected.size - fresh.size)
        return if (fresh.isEmpty()) index else index.withReplaced(fresh)
    }

    private suspend fun refetch(target: MinimalRecord, href: String, bearer: String, failures: AtomicInteger): MinimalRecord? {
        @Suppress("BlockingMethodInNonBlockingContext")
        val tempFile = Files.createTempFile("link-walker-recheck-", "-${target.resourceName}.json")
        return try {
            fintClient.streamToFile(href, bearer, tempFile)
            val node = tempFile.inputStream().use { mapper.readTree(it) }
            extractor.extract(node, target.component, target.resourceName)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            if (failures.incrementAndGet() == MAX_FAILURES) {
                logger.warn("Could not re-fetch {}: {}. That makes {} failures, skipping the rest", href, ex.message, MAX_FAILURES)
            } else if (failures.get() < MAX_FAILURES) {
                logger.warn("Could not re-fetch {}: {}", href, ex.message)
            }
            null
        } finally {
            runCatching { tempFile.deleteIfExists() }
        }
    }

    private companion object {
        const val MAX_FAILURES = 20
    }
}

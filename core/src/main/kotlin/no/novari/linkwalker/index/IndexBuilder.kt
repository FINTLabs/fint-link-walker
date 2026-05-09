package no.novari.linkwalker.index

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import no.novari.linkwalker.FintClient
import no.novari.linkwalker.NoDataException
import no.novari.linkwalker.NoRouteException
import no.novari.linkwalker.config.LinkWalkerConfig
import no.novari.metamodel.MetamodelService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

@Component
class IndexBuilder(
    private val config: LinkWalkerConfig,
    private val fintClient: FintClient,
    private val metamodelService: MetamodelService,
    private val extractor: RecordExtractor,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun buildIndex(
        components: List<String>,
        bearer: String,
        onComponentError: (component: String) -> Unit,
    ): TenantIndex = coroutineScope {
        val targets = mutableListOf<Triple<String, String, String>>()

        components.forEach { component ->
            val (domain, pkg) = parseComponent(component) ?: run {
                logger.warn("Component '{}' is not in 'domain_pkg' form — skipping", component)
                return@forEach
            }
            val resources = metamodelService.getResources(domain, pkg)
            if (resources.isEmpty()) {
                logger.warn("No resources in metamodel for {}/{}", domain, pkg)
                return@forEach
            }
            resources.forEach { resource ->
                targets += Triple(component, "$domain/$pkg/${resource.name}", resource.name)
            }
        }

        val perCollection = targets.map { (component, path, resourceName) ->
            async(Dispatchers.IO) {
                fetchAndExtract(component, path, resourceName, bearer, onComponentError)
            }
        }.awaitAll()

        val all = perCollection.flatten()
        val byKey = HashMap<String, MinimalRecord>(all.size * 2)
        all.forEach { record ->
            record.canonicalKeys.forEach { key -> byKey[key] = record }
        }
        TenantIndex(records = all, byKey = byKey)
    }

    private suspend fun fetchAndExtract(
        component: String,
        path: String,
        resourceName: String,
        bearer: String,
        onComponentError: (component: String) -> Unit,
    ): List<MinimalRecord> = withContext(Dispatchers.IO) {
        val url = "${config.baseUrl.trimEnd('/')}/$path?size=$PAGE_SIZE"
        val tempFile: Path = Files.createTempFile("link-walker-", "-$resourceName.json")
        try {
            fintClient.streamToFile(url, bearer, tempFile)
            extractor.extractFromFile(tempFile, component, resourceName)
        } catch (ex: NoRouteException) {
            logger.info("No route for {}/{} — {}", component, resourceName, ex.message)
            emptyList()
        } catch (ex: NoDataException) {
            logger.info("No data for {}/{} — {}", component, resourceName, ex.message)
            emptyList()
        } catch (ex: Exception) {
            logger.error("Fetch failed for {}", url, ex)
            onComponentError(component)
            emptyList()
        } finally {
            runCatching { tempFile.deleteIfExists() }
                .onFailure { logger.warn("Failed to delete {}: {}", tempFile, it.message) }
        }
    }

    private fun parseComponent(component: String): Pair<String, String>? =
        component.split('_', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }

    private companion object {
        const val PAGE_SIZE = 100_000
    }
}

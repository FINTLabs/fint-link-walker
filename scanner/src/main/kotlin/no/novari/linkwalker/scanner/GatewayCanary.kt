package no.novari.linkwalker.scanner

import kotlinx.coroutines.CancellationException
import no.novari.linkwalker.FetchRouting
import no.novari.linkwalker.FintClient
import no.novari.linkwalker.config.ScannerProperties
import no.novari.linkwalker.index.PageIntegrity
import no.novari.linkwalker.report.GatewayCanaryResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import kotlin.io.path.deleteIfExists

/**
 * Fetches one page through the public host and checks it for damage. Runs only when pages are
 * otherwise fetched around the Access Gateway, so a scan still notices when the gateway misbehaves.
 * A failing canary never fails the scan.
 */
@Component
class GatewayCanary(
    private val properties: ScannerProperties,
    private val fintClient: FintClient,
    private val routing: FetchRouting,
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val integrity = PageIntegrity(properties.baseUrl)

    suspend fun probe(bearer: String): GatewayCanaryResult? {
        if (!routing.bypassConfigured) return null
        val url = properties.baseUrl.trimEnd('/') + "/" + properties.canaryPath.trimStart('/')
        val tempFile = Files.createTempFile("link-walker-canary-", ".json")
        try {
            val fetched = fintClient.streamToFileThroughGateway(url, bearer, tempFile)
            val report = integrity.inspect(tempFile)
            val finding = report.finding
            if (finding == null) {
                logger.info("Gateway canary clean: url={} bytes={} sha256={} via={}", url, report.bytes, report.sha256, fetched.via)
            } else {
                logger.warn(
                    "Gateway canary damaged: url={} {} at byte {} bytes={} sha256={} via={}",
                    url, finding.signature, finding.offset, report.bytes, report.sha256, fetched.via,
                )
            }
            return GatewayCanaryResult(
                url = url,
                ok = finding == null,
                via = fetched.via,
                bytes = report.bytes,
                sha256 = report.sha256,
                signature = finding?.signature,
                byteOffset = finding?.offset,
                error = null,
            )
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            logger.warn("Gateway canary failed: url={} ({})", url, ex.message)
            return GatewayCanaryResult(
                url = url,
                ok = false,
                via = null,
                bytes = 0,
                sha256 = null,
                signature = null,
                byteOffset = null,
                error = ex.message,
            )
        } finally {
            runCatching { tempFile.deleteIfExists() }
        }
    }
}

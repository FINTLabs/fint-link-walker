package no.novari.linkwalker.report

import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import no.novari.linkwalker.config.LinkWalkerConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ReportStoreConfig {

    @Bean
    @ConditionalOnProperty(
        name = ["link-walker.storage.type"],
        havingValue = "file",
        matchIfMissing = true,
    )
    fun fileReportStore(config: LinkWalkerConfig, mapper: ObjectMapper): ReportStore =
        FileReportStore(config, mapper)

    @Bean
    @ConditionalOnProperty(name = ["link-walker.storage.type"], havingValue = "blob")
    fun blobReportStore(
        config: LinkWalkerConfig,
        mapper: ObjectMapper,
        container: BlobContainerClient,
    ): ReportStore = BlobReportStore(config, mapper, container)

    @Bean
    @ConditionalOnProperty(name = ["link-walker.storage.type"], havingValue = "blob")
    fun blobContainerClient(config: LinkWalkerConfig): BlobContainerClient {
        val blob = config.storage.blob
        val builder = BlobServiceClientBuilder()

        val connectionString = blob.connectionString
        if (!connectionString.isNullOrBlank()) {
            builder.connectionString(connectionString)
        } else {
            val endpoint = requireNotNull(blob.endpoint?.takeIf { it.isNotBlank() }) {
                "link-walker.storage.blob.endpoint required when no connection-string is set"
            }
            builder.endpoint(endpoint)
            builder.credential(DefaultAzureCredentialBuilder().build())
        }

        val container = builder.buildClient().getBlobContainerClient(blob.container)
        if (!container.exists()) container.create()
        return container
    }
}

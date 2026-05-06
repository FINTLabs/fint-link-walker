package no.novari.linkwalker.reader

import com.azure.storage.blob.BlobServiceClient
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.enterprise.inject.Produces
import no.novari.linkwalker.config.BlobStorageConfig
import no.novari.linkwalker.config.FileStorageConfig
import no.novari.linkwalker.config.LinkWalkerConfig
import no.novari.linkwalker.config.StorageConfig
import no.novari.linkwalker.reader.storage.JvmBlobReportStore
import no.novari.linkwalker.reader.storage.JvmFileReportStore
import no.novari.linkwalker.report.ReportStore
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class ReportStoreProducer {

    @Produces
    @ApplicationScoped
    fun reportStore(
        @ConfigProperty(name = "link-walker.storage.type", defaultValue = "file")
        storageType: String,
        @ConfigProperty(name = "link-walker.storage.file.directory", defaultValue = "/tmp/link-walker-reports")
        fileDir: String,
        @ConfigProperty(name = "link-walker.storage.blob.container", defaultValue = "link-walker-reports")
        blobContainer: String,
        // Lazy resolution: in file mode the extension's BlobServiceClient is never asked for.
        blobServiceClient: Instance<BlobServiceClient>,
        mapper: ObjectMapper,
    ): ReportStore {
        val config = LinkWalkerConfig(
            storage = StorageConfig(
                type = storageType,
                file = FileStorageConfig(directory = fileDir),
                blob = BlobStorageConfig(container = blobContainer),
            ),
        )

        return when (storageType.lowercase()) {
            "blob" -> {
                val container = blobServiceClient.get().getBlobContainerClient(blobContainer)
                if (!container.exists()) container.create()
                JvmBlobReportStore(mapper, container)
            }
            else -> JvmFileReportStore(config, mapper)
        }
    }
}

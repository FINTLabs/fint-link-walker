package no.novari.linkwalker.reader

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import no.novari.linkwalker.config.AutoRelationRule
import no.novari.linkwalker.config.BlobStorageConfig
import no.novari.linkwalker.config.FileStorageConfig
import no.novari.linkwalker.config.StorageConfig
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ComponentScan(
    basePackages = ["no.novari.linkwalker", "no.novari.metamodel"],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = ["no\\.novari\\.linkwalker\\.scanner\\..*"],
        ),
    ],
)
@ConfigurationPropertiesScan(basePackages = ["no.novari.linkwalker"])
@EnableScheduling
@RegisterReflectionForBinding(
    StorageConfig::class,
    FileStorageConfig::class,
    BlobStorageConfig::class,
    AutoRelationRule::class,
)
@OpenAPIDefinition(
    info = Info(
        title = "FINT Link Walker API",
        version = "1.0.0",
        description = "Per-org link-integrity scan reports for the FINT data platform. " +
            "Surfaces aggregate counts, per-component / per-resource integrity percentages, " +
            "and paginated broken-link rows produced by the scheduled scanner.",
        contact = Contact(
            name = "@nozoz",
            url = "https://github.com/nozoz",
        ),
    ),
)
class ReaderApp

fun main(args: Array<String>) {
    runApplication<ReaderApp>(*args)
}

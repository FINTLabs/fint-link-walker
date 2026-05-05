package no.novari.linkwalker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("link-walker")
data class LinkWalkerConfig(
    val scanInterval: Duration = Duration.ofHours(6),
    val baseUrl: String = "https://api.felleskomponent.no",
    val tenant: String? = null,
    val tenants: List<String> = emptyList(),
    val components: List<String> = ALL_FINT_COMPONENTS,
    val maxAttempts: Long = 5L,
    val fetchConcurrency: Int = 10,
    val connectTimeout: Duration = Duration.ofSeconds(10),
    val readTimeout: Duration = Duration.ofMinutes(10),
    val storage: StorageConfig = StorageConfig(),
    val autoRelations: List<AutoRelationRule> = emptyList(),
    val autoRelationComponents: List<String> = emptyList(),
    val piiIdentifiers: List<String> = listOf("fodselsnummer", "feidenavn"),
    val excludeRelations: List<String> = listOf("vigoreferanse", "grepreferanse"),
)

data class StorageConfig(
    val type: String = "file",
    val file: FileStorageConfig = FileStorageConfig(),
    val blob: BlobStorageConfig = BlobStorageConfig(),
)

data class FileStorageConfig(
    val directory: String = System.getProperty("java.io.tmpdir") + "/link-walker-reports",
)

data class BlobStorageConfig(
    val endpoint: String? = null,
    val container: String = "link-walker-reports",
    val connectionString: String? = null,
)

data class AutoRelationRule(
    val source: String,
    val relation: String,
    val target: String,
    val backRelation: String,
)

private val ALL_FINT_COMPONENTS = listOf(
    "administrasjon_fullmakt",
    "administrasjon_kodeverk",
    "administrasjon_organisasjon",
    "administrasjon_personal",
    "arkiv_noark",
    "arkiv_personal",
    "felles_kodeverk",
    "okonomi_faktura",
    "okonomi_kodeverk",
    "okonomi_regnskap",
    "personvern_samtykke",
    "ressurs_datautstyr",
    "ressurs_eiendel",
    "ressurs_tilgang",
    "utdanning_elev",
    "utdanning_kodeverk",
    "utdanning_larling",
    "utdanning_ot",
    "utdanning_timeplan",
    "utdanning_utdanningsprogram",
    "utdanning_vurdering",
)

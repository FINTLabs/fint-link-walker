package no.novari.linkwalker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("fint.link-walker.scanner")
data class ScannerProperties(
    val orgId: String,
    val baseUrl: String = "https://api.felleskomponent.no",
    val pageSize: Int = 10_000,
    val pageSizes: Map<String, Int> = emptyMap(),
    val components: List<String> = ALL_FINT_COMPONENTS,
    val fetchBaseUrl: String? = null,
    val canaryPath: String = "utdanning/elev/elevforhold?size=2000",
    val serviceRouting: ServiceRoutingProperties = ServiceRoutingProperties(),
) {
    private companion object {
        val ALL_FINT_COMPONENTS = listOf(
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
    }
}

/**
 * Fetching straight from the org's Kubernetes Services instead of through the public host.
 * The namespace is the org id with underscores turned into dashes unless [namespace] says
 * otherwise. Domains in [clientApiDomains] are served by [clientApiService]; every other domain
 * is served by the legacy consumer named by [legacyServicePattern].
 */
data class ServiceRoutingProperties(
    val enabled: Boolean = false,
    val clientApiService: String = "fint-core-client-api",
    val clientApiDomains: List<String> = listOf("utdanning"),
    val legacyServicePattern: String = "fint-core-consumer-{domain}-{package}",
    val hostPattern: String = "{service}.{namespace}.svc.cluster.local:8080",
    val namespace: String? = null,
)

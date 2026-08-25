package no.novari.linkwalker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("fint.link-walker.scanner")
data class ScannerProperties(
    val orgId: String,
    val baseUrl: String = "https://api.felleskomponent.no",
    val components: List<String> = ALL_FINT_COMPONENTS,
    val publishOnError: Boolean = false,
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

package no.fintlabs.linkwalker.auth.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

class ClientRequest(
    @get:JsonProperty("object")
    val clientData: ClientData = ClientData(),
    orgId: String
) {

    val orgId = orgId.replace("-", ".")
        .replace("_", ".")

}

class ClientData {

    val name: String = UUID.randomUUID().toString()
    val shortDescription: String = "Autogenerert relasjontester"
    val note: String = "En generert klient for relasjon testing"

    // Keep managed so user cannot access client credentials
    val managed = true

    // Access to all components due to cross-domain relations
    val components = listOf(
        "ou=arkiv_kodeverk,ou=components,o=fint",
        "ou=arkiv_kulturminnevern,ou=components,o=fint",
        "ou=arkiv_noark,ou=components,o=fint",
        "ou=arkiv_personal,ou=components,o=fint",
        "ou=arkiv_samferdsel,ou=components,o=fint",
        "ou=administrasjon_personal,ou=components,o=fint",
        "ou=administrasjon_organisasjon,ou=components,o=fint",
        "ou=administrasjon_okonomi,ou=components,o=fint",
        "ou=administrasjon_kodeverk,ou=components,o=fint",
        "ou=administrasjon_fullmakt,ou=components,o=fint",
        "ou=fint_metamodell,ou=components,o=fint",
        "ou=okonomi_faktura,ou=components,o=fint",
        "ou=okonomi_kodeverk,ou=components,o=fint",
        "ou=okonomi_regnskap,ou=components,o=fint",
        "ou=profilbilde,ou=components,o=fint",
        "ou=personvern_samtykke,ou=components,o=fint",
        "ou=personvern_kodeverk,ou=components,o=fint",
        "ou=felles_kodeverk,ou=components,o=fint",
        "ou=utdanning_kodeverk,ou=components,o=fint",
        "ou=utdanning_larling,ou=components,o=fint",
        "ou=utdanning_ot,ou=components,o=fint",
        "ou=utdanning_timeplan,ou=components,o=fint",
        "ou=utdanning_utdanningsprogram,ou=components,o=fint",
        "ou=utdanning_vurdering,ou=components,o=fint",
        "ou=vigokodeverk,ou=components,o=fint",
        "ou=utdanning_elev,ou=components,o=fint"
    )

}
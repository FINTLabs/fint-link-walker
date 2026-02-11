package no.fintlabs.linkwalker.auth.model

import com.fasterxml.jackson.annotation.JsonProperty
import no.fintlabs.linkwalker.auth.AuthConstants.CLIENT_NAME
import java.util.UUID

class ClientRequest(
    components: List<String>,
    orgId: String,
    @get:JsonProperty("object")
    val clientData: ClientData = ClientData(components),
) {

    val orgId = orgId.replace("-", ".")
        .replace("_", ".")

}

class ClientData(components: List<String>) {

    val name: String = CLIENT_NAME
    val shortDescription: String = "Autogenerert relasjontester"
    val note: String = "En generert klient for relasjon testing"

    // Keep managed so user cannot access client credentials
    val managed = true

    // Access to all components due to cross-domain relations
    val components = components.map { "ou=$it,ou=components,o=fint" }
}
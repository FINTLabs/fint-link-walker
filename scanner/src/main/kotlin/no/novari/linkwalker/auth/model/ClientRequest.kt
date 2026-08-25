package no.novari.linkwalker.auth.model

import com.fasterxml.jackson.annotation.JsonProperty
import no.novari.linkwalker.auth.AuthConstants.CLIENT_NAME

data class ClientRequest(
    val orgId: String,
    @get:JsonProperty("object")
    val clientData: ClientData,
)

data class ClientData(
    val components: List<String>,
    val name: String = CLIENT_NAME,
    val shortDescription: String = "Autogenerert relasjontester",
    val note: String = "En generert klient for relasjon testing",
    val managed: Boolean = true,
) {
    companion object {
        fun forComponents(components: List<String>): ClientData =
            ClientData(components = components.map { "ou=$it,ou=components,o=fint" })
    }
}

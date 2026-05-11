package no.novari.linkwalker.auth.model

data class AuthObject(
    val dn: String,
    val name: String,
    val shortDescription: String,
    val assetId: String,
    val asset: String,
    val note: String,
    val password: String,
    val clientSecret: String,
    val publicKey: String,
    val clientId: String,
    val components: List<String>,
    val accessPackages: List<String>,
    val managed: Boolean,
)

package no.fintlabs.linkwalker.model

data class RelationError(
    val baseUrl: String,
    val count: Int,
    val errors: Map<String, String>
)
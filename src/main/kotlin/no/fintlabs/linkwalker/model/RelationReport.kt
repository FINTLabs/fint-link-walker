package no.fintlabs.linkwalker.model

data class RelationReport(
    val baseUrl: String,
    val relationErrors: MutableList<RelationError>
) {
    val errorCount get() = relationErrors.size
    val hasErrors get() = relationErrors.isNotEmpty()
}
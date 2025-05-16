package no.fintlabs.linkwalker

import no.fintlabs.linkwalker.model.RelationError

data class RelationReport(
    val baseUrl: String,
    val relationErrors: MutableList<RelationError>
) {
    val errorCount get() = relationErrors.size
    val hasErrors get() = relationErrors.isNotEmpty()
}
package no.fintlabs.linkwalker.model

data class RelationReport(
    val baseUrl: String,
    val relationErrors: MutableList<RelationEntry>,
    val unknownLinks: MutableList<RelationEntry>
) {
    val hasErrors get() = relationErrors.isNotEmpty() || unknownLinks.isNotEmpty()
    val errorCount get() = relationErrors.size
    val unknownSize get() = unknownLinks.size
}
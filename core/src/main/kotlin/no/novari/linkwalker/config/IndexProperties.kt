package no.novari.linkwalker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("link-walker")
data class IndexProperties(
    val autoRelations: List<AutoRelationRule> = emptyList(),
    val autoRelationComponents: List<String> = emptyList(),
    val piiIdentifiers: List<String> = listOf("fodselsnummer", "feidenavn"),
    val excludeRelations: List<String> = listOf("vigoreferanse", "grepreferanse"),
)

data class AutoRelationRule(
    val source: String,
    val relation: String,
    val target: String,
    val backRelation: String,
)

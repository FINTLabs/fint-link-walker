package no.novari.linkwalker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("fint.link-walker.index")
data class IndexProperties(
    val autoRelations: List<AutoRelationRule> = emptyList(),
    val autoRelationComponents: List<String> = emptyList(),
    /**
     * Field names that hold personal identifiers. Their values are masked before any
     * row is written to the database so we never persist personal data alongside scan
     * results.
     */
    val piiIdentifiers: List<String> = listOf("fodselsnummer", "feidenavn"),
    /**
     * Relation names pointing to URLs we neither own nor can verify (external
     * vocabularies like vigoreferanse/grepreferanse). They are skipped during scanning
     * so we don't flag them as broken.
     */
    val excludeRelations: List<String> = listOf("vigoreferanse", "grepreferanse"),
)

data class AutoRelationRule(
    val source: String,
    val relation: String,
    val target: String,
    val backRelation: String,
)

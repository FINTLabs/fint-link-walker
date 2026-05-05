package no.novari.linkwalker.index

import no.novari.linkwalker.config.AutoRelationRule
import no.novari.linkwalker.config.LinkWalkerConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoRelationRulesTest {

    @Test
    fun `returns true when component enabled and rule matches`() {
        val rules = build(
            autoRelations = listOf(rule("utdanning-elev-elevforhold", "skole")),
            enabled = listOf("utdanning_elev"),
        )
        assertTrue(rules.isAutoRelation("utdanning_elev", "elevforhold", "skole"))
    }

    @Test
    fun `returns false when component is enabled but rule doesn't match`() {
        val rules = build(
            autoRelations = listOf(rule("utdanning-elev-elevforhold", "skole")),
            enabled = listOf("utdanning_elev"),
        )
        assertFalse(rules.isAutoRelation("utdanning_elev", "elevforhold", "elev"))
    }

    @Test
    fun `returns false when rule matches but component is not enabled`() {
        val rules = build(
            autoRelations = listOf(rule("utdanning-elev-elevforhold", "skole")),
            enabled = listOf("utdanning_vurdering"),
        )
        assertFalse(rules.isAutoRelation("utdanning_elev", "elevforhold", "skole"))
    }

    @Test
    fun `returns false when no components are enabled`() {
        val rules = build(
            autoRelations = listOf(rule("utdanning-elev-elevforhold", "skole")),
            enabled = emptyList(),
        )
        assertFalse(rules.isAutoRelation("utdanning_elev", "elevforhold", "skole"))
    }

    @Test
    fun `case-insensitive matching across component, resource, and relation`() {
        val rules = build(
            autoRelations = listOf(rule("utdanning-elev-elevforhold", "skole")),
            enabled = listOf("UTDANNING_ELEV"),
        )
        assertTrue(rules.isAutoRelation("utdanning_elev", "Elevforhold", "SKOLE"))
    }

    @Test
    fun `empty config always returns false`() {
        val rules = build()
        assertFalse(rules.isAutoRelation("any", "any", "any"))
    }

    private fun rule(source: String, relation: String) = AutoRelationRule(
        source = source,
        relation = relation,
        target = "ignored",
        backRelation = "ignored",
    )

    private fun build(
        autoRelations: List<AutoRelationRule> = emptyList(),
        enabled: List<String> = emptyList(),
    ) = AutoRelationRules(
        LinkWalkerConfig(
            autoRelations = autoRelations,
            autoRelationComponents = enabled,
        )
    )
}

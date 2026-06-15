package no.novari.linkwalker.index

import no.novari.linkwalker.config.IndexProperties
import org.springframework.stereotype.Component

@Component
class AutoRelationRules(config: IndexProperties) {

    private val enabledComponents: Set<String> =
        config.autoRelationComponents.mapTo(mutableSetOf()) { it.lowercase() }

    // Parsed once: source "utdanning-elev-elevforhold" → ("utdanning_elev", "elevforhold").
    // Lookup constructs the same RuleKey from in-memory (component, resource, relation)
    // so the hot path is one data-class allocation + a Set hit, no string transformation.
    private val keys: Set<RuleKey> = config.autoRelations
        .mapNotNull { ruleKey(it.source, it.relation) }
        .toSet()

    fun isAutoRelation(component: String, resourceName: String, relationName: String): Boolean {
        if (component.lowercase() !in enabledComponents) return false
        return RuleKey(component.lowercase(), resourceName.lowercase(), relationName.lowercase()) in keys
    }

    private fun ruleKey(dashSource: String, relation: String): RuleKey? {
        val parts = dashSource.split('-')
        if (parts.size < 3) return null
        val component = "${parts[0]}_${parts[1]}".lowercase()
        val resource = parts.drop(2).joinToString("-").lowercase()
        return RuleKey(component, resource, relation.lowercase())
    }

    private data class RuleKey(val component: String, val resource: String, val relation: String)
}

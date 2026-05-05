package no.novari.linkwalker.index

import no.novari.linkwalker.config.LinkWalkerConfig
import org.springframework.stereotype.Component

@Component
class AutoRelationRules(config: LinkWalkerConfig) {

    private val enabledComponents: Set<String> =
        config.autoRelationComponents.mapTo(mutableSetOf()) { it.lowercase() }

    private val keys: Set<Pair<String, String>> = config.autoRelations
        .map { it.source.lowercase() to it.relation.lowercase() }
        .toSet()

    fun isAutoRelation(component: String, resourceName: String, relationName: String): Boolean {
        if (component.lowercase() !in enabledComponents) return false
        val sourceKey = "${component.replace('_', '-')}-$resourceName".lowercase()
        return (sourceKey to relationName.lowercase()) in keys
    }
}

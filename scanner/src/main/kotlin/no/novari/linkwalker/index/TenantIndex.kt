package no.novari.linkwalker.index

import no.novari.linkwalker.report.LinkGroup
import no.novari.linkwalker.report.LinkScope

/**
 * [fetchedResources] holds every resource whose pages were fetched, including those that came
 * back empty. A link to any other resource is [LinkScope.NotCovered].
 */
class TenantIndex(
    val records: List<MinimalRecord>,
    private val byKey: Map<String, MinimalRecord>,
    private val fetchedResources: Set<ResourceKey>,
) {
    fun resolves(canonicalKey: String): Boolean = byKey.containsKey(canonicalKey)

    fun recordAt(canonicalKey: String): MinimalRecord? = byKey[canonicalKey]

    fun linkTarget(sourceComponent: String, targetHref: String): LinkTarget {
        val target = ResourceKey.ofHref(targetHref)
            ?: return LinkTarget(LinkScope.NotCovered, LinkGroup.UNKNOWN_TARGET)
        val scope = when {
            target !in fetchedResources -> LinkScope.NotCovered
            domainOf(sourceComponent) == domainOf(target.component) -> LinkScope.WithinDomain
            else -> LinkScope.CrossDomain
        }
        return LinkTarget(scope, target.component)
    }

    private fun domainOf(component: String): String? = ComponentId.parse(component)?.domain
}

data class LinkTarget(val scope: LinkScope, val component: String)

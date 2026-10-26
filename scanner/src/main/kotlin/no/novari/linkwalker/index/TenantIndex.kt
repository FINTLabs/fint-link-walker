package no.novari.linkwalker.index

import no.novari.linkwalker.report.LinkGroup
import no.novari.linkwalker.report.LinkScope
import java.util.IdentityHashMap

/**
 * [fetchedResources] holds every resource whose pages were fetched, including those that came
 * back empty. A link to any other resource is [LinkScope.NotCovered]. [replaced] maps keys to
 * records fetched again after the scan, see [withReplaced].
 */
class TenantIndex(
    val records: List<MinimalRecord>,
    private val byKey: Map<String, MinimalRecord>,
    private val fetchedResources: Set<ResourceKey>,
    private val replaced: Map<String, MinimalRecord> = emptyMap(),
) {
    fun resolves(canonicalKey: String): Boolean = recordAt(canonicalKey) != null

    fun recordAt(canonicalKey: String): MinimalRecord? = replaced[canonicalKey] ?: byKey[canonicalKey]

    /**
     * A copy where each record in [fresh] is swapped for its newer version, found by any key of
     * either version. [fresh] is keyed by identity. The key map is not copied, so this stays cheap
     * on an index with millions of records.
     */
    fun withReplaced(fresh: IdentityHashMap<MinimalRecord, MinimalRecord>): TenantIndex {
        val keys = HashMap(replaced)
        fresh.forEach { (old, new) -> (old.canonicalKeys + new.canonicalKeys).forEach { keys[it] = new } }
        return TenantIndex(records.map { fresh[it] ?: it }, byKey, fetchedResources, keys)
    }

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

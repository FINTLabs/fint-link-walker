package no.novari.linkwalker.index

class TenantIndex(
    val records: List<MinimalRecord>,
    private val byKey: Map<String, MinimalRecord>,
) {
    fun resolves(canonicalKey: String): Boolean = byKey.containsKey(canonicalKey)

    fun recordAt(canonicalKey: String): MinimalRecord? = byKey[canonicalKey]
}

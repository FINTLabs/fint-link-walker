package no.novari.linkwalker.index

/**
 * A scanned FINT entry, minimal enough to drive link-integrity validation.
 * Records without a `_links.self` href are not modelled here — see
 * [RecordExtractor.extract] which returns null in that case.
 */
data class MinimalRecord(
    val component: String,
    val resourceName: String,
    val canonicalKeys: List<String>,
    val outboundRefs: List<OutboundRef>,
    val malformedHrefs: List<String>,
) {
    init {
        require(canonicalKeys.isNotEmpty()) {
            "MinimalRecord must have at least one canonical key (self link)"
        }
    }
}

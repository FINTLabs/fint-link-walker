package no.novari.linkwalker.index

data class MinimalRecord(
    val component: String,
    val resourceName: String,
    val canonicalKeys: List<String>,
    val outboundRefs: List<OutboundRef>,
    val malformedHrefs: List<String>,
) {
    val displaySelf: String get() = canonicalKeys.firstOrNull() ?: "<no-self-link>"
}

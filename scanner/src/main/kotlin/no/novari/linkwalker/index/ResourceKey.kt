package no.novari.linkwalker.index

/**
 * A resource by component and name, both lowercase, e.g. `utdanning_elev` + `person`.
 * Sub-paths are dropped, so `felles/kodeverk/iso/kjonn` is `felles_kodeverk` + `kjonn`.
 */
data class ResourceKey private constructor(val component: String, val resource: String) {
    companion object {
        fun of(component: String, resource: String): ResourceKey =
            ResourceKey(component.lowercase(), resource.lowercase())

        fun ofHref(href: String): ResourceKey? {
            val prefix = HREF_REGEX.matchEntire(href)?.groupValues?.get(1) ?: return null
            val segments = prefix.substringAfter("://").split('/').drop(1)
            return of("${segments[0]}_${segments[1]}", segments.last())
        }
    }
}

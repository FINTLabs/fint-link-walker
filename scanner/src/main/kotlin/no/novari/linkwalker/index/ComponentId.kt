package no.novari.linkwalker.index

/**
 * Components are addressed by their `domain_pkg` shorthand (e.g. `utdanning_elev`).
 * This is the only place that parses or formats that shorthand.
 */
data class ComponentId(val domain: String, val pkg: String) {
    val raw: String get() = "${domain}_${pkg}"

    companion object {
        fun parse(raw: String): ComponentId? =
            raw.split('_', limit = 2)
                .takeIf { it.size == 2 }
                ?.let { ComponentId(it[0], it[1]) }
    }
}

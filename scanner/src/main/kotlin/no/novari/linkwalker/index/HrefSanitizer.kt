package no.novari.linkwalker.index

import no.novari.linkwalker.config.IndexProperties
import org.springframework.stereotype.Component

/**
 * Strips PII identifier values from hrefs before they are written to disk.
 * Validation correctness is unaffected — this only runs at report-emit time.
 */
@Component
class HrefSanitizer(config: IndexProperties) {

    private val piiTypes: Set<String> = config.piiIdentifiers.mapTo(mutableSetOf()) { it.lowercase() }

    /**
     * Returns a safe href for the given record: the first non-PII canonical
     * key if any, otherwise the first key with its PII value masked.
     * [MinimalRecord] guarantees at least one canonical key.
     */
    fun safeHref(record: MinimalRecord): String =
        record.canonicalKeys.firstOrNull { !isPii(it) }
            ?: mask(record.canonicalKeys.first())

    /**
     * Replaces the value portion of the href with `***` when its identifier type is in the PII list.
     * Hrefs that don't match the standard `…/component/idField/idValue` shape are returned unchanged.
     */
    fun mask(href: String): String {
        val parts = parseHref(href) ?: return href
        return if (parts.isPii) "${parts.prefix}/${parts.field}/***" else href
    }

    private fun isPii(href: String): Boolean = parseHref(href)?.isPii == true

    private fun parseHref(href: String): HrefParts? {
        val match = HREF_REGEX.matchEntire(href) ?: return null
        val (prefix, field, _) = match.destructured
        return HrefParts(prefix, field, field.lowercase() in piiTypes)
    }

    private data class HrefParts(val prefix: String, val field: String, val isPii: Boolean)
}

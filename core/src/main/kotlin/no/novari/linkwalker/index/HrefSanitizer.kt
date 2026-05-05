package no.novari.linkwalker.index

import no.novari.linkwalker.config.LinkWalkerConfig
import org.springframework.stereotype.Component

/**
 * Strips PII identifier values from hrefs before they are written to disk.
 * Validation correctness is unaffected — this only runs at report-emit time.
 */
@Component
class HrefSanitizer(config: LinkWalkerConfig) {

    private val piiTypes: Set<String> = config.piiIdentifiers.mapTo(mutableSetOf()) { it.lowercase() }

    /**
     * Returns a safe href for the given record, preferring a non-PII canonical key when available.
     * Falls back to masking the [fallback] href if no safe variant exists.
     */
    fun safeHref(record: MinimalRecord?, fallback: String): String {
        record?.canonicalKeys?.firstOrNull { !isPii(it) }?.let { return it }
        return mask(fallback)
    }

    /**
     * Replaces the value portion of the href with `***` when its identifier type is in the PII list.
     * Hrefs that don't match the standard `…/component/idField/idValue` shape are returned unchanged.
     */
    fun mask(href: String): String {
        val match = HREF_REGEX.matchEntire(href) ?: return href
        val (prefix, field, _) = match.destructured
        return if (field.lowercase() in piiTypes) "$prefix/$field/***" else href
    }

    private fun isPii(href: String): Boolean {
        val match = HREF_REGEX.matchEntire(href) ?: return false
        val (_, field, _) = match.destructured
        return field.lowercase() in piiTypes
    }

    companion object {
        /**
         * Matches a Fint resource href and splits out the identifier-field /
         * identifier-value pair so we can mask the value when the field is in
         * the configured PII list.
         *
         * Groups:
         *   1. URL prefix (host + path up to and including the resource name).
         *   2. Identifier field — the segment that classifies the identifier
         *      (`systemid`, `fodselsnummer`, `feidenavn`, ...).
         *   3. Identifier value — the actual ID, stops at `?` or `#`.
         *
         * Must stay in sync with `RecordExtractor.HREF_REGEX`.
         */
        private val HREF_REGEX =
            Regex("""(https?://[^/]+/(?:[^/]+/){2,}[^/]+)/([^/]+)/([^/?#]+)""")
    }
}

package no.novari.linkwalker

/**
 * The tenant identifier. Matches the reader's path-variable regex
 * (`^[a-z0-9_]+$`) so an [OrgId] can always be safely embedded in a URL,
 * a log line, an MDC value, or a DB column without further validation.
 */
@JvmInline
value class OrgId(val value: String) {
    init {
        require(REGEX.matches(value)) {
            "Invalid org-id: '$value' (expected lowercase alphanumeric with underscores, e.g. agderfk_no)"
        }
    }

    override fun toString(): String = value

    companion object {
        val REGEX: Regex = Regex("^[a-z0-9_]+\$")

        fun parseOrNull(raw: String): OrgId? = if (REGEX.matches(raw)) OrgId(raw) else null
    }
}

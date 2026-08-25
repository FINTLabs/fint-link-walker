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
        // Lowercase ASCII + digits + underscore, fully anchored. Matches the FINT tenant
        // naming convention (e.g. `afk_no`, `agderfk_no`) that flais-gateway stores under
        // `ou=<orgId>,ou=organisations,o=fint` — anything outside this charset would break
        // the LDAP DN, the URL path-variable pattern, or the DB column constraint.
        val REGEX: Regex = Regex("^[a-z0-9_]+\$")
    }
}

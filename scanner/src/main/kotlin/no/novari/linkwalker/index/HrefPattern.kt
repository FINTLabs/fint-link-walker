package no.novari.linkwalker.index

/**
 * Matches a Fint resource href and captures three groups:
 *   1. URL prefix up to and including the resource name
 *      (host + at least three path segments — domain/pkg/.../resource).
 *   2. Identifier field (e.g. `systemid`, `fodselsnummer`).
 *   3. Identifier value (stops at `?` or `#` to avoid query/fragment).
 *
 * Allows variable depth so sub-namespaced resources match too,
 * e.g. `https://host/felles/kodeverk/iso/kjonn/systemid/2`.
 *
 * Examples that match (host + 5 or 6 path segments):
 *   https://api.f.no/utdanning/elev/elev/systemid/abc
 *   https://api.f.no/felles/kodeverk/iso/kjonn/systemid/2
 *
 * Doesn't match: anything fewer than 5 path segments, malformed
 * placeholders like `${'$'}{...}/systemid/x`, or non-http(s) schemes.
 */
internal val HREF_REGEX: Regex =
    Regex("""(https?://[^/]+/(?:[^/]+/){2,}[^/]+)/([^/]+)/([^/?#]+)""")

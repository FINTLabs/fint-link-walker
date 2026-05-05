package no.novari.linkwalker.index

import no.novari.linkwalker.config.LinkWalkerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HrefSanitizerTest {

    @Test
    fun `safeHref returns non-PII canonical key when one exists`() {
        val record = recordOf(
            "https://host/utdanning/elev/person/fodselsnummer/12345",
            "https://host/utdanning/elev/person/systemid/p-1",
        )
        val href = sanitizer().safeHref(record, record.canonicalKeys.first())
        assertEquals("https://host/utdanning/elev/person/systemid/p-1", href)
    }

    @Test
    fun `safeHref masks fallback when all canonical keys are PII`() {
        val record = recordOf("https://host/utdanning/elev/person/fodselsnummer/12345")
        val href = sanitizer().safeHref(record, record.canonicalKeys.first())
        assertEquals("https://host/utdanning/elev/person/fodselsnummer/***", href)
    }

    @Test
    fun `safeHref masks fallback when record is null`() {
        val href = sanitizer().safeHref(
            null,
            "https://host/utdanning/elev/person/fodselsnummer/12345",
        )
        assertEquals("https://host/utdanning/elev/person/fodselsnummer/***", href)
    }

    @Test
    fun `mask leaves non-PII href unchanged`() {
        val href = sanitizer().mask("https://host/utdanning/elev/elev/systemid/abc")
        assertEquals("https://host/utdanning/elev/elev/systemid/abc", href)
    }

    @Test
    fun `mask replaces PII identifier value with asterisks`() {
        val href = sanitizer().mask("https://host/utdanning/elev/person/feidenavn/john.doe")
        assertEquals("https://host/utdanning/elev/person/feidenavn/***", href)
    }

    @Test
    fun `mask leaves malformed (non-matching) href unchanged`() {
        val href = sanitizer().mask("not-a-real-url")
        assertEquals("not-a-real-url", href)
    }

    @Test
    fun `custom PII identifier list is honored`() {
        val s = sanitizer(piiTypes = listOf("kontaktinformasjon"))
        val href = s.mask("https://host/personal/kontakt/person/kontaktinformasjon/john@example.com")
        assertEquals("https://host/personal/kontakt/person/kontaktinformasjon/***", href)
    }

    private fun sanitizer(piiTypes: List<String> = listOf("fodselsnummer", "feidenavn")) =
        HrefSanitizer(LinkWalkerConfig(piiIdentifiers = piiTypes))

    private fun recordOf(vararg canonicalKeys: String) = MinimalRecord(
        component = "test",
        resourceName = "test",
        canonicalKeys = canonicalKeys.toList(),
        outboundRefs = emptyList(),
        malformedHrefs = emptyList(),
    )
}

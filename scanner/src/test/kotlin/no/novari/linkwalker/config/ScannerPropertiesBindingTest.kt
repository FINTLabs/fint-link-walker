package no.novari.linkwalker.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

class ScannerPropertiesBindingTest {

    @Test
    fun `binds deployed config`() {
        val props = bind(
            "fint.link-walker.scanner.org-id" to "fintlabs_no",
            "fint.link-walker.scanner.base-url" to "https://beta.felleskomponent.no",
            "fint.link-walker.scanner.components[0]" to "utdanning_elev",
        )

        assertEquals("fintlabs_no", props.orgId)
        assertEquals("https://beta.felleskomponent.no", props.baseUrl)
        assertEquals(listOf("utdanning_elev"), props.components)
    }

    @Test
    fun `binds with only org-id, applying all defaults`() {
        val props = bind("fint.link-walker.scanner.org-id" to "fintlabs_no")

        assertEquals("https://api.felleskomponent.no", props.baseUrl)
        assertEquals(21, props.components.size)
    }

    @Test
    fun `binds service routing`() {
        val props = bind(
            "fint.link-walker.scanner.org-id" to "mrfylke_no",
            "fint.link-walker.scanner.service-routing.enabled" to "true",
            "fint.link-walker.scanner.service-routing.client-api-domains[0]" to "utdanning",
            "fint.link-walker.scanner.service-routing.client-api-domains[1]" to "administrasjon",
        )

        assertEquals(true, props.serviceRouting.enabled)
        assertEquals(listOf("utdanning", "administrasjon"), props.serviceRouting.clientApiDomains)
        assertEquals("fint-core-client-api", props.serviceRouting.clientApiService)
        assertEquals(null, props.serviceRouting.namespace)
    }

    private fun bind(vararg entries: Pair<String, String>): ScannerProperties =
        Binder(MapConfigurationPropertySource(mapOf(*entries)))
            .bind("fint.link-walker.scanner", ScannerProperties::class.java)
            .get()
}

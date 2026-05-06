package no.novari.linkwalker.scanner

import com.ninjasquad.springmockk.MockkBean
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(properties = ["link-walker.org-id=test"])
class ScannerContextSmokeTest {

    @MockkBean(relaxed = true)
    private lateinit var scanRunner: ScanRunner

    @Test
    fun `context loads with all beans wired`() = Unit
}

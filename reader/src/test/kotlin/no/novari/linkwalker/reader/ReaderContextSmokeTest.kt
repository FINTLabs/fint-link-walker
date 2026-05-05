package no.novari.linkwalker.reader

import com.ninjasquad.springmockk.MockkBean
import no.novari.linkwalker.report.ReportStore
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ReaderContextSmokeTest {

    @MockkBean(relaxed = true)
    private lateinit var reportStore: ReportStore

    @Test
    fun `context loads with all beans wired`() = Unit
}

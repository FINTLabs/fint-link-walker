package no.novari.linkwalker.reader

import com.ninjasquad.springmockk.MockkBean
import no.novari.linkwalker.report.ReportStore
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(PostgresTestConfig::class)
class ReaderContextSmokeTest {

    @MockkBean(relaxed = true)
    private lateinit var reportStore: ReportStore

    @Test
    fun `context loads with all beans wired`() = Unit
}

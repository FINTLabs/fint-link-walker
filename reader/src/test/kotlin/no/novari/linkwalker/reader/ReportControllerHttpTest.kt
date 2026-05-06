package no.novari.linkwalker.reader

import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import no.novari.linkwalker.report.LatestReportRows
import no.novari.linkwalker.report.LatestReportSummary
import no.novari.linkwalker.report.ReportRow
import no.novari.linkwalker.report.ReportStore
import no.novari.linkwalker.report.ScanSummary
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import java.time.Instant

/**
 * Hits the actual HTTP stack — Quarkus routing, JAX-RS resource discovery,
 * Jackson 2 JSON serialization, validation. Complements the in-process
 * [ReportControllerTest] which exercises only the controller method bodies.
 */
@QuarkusTest
class ReportControllerHttpTest {

    @InjectMock
    lateinit var reportStore: ReportStore

    @Test
    fun `GET summary returns 200 and JSON body`() {
        `when`(reportStore.getSummary("afk_no")).thenReturn(
            summaryDoc("afk_no", integrity = 99.5),
        )

        given()
            .`when`().get("/report/afk_no/summary")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("orgId", equalTo("afk_no"))
            .body("summary.integrityPercent", equalTo(99.5f))
    }

    @Test
    fun `GET summary returns 404 when missing`() {
        `when`(reportStore.getSummary("missing")).thenReturn(null)

        given()
            .`when`().get("/report/missing/summary")
            .then()
            .statusCode(404)
    }

    @Test
    fun `GET summary returns 400 on invalid orgId pattern`() {
        // uppercase + dash should fail the @Pattern validation
        given()
            .`when`().get("/report/AFK-NO/summary")
            .then()
            .statusCode(400)
    }

    @Test
    fun `GET rows returns 200 with paginated body`() {
        `when`(reportStore.getRows("afk_no")).thenReturn(rowsDoc("afk_no", count = 250))

        given()
            .queryParam("page", 0)
            .queryParam("size", 100)
            .`when`().get("/report/afk_no/rows")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("totalRows", equalTo(250))
            .body("totalPages", equalTo(3))
            .body("page", equalTo(0))
            .body("size", equalTo(100))
            .body("rows.size()", equalTo(100))
    }

    @Test
    fun `GET rows applies component filter via query param`() {
        `when`(reportStore.getRows("afk_no")).thenReturn(
            LatestReportRows(
                scanCompletedAt = Instant.parse("2026-01-01T00:00:00Z"),
                orgId = "afk_no",
                rows = listOf(
                    row("afk_no", "utdanning_elev", "elev", "missing-resource"),
                    row("afk_no", "utdanning_vurdering", "elevvurdering", "missing-resource"),
                    row("afk_no", "utdanning_elev", "person", "unknown-link"),
                ),
            )
        )

        given()
            .queryParam("component", "utdanning_elev")
            .`when`().get("/report/afk_no/rows")
            .then()
            .statusCode(200)
            .body("totalRows", equalTo(2))
            .body("rows.component", equalTo(listOf("utdanning_elev", "utdanning_elev")))
    }

    @Test
    fun `GET rows returns 404 when no rows blob exists`() {
        `when`(reportStore.getRows("missing")).thenReturn(null)

        given()
            .`when`().get("/report/missing/rows")
            .then()
            .statusCode(404)
    }

    @Test
    fun `liveness probe returns 200`() {
        given()
            .`when`().get("/actuator/health/liveness")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
    }

    @Test
    fun `prometheus endpoint exposes metrics`() {
        given()
            .`when`().get("/actuator/prometheus")
            .then()
            .statusCode(200)
            .body(containsString("# TYPE"))
    }

    private fun summaryDoc(orgId: String, integrity: Double) = LatestReportSummary(
        scanCompletedAt = Instant.parse("2026-01-01T00:00:00Z"),
        orgId = orgId,
        components = listOf("comp_x"),
        summary = ScanSummary(
            totalRecords = 0,
            totalRefs = 0,
            brokenLinkCount = 0,
            integrityPercent = integrity,
            byProblemType = emptyMap(),
            components = emptyList(),
        ),
    )

    private fun rowsDoc(orgId: String, count: Int) = LatestReportRows(
        scanCompletedAt = Instant.parse("2026-01-01T00:00:00Z"),
        orgId = orgId,
        rows = (1..count).map { row(orgId, "comp_x", "res", "missing-resource", suffix = it.toString()) },
    )

    private fun row(orgId: String, component: String, resource: String, problemType: String, suffix: String = "x") =
        ReportRow(
            orgId = orgId,
            component = component,
            resource = resource,
            problemType = problemType,
            sourceSelf = "https://host/$component/$resource/systemid/$suffix",
            targetHref = "https://host/$component/$resource/systemid/$suffix-target",
        )
}

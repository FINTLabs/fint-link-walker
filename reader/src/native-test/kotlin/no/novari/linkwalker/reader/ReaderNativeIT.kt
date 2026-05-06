package no.novari.linkwalker.reader

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

/**
 * Runs against the *packaged* binary — JVM jar via :testIntegration,
 * native image via :testNative. No mocks: file-storage backend starts
 * empty, so /summary/{orgId} returns 404 for any input, and the actuator
 * endpoints are always live.
 */
@QuarkusIntegrationTest
class ReaderNativeIT {

    @Test
    fun `liveness probe returns 200 on packaged binary`() {
        given()
            .`when`().get("/actuator/health/liveness")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
    }

    @Test
    fun `readiness probe returns 200`() {
        given()
            .`when`().get("/actuator/health/readiness")
            .then()
            .statusCode(200)
    }

    @Test
    fun `prometheus endpoint exposes metrics`() {
        given()
            .`when`().get("/actuator/prometheus")
            .then()
            .statusCode(200)
            .body(containsString("# TYPE"))
    }

    @Test
    fun `missing summary returns 404`() {
        given()
            .`when`().get("/report/nonexistent_org/summary")
            .then()
            .statusCode(404)
    }

    @Test
    fun `missing rows returns 404`() {
        given()
            .`when`().get("/report/nonexistent_org/rows")
            .then()
            .statusCode(404)
    }

    @Test
    fun `invalid orgId pattern returns 400`() {
        given()
            .`when`().get("/report/AFK-NO/summary")
            .then()
            .statusCode(400)
    }

    @Test
    fun `openapi spec is exposed`() {
        given()
            .`when`().get("/q/openapi")
            .then()
            .statusCode(200)
            .body(containsString("FINT Link Walker API"))
    }
}

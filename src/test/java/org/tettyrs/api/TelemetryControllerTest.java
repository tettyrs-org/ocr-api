package org.tettyrs.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestSecurity(user = "user123", roles = "USER")
@DisplayName("TelemetryController Integration Tests")
class TelemetryControllerTest {

    private UUID testDocumentId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    @DisplayName("Should accept telemetry timings batch")
    void testRecordTimings() {
        String payload = "{\"timings\": [{\"document_id\": \"" + testDocumentId +
                "\", \"component\": \"ocr\", \"duration_ms\": 1000, \"status\": \"SUCCESS\"}]}";

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/telemetry/timings")
                .then()
                .statusCode(202);
    }

    @Test
    @DisplayName("Should reject empty timings")
    void testEmptyTimings() {
        String payload = "{\"timings\": []}";

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/telemetry/timings")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("Should reject null timings")
    void testNullTimings() {
        String payload = "{\"timings\": null}";

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/telemetry/timings")
                .then()
                .statusCode(400);
    }
}

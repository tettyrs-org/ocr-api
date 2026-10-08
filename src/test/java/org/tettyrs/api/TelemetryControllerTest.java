package org.tettyrs.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.tettyrs.service.EnhancedJwtValidator;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@DisplayName("TelemetryController Integration Tests")
class TelemetryControllerTest {

    @Inject
    EnhancedJwtValidator jwtValidator;

    private String userToken;
    private UUID testDocumentId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setup() {
        userToken = "Bearer " + jwtValidator.generateToken(
                "user123",
                "user@example.com",
                Arrays.asList("USER")
        );
    }

    @Test
    @DisplayName("Should accept telemetry timings batch")
    void testRecordTimings() {
        String payload = "{\"timings\": [{\"document_id\": " + testDocumentId +
                ", \"component\": \"ocr\", \"duration_ms\": 1000, \"status\": \"SUCCESS\"}]}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", userToken)
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
                .header("Authorization", userToken)
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
                .header("Authorization", userToken)
                .body(payload)
                .when()
                .post("/api/v1/telemetry/timings")
                .then()
                .statusCode(400);
    }
}

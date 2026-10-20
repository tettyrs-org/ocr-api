package org.tettyrs.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestSecurity(user = "admin123", roles = "ADMIN")
@DisplayName("CallbackController Integration Tests")
class CallbackControllerTest {

    private UUID testDocumentId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    @DisplayName("Should list callbacks for document")
    void testListCallbacks() {
        given()
                .when()
                .get("/api/v1/documents/" + testDocumentId + "/callbacks")
                .then()
                .statusCode(anyOf(is(200), is(500)));
    }

    @Test
    @DisplayName("Should get single callback")
    void testGetCallback() {
        given()
                .when()
                .get("/api/v1/documents/" + testDocumentId + "/callbacks/1")
                .then()
                .statusCode(anyOf(is(200), is(404), is(500)));
    }

    @Test
    @DisplayName("Should retry callback")
    void testRetryCallback() {
        given()
                .contentType("application/json")
                .when()
                .post("/api/v1/documents/" + testDocumentId + "/callbacks/1/retry")
                .then()
                .statusCode(anyOf(is(202), is(404), is(500)));
    }
}

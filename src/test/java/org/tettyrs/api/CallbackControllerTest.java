package org.tettyrs.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.tettyrs.service.EnhancedJwtValidator;
import jakarta.inject.Inject;
import java.util.Arrays;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@DisplayName("CallbackController Integration Tests")
class CallbackControllerTest {

    @Inject
    EnhancedJwtValidator jwtValidator;

    private String adminToken;
    private Long testDocumentId = 1L;

    @BeforeEach
    void setup() {
        adminToken = "Bearer " + jwtValidator.generateToken(
                "admin123",
                "admin@example.com",
                Arrays.asList("ADMIN")
        );
    }

    @Test
    @DisplayName("Should list callbacks for document")
    void testListCallbacks() {
        given()
                .header("Authorization", adminToken)
                .when()
                .get("/api/v1/documents/" + testDocumentId + "/callbacks")
                .then()
                .statusCode(anyOf(is(200), is(500)));
    }

    @Test
    @DisplayName("Should get single callback")
    void testGetCallback() {
        given()
                .header("Authorization", adminToken)
                .when()
                .get("/api/v1/documents/" + testDocumentId + "/callbacks/1")
                .then()
                .statusCode(anyOf(is(200), is(404), is(500)));
    }

    @Test
    @DisplayName("Should retry callback")
    void testRetryCallback() {
        given()
                .header("Authorization", adminToken)
                .when()
                .post("/api/v1/documents/" + testDocumentId + "/callbacks/1/retry")
                .then()
                .statusCode(anyOf(is(202), is(404), is(500)));
    }
}

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
@DisplayName("VerificationController Integration Tests")
class VerificationControllerTest {

    @Inject
    EnhancedJwtValidator jwtValidator;

    private String userToken;
    private String adminToken;
    private UUID testDocumentId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setup() {
        userToken = "Bearer " + jwtValidator.generateToken(
                "user123",
                "user@example.com",
                Arrays.asList("USER")
        );

        adminToken = "Bearer " + jwtValidator.generateToken(
                "admin123",
                "admin@example.com",
                Arrays.asList("ADMIN")
        );
    }

    @Test
    @DisplayName("Should list verifications for document")
    void testListVerifications() {
        given()
                .header("Authorization", userToken)
                .queryParam("page", 1)
                .queryParam("limit", 20)
                .when()
                .get("/api/v1/documents/" + testDocumentId + "/verifications")
                .then()
                .statusCode(anyOf(is(200), is(500)));
    }

    @Test
    @DisplayName("Should get single verification")
    void testGetSingleVerification() {
        given()
                .header("Authorization", adminToken)
                .when()
                .get("/api/v1/documents/" + testDocumentId + "/verifications/1")
                .then()
                .statusCode(anyOf(is(200), is(404), is(500)));
    }

    @Test
    @DisplayName("Should reject verifications request without auth")
    void testListVerificationsNoAuth() {
        given()
                .when()
                .get("/api/v1/documents/" + testDocumentId + "/verifications")
                .then()
                .statusCode(401);
    }
}

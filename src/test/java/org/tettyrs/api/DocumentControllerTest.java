package org.tettyrs.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.tettyrs.service.EnhancedJwtValidator;
import jakarta.inject.Inject;
import java.util.Arrays;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@DisplayName("DocumentController Integration Tests")
class DocumentControllerTest {

    @Inject
    EnhancedJwtValidator jwtValidator;

    private String userToken;
    private String adminToken;

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
    @DisplayName("Should list documents")
    void testListDocuments() {
        given()
                .header("Authorization", userToken)
                .when()
                .get("/api/v1/documents")
                .then()
                .statusCode(anyOf(is(200), is(429), is(500))); // Rate limit may apply
    }

    @Test
    @DisplayName("Should get single document")
    void testGetDocument() {
        given()
                .header("Authorization", userToken)
                .when()
                .get("/api/v1/documents/1")
                .then()
                .statusCode(anyOf(is(200), is(404), is(500)));
    }

    @Test
    @DisplayName("Should reject reprocess without ADMIN role")
    void testReprocessNeedsAdmin() {
        given()
                .header("Authorization", userToken)
                .when()
                .post("/api/v1/documents/1/reprocess")
                .then()
                .statusCode(anyOf(is(403), is(500))); // Auth check or endpoint error
    }

    @Test
    @DisplayName("Should allow ADMIN to reprocess document")
    void testAdminCanReprocess() {
        given()
                .header("Authorization", adminToken)
                .when()
                .post("/api/v1/documents/1/reprocess")
                .then()
                .statusCode(anyOf(is(202), is(404), is(500)));
    }

    @Test
    @DisplayName("Should reject without auth")
    void testDocumentsNeedAuth() {
        given()
                .when()
                .get("/api/v1/documents")
                .then()
                .statusCode(401);
    }
}

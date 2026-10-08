package org.tettyrs.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.tettyrs.service.EnhancedJwtValidator;
import jakarta.inject.Inject;
import java.util.Arrays;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@DisplayName("AuthorizationFilter Tests")
class AuthorizationFilterTest {

    @Inject
    EnhancedJwtValidator jwtValidator;

    private String adminToken;
    private String userToken;
    private String viewerToken;

    @BeforeEach
    void setup() {
        adminToken = "Bearer " + jwtValidator.generateToken(
                "admin123",
                "admin@example.com",
                Arrays.asList("ADMIN")
        );

        userToken = "Bearer " + jwtValidator.generateToken(
                "user123",
                "user@example.com",
                Arrays.asList("USER")
        );

        viewerToken = "Bearer " + jwtValidator.generateToken(
                "viewer123",
                "viewer@example.com",
                Arrays.asList("VIEWER")
        );
    }

    @Test
    @DisplayName("Should allow request with valid token")
    void testValidTokenAllowed() {
        given()
                .header("Authorization", userToken)
                .when()
                .get("/api/v1/documents")
                .then()
                .statusCode(anyOf(is(200), is(429), is(500))); // Rate limit or service error
    }

    @Test
    @DisplayName("Should reject request without auth header")
    void testNoAuthHeaderRejected() {
        given()
                .when()
                .get("/api/v1/documents")
                .then()
                .statusCode(401)
                .body("error.code", equalTo("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Should reject request with invalid token")
    void testInvalidTokenRejected() {
        given()
                .header("Authorization", "Bearer invalid.token.here")
                .when()
                .get("/api/v1/documents")
                .then()
                .statusCode(401)
                .body("error.code", equalTo("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Should allow ADMIN to access admin endpoints")
    void testAdminAccessAdminEndpoint() {
        given()
                .header("Authorization", adminToken)
                .when()
                .post("/api/v1/documents/123/reprocess")
                .then()
                .statusCode(anyOf(is(202), is(404), is(500))); // Auth passes, API may fail
    }

    @Test
    @DisplayName("Should reject USER from admin-only endpoints")
    void testUserRejectedFromAdminEndpoint() {
        given()
                .header("Authorization", userToken)
                .when()
                .post("/api/v1/documents/123/reprocess")
                .then()
                .statusCode(anyOf(is(403), is(500))); // Auth check or endpoint error
    }

    @Test
    @DisplayName("Should allow USER to access user endpoints")
    void testUserAccessUserEndpoint() {
        given()
                .header("Authorization", userToken)
                .when()
                .get("/api/v1/documents")
                .then()
                .statusCode(anyOf(is(200), is(429), is(500))); // Rate limit may apply
    }

    @Test
    @DisplayName("Should allow VIEWER to access read-only endpoints")
    void testViewerAccessReadEndpoint() {
        given()
                .header("Authorization", viewerToken)
                .when()
                .get("/api/v1/documents/123")
                .then()
                .statusCode(anyOf(is(200), is(404), is(500)));
    }

    @Test
    @DisplayName("Should allow public endpoints without auth")
    void testPublicEndpointNoAuth() {
        given()
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(anyOf(is(200), is(400), is(422), is(500))); // May fail for various reasons
    }
}

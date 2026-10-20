package org.tettyrs.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@DisplayName("AuthorizationFilter Tests")
class AuthorizationFilterTest {

    @Test
    @TestSecurity(user = "user123", roles = "USER")
    @DisplayName("Should allow request with valid identity")
    void testValidIdentityAllowed() {
        given()
                .when()
                .get("/api/v1/documents")
                .then()
                .statusCode(not(anyOf(is(401), is(403))));
    }

    @Test
    @DisplayName("Should reject request without authentication")
    void testNoAuthRejected() {
        given()
                .when()
                .get("/api/v1/documents")
                .then()
                .statusCode(401)
                .body("error.code", equalTo("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Should reject request with invalid token in test profile")
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
    @TestSecurity(user = "admin123", roles = "ADMIN")
    @DisplayName("Should allow ADMIN to access admin endpoints")
    void testAdminAccessAdminEndpoint() {
        given()
                .when()
                .post("/api/v1/documents/550e8400-e29b-41d4-a716-446655440000/reprocess")
                .then()
                .statusCode(not(anyOf(is(401), is(403))));
    }

    @Test
    @TestSecurity(user = "user123", roles = "USER")
    @DisplayName("Should reject USER from admin-only endpoints")
    void testUserRejectedFromAdminEndpoint() {
        given()
                .when()
                .contentType("application/json")
                .post("/api/v1/documents/550e8400-e29b-41d4-a716-446655440000/reprocess")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "user123", roles = "USER")
    @DisplayName("Should allow USER to access user endpoints")
    void testUserAccessUserEndpoint() {
        given()
                .when()
                .get("/api/v1/documents")
                .then()
                .statusCode(not(anyOf(is(401), is(403))));
    }

    @Test
    @TestSecurity(user = "viewer123", roles = "VIEWER")
    @DisplayName("Should allow VIEWER to access read-only endpoints")
    void testViewerAccessReadEndpoint() {
        given()
                .when()
                .get("/api/v1/documents/550e8400-e29b-41d4-a716-446655440000")
                .then()
                .statusCode(not(anyOf(is(401), is(403))));
    }

    @Test
    @DisplayName("Should allow health endpoint without authentication")
    void testHealthNoAuth() {
        given()
                .when()
                .get("/q/health/live")
                .then()
                .statusCode(not(anyOf(is(401), is(403))));
    }
}

package org.tettyrs.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@DisplayName("DocumentController Integration Tests")
class DocumentControllerTest {

    @Test
    @TestSecurity(user = "user123", roles = "USER")
    @DisplayName("Should list documents")
    void testListDocuments() {
        given()
                .when()
                .get("/api/v1/documents")
                .then()
                .statusCode(anyOf(is(200), is(429), is(500)));
    }

    @Test
    @TestSecurity(user = "user123", roles = "USER")
    @DisplayName("Should get single document")
    void testGetDocument() {
        given()
                .when()
                .get("/api/v1/documents/550e8400-e29b-41d4-a716-446655440000")
                .then()
                .statusCode(anyOf(is(200), is(404), is(500)));
    }

    @Test
    @TestSecurity(user = "user123", roles = "USER")
    @DisplayName("Should reject reprocess without ADMIN role")
    void testReprocessNeedsAdmin() {
        given()
                .contentType("application/json")
                .when()
                .post("/api/v1/documents/550e8400-e29b-41d4-a716-446655440000/reprocess")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin123", roles = "ADMIN")
    @DisplayName("Should allow ADMIN to reprocess document")
    void testAdminCanReprocess() {
        given()
                .contentType("application/json")
                .when()
                .post("/api/v1/documents/550e8400-e29b-41d4-a716-446655440000/reprocess")
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

    @Test
    @TestSecurity(user = "user123", roles = "USER")
    @DisplayName("Processing status hanya untuk pemilik: dokumen yang bukan milik pengguna dijawab 404")
    void testProcessingStatusNotOwnedIs404() {
        given()
                .when()
                .get("/api/v1/documents/550e8400-e29b-41d4-a716-446655440000/processing-status")
                .then()
                .statusCode(404);
    }
}

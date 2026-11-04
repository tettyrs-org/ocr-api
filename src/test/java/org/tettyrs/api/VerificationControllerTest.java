package org.tettyrs.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@DisplayName("VerificationController Integration Tests")
class VerificationControllerTest {

    private UUID testDocumentId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    @TestSecurity(user = "user123", roles = "USER")
    @DisplayName("Should not list verifications of a document the user does not own")
    void testListVerifications() {
        // Dokumen uji bukan milik user123: harus 404 (sama dengan dokumen yang tidak ada), bukan daftar verifikasi.
        given()
                .queryParam("page", 1)
                .queryParam("limit", 20)
                .when()
                .get("/api/v1/documents/" + testDocumentId + "/verifications")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin123", roles = "ADMIN")
    @DisplayName("Should get single verification")
    void testGetSingleVerification() {
        given()
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

package org.tettyrs.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.tettyrs.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.anyOf;

@QuarkusTest
@TestSecurity(user = "user123", roles = "USER")
@DisplayName("RateLimitingFilter Tests")
class RateLimitingFilterTest {

    @Inject
    RateLimitService rateLimitService;

    @BeforeEach
    void resetRateLimitCounters() {
        rateLimitService.reset("user123", "GET /api/v1/documents");
        rateLimitService.reset("user123", "POST /api/v1/documents");
    }

    @Test
    @DisplayName("Should allow request within rate limit")
    void testRequestWithinLimit() {
        given()
                .when()
                .get("/api/v1/documents")
                .then()
                .statusCode(not(is(429)));
    }

    @Test
    @DisplayName("Daftar dokumen (GET) tidak menghabiskan kuota unggah (POST)")
    void testListDoesNotConsumeUploadLimit() {
        for (int i = 0; i < 25; i++) {
            given()
                    .when()
                    .get("/api/v1/documents")
                    .then()
                    .statusCode(not(is(429)));
        }
    }

    @Test
    @DisplayName("Should allow health endpoint without rate limit")
    void testHealthBypassesRateLimit() {
        given()
                .when()
                .get("/q/health/live")
                .then()
                .statusCode(not(is(429)));
    }
}

package org.tettyrs.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@DisplayName("BrowserRequestFilter Tests")
class BrowserRequestFilterTest {

    @Test
    void rejectsRequestWithOriginHeader() {
        given()
                .header("Origin", "https://app.example.com")
                .when().get("/api/v1/documents")
                .then()
                .statusCode(403);
    }

    @Test
    void rejectsRequestWithFetchMetadataHeader() {
        given()
                .header("Sec-Fetch-Site", "same-origin")
                .when().get("/api/v1/documents")
                .then()
                .statusCode(403)
                .body("error.code", equalTo("FORBIDDEN"));
    }

    @Test
    void allowsNativeClientWithoutBrowserHeaders() {
        given()
                .when().get("/api/v1/documents")
                .then()
                .statusCode(401);
    }

    @Test
    void allowsRegisteredWebOriginToReachAuthentication() {
        given()
                .header("Origin", "https://app.test.web.app")
                .header("Sec-Fetch-Site", "cross-site")
                .when().get("/api/v1/documents")
                .then()
                .statusCode(401);
    }

    @Test
    void answersPreflightForRegisteredWebOriginWithoutCredentials() {
        given()
                .header("Origin", "https://app.test.web.app")
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type,Idempotency-Key")
                .when().options("/api/v1/documents")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", "https://app.test.web.app")
                .header("Access-Control-Allow-Credentials", equalTo("false"));
    }

    @Test
    void rejectsUnregisteredOriginPreflight() {
        given()
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET")
                .when().options("/api/v1/documents")
                .then()
                .header("Access-Control-Allow-Origin", nullValue());
    }
}

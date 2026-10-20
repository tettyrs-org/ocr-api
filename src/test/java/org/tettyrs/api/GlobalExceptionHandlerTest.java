package org.tettyrs.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    @Test
    @TestSecurity(user = "user123", roles = "USER")
    @DisplayName("Unknown route returns 404 with NOT_FOUND code, not 500")
    void unknownRouteReturns404() {
        given()
                .when()
                .get("/api/v1/tidak-ada")
                .then()
                .statusCode(404)
                .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = "user123", roles = "USER")
    @DisplayName("Wrong Content-Type returns 415 instead of 500")
    void wrongContentTypeReturns415() {
        given()
                .contentType("text/plain")
                .body("x")
                .when()
                .post("/api/v1/documents/550e8400-e29b-41d4-a716-446655440000/reprocess")
                .then()
                .statusCode(415);
    }
}

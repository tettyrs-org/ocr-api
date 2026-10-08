package org.tettyrs.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@DisplayName("EnhancedJwtValidator Tests")
public class EnhancedValidatorTest {

    @Inject
    EnhancedJwtValidator jwtValidator;

    private String testUserId;
    private String testEmail;
    private String validToken;

    @BeforeEach
    void setup() {
        testUserId = "user123";
        testEmail = "user@example.com";
        validToken = jwtValidator.generateToken(
                testUserId,
                testEmail,
                Arrays.asList("USER", "ADMIN")
        );
    }

    @Test
    @DisplayName("Should generate valid jwt token")
    void testGenerateToken(){
        String token = jwtValidator.generateToken(testUserId, testEmail, Arrays.asList("USER"));
        assertNotNull(token);
        assertTrue(token.contains("."));
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    @DisplayName("Should validate and extract token succesfully")
    void testValidateAndExtractToken(){
        EnhancedJwtValidator.UserInfo userInfo = jwtValidator.validateAndExtract("Bearer "+validToken);
        assertNotNull(userInfo);
        assertEquals(testUserId, userInfo.userId);
        assertEquals(testEmail, userInfo.email);
        assertTrue(userInfo.hasRole("USER"));
        assertTrue(userInfo.hasRole("ADMIN"));
    }

    @Test
    @DisplayName("Should return null for invalid token format")
    void testInvalidTokenFormat(){
        EnhancedJwtValidator.UserInfo userInfo = jwtValidator.validateAndExtract("InvalidToken");
        assertNull(userInfo);
    }

    @Test
    @DisplayName("Should return null for missing bearer prefix")
    void testMissingBearerPrefix(){
        EnhancedJwtValidator.UserInfo userInfo = jwtValidator.validateAndExtract(validToken);
        assertNull(userInfo);
    }

    @Test
    @DisplayName("Should validate with single role")
    void testNullAuthHeader(){
        boolean hasUserRole = jwtValidator.validateWithRole("Bearer "+validToken, "USER");
        assertTrue(hasUserRole);
    }

    @Test
    @DisplayName("Should fail validation with missing role")
    void testValidateWithRoleFail() {
        boolean hasViewerRole = jwtValidator.validateWithRole("Bearer " + validToken, "VIEWER");
        assertFalse(hasViewerRole);
    }

    @Test
    @DisplayName("Should validate with multiple roles - any match")
    void testValidateWithMultipleRoles() {
        boolean hasRole = jwtValidator.validateWithRole("Bearer " + validToken, "VIEWER", "ADMIN", "GUEST");
        assertTrue(hasRole);
    }

    @Test
    @DisplayName("Should extract all roles from token")
    void testExtractAllRoles() {
        String token = jwtValidator.generateToken(
                testUserId,
                testEmail,
                Arrays.asList("ADMIN", "USER", "VIEWER")
        );

        EnhancedJwtValidator.UserInfo userInfo = jwtValidator.validateAndExtract("Bearer " + token);
        assertNotNull(userInfo);
        assertTrue(userInfo.hasAnyRole("ADMIN", "USER", "VIEWER"));
    }

    @Test
    @DisplayName("Should have expiration date")
    void testTokenExpiration() {
        EnhancedJwtValidator.UserInfo userInfo = jwtValidator.validateAndExtract("Bearer " + validToken);
        assertNotNull(userInfo.expiresAt);
    }




}

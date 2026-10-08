package org.tettyrs.config;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.tettyrs.service.EnhancedJwtValidator;

import java.util.Arrays;

@QuarkusTest
public class TestConfig {

    @Inject
    public EnhancedJwtValidator jwtValidator;

    public String generateTestToken(String userId, String email, String... roles){
        return jwtValidator.generateToken(userId, email, Arrays.asList(roles));
    }

    public String generateTestTokenWithRole(String userId, String role){
        return jwtValidator.generateToken(userId, userId + "@example.com", Arrays.asList(role));
    }
}

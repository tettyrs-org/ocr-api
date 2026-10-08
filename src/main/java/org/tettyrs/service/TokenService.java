package org.tettyrs.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Arrays;

@ApplicationScoped
public class TokenService {

    @Inject
    EnhancedJwtValidator jwtValidator;

    public String generateToken(String userId, String email) {
        return jwtValidator.generateToken(userId, email, Arrays.asList("USER"));
    }
}

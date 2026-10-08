package org.tettyrs.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.Base64;

@ApplicationScoped
public class EnhancedJwtValidator {

    @ConfigProperty(name = "jwt.private-key-location")
    String privateKeyLocation;

    @ConfigProperty(name = "jwt.public-key-location")
    String publicKeyLocation;

    @ConfigProperty(name = "jwt.expiration", defaultValue = "86400")  // 24 hours
    Long jwtExpirationSeconds;

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";
    public static final String ROLE_VIEWER = "VIEWER";

    private PrivateKey signingKey;
    private PublicKey verifyingKey;

    private PrivateKey getSigningKey() {
        if (signingKey == null) {
            signingKey = loadPrivateKey(privateKeyLocation);
        }
        return signingKey;
    }

    private PublicKey getVerifyingKey() {
        if (verifyingKey == null) {
            verifyingKey = loadPublicKey(publicKeyLocation);
        }
        return verifyingKey;
    }

    private PrivateKey loadPrivateKey(String keyLocation) {
        try {
            String keyContent = loadKeyFromClasspath(keyLocation);
            keyContent = keyContent
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] decodedKey = Base64.getDecoder().decode(keyContent);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            Log.error("Failed to load private key", e);
            throw new RuntimeException("Cannot load private key", e);
        }
    }

    private PublicKey loadPublicKey(String keyLocation) {
        try {
            String keyContent = loadKeyFromClasspath(keyLocation);
            keyContent = keyContent
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                    .replace("-----END RSA PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] decodedKey = Base64.getDecoder().decode(keyContent);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        } catch (Exception e) {
            Log.error("Failed to load public key", e);
            throw new RuntimeException("Cannot load public key", e);
        }
    }

    private String loadKeyFromClasspath(String location) throws IOException {
        String path = location.replace("classpath:", "");
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (is == null) {
            throw new IOException("Key file not found: " + path);
        }
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Validate token + extract user info
     */
    public UserInfo validateAndExtract(String authHeader) {
        try {
            if (authHeader == null || authHeader.isEmpty()) {
                Log.warn("Missing authorization header");
                return null;
            }

            if (!authHeader.startsWith("Bearer ")) {
                Log.warn("Invalid authorization header format");
                return null;
            }

            String token = authHeader.substring(7);

            // Parse dan validate token
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getVerifyingKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // Extract user info dari claims
            UserInfo userInfo = new UserInfo();
            userInfo.userId = claims.getSubject();  // JWT standard: "sub" = userId
            userInfo.email = claims.get("email", String.class);

            // Extract roles dari claims
            @SuppressWarnings("unchecked")
            List<String> rolesList = claims.get("roles", List.class);
            userInfo.roles = new HashSet<>(rolesList != null ? rolesList : new ArrayList<>());

            // Check expiration (automatic di parseClaimsJws)
            userInfo.expiresAt = claims.getExpiration();

            Log.info("Token validated for user: " + userInfo.userId);
            return userInfo;

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            Log.warn("Token expired: " + e.getMessage());
            return null;
        } catch (io.jsonwebtoken.UnsupportedJwtException e) {
            Log.warn("Unsupported JWT: " + e.getMessage());
            return null;
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            Log.warn("Malformed JWT: " + e.getMessage());
            return null;
        } catch (io.jsonwebtoken.SignatureException e) {
            Log.warn("Invalid JWT signature: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.error("JWT validation error", e);
            return null;
        }
    }

    /**
     * Generate token (untuk login)
     */
    public String generateToken(String userId, String email, List<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (jwtExpirationSeconds * 1000));

        return Jwts.builder()
                .setSubject(userId)  // Standard: "sub"
                .claim("email", email)
                .claim("roles", roles)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.RS256)
                .compact();
    }

    /**
     * Validate dengan role check
     */
    public boolean validateWithRole(String authHeader, String... requiredRoles) {
        UserInfo userInfo = validateAndExtract(authHeader);
        if (userInfo == null) {
            return false;
        }

        for (String role : requiredRoles) {
            if (userInfo.roles.contains(role)) {
                return true;
            }
        }

        Log.warn("User " + userInfo.userId + " does not have required roles");
        return false;
    }

    /**
     * User info holder
     */
    public static class UserInfo {
        public String userId;
        public String email;
        public Set<String> roles;
        public Date expiresAt;

        public boolean hasRole(String role) {
            return roles != null && roles.contains(role);
        }

        public boolean hasAnyRole(String... roleNames) {
            if (roles == null) return false;
            return Arrays.stream(roleNames)
                    .anyMatch(roles::contains);
        }
    }
}

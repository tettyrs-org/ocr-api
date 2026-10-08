package org.tettyrs.api;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.service.EnhancedJwtValidator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Provider
public class AuthorizationFilter implements ContainerRequestFilter {

    @Inject
    EnhancedJwtValidator jwtValidator;

    private static final Map<String, Set<String>> ENDPOINT_ROLES = Map.ofEntries(
            Map.entry("/api/v1/documents", Set.of("USER", "ADMIN")),
            Map.entry("/api/v1/documents/{id}", Set.of("USER", "ADMIN", "VIEWER")),
            Map.entry("/api/v1/documents/{id}/reprocess", Set.of("ADMIN")),
            Map.entry("/api/v1/documents/{id}/verifications", Set.of("USER", "ADMIN", "VIEWER")),
            Map.entry("/api/v1/telemetry", Set.of("USER", "ADMIN", "VIEWER")),
            Map.entry("/api/v1/telemetry/timings", Set.of("USER", "ADMIN", "VIEWER")),
            Map.entry("/api/v1/callbacks", Set.of("ADMIN")),
            Map.entry("/api/audit", Set.of("ADMIN")),
            Map.entry("/api/auth/login", Set.of())  // Public
    );

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();

        if (isPublicEndpoint(path)) {
            return;
        }

        String authHeader = requestContext.getHeaderString("Authorization");
        EnhancedJwtValidator.UserInfo userInfo = jwtValidator.validateAndExtract(authHeader);

        if (userInfo == null) {
            Log.warn("Authentication failed: invalid or missing token for path: " + path);
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.UNAUTHENTICATED.code,
                    "Invalid or missing authentication token"
            );
            requestContext.abortWith(
                    Response.status(ErrorCode.UNAUTHENTICATED.httpStatus)
                            .entity(response)
                            .build()
            );
            return;
        }

        String normalizedPath = normalizeEndpoint(path);
        Set<String> requiredRoles = ENDPOINT_ROLES.getOrDefault(normalizedPath, Set.of("ADMIN"));

        boolean hasRequiredRole = userInfo.hasAnyRole(requiredRoles.toArray(new String[0]));

        if (!hasRequiredRole) {
            Log.warn("Authorization failed: user " + userInfo.userId +
                     " does not have required roles for path: " + path);
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.FORBIDDEN.code,
                    "Insufficient permissions to access this resource"
            );
            requestContext.abortWith(
                    Response.status(ErrorCode.FORBIDDEN.httpStatus)
                            .entity(response)
                            .build()
            );
            return;
        }

        requestContext.setProperty("user", userInfo);
        Log.info("Authorization successful for user: " + userInfo.userId +
                 ", roles: " + userInfo.roles);
    }

    private String normalizeEndpoint(String path) {
        return path
                .replaceAll("/\\d+(/|$)", "/{id}$1")
                .replaceAll("/[a-f0-9-]{36}(/|$)", "/{id}$1")
                .replaceAll("\\?.*$", "");
    }

    private boolean isPublicEndpoint(String path) {
        return path.contains("/health") ||
               path.contains("/metrics") ||
               path.contains("/auth/login");
    }
}

package org.tettyrs.api;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.security.CurrentUser;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Provider
public class AuthorizationFilter implements ContainerRequestFilter {

    @Inject
    CurrentUser currentUser;

    private static final Map<String, Set<String>> ENDPOINT_ROLES = Map.ofEntries(
            Map.entry("/api/v1/documents", Set.of("USER", "ADMIN")),
            Map.entry("/api/v1/documents/{id}", Set.of("USER", "ADMIN", "VIEWER")),
            Map.entry("/api/v1/documents/{id}/upload", Set.of("USER", "ADMIN")),
            Map.entry("/api/v1/documents/{id}/process", Set.of("USER", "ADMIN")),
            Map.entry("/api/v1/documents/{id}/processing-status", Set.of("USER", "ADMIN", "VIEWER")),
            Map.entry("/api/v1/documents/{id}/reprocess", Set.of("ADMIN")),
            Map.entry("/api/v1/documents/{id}/verifications", Set.of("USER", "ADMIN", "VIEWER")),
            Map.entry("/api/v1/documents/{id}/verification", Set.of("USER", "ADMIN")),
            Map.entry("/api/v1/documents/{id}/template", Set.of("USER", "ADMIN")),
            Map.entry("/api/v1/me", Set.of("USER", "ADMIN", "VIEWER")),
            Map.entry("/api/v1/me/profile", Set.of("USER", "ADMIN", "VIEWER")),
            Map.entry("/api/v1/admin/employees", Set.of("ADMIN")),
            Map.entry("/api/v1/admin/backfill-encryption", Set.of("ADMIN")),
            Map.entry("/api/v1/telemetry", Set.of("USER", "ADMIN", "VIEWER")),
            Map.entry("/api/v1/telemetry/timings", Set.of("USER", "ADMIN", "VIEWER")),
            Map.entry("/api/v1/callbacks", Set.of("ADMIN")),
            Map.entry("/api/audit", Set.of("ADMIN"))
    );

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();

        if (isPublicEndpoint(path)) {
            return;
        }

        if (!currentUser.isAuthenticated()) {
            Log.warn("Authentication failed: invalid or missing token for path: " + path);
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.UNAUTHENTICATED.code,
                    "Invalid or missing authentication token"
            );
            requestContext.abortWith(
                    Response.status(ErrorCode.UNAUTHENTICATED.httpStatus).entity(response).build()
            );
            return;
        }

        Set<String> requiredRoles = ENDPOINT_ROLES.getOrDefault(normalizeEndpoint(path), Set.of("ADMIN"));

        if (!currentUser.hasAnyRole(requiredRoles.toArray(new String[0]))) {
            Log.warn("Authorization failed: user " + currentUser.userId() +
                     " does not have required roles for path: " + path);
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.FORBIDDEN.code,
                    "Insufficient permissions to access this resource"
            );
            requestContext.abortWith(
                    Response.status(ErrorCode.FORBIDDEN.httpStatus).entity(response).build()
            );
        }
    }

    private String normalizeEndpoint(String path) {
        return path
                .replaceAll("/\\d+(/|$)", "/{id}$1")
                .replaceAll("/[a-f0-9-]{36}(/|$)", "/{id}$1")
                .replaceAll("\\?.*$", "");
    }

    private boolean isPublicEndpoint(String path) {
        // /internal/* dilindungi X-Internal-Token (InternalAuth), bukan token pengguna. ms-ocr tidak punya token pengguna.
        String normalized = path.startsWith("/") ? path : "/" + path;
        // /api/v1/invitations/* dilindungi token undangan di body (api-spec.md bagian 16).
        return normalized.startsWith("/internal/") ||
               normalized.startsWith("/api/v1/invitations/") ||
               path.contains("/health") ||
               path.contains("/metrics");
    }
}

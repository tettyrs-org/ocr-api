package org.tettyrs.api;

import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.enums.ErrorCode;

import java.io.IOException;
import java.util.List;

@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class BrowserRequestFilter implements ContainerRequestFilter {

    @ConfigProperty(name = "quarkus.http.cors.origins")
    List<String> allowedOrigins;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String origin = requestContext.getHeaderString("Origin");
        if (origin != null) {
            if (allowedOrigins.contains(origin)) {
                return;
            }
            reject(requestContext, "Origin tidak diizinkan");
            return;
        }
        if (requestContext.getHeaderString("Sec-Fetch-Site") != null) {
            reject(requestContext, "Permintaan dari browser tidak diizinkan");
        }
    }

    private void reject(ContainerRequestContext requestContext, String message) {
        Log.warn("Rejected browser-originated request to " + requestContext.getUriInfo().getPath());
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.FORBIDDEN.code, message);
        requestContext.abortWith(
                Response.status(ErrorCode.FORBIDDEN.httpStatus).entity(response).build()
        );
    }
}

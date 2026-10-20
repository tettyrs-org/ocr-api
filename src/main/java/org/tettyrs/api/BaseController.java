package org.tettyrs.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.security.CurrentUser;

public abstract class BaseController {

    @Inject
    protected CurrentUser currentUser;

    protected Response validateAuth(HttpHeaders headers) {
        if (!currentUser.isAuthenticated()) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.UNAUTHENTICATED.code,
                    "Missing or invalid authentication token"
            );
            return Response.status(ErrorCode.UNAUTHENTICATED.httpStatus)
                    .entity(response)
                    .build();
        }
        return null;
    }

    protected Response errorResponse(ErrorCode code, String message) {
        ApiResponse<Void> response = ApiResponse.error(code.code, message);
        return Response.status(code.httpStatus)
                .entity(response)
                .build();
    }
}

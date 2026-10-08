package org.tettyrs.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.service.EnhancedJwtValidator;

public abstract class BaseController {

    @Inject
    protected EnhancedJwtValidator jwtValidator;

    protected Response validateAuth(HttpHeaders headers) {
        EnhancedJwtValidator.UserInfo userInfo =
            jwtValidator.validateAndExtract(headers.getHeaderString("Authorization"));

        if (userInfo == null) {
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

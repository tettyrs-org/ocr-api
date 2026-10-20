package org.tettyrs.api;

import io.quarkus.logging.Log;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.enums.ErrorCode;

@Provider
public class GlobalExceptionHandler implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception e) {
        if (e instanceof WebApplicationException web) {
            return fromWebException(web);
        }

        if (e instanceof IllegalArgumentException) {
            Log.warn("Invalid argument: " + e.getMessage());
            return error(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }

        if (e instanceof SecurityException) {
            Log.warn("Security exception: " + e.getMessage());
            return error(ErrorCode.UNAUTHENTICATED, "Authentication failed");
        }

        Log.error("Unhandled exception", e);
        return error(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred");
    }

    private Response fromWebException(WebApplicationException web) {
        int status = web.getResponse() != null ? web.getResponse().getStatus() : 500;

        if (status >= 500) {
            Log.error("Server error", web);
            return error(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred");
        }

        Log.warn("Client error " + status + ": " + web.getMessage());
        ErrorCode code = status == 404 ? ErrorCode.NOT_FOUND : ErrorCode.VALIDATION_ERROR;
        ApiResponse<Void> body = ApiResponse.error(code.code, code.description);
        return Response.status(status).entity(body).build();
    }

    private Response error(ErrorCode code, String message) {
        ApiResponse<Void> body = ApiResponse.error(code.code, message);
        return Response.status(code.httpStatus).entity(body).build();
    }
}

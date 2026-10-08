package org.tettyrs.api;

import jakarta.resource.spi.SecurityException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import io.quarkus.logging.Log;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.enums.ErrorCode;

@Provider
public class GlobalExceptionHandler implements ExceptionMapper<Exception> {


    @Override
    public Response toResponse(Exception e) {
        Log.error("Unhandled exception", e);

        if (e instanceof IllegalArgumentException){
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.VALIDATION_ERROR.code,
                    e.getMessage());
            return Response
                    .status(ErrorCode.VALIDATION_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }

        if (e instanceof SecurityException) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.UNAUTHENTICATED.code,
                    "Authentication failed"
            );
            return Response
                    .status(ErrorCode.UNAUTHENTICATED.httpStatus)
                    .entity(response)
                    .build();
        }
        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.INTERNAL_ERROR.code,
                "An unexpected error occured"
        );
        return Response
                .status(ErrorCode.INTERNAL_ERROR.httpStatus)
                .entity(response)
                .build();

    }
}

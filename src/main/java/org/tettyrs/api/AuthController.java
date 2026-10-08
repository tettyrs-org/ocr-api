package org.tettyrs.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.LoginRequest;
import org.tettyrs.dto.TokenResponse;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.service.TokenService;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthController {

    @Inject
    TokenService tokenService;


    @POST
    @Path("/login")
    public Response login(
            LoginRequest request,
            @Context UriInfo uriInfo) {

        try {
            if (request.email == null || request.password == null) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.VALIDATION_ERROR.code,
                        "Email and password required"
                );
                return Response.status(ErrorCode.VALIDATION_ERROR.httpStatus)
                        .entity(response)
                        .build();
            }

            String userId = request.email.split("@")[0];
            String token = tokenService.generateToken(userId, request.email);

            TokenResponse tokenResponse = new TokenResponse();
            tokenResponse.token = token;
            tokenResponse.expiresIn = 86400;
            tokenResponse.tokenType = "Bearer";

            ApiResponse<TokenResponse> response = ApiResponse.success(tokenResponse,
                    new ApiResponse.ResponseMeta(uriInfo.getPath())
            );
            return Response.ok(response).build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to process login"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }
}

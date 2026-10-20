package org.tettyrs.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.tettyrs.dto.ApiError;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.service.InvitationService;
import org.tettyrs.service.ProfileService;

import java.util.LinkedHashMap;
import java.util.Map;

// Undangan pegawai (api-spec.md bagian 16). Publik: token dikirim di body, bukan di path URL.
@Path("/api/v1/invitations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InvitationController {

    @Inject
    InvitationService invitations;

    @Inject
    ProfileService profiles;

    public static class TokenRequest {
        public String token;
    }

    public static class RedeemRequest {
        public String token;
        public String full_name;
        public String nip;
    }

    @POST
    @Path("/check")
    public Response check(TokenRequest req) {
        try {
            InvitationService.Check c = invitations.check(req == null ? null : req.token);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("email", c.email());
            data.put("expires_at", c.expiresAt().toString());
            data.put("state", "valid");
            return Response.ok(ApiResponse.success(data)).build();
        } catch (InvitationService.Rejected e) {
            return error(e);
        }
    }

    @POST
    @Path("/redeem")
    public Response redeem(RedeemRequest req) {
        try {
            InvitationService.Redeemed r = invitations.redeem(
                    req == null ? null : req.token,
                    new ProfileService.Input(req == null ? null : req.full_name, req == null ? null : req.nip),
                    profiles);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("uid", r.uid());
            data.put("email", r.email());
            return Response.ok(ApiResponse.success(data)).build();
        } catch (InvitationService.Rejected e) {
            return error(e);
        }
    }

    @POST
    @Path("/release")
    public Response release(TokenRequest req) {
        try {
            invitations.release(req == null ? null : req.token);
            return Response.ok(ApiResponse.success(Map.of("released", true))).build();
        } catch (InvitationService.Rejected e) {
            return error(e);
        }
    }

    private static Response error(InvitationService.Rejected e) {
        ApiError err = new ApiError(e.code.code, e.getMessage());
        err.field = e.field;
        return Response.status(e.code.httpStatus).entity(ApiResponse.error(err)).build();
    }
}

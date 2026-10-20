package org.tettyrs.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.tettyrs.dto.ApiError;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.entities.AppUser;
import org.tettyrs.security.CurrentUser;
import org.tettyrs.service.ProfileService;

import java.util.LinkedHashMap;
import java.util.Map;

// Profil pengguna yang sedang login (api-spec.md bagian 12).
@Path("/api/v1/me")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProfileController {

    @Inject
    CurrentUser currentUser;

    @Inject
    ProfileService profileService;

    public static class ProfileRequest {
        public String full_name;
        public String nip;
    }

    @GET
    public Response me(@Context UriInfo uriInfo) {
        AppUser user = AppUser.findById(currentUser.userId());
        if (user == null) {
            return error(ErrorCode.NOT_FOUND, "Profil pengguna tidak ditemukan.", null);
        }
        return Response.ok(ApiResponse.success(view(user), new ApiResponse.ResponseMeta(uriInfo.getPath()))).build();
    }

    @PUT
    @Path("/profile")
    public Response updateProfile(ProfileRequest req, @Context UriInfo uriInfo) {
        try {
            AppUser user = profileService.complete(currentUser.userId(),
                    new ProfileService.Input(req == null ? null : req.full_name, req == null ? null : req.nip));
            return Response.ok(ApiResponse.success(view(user), new ApiResponse.ResponseMeta(uriInfo.getPath()))).build();
        } catch (ProfileService.Rejected e) {
            return error(e.code, e.getMessage(), e.field);
        }
    }

    static Map<String, Object> view(AppUser u) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("uid", u.uid);
        v.put("email", u.email);
        v.put("roles", u.roles == null ? null : java.util.Arrays.asList(u.roles));
        v.put("full_name", u.fullName);
        v.put("nip", u.nip);
        v.put("profile_completed", u.profileCompleted);
        return v;
    }

    private static Response error(ErrorCode code, String message, String field) {
        ApiError err = new ApiError(code.code, message);
        err.field = field;
        return Response.status(code.httpStatus).entity(ApiResponse.error(err)).build();
    }
}

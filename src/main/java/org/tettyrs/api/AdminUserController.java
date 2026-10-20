package org.tettyrs.api;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.entities.AppUser;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Pengelolaan profil pengguna oleh admin. Hanya role ADMIN (default AuthorizationFilter untuk path yang
// tidak terdaftar). Dipanggil dari adminBff (Cloud Functions), yang sudah mengurus akun Firebase.
// Kontrak: documents/tech/admin-bff.md
@Path("/api/v1/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminUserController extends BaseController {

    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ADMIN", "VIEWER");

    public static class CreateUserRequest {
        public String uid;
        public String email;
        public List<String> roles;
    }

    public static class RolesRequest {
        public List<String> roles;
    }

    public static class UserView {
        public String uid;
        public String email;
        public List<String> roles;
        public boolean mustChangePassword;
        public String full_name;
        public String nip;
        public boolean profile_completed;
        public OffsetDateTime createdAt;

        static UserView of(AppUser u) {
            UserView v = new UserView();
            v.uid = u.uid;
            v.email = u.email;
            v.roles = Arrays.asList(u.roles);
            v.mustChangePassword = u.mustChangePassword;
            v.full_name = u.fullName;
            v.nip = u.nip;
            v.profile_completed = u.profileCompleted;
            v.createdAt = u.createdAt;
            return v;
        }
    }

    @GET
    public Response listUsers() {
        List<UserView> users = AppUser.<AppUser>list("ORDER BY createdAt DESC").stream()
                .map(UserView::of)
                .collect(Collectors.toList());
        return Response.ok(ApiResponse.success(users)).build();
    }

    @POST
    @Transactional
    public Response createUser(CreateUserRequest req) {
        String error = validateRoles(req == null ? null : req.roles);
        if (req == null || req.uid == null || req.uid.isBlank() || req.email == null || !req.email.contains("@")) {
            return validation("uid dan email wajib diisi");
        }
        if (error != null) return validation(error);

        if (AppUser.findById(req.uid) != null) {
            return conflict("Pengguna sudah ada");
        }
        AppUser u = new AppUser();
        u.uid = req.uid;
        u.email = req.email.trim().toLowerCase();
        u.roles = req.roles.stream().map(r -> r.trim().toUpperCase()).distinct().toArray(String[]::new);
        // Undangan lewat email (api-spec / auth-firebase.md bagian 8): tidak ada sandi sementara.
        u.mustChangePassword = false;
        u.profileCompleted = false;
        u.persist();
        return Response.status(Response.Status.CREATED).entity(ApiResponse.success(UserView.of(u))).build();
    }

    @PUT
    @Path("/{uid}/roles")
    @Transactional
    public Response updateRoles(@PathParam("uid") String uid, RolesRequest req) {
        String error = validateRoles(req == null ? null : req.roles);
        if (error != null) return validation(error);

        AppUser u = AppUser.findById(uid);
        if (u == null) {
            return Response.status(ErrorCode.NOT_FOUND.httpStatus)
                    .entity(ApiResponse.error(ErrorCode.NOT_FOUND.code, "Pengguna tidak ditemukan"))
                    .build();
        }
        u.roles = req.roles.stream().map(r -> r.trim().toUpperCase()).distinct().toArray(String[]::new);
        return Response.ok(ApiResponse.success(UserView.of(u))).build();
    }

    private String validateRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) return "roles wajib diisi";
        for (String r : roles) {
            if (r == null || !ALLOWED_ROLES.contains(r.trim().toUpperCase())) {
                return "role tidak valid: " + r + ". Pilihan: USER, ADMIN, VIEWER";
            }
        }
        return null;
    }

    private Response validation(String message) {
        return Response.status(ErrorCode.VALIDATION_ERROR.httpStatus)
                .entity(ApiResponse.error(ErrorCode.VALIDATION_ERROR.code, message))
                .build();
    }

    private Response conflict(String message) {
        return Response.status(ErrorCode.CONFLICT.httpStatus)
                .entity(ApiResponse.error(ErrorCode.CONFLICT.code, message))
                .build();
    }

    public static class InvitationRequest {
        public String token_hash;
        public String expires_at;
    }

    @Inject
    org.tettyrs.service.InvitationService invitations;

    /** Undangan baru untuk akun yang belum lengkap profilnya (api-spec.md bagian 16). Token asli tidak diterima di sini. */
    @POST
    @Path("/{uid}/invitations")
    @Transactional
    public Response createInvitation(@PathParam("uid") String uid, InvitationRequest req) {
        try {
            if (req == null || req.expires_at == null) {
                return validation("expires_at wajib diisi");
            }
            invitations.replace(uid, req.token_hash == null ? null : req.token_hash.toLowerCase(),
                    java.time.OffsetDateTime.parse(req.expires_at));
            return Response.status(Response.Status.CREATED).entity(ApiResponse.success(java.util.Map.of("uid", uid))).build();
        } catch (org.tettyrs.service.InvitationService.Rejected e) {
            org.tettyrs.dto.ApiError err = new org.tettyrs.dto.ApiError(e.code.code, e.getMessage());
            err.field = e.field;
            return Response.status(e.code.httpStatus).entity(ApiResponse.error(err)).build();
        } catch (java.time.format.DateTimeParseException e) {
            return validation("expires_at harus ISO-8601");
        }
    }
}

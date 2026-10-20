package org.tettyrs.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.service.EncryptionBackfillService;

/**
 * Enkripsi ulang data lama (admin). {@code apply=false} (default) hanya menghitung, tanpa mengubah data.
 * {@code apply=true} benar-benar mengenkripsi. Hanya ADMIN, lewat AuthorizationFilter.
 */
@Path("/api/v1/admin/backfill-encryption")
@Produces(MediaType.APPLICATION_JSON)
public class EncryptionBackfillController extends BaseController {

    @Inject
    EncryptionBackfillService backfill;

    @POST
    public Response run(@QueryParam("apply") @DefaultValue("false") boolean apply) {
        var report = backfill.run(apply, currentUser.userId());
        return Response.ok(ApiResponse.success(report)).build();
    }
}

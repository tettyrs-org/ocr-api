package org.tettyrs.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.AuditEventResponse;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.service.AuditEventService;

import java.util.List;
import java.util.UUID;

import io.quarkus.security.Authenticated;

@Path("/api/documents/{documentId}/audit")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuditEventController {

    @Inject
    AuditEventService auditEventService;

    @Authenticated
    @GET
    public Response getAuditTrail(
            @PathParam("documentId") UUID documentId,
            @Context UriInfo uriInfo) {

        try {
            List<AuditEventResponse> auditTrail = auditEventService.getAuditTrail(documentId);
            ApiResponse<List<AuditEventResponse>> response = ApiResponse.success(auditTrail,
                    new ApiResponse.ResponseMeta(uriInfo.getPath())
            );
            return Response.ok(response).build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to retrieve audit trail"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }
}

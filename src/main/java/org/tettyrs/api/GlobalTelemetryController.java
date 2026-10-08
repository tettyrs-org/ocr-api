package org.tettyrs.api;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.TimingsBatch;
import org.tettyrs.dto.TimingEntry;
import org.tettyrs.dto.enums.ErrorCode;

@Path("/api/v1/telemetry")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GlobalTelemetryController extends BaseController {

    @POST
    @Path("/timings")
    public Response recordTimings(
            TimingsBatch batch,
            @Context UriInfo uriInfo) {

        try {
            if (batch == null || batch.timings == null || batch.timings.isEmpty()) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.VALIDATION_ERROR.code,
                        "timings array required and cannot be empty"
                );
                return Response.status(ErrorCode.VALIDATION_ERROR.httpStatus)
                        .entity(response)
                        .build();
            }

            for (TimingEntry entry : batch.timings) {
                if (entry.document_id == null || entry.component == null || entry.duration_ms == null) {
                    ApiResponse<Void> response = ApiResponse.error(
                            ErrorCode.VALIDATION_ERROR.code,
                            "document_id, component, and duration_ms required for each timing"
                    );
                    return Response.status(ErrorCode.VALIDATION_ERROR.httpStatus)
                            .entity(response)
                            .build();
                }
            }

            ApiResponse<Void> response = ApiResponse.success(null,
                    new ApiResponse.ResponseMeta(uriInfo.getPath())
            );
            return Response.accepted(response).build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to record timings"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }
}

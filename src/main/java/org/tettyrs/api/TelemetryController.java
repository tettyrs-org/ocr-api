package org.tettyrs.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.TelemetryResponse;
import org.tettyrs.dto.TimingsBatch;
import org.tettyrs.dto.TimingEntry;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.entities.TelemetryTiming;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/v1/documents/{documentId}/telemetry")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TelemetryController extends BaseController {

    @POST
    public Response recordTimings(
            @PathParam("documentId") Long documentId,
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
                if (entry.document_id == null) {
                    entry.document_id = documentId;
                }
                if (entry.component == null || entry.duration_ms == null) {
                    ApiResponse<Void> response = ApiResponse.error(
                            ErrorCode.VALIDATION_ERROR.code,
                            "component and duration_ms required for each timing"
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

    @GET
    public Response listTelemetry(
            @PathParam("documentId") Long documentId,
            @QueryParam("component") String component,
            @QueryParam("page") Integer page,
            @QueryParam("limit") Integer limit,
            @Context UriInfo uriInfo
            ){

        try {
            page = page != null ? page : 1;
            limit = limit != null && limit <= 100 ? limit : 20;

            String query = "documentId = ?1";
            Object[] params;

            if (component != null && !component.isEmpty()) {
                query += " AND component = ?2 ORDER BY createdAt DESC";
                params = new Object[]{documentId, component};
            } else {
                query += " ORDER BY createdAt DESC";
                params = new Object[]{documentId};
            }

            List<TelemetryTiming> timings = TelemetryTiming.find(query, params).list();
            long total = TelemetryTiming.count("documentId = ?1", documentId);

            List<TelemetryResponse> responses = timings.stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());

            ApiResponse.ResponseMeta meta = new ApiResponse.ResponseMeta(
                    uriInfo.getPath(),
                    page,
                    limit,
                    total
            );
            ApiResponse<List<TelemetryResponse>> response = ApiResponse.success(responses, meta);
            return Response.ok(response).build();
        }catch (Exception e){
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to list telemetry"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }

    @GET
    @Path("{telemetryId}")
    public Response getTelemetry(
        @PathParam("documentId") Long documentId,
        @PathParam("telemetryId") Long telemetryId,
        @Context UriInfo uriInfo
    ){

        try {
            TelemetryTiming timing = TelemetryTiming.find(
                    "id = ?1 AND documentId = ?2",
                    telemetryId, documentId
            ).firstResult();

            if (timing == null) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.DOCUMENT_NOT_FOUND.code,
                        "Telemetry record not found"
                );
                return Response.status(ErrorCode.DOCUMENT_NOT_FOUND.httpStatus)
                        .entity(response)
                        .build();
            }

            ApiResponse<TelemetryResponse> response = ApiResponse.success(
                    toResponse(timing),
                    new ApiResponse.ResponseMeta(uriInfo.getPath())
            );
            return Response.ok(response).build();
        }catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to retrieve telemetry"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }

    private TelemetryResponse toResponse(TelemetryTiming entity){
        TelemetryResponse dto = new TelemetryResponse();
        dto.id = entity.id;
        dto.documentId = entity.documentId;
        dto.component = entity.component;
        dto.startTime = entity.startTime;
        dto.endTime = entity.endTime;
        dto.durationMs = entity.durationMs;
        dto.status = entity.status.toString();
        dto.errorMessage = entity.errorMessage;
        dto.createdAt = entity.createdAt;
        return dto;
    }
}

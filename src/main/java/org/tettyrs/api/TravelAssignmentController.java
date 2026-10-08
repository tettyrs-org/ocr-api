package org.tettyrs.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.TravelAssignmentRequest;
import org.tettyrs.dto.TravelAssignmentResponse;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.service.TravelAssignmentService;

import java.util.List;

import io.quarkus.security.Authenticated;


@Path("/api/assignments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TravelAssignmentController {

    @Inject
    TravelAssignmentService assignmentService;

    @Authenticated
    @POST
    public Response createAssignment(
            TravelAssignmentRequest request,
            @Context UriInfo uriInfo) {

        try {
            TravelAssignmentResponse assignmentResponse = assignmentService.createAssignment(request);
            ApiResponse<TravelAssignmentResponse> response = ApiResponse.success(assignmentResponse,
                    new ApiResponse.ResponseMeta(uriInfo.getPath())
            );
            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (IllegalArgumentException e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.VALIDATION_ERROR.code,
                    e.getMessage()
            );
            return Response.status(ErrorCode.VALIDATION_ERROR.httpStatus)
                    .entity(response)
                    .build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to create assignment"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }

    @Authenticated
    @GET
    public Response listAssignments(
            @QueryParam("page") Integer page,
            @QueryParam("limit") Integer limit,
            @Context UriInfo uriInfo) {

        try {
            page = page != null ? page : 1;
            limit = limit != null && limit <= 100 ? limit : 20;

            List<TravelAssignmentResponse> assignments = assignmentService.listAssignments();
            long total = (long) assignments.size();

            ApiResponse.ResponseMeta meta = new ApiResponse.ResponseMeta(
                    uriInfo.getPath(),
                    page,
                    limit,
                    total
            );
            ApiResponse<List<TravelAssignmentResponse>> response = ApiResponse.success(assignments, meta);

            return Response.ok(response).build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to list assignments"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }

    @Authenticated
    @PATCH
    @Path("/{id}")
    public Response updateAssignmentStatus(
            @PathParam("id") Long id,
            TravelAssignmentRequest request,
            @Context UriInfo uriInfo) {

        try {
            TravelAssignmentResponse assignmentResponse = assignmentService.updateAssignmentStatus(id, request.status);
            ApiResponse<TravelAssignmentResponse> response = ApiResponse.success(assignmentResponse,
                    new ApiResponse.ResponseMeta(uriInfo.getPath())
            );
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.DOCUMENT_NOT_FOUND.code,
                    "Assignment not found"
            );
            return Response.status(ErrorCode.DOCUMENT_NOT_FOUND.httpStatus)
                    .entity(response)
                    .build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to update assignment"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }
}

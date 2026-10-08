package org.tettyrs.api;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.CallbackResponse;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.entities.ExtractionCallback;
import org.tettyrs.entities.enums.CallbackStatus;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/v1/documents/{documentId}/callbacks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CallbackController extends BaseController {

    @GET
    public Response listCallbacks(
            @PathParam("documentId") Long documentId,
            @QueryParam("status") String status,
            @QueryParam("page") Integer page,
            @QueryParam("limit") Integer limit,
            @Context UriInfo uriInfo
            ){

        try {
            page = page != null ? page : 1;
            limit = limit != null && limit <= 100 ? limit : 20;

            String query = "documentId = ?1";
            Object[] params;

            if (status != null && !status.isEmpty()) {
                query += " AND callbackStatus = ?2 ORDER BY createdAt DESC";
                params = new Object[]{documentId, status};
            } else {
                query += " ORDER BY createdAt DESC";
                params = new Object[]{documentId};
            }

            List<ExtractionCallback> callbacks = ExtractionCallback.find(query, params).list();
            long total = ExtractionCallback.count("documentId = ?1", documentId);

            List<CallbackResponse> responses = callbacks.stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());

            ApiResponse.ResponseMeta meta = new ApiResponse.ResponseMeta(
                    uriInfo.getPath(),
                    page,
                    limit,
                    total
            );
            ApiResponse<List<CallbackResponse>> response = ApiResponse.success(responses, meta);

            return Response.ok(response).build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to list callbacks"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }

    @GET
    @Path("/{callbackId}")
    public Response getCallback(
            @PathParam("documentId") Long documentId,
            @PathParam("callbackId") Long callbackId,
            @Context UriInfo uriInfo
    ){

        try {
            ExtractionCallback callback = ExtractionCallback.find(
                    "id = ?1 AND documentId = ?2",
                    callbackId, documentId
            ).firstResult();

            if (callback == null) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.DOCUMENT_NOT_FOUND.code,
                        "Callback not found"
                );
                return Response.status(ErrorCode.DOCUMENT_NOT_FOUND.httpStatus)
                        .entity(response)
                        .build();
            }

            ApiResponse<CallbackResponse> response = ApiResponse.success(
                    toResponse(callback),
                    new ApiResponse.ResponseMeta(uriInfo.getPath())
            );
            return Response.ok(response).build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to retrieve callback"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }

    }

    @POST
    @Path("/{callbackId}/retry")
    @Transactional
    public  Response retryCallback(
            @PathParam("documentId") Long documentId,
            @PathParam("callbackId") Long callbackId,
            @Context UriInfo uriInfo
    ){

        try {
            ExtractionCallback callback = ExtractionCallback.find(
                    "id = ?1 AND documentId = ?2",
                    callbackId, documentId
            ).firstResult();

            if (callback == null) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.DOCUMENT_NOT_FOUND.code,
                        "Callback not found"
                );
                return Response.status(ErrorCode.DOCUMENT_NOT_FOUND.httpStatus)
                        .entity(response)
                        .build();
            }

            callback.retryCount = 0;
            callback.callbackStatus = CallbackStatus.RETRYING;

            ApiResponse<CallbackResponse> response = ApiResponse.success(
                    toResponse(callback),
                    new ApiResponse.ResponseMeta(uriInfo.getPath())
            );
            return Response.accepted(response).build();  // 202 Accepted
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to retry callback"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }

    }

    private CallbackResponse toResponse(ExtractionCallback entity) {
        CallbackResponse dto = new CallbackResponse();
        dto.id = entity.id;
        dto.documentId = entity.documentId;
        dto.webhookUrl = entity.webhookUrl;
        dto.eventType = entity.eventType.toString();
        dto.payload = entity.payload;
        dto.callbackStatus = entity.callbackStatus.toString();
        dto.retryCount = entity.retryCount;
        dto.maxRetries = entity.maxRetries;
        dto.lastRetryAt = entity.lastRetryAt;
        dto.lastResponseCode = entity.lastResponseCode;
        dto.lastErrorMessage = entity.lastErrorMessage;
        dto.createdAt = entity.createdAt;
        dto.updatedAt = entity.updatedAt;

        return dto;
    }
}

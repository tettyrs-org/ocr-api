package org.tettyrs.api;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.CallbackRequest;
import org.tettyrs.dto.CallbackResponse;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.ExtractionCallback;
import org.tettyrs.entities.enums.CallbackEventType;
import org.tettyrs.entities.enums.CallbackStatus;
import org.tettyrs.service.CallbackService;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/v1/callbacks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GlobalCallbackController extends BaseController {

    @Inject
    CallbackService callbackService;

    @POST
    @Transactional
    public Response createCallback(
            CallbackRequest request,
            @Context UriInfo uriInfo) {

        try {
            if (request == null || request.webhookUrl == null) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.VALIDATION_ERROR.code,
                        "webhookUrl required"
                );
                return Response.status(ErrorCode.VALIDATION_ERROR.httpStatus)
                        .entity(response)
                        .build();
            }

            if (request.documentId == null) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.VALIDATION_ERROR.code,
                        "documentId required"
                );
                return Response.status(ErrorCode.VALIDATION_ERROR.httpStatus)
                        .entity(response)
                        .build();
            }

            Document doc = Document.findById(request.documentId);
            if (doc == null) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.DOCUMENT_NOT_FOUND.code,
                        "Document not found"
                );
                return Response.status(ErrorCode.DOCUMENT_NOT_FOUND.httpStatus)
                        .entity(response)
                        .build();
            }

            CallbackEventType eventType = CallbackEventType.valueOf(request.eventType);
            ExtractionCallback callback = callbackService.createCallback(
                    request.documentId,
                    request.webhookUrl,
                    eventType,
                    request.payload != null ? request.payload : "{}"
            );

            CallbackResponse callbackResponse = toResponse(callback);
            ApiResponse<CallbackResponse> response = ApiResponse.success(callbackResponse,
                    new ApiResponse.ResponseMeta(uriInfo.getPath())
            );
            return Response.status(Response.Status.CREATED)
                    .entity(response)
                    .build();
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
                    "Failed to create callback"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }

    @GET
    public Response listCallbacks(
            @QueryParam("page") Integer page,
            @QueryParam("limit") Integer limit,
            @QueryParam("status") String status,
            @Context UriInfo uriInfo) {

        try {
            page = page != null ? page : 1;
            limit = limit != null && limit <= 100 ? limit : 20;

            List<ExtractionCallback> callbacks;
            long total;

            if (status != null && !status.isEmpty()) {
                callbacks = ExtractionCallback.find(
                        "callbackStatus = ?1 ORDER BY createdAt DESC",
                        status
                ).list();
                total = ExtractionCallback.count("callbackStatus = ?1", status);
            } else {
                callbacks = ExtractionCallback.findAll().list();
                total = ExtractionCallback.count();
            }

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
            @PathParam("callbackId") Long callbackId,
            @Context UriInfo uriInfo) {

        try {
            ExtractionCallback callback = ExtractionCallback.findById(callbackId);
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

package org.tettyrs.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.VerificationResponse;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.entities.DocumentVerification;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/v1/documents/{documentId}/verifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VerificationController extends BaseController {

    @GET
    public Response listVerifications(
            @PathParam("documentId") Long documentId,
            @QueryParam("page") Integer page,
            @QueryParam("limit") Integer limit,
            @Context UriInfo uriInfo
    ) {

        try {
            page = page != null ? page : 1;
            limit = limit != null && limit < 100 ? limit : 20;

            List<DocumentVerification> verifications = DocumentVerification.find(
                    "documentId = ?1 ORDER BY createdAt DESC",
                    documentId
            ).list();
            long total = DocumentVerification.count("documentId = ?1", documentId);

            List<VerificationResponse> responses = verifications.stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());

            ApiResponse.ResponseMeta meta = new ApiResponse.ResponseMeta(
                    uriInfo.getPath(),
                    page,
                    limit,
                    total
            );
            ApiResponse<List<VerificationResponse>> response = ApiResponse.success(responses, meta);

            return Response.ok(response).build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to list verifications"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }

    @GET
    @Path("/{verificationId}")
    public Response getVerification(
            @PathParam("documentId") Long documentId,
            @PathParam("verificationId") Long verificationId,
            @Context UriInfo uriInfo
    ) {

        try {
            DocumentVerification verification = DocumentVerification.find(
                    "id = ?1 AND documentId = ?2", verificationId, documentId
            ).firstResult();

            if (verification == null) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.DOCUMENT_NOT_FOUND.code,
                        "Verification not found"
                );
                return Response.status(ErrorCode.NOT_FOUND.httpStatus)
                        .entity(response)
                        .build();
            }

            ApiResponse<VerificationResponse> response = ApiResponse.success(
                    toResponse(verification),
                    new ApiResponse.ResponseMeta(uriInfo.getPath())
            );
            return Response.ok(response).build();
        }catch (Exception e){
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to retrieve verification"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }

    private VerificationResponse toResponse(DocumentVerification entity) {
        VerificationResponse dto = new VerificationResponse();
        dto.id = entity.id;
        dto.documentId = entity.documentId;
        dto.verificationStatus = entity.verificationStatus.toString();
        dto.verificationDetails = entity.verificationDetails;
        dto.confidenceScore = entity.confidenceScore;
        dto.verifiedBy = entity.verifiedBy;
        dto.verifiedAt = entity.verifiedAt;
        dto.notes = entity.notes;
        dto.createdAt = entity.createdAt;

        // Optional: include document info
        if (entity.document != null) {
            VerificationResponse.DocumentInfo docInfo = new VerificationResponse.DocumentInfo();
            docInfo.id = entity.document.id;
            docInfo.filename = entity.document.filename;
            docInfo.documentType = entity.document.documentType.toString();
            docInfo.createdAt = entity.document.createdAt;
            dto.document = docInfo;
        }

        return dto;
    }
}



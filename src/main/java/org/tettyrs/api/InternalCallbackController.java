package org.tettyrs.api;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.enums.DocumentStatus;
import org.tettyrs.entities.enums.ProcessingStatus;
import org.tettyrs.service.DocumentService;

@Path("/internal/documents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InternalCallbackController {

    @Inject
    DocumentService documentService;

    @PUT
    @Path("/{id}/extraction")
    @Transactional
    public Response extractionCallback(
            @PathParam("id") Long documentId,
            ExtractionResult result,
            @Context UriInfo uriInfo) {

        try {
            if (result == null || result.extraction_text == null) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.VALIDATION_ERROR.code,
                        "extraction_text required"
                );
                return Response.status(ErrorCode.VALIDATION_ERROR.httpStatus)
                        .entity(response)
                        .build();
            }

            Document doc = Document.findById(documentId);
            if (doc == null) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.DOCUMENT_NOT_FOUND.code,
                        "Document not found"
                );
                return Response.status(ErrorCode.DOCUMENT_NOT_FOUND.httpStatus)
                        .entity(response)
                        .build();
            }

            // Create processing result
            org.tettyrs.entities.DocumentProcessingResult processingResult =
                new org.tettyrs.entities.DocumentProcessingResult();
            processingResult.documentId = documentId;
            processingResult.processingCompletedAt = java.time.LocalDateTime.now();

            if (result.success) {
                doc.status = DocumentStatus.COMPLETED;
                processingResult.processingStatus = ProcessingStatus.COMPLETED;
                processingResult.ocrText = result.extraction_text;
                processingResult.summary = result.extracted_fields;
            } else {
                doc.status = DocumentStatus.FAILED;
                processingResult.processingStatus = ProcessingStatus.FAILED;
                processingResult.errorMessage = result.error_message;
            }

            processingResult.persist();

            ApiResponse<Void> response = ApiResponse.success(null,
                    new ApiResponse.ResponseMeta(uriInfo.getPath())
            );
            return Response.accepted(response).build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to process extraction callback"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }

    public static class ExtractionResult {
        public boolean success;
        public String extraction_text;
        public String extracted_fields;
        public String error_message;
        public String traceparent;
    }
}

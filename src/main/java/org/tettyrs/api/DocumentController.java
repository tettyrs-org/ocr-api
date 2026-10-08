package org.tettyrs.api;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.tettyrs.dto.*;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.DocumentProcessingResult;
import org.tettyrs.service.DocumentService;
import org.tettyrs.service.ProcessingService;
import org.tettyrs.service.S3Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

@Path("/api/v1/documents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DocumentController {

    @Inject
    DocumentService documentService;

    @Inject
    S3Service s3Service;

    @Inject
    ProcessingService processingService;

    @Inject
    EntityManager em;


    @POST
    public Response createDocument(
            DocumentRequest request,
            @Context UriInfo uriInfo) {

        try {
            DocumentResponse docResponse = documentService.createDocument(request);
            ApiResponse<DocumentResponse> response = ApiResponse.success(docResponse,
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
                    "Failed to create document"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }

    @GET
    public Response listDocuments(
            @QueryParam("page") Integer page,
            @QueryParam("limit") Integer limit,
            @Context UriInfo uriInfo) {

        try {
            page = page != null ? page : 1;
            limit = limit != null && limit <= 100 ? limit : 20;

            List<DocumentResponse> documents = documentService.listDocuments();
            long total = (long) documents.size();

            ApiResponse.ResponseMeta meta = new ApiResponse.ResponseMeta(
                    uriInfo.getPath(),
                    page,
                    limit,
                    total
            );
            ApiResponse<List<DocumentResponse>> response = ApiResponse.success(documents, meta);

            return Response.ok(response).build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to list documents"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }

    @GET
    @Path("/{id}")
    public Response getDocument(
            @PathParam("id") UUID id,
            @Context UriInfo uriInfo) {

        try {
            DocumentResponse document = documentService.getDocumentById(id);
            ApiResponse<DocumentResponse> response = ApiResponse.success(document,
                    new ApiResponse.ResponseMeta(uriInfo.getPath())
            );
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.DOCUMENT_NOT_FOUND.code,
                    "Document not found"
            );
            return Response.status(ErrorCode.DOCUMENT_NOT_FOUND.httpStatus)
                    .entity(response)
                    .build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to retrieve document"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteDocument(
            @PathParam("id") UUID id) {

        try {
            documentService.deleteDocument(id);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.DOCUMENT_NOT_FOUND.code,
                    "Document not found"
            );
            return Response.status(ErrorCode.DOCUMENT_NOT_FOUND.httpStatus)
                    .entity(response)
                    .build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to delete document"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }

    @POST
    @Path("/{id}/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadDocument(
            @PathParam("id") UUID documentId,
            @RestForm("file") FileUpload file,
            @Context UriInfo uriInfo) {

        try {
            if (file == null || file.filePath() == null) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.VALIDATION_ERROR.code,
                        "No file provided"
                );
                return Response.status(ErrorCode.VALIDATION_ERROR.httpStatus)
                        .entity(response)
                        .build();
            }

            String contentType = file.contentType() != null ? file.contentType() : "application/octet-stream";
            Long fileSize = Files.size(file.filePath());
            InputStream fileStream = Files.newInputStream(file.filePath());

            DocumentResponse docResponse = documentService.uploadFile(documentId, fileStream, fileSize, contentType);
            ApiResponse<DocumentResponse> response = ApiResponse.success(docResponse,
                    new ApiResponse.ResponseMeta(uriInfo.getPath())
            );
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.VALIDATION_ERROR.code,
                    e.getMessage()
            );
            return Response.status(ErrorCode.VALIDATION_ERROR.httpStatus)
                    .entity(response)
                    .build();
        } catch (java.nio.file.NoSuchFileException e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.VALIDATION_ERROR.code,
                    "Uploaded file not found"
            );
            return Response.status(ErrorCode.VALIDATION_ERROR.httpStatus)
                    .entity(response)
                    .build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to upload file"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }

    @GET
    @Path("/{id}/file")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadDocument(
            @PathParam("id") UUID documentId) {

        try {
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

            if (doc.s3Path == null) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.VALIDATION_ERROR.code,
                        "Document has no file"
                );
                return Response.status(ErrorCode.VALIDATION_ERROR.httpStatus)
                        .entity(response)
                        .build();
            }

            String s3Key = s3Service.extractKeyFromPath(doc.s3Path);
            InputStream fileStream = s3Service.downloadFile(s3Key);

            return Response.ok(fileStream)
                    .header("Content-Disposition", "attachment; filename=\"" + doc.filename + "\"")
                    .header("Cache-Control", "no-store")
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @POST
    @Path("/{id}/reprocess")
    public Response reprocessDocument(
            @PathParam("id") UUID documentId,
            @Context UriInfo uriInfo) {

        try {
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

            if (!"extraction_failed".equals(doc.status)) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.CONFLICT.code,
                        "Only extraction_failed documents can be reprocessed"
                );
                return Response.status(ErrorCode.CONFLICT.httpStatus)
                        .entity(response)
                        .build();
            }

            processingService.startProcessing(documentId);

            DocumentResponse docResponse = documentService.getDocumentById(documentId);
            ApiResponse<DocumentResponse> response = ApiResponse.success(docResponse,
                    new ApiResponse.ResponseMeta(uriInfo.getPath())
            );
            return Response.accepted(response).build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to reprocess document"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }

    @POST
    @Path("/{id}/process")
    public Response processDocument(
            @PathParam("id") UUID documentId,
            @Context UriInfo uriInfo) {

        try {
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

            if (doc.s3Path == null) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.VALIDATION_ERROR.code,
                        "Document has no uploaded file"
                );
                return Response.status(ErrorCode.VALIDATION_ERROR.httpStatus)
                        .entity(response)
                        .build();
            }

            processingService.startProcessing(documentId);

            DocumentResponse docResponse = documentService.getDocumentById(documentId);
            ApiResponse<DocumentResponse> response = ApiResponse.success(docResponse,
                    new ApiResponse.ResponseMeta(uriInfo.getPath())
            );
            return Response.accepted(response).build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to start processing"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }

    @GET
    @Path("/{id}/processing-status")
    public Response getProcessingStatus(
            @PathParam("id") UUID documentId,
            @Context UriInfo uriInfo) {

        try {
            DocumentProcessingResult result = em.createQuery(
                            "SELECT d FROM DocumentProcessingResult d WHERE d.documentId = :id ORDER BY d.id DESC",
                            DocumentProcessingResult.class
                    )
                    .setParameter("id", documentId)
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);

            if (result == null) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.DOCUMENT_NOT_FOUND.code,
                        "No processing result found"
                );
                return Response.status(ErrorCode.DOCUMENT_NOT_FOUND.httpStatus)
                        .entity(response)
                        .build();
            }

            ProcessingResult processingResult = new ProcessingResult();
            processingResult.ocrText = result.ocrText;
            processingResult.ocrConfidence = result.ocrConfidence;
            processingResult.classificationCategory = result.classificationCategory;
            processingResult.classificationConfidence = result.classificationConfidence;
            processingResult.summary = result.summary;
            processingResult.processingStatus = result.processingStatus.toString();
            processingResult.errorMessage = result.errorMessage;
            processingResult.completedAt = result.processingCompletedAt;

            ApiResponse<ProcessingResult> response = ApiResponse.success(processingResult,
                    new ApiResponse.ResponseMeta(uriInfo.getPath())
            );
            return Response.ok(response).build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to retrieve processing status"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }


    @PUT
    @Path("/{id}/verification")
    @Transactional
    public Response verifyDocument(
            @PathParam("id") UUID documentId,
            VerificationRequest request,
            @Context UriInfo uriInfo) {

        try {
            if (request == null || request.verification_details == null) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.VALIDATION_ERROR.code,
                        "verification_details required"
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

            if (doc.status == org.tettyrs.entities.enums.DocumentStatus.VERIFIED) {
                ApiResponse<Void> response = ApiResponse.error(
                        ErrorCode.CONFLICT.code,
                        "Document already verified"
                );
                return Response.status(ErrorCode.CONFLICT.httpStatus)
                        .entity(response)
                        .build();
            }

            // Create verification record
            org.tettyrs.entities.DocumentVerification verification = new org.tettyrs.entities.DocumentVerification();
            verification.documentId = documentId;
            verification.verificationStatus = org.tettyrs.entities.enums.VerificationStatus.PASSED;
            verification.verificationDetails = request.verification_details;
            verification.notes = request.notes;
            verification.verifiedBy = request.verified_by;
            verification.verifiedAt = java.time.LocalDateTime.now();
            verification.persist();

            // Update document status
            doc.status = org.tettyrs.entities.enums.DocumentStatus.VERIFIED;

            DocumentResponse docResponse = documentService.getDocumentById(documentId);
            ApiResponse<DocumentResponse> response = ApiResponse.success(docResponse,
                    new ApiResponse.ResponseMeta(uriInfo.getPath())
            );
            return Response.ok(response).build();
        } catch (Exception e) {
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.INTERNAL_ERROR.code,
                    "Failed to verify document"
            );
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(response)
                    .build();
        }
    }



}

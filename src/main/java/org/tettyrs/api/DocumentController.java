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
import org.tettyrs.service.DocumentVerifier;
import org.tettyrs.service.ProcessingService;
import org.tettyrs.service.S3Service;
import org.tettyrs.service.UploadValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    // Pengguna yang sedang login (dari token). Dipakai untuk memfilter dokumen milik pengguna.
    @Inject
    org.tettyrs.security.CurrentUser currentUser;

    @Inject
    org.tettyrs.service.JobQueue jobQueue;

    @Inject
    org.tettyrs.service.FieldCipher fieldCipher;

    @Inject
    DocumentVerifier documentVerifier;

    @Inject
    org.tettyrs.service.FilePurgeService filePurgeService;

    @Inject
    org.tettyrs.service.QualityGate qualityGate;

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(DocumentController.class);


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

    /**
     * Unggah berkas (api-spec bagian 2). Respons 202: berkas diterima, pemrosesan belum selesai.
     * Idempotency-Key yang sama mengembalikan dokumen yang sama tanpa memproses ulang.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createFromUpload(
            @RestForm("file") FileUpload file,
            @RestForm("category") String category,
            @RestForm("doc_type_hint") String docTypeHint,
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            @Context UriInfo uriInfo) {

        try {
            // Gerbang profil (api-spec.md bagian 12): pengguna undangan harus melengkapi nama dan NIP dulu.
            org.tettyrs.entities.AppUser profile = org.tettyrs.entities.AppUser.findById(currentUser.userId());
            if (profile == null || !profile.profileCompleted) {
                return Response.status(ErrorCode.PROFILE_INCOMPLETE.httpStatus)
                        .entity(ApiResponse.error(ErrorCode.PROFILE_INCOMPLETE.code,
                                "Lengkapi nama dan NIP Anda sebelum mengunggah dokumen."))
                        .build();
            }
            if (file == null || file.filePath() == null) {
                return validationError("Berkas wajib diisi");
            }
            if (idempotencyKey != null && idempotencyKey.length() > 64) {
                return validationError("Idempotency-Key terlalu panjang");
            }
            byte[] bytes = Files.readAllBytes(file.filePath());
            // Gambar diperiksa mutunya sebelum disimpan, supaya pengguna bisa memfoto ulang (PDF dilewati).
            String mime = org.tettyrs.service.UploadValidator.validate(bytes).kind().mimeType;
            org.tettyrs.service.QualityGate.Decision quality = mime.startsWith("image/")
                    ? qualityGate.check(bytes, mime) : null;
            if (quality != null && quality.blocked()) {
                org.tettyrs.dto.ApiError error = new org.tettyrs.dto.ApiError(
                        ErrorCode.QUALITY_REJECTED.code,
                        "Foto belum dapat dibaca. Silakan foto ulang dengan memperhatikan saran berikut.");
                error.details = quality.issues();
                return Response.status(ErrorCode.QUALITY_REJECTED.httpStatus).entity(ApiResponse.error(error)).build();
            }
            DocumentService.UploadOutcome outcome = documentService.createFromUpload(
                    currentUser.userId(), category, docTypeHint, idempotencyKey, file.fileName(), bytes);

            Document doc = outcome.document();
            if (outcome.created()) {
                // Pemrosesan dikerjakan ms-ocr lewat antrian. Kegagalan antrian dicatat, tidak membatalkan
                // unggahan yang sudah tersimpan (api-spec bagian 8: pengiriman at-least-once).
                try {
                    jobQueue.enqueue(doc.id, doc.category);
                } catch (RuntimeException e) {
                    LOG.errorf(e, "gagal memasukkan job dokumen %s ke antrian", doc.id);
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("document_id", doc.id);
            data.put("doc_ref", doc.docRef);
            data.put("status", DocumentView.apiStatus(doc));
            data.put("poll_after_ms", 2000);
            if (quality != null && !quality.issues().isEmpty()) {
                // Peringatan mutu: unggahan tetap diterima, dan aplikasi menampilkan saran untuk foto berikutnya.
                data.put("quality_warnings", quality.issues());
            }
            return Response.status(Response.Status.ACCEPTED)
                    .entity(ApiResponse.success(data, new ApiResponse.ResponseMeta(uriInfo.getPath())))
                    .build();
        } catch (UploadValidator.RejectedException e) {
            return Response.status(e.code.httpStatus)
                    .entity(ApiResponse.error(e.code.code, e.getMessage()))
                    .build();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        } catch (IOException e) {
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(ApiResponse.error(ErrorCode.INTERNAL_ERROR.code, "Gagal membaca berkas"))
                    .build();
        }
    }

    private Response validationError(String message) {
        return Response.status(ErrorCode.VALIDATION_ERROR.httpStatus)
                .entity(ApiResponse.error(ErrorCode.VALIDATION_ERROR.code, message))
                .build();
    }

    @GET
    public Response listDocuments(
            @QueryParam("page") Integer page,
            @QueryParam("limit") Integer limit,
            @Context UriInfo uriInfo) {

        try {
            page = page != null ? page : 1;
            limit = limit != null && limit <= 100 ? limit : 20;

            String uploader = currentUser.userId();
            List<DocumentView> documents = documentService.listForUser(uploader, page, limit).stream()
                    .map(d -> DocumentView.from(d, fieldCipher))
                    .collect(java.util.stream.Collectors.toList());
            long total = documentService.countForUser(uploader);

            ApiResponse.ResponseMeta meta = new ApiResponse.ResponseMeta(
                    uriInfo.getPath(),
                    page,
                    limit,
                    total
            );
            ApiResponse<List<DocumentView>> response = ApiResponse.success(documents, meta);

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
            Document owned = documentService.findOwned(id, currentUser.userId());
            if (owned == null) {
                return Response.status(ErrorCode.DOCUMENT_NOT_FOUND.httpStatus)
                        .entity(ApiResponse.error(ErrorCode.DOCUMENT_NOT_FOUND.code, "Document not found"))
                        .build();
            }
            ApiResponse<DocumentView> response = ApiResponse.success(DocumentView.from(owned, fieldCipher),
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
            documentService.deleteDocument(id, currentUser.userId());
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

            // Ganti berkas hanya untuk dokumen milik pengguna yang belum final.
            Document owned = documentService.findOwned(documentId, currentUser.userId());
            if (owned == null) {
                return Response.status(ErrorCode.DOCUMENT_NOT_FOUND.httpStatus)
                        .entity(ApiResponse.error(ErrorCode.DOCUMENT_NOT_FOUND.code, "Document not found"))
                        .build();
            }
            if (owned.status == org.tettyrs.entities.enums.DocumentStatus.VERIFIED) {
                return Response.status(ErrorCode.ALREADY_VERIFIED.httpStatus)
                        .entity(ApiResponse.error(ErrorCode.ALREADY_VERIFIED.code, "Dokumen sudah ditinjau, tidak bisa diganti"))
                        .build();
            }

            String contentType = file.contentType() != null ? file.contentType() : "application/octet-stream";
            Long fileSize = Files.size(file.filePath());
            InputStream fileStream = Files.newInputStream(file.filePath());

            DocumentResponse docResponse = documentService.uploadFile(documentId, fileStream, fileSize, contentType);
            // Berkas baru harus diekstraksi ulang; hasil lama dibuang dan dokumen diantrekan lagi.
            documentService.resetForReprocessing(documentId);
            try {
                jobQueue.enqueue(documentId, owned.category);
            } catch (RuntimeException e) {
                LOG.errorf(e, "gagal memasukkan ulang dokumen %s ke antrian setelah penggantian berkas", documentId);
            }
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

    /** Kerangka field kosong untuk isi manual dokumen gagal (api-spec.md bagian 13). */
    @GET
    @Path("/{id}/template")
    public Response template(
            @PathParam("id") UUID id,
            @QueryParam("doc_type") String docType,
            @Context UriInfo uriInfo) {
        try {
            Document owned = documentService.findOwned(id, currentUser.userId());
            if (owned == null) {
                return Response.status(ErrorCode.DOCUMENT_NOT_FOUND.httpStatus)
                        .entity(ApiResponse.error(ErrorCode.DOCUMENT_NOT_FOUND.code, "Document not found"))
                        .build();
            }
            if (owned.processingStatus != org.tettyrs.entities.enums.ProcessingStatus.FAILED) {
                return Response.status(ErrorCode.CONFLICT.httpStatus)
                        .entity(ApiResponse.error(ErrorCode.CONFLICT.code,
                                "Kerangka isi manual hanya untuk dokumen yang ekstraksinya gagal."))
                        .build();
            }
            Map<String, Object> fields = org.tettyrs.service.FieldTemplates.forDocType(docType);
            if (fields == null) {
                return Response.status(ErrorCode.VALIDATION_ERROR.httpStatus)
                        .entity(ApiResponse.error(ErrorCode.VALIDATION_ERROR.code,
                                "doc_type harus SURAT_TUGAS, SPD, atau RECEIPT."))
                        .build();
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("schema_version", "1.0");
            data.put("doc_type", docType);
            data.put("fields", fields);
            return Response.ok(ApiResponse.success(data, new ApiResponse.ResponseMeta(uriInfo.getPath()))).build();
        } catch (Exception e) {
            LOG.error("Gagal membuat kerangka isi manual untuk " + id, e);
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(ApiResponse.error(ErrorCode.INTERNAL_ERROR.code, "Failed to build template"))
                    .build();
        }
    }

    /** Simpan hasil tinjau pengguna (api-spec bagian 5). Hanya pemilik dokumen yang boleh. */
    @PUT
    @Path("/{id}/verification")
    public Response verifyDocument(
            @PathParam("id") UUID id,
            com.fasterxml.jackson.databind.JsonNode body,
            @Context UriInfo uriInfo) {
        String userId = currentUser.userId();
        try {
            Document owned = documentService.findOwned(id, userId);
            if (owned == null) {
                return Response.status(ErrorCode.DOCUMENT_NOT_FOUND.httpStatus)
                        .entity(ApiResponse.error(ErrorCode.DOCUMENT_NOT_FOUND.code, "Document not found"))
                        .build();
            }
            Document verified = documentVerifier.verify(id, userId, body);
            // D-11: berkas dihapus setelah verifikasi commit. Gagal hapus tidak membatalkan verifikasi;
            // berkas tetap tercatat (s3_path) dan bisa dihapus ulang.
            try {
                filePurgeService.purge(id);
            } catch (RuntimeException e) {
                LOG.warnf("gagal menghapus berkas dokumen %s setelah verifikasi: %s", id, e.getMessage());
            }
            return Response.ok(ApiResponse.success(DocumentView.from(verified, fieldCipher),
                    new ApiResponse.ResponseMeta(uriInfo.getPath()))).build();
        } catch (DocumentVerifier.Rejected e) {
            ApiError error = new ApiError(e.code.code, e.getMessage());
            error.details = e.violations.isEmpty() ? null : e.violations;
            return Response.status(e.code.httpStatus).entity(ApiResponse.error(error)).build();
        } catch (Exception e) {
            LOG.error("Gagal menyimpan verifikasi dokumen " + id, e);
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(ApiResponse.error(ErrorCode.INTERNAL_ERROR.code, "Failed to save verification"))
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
            if ("identity".equals(doc.category)) {
                // KTP disimpan terenkripsi di aplikasi. Didekripsi hanya untuk pemilik dokumen.
                fileStream = new java.io.ByteArrayInputStream(
                        fieldCipher.decryptBytes(org.tettyrs.service.DocumentService.readAll(fileStream)));
            }

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
            // Hanya pemilik dokumen. Dokumen milik orang lain dijawab 404, sama seperti dokumen yang tidak ada.
            if (documentService.findOwned(documentId, currentUser.userId()) == null) {
                return Response.status(ErrorCode.DOCUMENT_NOT_FOUND.httpStatus)
                        .entity(ApiResponse.error(ErrorCode.DOCUMENT_NOT_FOUND.code, "Document not found"))
                        .build();
            }
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
            // Teks OCR mentah tidak dikirim ke klien: memuat data pribadi dan tidak dibutuhkan aplikasi.
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

}

package org.tettyrs.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.DocumentView;
import org.tettyrs.dto.ExtractionCallbackRequest;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.DocumentProcessingResult;
import org.tettyrs.entities.enums.DocumentStatus;
import org.tettyrs.entities.enums.DocumentType;
import org.tettyrs.entities.enums.ProcessingStatus;
import org.tettyrs.service.FieldCipher;
import org.tettyrs.service.InternalAuth;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Callback hasil ekstraksi dari ms-ocr (api-spec bagian 8). Bukan endpoint publik: wajib X-Internal-Token.
 * Idempoten: callback ulang untuk dokumen yang sudah final dijawab 200 tanpa perubahan.
 */
@Path("/internal/documents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InternalCallbackController {

    private static final Logger LOG = Logger.getLogger(InternalCallbackController.class);

    @Inject
    InternalAuth internalAuth;

    @Inject
    FieldCipher fieldCipher;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EntityManager em;

    @PUT
    @Path("/{id}/extraction")
    @Transactional
    public Response extractionCallback(
            @PathParam("id") UUID documentId,
            @HeaderParam(InternalAuth.HEADER) String token,
            ExtractionCallbackRequest req) {

        if (!internalAuth.isValid(token)) {
            return error(ErrorCode.UNAUTHENTICATED, "Token internal tidak valid");
        }
        if (req == null || req.status == null) {
            return error(ErrorCode.VALIDATION_ERROR, "status wajib diisi");
        }

        Document doc = Document.findById(documentId);
        if (doc == null) {
            return error(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found");
        }

        if (req.enrichment) {
            return enrich(doc, req.fields);
        }

        // Dokumen yang sudah final tidak ditimpa (idempoten, dan hasil yang sudah diperiksa manusia dilindungi).
        if (doc.status == DocumentStatus.VERIFIED || doc.processingStatus == ProcessingStatus.COMPLETED) {
            return ok(documentId, "unchanged");
        }

        DocumentProcessingResult result = new DocumentProcessingResult();
        result.documentId = documentId;
        result.processingStartedAt = LocalDateTime.now();
        result.processingCompletedAt = LocalDateTime.now();

        try {
            if ("failed".equals(req.status)) {
                result.processingStatus = ProcessingStatus.FAILED;
                result.errorMessage = req.errorCode != null ? req.errorCode : "EXTRACTION_FAILED";
                doc.processingStatus = ProcessingStatus.FAILED;
            } else if ("extracted".equals(req.status)) {
                boolean identity = "identity".equals(doc.category);
                String fieldsJson = objectMapper.writeValueAsString(req.fields == null ? Map.of() : req.fields);
                String ocrText = req.ocrText;

                // Semua data pribadi (teks OCR dan field) dienkripsi sebelum disimpan, untuk semua kategori
                // (PRD bagian 12). Surat tugas, SPD, dan struk memuat nama, NIP, dan nominal.
                if (ocrText != null) ocrText = fieldCipher.encrypt(ocrText);
                fieldsJson = fieldCipher.encrypt(fieldsJson);

                result.processingStatus = ProcessingStatus.COMPLETED;
                result.ocrText = ocrText;
                result.summary = null;
                doc.extractedJson = fieldsJson;
                doc.documentType = parseDocType(req.docType, doc.documentType);
                doc.processingStatus = ProcessingStatus.COMPLETED;
            } else {
                return error(ErrorCode.VALIDATION_ERROR, "status tidak dikenal");
            }
        } catch (JsonProcessingException e) {
            return error(ErrorCode.VALIDATION_ERROR, "fields tidak valid");
        } catch (IllegalStateException e) {
            // Kunci enkripsi belum diatur: penyimpanan data sensitif ditolak, tidak ada fallback teks polos.
            LOG.error("penyimpanan dokumen identitas ditolak: " + e.getMessage());
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(ApiResponse.error(ErrorCode.INTERNAL_ERROR.code, "Penyimpanan ditolak"))
                    .build();
        }

        em.persist(result);
        doc.persist();
        return ok(documentId, req.status);
    }

    /**
     * Pengayaan dari pendamping vision setelah hasil teks sudah tersimpan. Hanya field yang masih kosong yang diisi,
     * atau field yang dikirim dengan tanda "replace": true. Status dokumen tidak berubah. Dokumen yang sudah
     * diverifikasi tidak disentuh.
     */
    @SuppressWarnings("unchecked")
    private Response enrich(Document doc, Map<String, Object> incoming) {
        if (doc.status == DocumentStatus.VERIFIED || doc.processingStatus != ProcessingStatus.COMPLETED
                || doc.extractedJson == null || incoming == null) {
            return ok(doc.id, "unchanged");
        }
        try {
            String json = DocumentView.readStored(doc.extractedJson, fieldCipher);
            if (json == null) return ok(doc.id, "unchanged");
            Map<String, Object> existing = objectMapper.readValue(json, Map.class);
            boolean changed = false;
            for (Map.Entry<String, Object> e : incoming.entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?> leaf) || !leaf.containsKey("source")) continue;
                Map<String, Object> copy = new java.util.LinkedHashMap<>((Map<String, Object>) leaf);
                boolean replace = Boolean.TRUE.equals(copy.remove("replace"));
                Object cur = existing.get(e.getKey());
                boolean kosong = !(cur instanceof Map<?, ?> m) || m.get("value") == null;
                if (kosong || replace) {
                    existing.put(e.getKey(), copy);
                    changed = true;
                }
            }
            if (!changed) return ok(doc.id, "unchanged");
            doc.extractedJson = fieldCipher.encrypt(objectMapper.writeValueAsString(existing));
            doc.persist();
            return ok(doc.id, "enriched");
        } catch (JsonProcessingException e) {
            return error(ErrorCode.VALIDATION_ERROR, "fields tidak valid");
        } catch (IllegalStateException e) {
            LOG.error("pengayaan ditolak: kunci enkripsi belum diatur");
            return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus)
                    .entity(ApiResponse.error(ErrorCode.INTERNAL_ERROR.code, "Penyimpanan ditolak"))
                    .build();
        }
    }

    private static DocumentType parseDocType(String raw, DocumentType fallback) {
        if (raw == null) return fallback;
        try {
            return DocumentType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static Response ok(UUID documentId, String status) {
        return Response.ok(ApiResponse.success(Map.of("document_id", documentId, "status", status))).build();
    }

    private static Response error(ErrorCode code, String message) {
        return Response.status(code.httpStatus)
                .entity(ApiResponse.error(code.code, message))
                .build();
    }
}

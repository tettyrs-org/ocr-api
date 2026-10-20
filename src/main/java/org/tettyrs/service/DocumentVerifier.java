package org.tettyrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.enums.AuditAction;
import org.tettyrs.entities.enums.DocumentType;
import org.tettyrs.entities.enums.DocumentStatus;
import org.tettyrs.entities.enums.ProcessingStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Menyimpan hasil tinjau pengguna dan memindahkan dokumen ke VERIFIED (api-spec bagian 5, 7a).
 * Satu transaksi: tulis `verified_json`, ubah status, catat audit.
 *
 * Belum dikerjakan: baris `travel_assignments` (menunggu Surat Tugas) dan penghapusan berkas di MinIO (D-11).
 */
@ApplicationScoped
public class DocumentVerifier {

    /** Penolakan bisnis dengan kode error spesifikasi. */
    public static class Rejected extends RuntimeException {
        public final ErrorCode code;
        public final List<VerificationPayloadValidator.Violation> violations;

        public Rejected(ErrorCode code, String message, List<VerificationPayloadValidator.Violation> violations) {
            super(message);
            this.code = code;
            this.violations = violations == null ? List.of() : violations;
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    AuditEventService auditEventService;

    /** Nama peninjau untuk tampilan: "Nama (NIP ...)". Tanpa nama, dipakai "Peninjau". */
    public static String reviewerLabel(String fullName, String nip) {
        if (fullName == null || fullName.isBlank()) return "Peninjau";
        return nip == null || nip.isBlank() ? fullName.trim() : fullName.trim() + " (NIP " + nip.trim() + ")";
    }

    @Inject
    FieldCipher fieldCipher;

    /** Aturan tambahan untuk isi manual (api-spec.md bagian 13): jenis didukung, semua field manual, minimal satu terisi. */
    static List<VerificationPayloadValidator.Violation> manualViolations(JsonNode body) {
        List<VerificationPayloadValidator.Violation> out = new java.util.ArrayList<>(
                VerificationPayloadValidator.validate(body, null));
        JsonNode dt = body == null ? null : body.get("doc_type");
        if (dt == null || !dt.isTextual() || !FieldTemplates.DOC_TYPES.contains(dt.asText())) {
            out.add(new VerificationPayloadValidator.Violation("doc_type",
                    "Untuk dokumen gagal, doc_type harus SURAT_TUGAS, SPD, atau RECEIPT."));
        }
        if (body != null && body.has("fields")) {
            collectManual(body.get("fields"), "", out, new boolean[1]);
            boolean[] anyValue = new boolean[1];
            hasValue(body.get("fields"), anyValue);
            if (!anyValue[0]) {
                out.add(new VerificationPayloadValidator.Violation("fields",
                        "Isi minimal satu field dengan nilai."));
            }
        }
        return out;
    }

    private static void collectManual(JsonNode node, String path, List<VerificationPayloadValidator.Violation> out, boolean[] unused) {
        if (node.isObject() && node.has("source")) {
            if (!"manual".equals(node.path("source").asText())) {
                out.add(new VerificationPayloadValidator.Violation(path,
                        "Pada dokumen gagal, semua field harus bersumber manual."));
            }
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> collectManual(e.getValue(),
                    path.isEmpty() ? e.getKey() : path + "." + e.getKey(), out, unused));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) collectManual(node.get(i), path + "[" + i + "]", out, unused);
        }
    }

    private static void hasValue(JsonNode node, boolean[] found) {
        if (found[0] || node == null) return;
        if (node.isObject() && node.has("source")) {
            JsonNode v = node.get("value");
            if (v != null && !v.isNull() && !v.asText().isBlank()) found[0] = true;
            return;
        }
        if (node.isObject()) node.elements().forEachRemaining(c -> hasValue(c, found));
        else if (node.isArray()) node.forEach(c -> hasValue(c, found));
    }

    @Transactional
    public Document verify(UUID documentId, String actor, JsonNode body) {
        Document doc = Document.findById(documentId);
        if (doc == null) {
            throw new Rejected(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found", null);
        }
        if (doc.status == DocumentStatus.VERIFIED) {
            throw new Rejected(ErrorCode.ALREADY_VERIFIED, "Dokumen sudah diverifikasi.", null);
        }
        if (!"reimburse".equals(doc.category) && !"identity".equals(doc.category)) {
            // Dokumen `other` tidak punya field untuk ditinjau (lihat DocumentView.apiStatus).
            throw new Rejected(ErrorCode.CONFLICT, "Dokumen ini tidak memiliki field untuk ditinjau.", null);
        }
        ProcessingStatus processing = doc.processingStatus == null ? ProcessingStatus.PENDING : doc.processingStatus;
        if (processing == ProcessingStatus.PENDING || processing == ProcessingStatus.PROCESSING) {
            throw new Rejected(ErrorCode.EXTRACTION_IN_PROGRESS, "Ekstraksi masih berjalan.", null);
        }
        // Q-3 (2026-10-15): dokumen gagal boleh diisi manual. Jenis dipilih pengguna, semua field manual.
        boolean manual = processing == ProcessingStatus.FAILED;
        List<VerificationPayloadValidator.Violation> violations = manual
                ? manualViolations(body)
                : VerificationPayloadValidator.validate(body, doc.documentType == null ? null : doc.documentType.name());
        if (!violations.isEmpty()) {
            throw new Rejected(ErrorCode.VALIDATION_ERROR, "Hasil tinjau tidak valid.", violations);
        }
        if (manual) {
            doc.documentType = DocumentType.valueOf(body.get("doc_type").asText());
        }

        ObjectNode verified = MAPPER.createObjectNode();
        verified.put("schema_version", body.get("schema_version").asText());
        verified.put("doc_type", body.get("doc_type").asText());
        verified.set("fields", body.get("fields"));
        verified.put("verified_by", actor);
        // Nama untuk ditampilkan. UID tidak ditampilkan ke pengguna.
        org.tettyrs.entities.AppUser reviewer = org.tettyrs.entities.AppUser.findById(actor);
        verified.put("verified_by_name", reviewer == null
                ? reviewerLabel(null, null)
                : reviewerLabel(reviewer.fullName, reviewer.nip));
        verified.put("verified_at", LocalDateTime.now().toString());

        try {
            String json = MAPPER.writeValueAsString(verified);
            // Hasil tinjau memuat data pribadi, sehingga dienkripsi untuk semua kategori (PRD bagian 12).
            doc.verifiedJson = fieldCipher.encrypt(json);
        } catch (Exception e) {
            throw new IllegalStateException("Gagal menyimpan hasil tinjau", e);
        }
        doc.status = DocumentStatus.VERIFIED;
        doc.updatedAt = LocalDateTime.now();
        doc.persist();

        auditEventService.logAuditEvent(doc.id, AuditAction.CHANGED, actor, "Dokumen diverifikasi");
        return doc;
    }
}

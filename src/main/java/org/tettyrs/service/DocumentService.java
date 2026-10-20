package org.tettyrs.service;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.tettyrs.dto.DocumentRequest;
import org.tettyrs.dto.DocumentResponse;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.enums.AuditAction;
import org.tettyrs.entities.enums.DocumentStatus;
import org.tettyrs.entities.enums.DocumentType;
import org.tettyrs.entities.enums.ProcessingStatus;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class DocumentService extends PanacheEntity {

    @Inject
    AuditEventService auditEventService;
    @Inject
    S3Service s3Service;
    @Inject
    FieldCipher fieldCipher;

    @ConfigProperty(name = "tettyrs.s3.max-file-size-mb", defaultValue = "25")
    Long maxFileSIzeMb;

    @Transactional
    public DocumentResponse createDocument(DocumentRequest request) {
        Document doc = new Document();
        doc.filename = request.filename;
        doc.originalFilename = request.filename;
        doc.fileSize = request.fileSize;
        doc.mimeType = request.mimeType;
        doc.documentType = request.documentType;
        doc.status = DocumentStatus.PENDING;
        doc.createdBy = request.createdBy;
        doc.persist();

        // log audit event
        auditEventService.logAuditEvent(doc.id, AuditAction.UPLOADED, request.createdBy, null);

        return toResponse(doc);
    }

    public DocumentResponse getDocumentById(UUID id) {
        Document doc = Document.findById(id);
        if (doc == null) {
            throw new IllegalArgumentException("Document not found: " + id);
        }
        return toResponse(doc);
    }

    public List<DocumentResponse> listDocuments() {
        return ((List<Document>) (List<?>) Document.listAll())
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public void updateDocumentStatus(UUID id, DocumentStatus status){
        Document document = Document.findById(id);
        document.status = status;
        document.persist();

        // log audit event
        auditEventService.logAuditEvent(id, AuditAction.CHANGED, "system", null);
    }

    /** Penghapusan lunak oleh pemilik. Baris dan jejak audit tetap ada; dokumen tidak lagi tampil. */
    @Transactional
    public void deleteDocument(UUID id, String uploader) {
        Document doc = findOwned(id, uploader);
        if (doc == null) {
            throw new IllegalArgumentException("Document not found: " + id);
        }
        doc.deletedAt = java.time.LocalDateTime.now();
        doc.persist();

        auditEventService.logAuditEvent(id, AuditAction.DELETED, uploader, "Dihapus oleh pengguna");
    }

    /** Hapus hasil ekstraksi lama dan antrikan ulang setelah berkas diganti. */
    @Transactional
    public void resetForReprocessing(UUID documentId) {
        Document doc = Document.findById(documentId);
        if (doc == null) return;
        doc.extractedJson = null;
        doc.verifiedJson = null;
        doc.processingStatus = ProcessingStatus.PROCESSING;
        doc.persist();
    }

    @Transactional
    public DocumentResponse uploadFile(UUID documentId, InputStream fileStream, Long fileSize, String contentType){
        //validation
        if (fileSize > maxFileSIzeMb * 1024 * 1024) {
            throw new IllegalArgumentException("File too large. Max: "+maxFileSIzeMb+ "MB");
        }
        if (!isAllowedContentType(contentType)) {
            throw new IllegalArgumentException("Content-type not allowed: "+contentType);
        }

        Document doc = Document.findById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("Document not found: "+documentId);
        }
        //s3 generate key
        String s3Key = FilePurgeService.s3KeyFor(documentId, doc.filename);
        //upload to s3
        // KTP: berkas dienkripsi di aplikasi sebelum disimpan (sampai KES aktif, lihat kes-minio.md).
        byte[] content = readAll(fileStream);
        if ("identity".equals(doc.category)) content = fieldCipher.encryptBytes(content);
        String s3Path = s3Service.uploadFile(s3Key, new ByteArrayInputStream(content), (long) content.length, contentType);
        doc.s3Path = s3Path;
        doc.fileSize = fileSize;
        doc.mimeType = contentType;
        doc.persist();

        //log audit event
        auditEventService.logAuditEvent(documentId, AuditAction.UPLOADED, doc.createdBy, "File uploaded to s3");
        return  toResponse(doc);
    }

    /** Daftar dokumen milik pengguna, terbaru dulu (api-spec bagian 3). Halaman dimulai dari 1. */
    public List<Document> listForUser(String uploader, int page, int limit) {
        return Document.<Document>find("createdBy = ?1 and deletedAt is null order by createdAt desc", uploader)
                .page(page - 1, limit)
                .list();
    }

    public long countForUser(String uploader) {
        return Document.count("createdBy = ?1 and deletedAt is null", uploader);
    }

    /** Dokumen milik pengguna, atau null bila tidak ada atau milik orang lain (tidak dibedakan, agar tidak bocor). */
    public Document findOwned(UUID id, String uploader) {
        Document doc = Document.findById(id);
        if (doc == null || doc.deletedAt != null || !uploader.equals(doc.createdBy)) return null;
        return doc;
    }

    /** Hasil unggahan: dokumen dan apakah baru dibuat (false bila kunci idempotensi sudah pernah dipakai). */
    public record UploadOutcome(Document document, boolean created) {
    }

    private static final Set<String> CATEGORIES = Set.of("identity", "reimburse", "other");
    private static final String DOC_REF_ALPHABET = "0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Membuat dokumen dari unggahan multipart (api-spec bagian 2). Validasi di server, idempotensi per pengunggah,
     * berkas disimpan ke S3. Pemrosesan dimulai pemanggil setelah transaksi ini commit.
     */
    @Transactional
    public UploadOutcome createFromUpload(String uploader, String category, String docTypeHint,
                                          String idempotencyKey, String filename, byte[] bytes) {
        if (category == null || !CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("category harus identity, reimburse, atau other");
        }

        // Percobaan ulang dengan kunci yang sama mengembalikan dokumen yang sama, tanpa memproses ulang.
        if (idempotencyKey != null) {
            Document existing = Document.find("createdBy = ?1 and uploadIdempotencyKey = ?2",
                    uploader, idempotencyKey).firstResult();
            if (existing != null) {
                return new UploadOutcome((Document) existing, false);
            }
        }

        UploadValidator.Result check = UploadValidator.validate(bytes);
        DocumentType hinted = resolveHint(category, docTypeHint);

        Document doc = new Document();
        doc.filename = safeFilename(filename, check.kind());
        doc.originalFilename = filename;
        doc.fileSize = (long) bytes.length;
        doc.mimeType = check.kind().mimeType;
        doc.status = DocumentStatus.PENDING;
        doc.processingStatus = ProcessingStatus.PENDING;
        doc.documentType = hinted != null ? hinted : defaultType(category);
        doc.createdBy = uploader;
        doc.category = category;
        doc.uploadIdempotencyKey = idempotencyKey;
        doc.docRef = newDocRef();
        doc.persist();

        String s3Key = FilePurgeService.s3KeyFor(doc.id, doc.filename);
        // KTP: berkas dienkripsi di aplikasi sebelum disimpan (sampai KES aktif, lihat kes-minio.md).
        byte[] stored = "identity".equals(doc.category) ? fieldCipher.encryptBytes(bytes) : bytes;
        doc.s3Path = s3Service.uploadFile(s3Key, new ByteArrayInputStream(stored), (long) stored.length, check.kind().mimeType);
        doc.persist();

        auditEventService.logAuditEvent(doc.id, AuditAction.UPLOADED, uploader, "File uploaded to s3");
        return new UploadOutcome(doc, true);
    }

    /** Membaca seluruh aliran. Berkas dibatasi ukurannya sebelum sampai sini, jadi aman dibaca ke memori. */
    public static byte[] readAll(InputStream in) {
        try {
            return in.readAllBytes();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Jenis dari hint hanya diterima bila sesuai kategori. Di luar itu ditolak, bukan ditebak. */
    private DocumentType resolveHint(String category, String hint) {
        if (hint == null || hint.isBlank()) return null;
        DocumentType type;
        try {
            type = DocumentType.valueOf(hint.trim());
        } catch (IllegalArgumentException e) {
            throw new UploadValidator.RejectedException(ErrorCode.DOC_TYPE_NOT_SUPPORTED, "Jenis dokumen tidak dikenal.");
        }
        boolean allowed = switch (category) {
            case "identity" -> type == DocumentType.IDENTITY_CARD;
            case "reimburse" -> type == DocumentType.REIMBURSEMENT_FUEL
                    || type == DocumentType.PARKING_CARD
                    || type == DocumentType.RECEIPT
                    || type == DocumentType.SURAT_TUGAS
                    || type == DocumentType.SPD;
            case "other" -> type == DocumentType.OTHER;
            default -> false;
        };
        if (!allowed) {
            throw new UploadValidator.RejectedException(ErrorCode.DOC_TYPE_NOT_SUPPORTED,
                    "Jenis dokumen ini belum diaktifkan untuk kategori yang dipilih.");
        }
        return type;
    }

    private DocumentType defaultType(String category) {
        return switch (category) {
            case "identity" -> DocumentType.IDENTITY_CARD;
            case "other" -> DocumentType.OTHER;
            default -> DocumentType.UNKNOWN; // reimburse: jenis ditentukan setelah OCR
        };
    }

    /** Nomor rujukan DOC-######## yang unik. Tabrakan diulang sampai tidak ada yang sama. */
    private String newDocRef() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("DOC-");
            for (int i = 0; i < 8; i++) {
                sb.append(DOC_REF_ALPHABET.charAt(RANDOM.nextInt(DOC_REF_ALPHABET.length())));
            }
            String ref = sb.toString();
            if (Document.count("docRef", ref) == 0) return ref;
        }
        throw new IllegalStateException("Gagal membuat nomor rujukan unik");
    }

    /** Nama berkas aman untuk kunci S3: hanya karakter aman, dengan ekstensi sesuai jenis sebenarnya. */
    private static String safeFilename(String original, UploadValidator.Kind kind) {
        String base = original == null ? "berkas" : original.replaceAll(".*[/\\\\]", "");
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.length() > 120) base = base.substring(base.length() - 120);
        if (base.isBlank()) base = "berkas";
        String ext = switch (kind) {
            case JPEG -> ".jpg";
            case PNG -> ".png";
            case PDF -> ".pdf";
        };
        return base.toLowerCase().endsWith(ext) ? base : base + ext;
    }

    private DocumentResponse toResponse(Document doc) {
        DocumentResponse response = new DocumentResponse();
        response.id = doc.id;
        response.filename = doc.filename;
        response.fileSize = doc.fileSize;
        response.mimeType = doc.mimeType;
        response.s3Path = doc.s3Path;
        response.status = doc.status;
        response.documentType = doc.documentType;
        response.createdBy = doc.createdBy;
        response.createdAt = doc.createdAt;
        response.updatedAt = doc.updatedAt;
        return response;
    }

    // helper method to validate content type
    private boolean isAllowedContentType(String contentType){
        return contentType != null && (
                contentType.equals("application/pdf") ||
                contentType.equals("image/jpeg") ||
                contentType.equals("image/png") ||
                contentType.equals("application/octet-stream")
                );
    }
}

package org.tettyrs.entities;


import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.tettyrs.entities.enums.DocumentStatus;
import org.tettyrs.entities.enums.DocumentType;
import org.tettyrs.entities.enums.ProcessingStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ocr_documents", indexes ={
        @Index(name = "idx_ocr_document_type", columnList = "document_type"),
        @Index(name = "idx_ocr_created_by", columnList = "created_by"),
        @Index(name = "idx_ocr_created_at", columnList = "created_at")
} )

@NoArgsConstructor
public class Document extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(nullable = false, name = "filename")
    public String filename;

    @Column(name = "original_filename")
    public String originalFilename; // backup filename

    @Column(nullable = false, name = "file_size")
    public Long fileSize; // bytes

    @Column(nullable = false, name = "mime_type")
    public String mimeType;

    @Column(name = "s3_path")
    public String s3Path;

    @Column(nullable = false, name = "status")
    @Enumerated(EnumType.STRING)
    public DocumentStatus status = DocumentStatus.PENDING;

    @Column(nullable = false, name = "document_type")
    @Enumerated(EnumType.STRING)
    public DocumentType documentType;

    @Column(nullable = false, name = "created_by")
    public String createdBy; // userId

    // Nomor rujukan untuk manusia, ditampilkan ke pengguna (api-spec bagian 2).
    @Column(nullable = false, name = "doc_ref", unique = true, length = 12)
    public String docRef;

    // Kategori dari S4a: identity | reimburse | other. Menentukan cara pemrosesan.
    @Column(name = "category", length = 16)
    public String category;

    // Field hasil ekstraksi (JSON). Untuk identitas diri, dienkripsi (FieldCipher).
    @Column(name = "extracted_json", columnDefinition = "TEXT")
    public String extractedJson;

    // Hasil tinjau pengguna (api-spec bagian 5). Terisi saat dokumen menjadi VERIFIED.
    @Column(name = "verified_json", columnDefinition = "TEXT")
    public String verifiedJson;

    // Kunci Idempotency-Key dari klien. Unik per pengunggah.
    @Column(name = "upload_idempotency_key", length = 64)
    public String uploadIdempotencyKey;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, name = "created_at")
    public LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, name = "updated_at")
    public LocalDateTime updatedAt;

    /** Diisi saat pengguna menghapus dokumen (penghapusan lunak). Null berarti masih aktif. */
    @Column(name = "deleted_at")
    public LocalDateTime deletedAt;

    /** Diisi saat berkas asli dihapus dari object storage setelah dokumen final (D-11). */
    @Column(name = "file_purged_at")
    public LocalDateTime filePurgedAt;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<AuditEvent> auditEvents;

    @OneToOne(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    public TravelAssignment travelAssignment;

    @Column(name = "processing_status")
    @Enumerated(EnumType.STRING)
    public ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @Column(name = "processing_result_id")
    public Long processingResultId;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<DocumentVerification> verifications;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<TelemetryTiming> telemetryTimings;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<ExtractionCallback> extractionCallbacks;

}

package org.tettyrs.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.persistence.EntityManager;
import org.tettyrs.dto.ClassificationResult;
import org.tettyrs.dto.OcrResult;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.DocumentProcessingResult;
import org.tettyrs.entities.AuditEvent;
import org.tettyrs.entities.enums.DocumentType;
import org.tettyrs.entities.enums.ProcessingStatus;
import org.tettyrs.entities.enums.AuditAction;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class ProcessingService {

    @Inject
    OcrService ocrService;

    @Inject
    LlmService llmService;

    @Inject
    FieldCipher fieldCipher;

    @Inject
    EntityManager em;

    @Inject
    JobQueue jobQueue;

    /**
     * Memetakan kategori hasil klasifikasi ke jenis dokumen reimburse. Nilai yang tidak dikenali
     * menjadi RECEIPT (kwitansi umum), bukan ditebak ke jenis lain.
     */
    static DocumentType mapReimburseType(String classified) {
        if (classified == null) return DocumentType.RECEIPT;
        String c = classified.toLowerCase(java.util.Locale.ROOT);
        // Surat Tugas dan SPD termasuk kategori reimburse: keduanya inti MVP (PRD bagian 4).
        if (c.contains("surat tugas") || c.contains("surat_tugas")) return DocumentType.SURAT_TUGAS;
        if (c.contains("spd") || c.contains("perjalanan dinas")) return DocumentType.SPD;
        if (c.contains("bensin") || c.contains("bbm") || c.contains("fuel")) return DocumentType.REIMBURSEMENT_FUEL;
        if (c.contains("parkir") || c.contains("parking")) return DocumentType.PARKING_CARD;
        return DocumentType.RECEIPT;
    }

    /** Kategori yang field-nya diekstrak ms-ocr lewat antrean (KTP dan reimburse). Kategori lain diproses lokal. */
    static boolean usesEngineExtraction(String category) {
        return "identity".equals(category) || "reimburse".equals(category);
    }

    // Transaksi di sini, bukan di requeueForExtraction: pemanggilan dari dalam kelas melewati proxy,
    // sehingga anotasi di method itu tidak aktif dan persist() gagal tanpa transaksi.
    @Transactional
    public void startProcessing(UUID documentId) {
        Document doc = (Document) Document.findById(documentId);
        if (doc != null && usesEngineExtraction(doc.category)) {
            // KTP dan reimburse: field diekstrak ms-ocr, lalu disimpan lewat callback. Jalur lokal di bawah
            // tidak menulis field, jadi dokumen bisa ditandai selesai tanpa hasil ekstraksi.
            requeueForExtraction(documentId);
            return;
        }
        CompletableFuture.runAsync(() -> processDocument(documentId));
    }

    @Transactional
    protected void requeueForExtraction(UUID documentId) {
        Document doc = (Document) Document.findById(documentId);
        if (doc == null) return;
        // Status COMPLETED membuat callback diabaikan (unchanged), jadi dikembalikan ke PROCESSING dulu.
        doc.processingStatus = ProcessingStatus.PROCESSING;
        doc.persist();
        jobQueue.enqueue(documentId, doc.category);
    }

    @Transactional
    protected void processDocument(UUID documentId) {
        Document doc = (Document) Document.findById(documentId);
        if (doc == null) return;

        DocumentProcessingResult result = new DocumentProcessingResult();
        result.documentId = documentId;
        result.processingStatus = ProcessingStatus.PROCESSING;
        result.processingStartedAt = LocalDateTime.now();
        em.persist(result);

        doc.processingStatus = ProcessingStatus.PROCESSING;
        doc.persist();

        try {
            OcrResult ocrResult = ocrService.extractText(doc.filename, null);
            // Teks OCR selalu disimpan terenkripsi (AES-256-GCM), untuk semua kategori (PRD bagian 12).
            result.ocrText = fieldCipher.encrypt(ocrResult.text);
            result.ocrConfidence = ocrResult.confidence;

            if ("identity".equals(doc.category)) {
                // Identitas diri TIDAK dikirim ke LLM. Masking NIK dan batas retensi menunggu OQ-8.
            } else {
                ClassificationResult classResult = llmService.classify(ocrResult.text, doc.documentType.toString());
                if ("reimburse".equals(doc.category)) {
                    // Reimburse: jenis dikenali otomatis dari hasil klasifikasi (keputusan pengguna 2026-10-14).
                    doc.documentType = mapReimburseType(classResult.category);
                }
                result.classificationCategory = classResult.category;
                result.classificationConfidence = classResult.confidence;
                result.summary = classResult.summary;
            }

            result.processingStatus = ProcessingStatus.COMPLETED;
            result.processingCompletedAt = LocalDateTime.now();
            em.merge(result);

            doc.processingStatus = ProcessingStatus.COMPLETED;
            doc.persist();

            AuditEvent audit = new AuditEvent();
            audit.document = doc;
            audit.action = AuditAction.PROCESSING_COMPLETED;
            audit.details = "Document processed successfully";
            audit.actor = "system";
            audit.persist();

        } catch (Exception e) {
            result.processingStatus = ProcessingStatus.FAILED;
            result.errorMessage = e.getMessage();
            result.processingCompletedAt = LocalDateTime.now();
            em.merge(result);

            doc.processingStatus = ProcessingStatus.FAILED;
            doc.persist();

            AuditEvent audit = new AuditEvent();
            audit.document = doc;
            audit.action = AuditAction.PROCESSING_FAILED;
            audit.details = "Error: " + e.getMessage();
            audit.actor = "system";
            audit.persist();
        }
    }
}

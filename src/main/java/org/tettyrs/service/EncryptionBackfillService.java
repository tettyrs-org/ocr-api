package org.tettyrs.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.tettyrs.entities.AuditEvent;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.DocumentProcessingResult;
import org.tettyrs.entities.enums.AuditAction;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

/**
 * Enkripsi ulang data lama yang masih teks biasa (PRD bagian 12). Dijalankan admin lewat endpoint, dan
 * default-nya hanya simulasi: {@code apply=false} menghitung tanpa mengubah apa pun. Setiap dokumen yang
 * diubah dicatat di audit. Data yang sudah terenkripsi dilewati, sehingga aman dijalankan ulang.
 */
@ApplicationScoped
public class EncryptionBackfillService {

    @Inject
    FieldCipher fieldCipher;

    @Inject
    S3Service s3Service;

    @Inject
    AuditEventService auditEventService;

    public record Report(boolean applied, int documents, int textFields, int files, int processingResults) {
    }

    /** Teks yang perlu dienkripsi: ada isinya dan belum berprefiks enkripsi. */
    static boolean needsText(String v) {
        return v != null && !FieldCipher.isEncryptedText(v);
    }

    /** Berkas KTP yang perlu dienkripsi: sudah dibaca dan belum berawalan magic. */
    static boolean needsFile(byte[] b) {
        return b != null && !FieldCipher.hasFileMagic(b);
    }

    @Transactional
    public Report run(boolean apply, String actor) {
        int docs = 0;
        int texts = 0;
        int files = 0;
        List<Document> all = Document.listAll();
        for (Document d : all) {
            boolean touched = false;
            if (needsText(d.extractedJson)) {
                texts++;
                touched = true;
                if (apply) d.extractedJson = fieldCipher.encrypt(d.extractedJson);
            }
            if (needsText(d.verifiedJson)) {
                texts++;
                touched = true;
                if (apply) d.verifiedJson = fieldCipher.encrypt(d.verifiedJson);
            }
            // Berkas: KTP saja, sesuai kebijakan enkripsi berkas di tingkat aplikasi (kes-minio.md bagian 9).
            if ("identity".equals(d.category) && d.s3Path != null) {
                byte[] bytes = DocumentService.readAll(s3Service.downloadFile(s3Service.extractKeyFromPath(d.s3Path)));
                if (needsFile(bytes)) {
                    files++;
                    touched = true;
                    if (apply) {
                        byte[] enc = fieldCipher.encryptBytes(bytes);
                        s3Service.uploadFile(FilePurgeService.s3KeyFor(d.id, d.filename),
                                new ByteArrayInputStream(enc), (long) enc.length, d.mimeType);
                    }
                }
            }
            if (touched) {
                docs++;
                if (apply) {
                    d.persist();
                    auditEventService.logAuditEvent(d.id, AuditAction.DATA_ENCRYPTED, actor,
                            "Data lama dienkripsi (backfill)");
                }
            }
        }

        int results = 0;
        List<DocumentProcessingResult> all2 = DocumentProcessingResult.listAll();
        for (DocumentProcessingResult r : all2) {
            if (needsText(r.ocrText)) {
                results++;
                if (apply) {
                    r.ocrText = fieldCipher.encrypt(r.ocrText);
                    r.persist();
                }
            }
        }
        return new Report(apply, docs, texts, files, results);
    }
}

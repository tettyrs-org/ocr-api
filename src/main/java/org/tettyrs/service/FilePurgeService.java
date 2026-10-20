package org.tettyrs.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.enums.AuditAction;
import org.tettyrs.entities.enums.DocumentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Penghapusan berkas asli di object storage (D-11). Berkas hanya hidup selama dokumen belum final;
 * setelah verifikasi, yang tersisa adalah data terstruktur di PostgreSQL.
 *
 * Dua jalur: langsung setelah verifikasi (controller), dan job berkala yang mengejar sisa berkas
 * yang gagal dihapus serta berkas yang sudah melewati batas usia (`tettyrs.s3.file-expiry-days`).
 */
@ApplicationScoped
public class FilePurgeService {

    private static final Logger LOG = Logger.getLogger(FilePurgeService.class);

    @Inject
    S3Service s3Service;

    @Inject
    AuditEventService auditEventService;

    // Batas usia berkas dokumen yang belum final. Angka 7 adalah usulan awal, dan perlu persetujuan (OQ-8).
    @ConfigProperty(name = "tettyrs.s3.file-expiry-days", defaultValue = "7")
    int fileExpiryDays;

    /** Kunci objek di object storage. Dipakai saat unggah dan saat hapus, agar keduanya selalu sama. */
    public static String s3KeyFor(UUID documentId, String filename) {
        return "documents/" + documentId + "/" + filename;
    }

    /** Batas usia: berkas yang dibuat sebelum titik ini sudah melewati masa hidupnya. */
    public static LocalDateTime ageCutoff(LocalDateTime now, int days) {
        return now.minusDays(days);
    }

    /**
     * Menghapus berkas dan mencatat `file_purged_at`. Dipanggil setelah transaksi verifikasi commit.
     * Bila hapus gagal, pengecualian diteruskan dan `s3_path` dibiarkan, sehingga bisa dicoba lagi.
     */
    @Transactional
    public void purge(UUID documentId) {
        Document doc = (Document) Document.findById(documentId);
        if (doc == null || doc.s3Path == null) return;

        s3Service.deleteFile(s3KeyFor(doc.id, doc.filename));
        doc.s3Path = null;
        doc.filePurgedAt = LocalDateTime.now();
        doc.persist();
        auditEventService.logAuditEvent(doc.id, AuditAction.FILE_PURGED, "system",
                "Berkas asli dihapus setelah dokumen final (D-11)");
    }

    /**
     * Mengejar berkas yang masih tersisa: dokumen terverifikasi yang gagal dihapus sebelumnya, dan dokumen
     * lain yang sudah melewati batas usia. Kegagalan satu dokumen dicatat dan tidak menghentikan yang lain.
     * Mengembalikan jumlah berkas yang berhasil dihapus.
     */
    @Transactional
    public int purgePending(LocalDateTime now) {
        LocalDateTime cutoff = ageCutoff(now, fileExpiryDays);
        List<Document> docs = Document.<Document>find(
                "s3Path is not null and (status = ?1 or createdAt < ?2)",
                DocumentStatus.VERIFIED, cutoff).list();

        int purged = 0;
        for (Document doc : docs) {
            try {
                purge(doc.id);
                purged++;
            } catch (RuntimeException e) {
                LOG.warnf("gagal menghapus berkas dokumen %s, dicoba lagi pada putaran berikutnya: %s",
                        doc.id, e.getMessage());
            }
        }
        return purged;
    }
}

package org.tettyrs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.enums.DocumentStatus;
import org.tettyrs.entities.enums.DocumentType;
import org.tettyrs.service.FieldCipher;
import org.tettyrs.entities.enums.ProcessingStatus;

import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/**
 * Bentuk dokumen yang dikembalikan ke klien (api-spec bagian 2–4). Nilai status dan jenis
 * diterjemahkan dari model internal di sini, sehingga klien hanya melihat nilai spesifikasi.
 */
public class DocumentView {

    public static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");

    @JsonProperty("document_id")
    public UUID documentId;

    @JsonProperty("doc_ref")
    public String docRef;

    @JsonProperty("doc_type")
    public String docType;

    @JsonProperty("status")
    public String status;

    @JsonProperty("created_at")
    public String createdAt;

    @JsonProperty("summary")
    public Map<String, Object> summary;

    @JsonProperty("file")
    public Map<String, Object> file;

    /** Envelope ekstraksi. Null untuk identitas (ditahan, lihat OQ-8) dan bila belum selesai. */
    @JsonProperty("extraction")
    public Object extraction;

    /** Hasil tinjau pengguna. Null bila belum diverifikasi. */
    @JsonProperty("verification")
    public Object verification;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(DocumentView.class);

    /** Bentuk klien. `cipher` dipakai untuk mendekripsi field identitas milik pemilik (api-spec.md bagian 14). */
    public static DocumentView from(Document d, FieldCipher cipher) {
        DocumentView v = new DocumentView();
        v.documentId = d.id;
        v.docRef = d.docRef;
        v.docType = d.documentType == null ? "UNKNOWN" : d.documentType.name();
        v.status = apiStatus(d);
        // Waktu lokal server disimpan sebagai WIB (Asia/Jakarta). Dikirim dengan offset +07:00 supaya klien tidak salah zona.
        v.createdAt = d.createdAt == null ? null : d.createdAt.atZone(JAKARTA).toOffsetDateTime().toString();
        // Penanda field perlu ditinjau belum dihitung; S9 (tinjau dan koreksi) belum dibangun.
        v.summary = Map.of("fields_need_review", 0);
        v.file = fileInfo(d);
        boolean identity = d.documentType == DocumentType.IDENTITY_CARD;
        boolean reimburse = "reimburse".equals(d.category);
        v.extraction = (identity || reimburse) && d.processingStatus == ProcessingStatus.COMPLETED
                ? envelope(d, identity, cipher) : null;
        // Hasil tinjau memuat data pribadi: disimpan terenkripsi dan hanya didekripsi untuk pemilik.
        v.verification = parse(readStored(d.verifiedJson, cipher));
        return v;
    }

    /** Metadata berkas. `url` null bila berkas sudah dihapus (D-11). Jumlah halaman belum disimpan. */
    private static Map<String, Object> fileInfo(Document d) {
        boolean available = d.s3Path != null;
        java.util.HashMap<String, Object> info = new java.util.HashMap<>();
        info.put("mime_type", d.mimeType);
        info.put("size_bytes", d.fileSize);
        info.put("page_count", null);
        info.put("available", available);
        info.put("url", available ? "/api/v1/documents/" + d.id + "/file" : null);
        return info;
    }

    /** Envelope ekstraksi (extraction-schema.md bagian 2): schema_version, doc_type, dan fields. */
    private static Object envelope(Document d, boolean identity, FieldCipher cipher) {
        // Field tersimpan terenkripsi untuk semua kategori; didekripsi hanya di sini, untuk pemilik dokumen.
        String json = readStored(d.extractedJson, cipher);
        Object fields = parse(json);
        if (fields == null) return null;
        java.util.LinkedHashMap<String, Object> env = new java.util.LinkedHashMap<>();
        env.put("schema_version", "1.0");
        env.put("doc_type", d.documentType == null ? null : d.documentType.name());
        env.put("fields", fields);
        return env;
    }

    /**
     * Membaca nilai tersimpan. Nilai terenkripsi didekripsi. Nilai tanpa prefiks (data sebelum enkripsi
     * diterapkan ke kategori ini) dikembalikan apa adanya, supaya dokumen lama tetap bisa dibuka.
     */
    public static String readStored(String stored, FieldCipher cipher) {
        if (stored == null) return null;
        if (!stored.startsWith(FieldCipher.PREFIX)) return stored;
        if (cipher == null) return null;
        try {
            return cipher.decrypt(stored);
        } catch (RuntimeException e) {
            // Jangan catat nilainya. Gagal dekripsi berarti data tidak bisa ditampilkan, jadi perlu terlihat di log.
            LOG.warnf("dekripsi data dokumen gagal: %s", e.getClass().getSimpleName());
            return null;
        }
    }

    private static Object parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Status spesifikasi: processing | extracted | verified | extraction_failed.
     * Reimburse dan identitas punya field yang harus ditinjau pemilik. Dokumen `other` tidak punya field,
     * jadi selesai OCR berarti tersimpan.
     */
    public static String apiStatus(Document d) {
        if (d.status == DocumentStatus.VERIFIED) return "verified";
        if (d.processingStatus == ProcessingStatus.FAILED) return "extraction_failed";
        if (d.processingStatus == ProcessingStatus.COMPLETED) {
            return "reimburse".equals(d.category) || "identity".equals(d.category) ? "extracted" : "verified";
        }
        return "processing";
    }
}

package org.tettyrs.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kerangka field kosong untuk isi manual dokumen gagal (api-spec.md bagian 13). Bentuknya mengikuti
 * extraction-schema.md bagian 4 agar tinjauan dan hasil ekstraksi memakai struktur yang sama.
 */
public final class FieldTemplates {

    public static final Set<String> DOC_TYPES = Set.of("SURAT_TUGAS", "SPD", "RECEIPT");

    private FieldTemplates() {
    }

    /** Null bila jenis dokumen tidak didukung untuk isi manual. */
    public static Map<String, Object> forDocType(String docType) {
        if (docType == null) return null;
        return switch (docType) {
            case "SURAT_TUGAS" -> suratTugas();
            case "SPD" -> spd();
            case "RECEIPT" -> receipt();
            default -> null;
        };
    }

    private static Map<String, Object> suratTugas() {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("nomor_surat", leaf());
        f.put("tanggal_surat", leaf());
        f.put("dasar_penugasan", leaf());
        Map<String, Object> pemberi = new LinkedHashMap<>();
        pemberi.put("nama", leaf());
        pemberi.put("jabatan", leaf());
        f.put("pemberi_tugas", pemberi);
        f.put("pegawai", List.of(pegawai(false)));
        f.put("uraian_tugas", leaf());
        f.put("tempat_tujuan", leaf());
        f.put("tanggal_mulai", leaf());
        f.put("tanggal_selesai", leaf());
        f.put("sumber_biaya", leaf());
        return f;
    }

    private static Map<String, Object> spd() {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("nomor_spd", leaf());
        f.put("tanggal_spd", leaf());
        Map<String, Object> pbk = new LinkedHashMap<>();
        pbk.put("nama", leaf());
        f.put("pejabat_pembuat_komitmen", pbk);
        f.put("pegawai", List.of(pegawai(true)));
        f.put("maksud_perjalanan", leaf());
        f.put("alat_angkut", leaf());
        f.put("tempat_kedudukan", leaf());
        f.put("tempat_tujuan", leaf());
        f.put("tanggal_berangkat", leaf());
        f.put("tanggal_harus_kembali", leaf());
        f.put("lama_perjalanan_hari", leaf());
        f.put("pengikut", new ArrayList<Map<String, Object>>());
        Map<String, Object> anggaran = new LinkedHashMap<>();
        anggaran.put("instansi", leaf());
        anggaran.put("akun", leaf());
        f.put("pembebanan_anggaran", anggaran);
        f.put("keterangan", leaf());
        return f;
    }

    private static Map<String, Object> receipt() {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("tanggal", leaf());
        f.put("penerbit", leaf());
        f.put("nomor_referensi", leaf());
        f.put("nominal_total", leaf());
        f.put("kategori", leaf());
        return f;
    }

    private static Map<String, Object> pegawai(boolean withTingkat) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("nama", leaf());
        p.put("nip", leaf());
        p.put("pangkat_golongan", leaf());
        p.put("jabatan", leaf());
        if (withTingkat) p.put("tingkat_biaya_perjalanan", leaf());
        return p;
    }

    /** Daun kosong: value null, source manual, confidence 0 (extraction-schema.md bagian 1). */
    static Map<String, Object> leaf() {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("value", null);
        l.put("confidence", 0.0);
        l.put("source", "manual");
        l.put("raw_text", null);
        l.put("correction", null);
        l.put("page", 1);
        l.put("bbox", null);
        return l;
    }
}

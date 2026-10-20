package org.tettyrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Isi manual dokumen gagal (api-spec.md bagian 13) dan kerangka template. */
class ManualVerificationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String s) throws Exception {
        return MAPPER.readTree(s);
    }

    @Test
    void templateSuratTugasPunyaPegawaiTanpaTingkatBiaya() {
        Map<String, Object> f = FieldTemplates.forDocType("SURAT_TUGAS");
        assertNotNull(f);
        assertTrue(f.containsKey("pegawai"));
        assertFalse(MAPPER.valueToTree(f.get("pegawai")).get(0).has("tingkat_biaya_perjalanan"));
    }

    @Test
    void templateSpdPunyaTingkatBiayaDanPengikutKosong() throws Exception {
        JsonNode spd = MAPPER.valueToTree(FieldTemplates.forDocType("SPD"));
        assertTrue(spd.get("pegawai").get(0).has("tingkat_biaya_perjalanan"));
        assertEquals(0, spd.get("pengikut").size());
        assertEquals("manual", spd.get("nomor_spd").get("source").asText());
        assertTrue(spd.get("nomor_spd").get("value").isNull());
    }

    @Test
    void jenisTidakDidukungTanpaTemplate() {
        assertEquals(null, FieldTemplates.forDocType("UNKNOWN"));
        assertEquals(null, FieldTemplates.forDocType("IDENTITY_CARD"));
    }

    @Test
    void manualDenganSatuNilaiDiterima() throws Exception {
        JsonNode body = json("{\"schema_version\":\"1.0\",\"doc_type\":\"RECEIPT\",\"fields\":{"
                + "\"nominal_total\":{\"value\":150000,\"source\":\"manual\"},"
                + "\"penerbit\":{\"value\":null,\"source\":\"manual\"}}}");
        assertTrue(DocumentVerifier.manualViolations(body).isEmpty());
    }

    @Test
    void sumberSelainManualDitolak() throws Exception {
        JsonNode body = json("{\"schema_version\":\"1.0\",\"doc_type\":\"RECEIPT\",\"fields\":{"
                + "\"nominal_total\":{\"value\":150000,\"source\":\"rule\"}}}");
        assertTrue(DocumentVerifier.manualViolations(body).stream()
                .anyMatch(v -> v.field().equals("nominal_total")));
    }

    @Test
    void jenisDokumenHarusDidukung() throws Exception {
        JsonNode body = json("{\"schema_version\":\"1.0\",\"doc_type\":\"UNKNOWN\",\"fields\":{"
                + "\"nominal_total\":{\"value\":1,\"source\":\"manual\"}}}");
        assertTrue(DocumentVerifier.manualViolations(body).stream()
                .anyMatch(v -> v.field().equals("doc_type")));
    }

    @Test
    void tanpaSatuPunNilaiDitolak() throws Exception {
        JsonNode body = json("{\"schema_version\":\"1.0\",\"doc_type\":\"RECEIPT\",\"fields\":{"
                + "\"nominal_total\":{\"value\":null,\"source\":\"manual\"}}}");
        assertTrue(DocumentVerifier.manualViolations(body).stream()
                .anyMatch(v -> v.field().equals("fields")));
    }

    @Test
    void normalisasiNamaTidakMembedakanKapitalDanSpasi() {
        assertEquals(ProfileService.normalizeName("Budi  Santoso "), ProfileService.normalizeName("budi santoso"));
    }
}

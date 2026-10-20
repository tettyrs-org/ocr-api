package org.tettyrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationPayloadValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String s) throws Exception {
        return MAPPER.readTree(s);
    }

    private static List<VerificationPayloadValidator.Violation> check(String body, String docType) throws Exception {
        return VerificationPayloadValidator.validate(json(body), docType);
    }

    @Test
    void bodyValidDenganFieldDanKelompok() throws Exception {
        String body = """
                {"schema_version":"1.0","doc_type":"RECEIPT","fields":{
                  "nomor":{"value":"INV-1","source":"rule"},
                  "pegawai":{"nip":{"value":"198503122010011004","source":"manual"}},
                  "total":{"value":1500000,"source":"llm"}
                }}""";
        assertTrue(check(body, "RECEIPT").isEmpty());
    }

    @Test
    void schemaVersionDanDocTypeWajibSesuai() throws Exception {
        List<VerificationPayloadValidator.Violation> v = check(
                "{\"schema_version\":\"0.9\",\"doc_type\":\"SPD\",\"fields\":{\"a\":{\"value\":\"x\",\"source\":\"manual\"}}}",
                "RECEIPT");
        assertEquals(2, v.size());
        assertEquals("schema_version", v.get(0).field());
        assertEquals("doc_type", v.get(1).field());
    }

    @Test
    void docTypeHilangDitolak() throws Exception {
        List<VerificationPayloadValidator.Violation> v = check(
                "{\"schema_version\":\"1.0\",\"fields\":{\"a\":{\"value\":\"x\",\"source\":\"manual\"}}}", "RECEIPT");
        assertEquals("doc_type", v.get(0).field());
    }

    @Test
    void fieldsKosongDitolak() throws Exception {
        List<VerificationPayloadValidator.Violation> v = check(
                "{\"schema_version\":\"1.0\",\"doc_type\":\"RECEIPT\",\"fields\":{}}", "RECEIPT");
        assertEquals("fields", v.get(0).field());
    }

    @Test
    void sourceTidakDikenalDitolak() throws Exception {
        List<VerificationPayloadValidator.Violation> v = check(
                "{\"schema_version\":\"1.0\",\"doc_type\":\"RECEIPT\",\"fields\":{\"a\":{\"value\":\"x\",\"source\":\"tebakan\"}}}",
                "RECEIPT");
        assertEquals("a", v.get(0).field());
    }

    @Test
    void valueWajibAdaTetapiBolehNull() throws Exception {
        List<VerificationPayloadValidator.Violation> hilang = check(
                "{\"schema_version\":\"1.0\",\"doc_type\":\"RECEIPT\",\"fields\":{\"a\":{\"source\":\"manual\"}}}",
                "RECEIPT");
        assertEquals("a", hilang.get(0).field());

        List<VerificationPayloadValidator.Violation> kosong = check(
                "{\"schema_version\":\"1.0\",\"doc_type\":\"RECEIPT\",\"fields\":{\"a\":{\"value\":null,\"source\":\"manual\"}}}",
                "RECEIPT");
        assertTrue(kosong.isEmpty());
    }

    @Test
    void valueObjekDitolak() throws Exception {
        List<VerificationPayloadValidator.Violation> v = check(
                "{\"schema_version\":\"1.0\",\"doc_type\":\"RECEIPT\",\"fields\":{\"a\":{\"value\":{\"x\":1},\"source\":\"manual\"}}}",
                "RECEIPT");
        assertEquals("a", v.get(0).field());
    }

    @Test
    void kelompokKosongDanBukanObjekDitolak() throws Exception {
        List<VerificationPayloadValidator.Violation> v = check(
                "{\"schema_version\":\"1.0\",\"doc_type\":\"RECEIPT\",\"fields\":{\"grup\":{},\"teks\":\"x\"}}",
                "RECEIPT");
        assertEquals(2, v.size());
    }

    @Test
    void arrayPegawaiDanPengikutDiterima() throws Exception {
        String body = """
                {"schema_version":"1.0","doc_type":"SPD","fields":{
                  "pegawai":[
                    {"nama":{"value":"Budi","source":"rule"},"nip":{"value":"198503122010011004","source":"manual"}},
                    {"nama":{"value":"Siti","source":"rule"},"nip":{"value":"199007152015022003","source":"rule"}}
                  ],
                  "pengikut":[]
                }}""";
        assertTrue(check(body, "SPD").isEmpty());
    }

    @Test
    void elemenArrayRusakDilaporkanDenganIndeks() throws Exception {
        List<VerificationPayloadValidator.Violation> v = check(
                "{\"schema_version\":\"1.0\",\"doc_type\":\"SPD\",\"fields\":{\"pegawai\":["
                        + "{\"nama\":{\"value\":\"Budi\",\"source\":\"rule\"}},"
                        + "{\"nama\":{\"value\":\"Siti\",\"source\":\"tebakan\"}}]}}",
                "SPD");
        assertEquals("pegawai[1].nama", v.get(0).field());
    }

    @Test
    void bodyBukanObjekDitolak() throws Exception {
        assertEquals("", check("[]", "RECEIPT").get(0).field());
    }

    @Test
    void terlaluBanyakFieldDitolak() throws Exception {
        StringBuilder sb = new StringBuilder("{\"schema_version\":\"1.0\",\"doc_type\":\"RECEIPT\",\"fields\":{");
        for (int i = 0; i <= VerificationPayloadValidator.MAX_FIELDS; i++) {
            if (i > 0) sb.append(',');
            sb.append("\"f").append(i).append("\":{\"value\":\"x\",\"source\":\"manual\"}");
        }
        sb.append("}}");
        assertEquals("fields", check(sb.toString(), "RECEIPT").get(0).field());
    }
}

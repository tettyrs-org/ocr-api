package org.tettyrs.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validasi body `PUT /api/v1/documents/{id}/verification` (api-spec bagian 5).
 *
 * Bentuk field: daun berisi `value` dan `source` (rule | llm | manual); simpul lain adalah kelompok
 * (mis. `pegawai.nip`). Fungsi ini murni sehingga bisa diuji tanpa database.
 */
public final class VerificationPayloadValidator {

    static final String SCHEMA_VERSION = "1.0";
    static final Set<String> SOURCES = Set.of("rule", "llm", "manual");
    static final int MAX_FIELDS = 200;
    static final int MAX_DEPTH = 4;

    private VerificationPayloadValidator() {
    }

    /** Daftar pelanggaran. Kosong berarti body valid. */
    public static List<Violation> validate(JsonNode body, String documentType) {
        List<Violation> out = new ArrayList<>();
        if (body == null || !body.isObject()) {
            out.add(new Violation("", "Body harus berupa objek JSON."));
            return out;
        }
        if (!SCHEMA_VERSION.equals(text(body.get("schema_version")))) {
            out.add(new Violation("schema_version", "schema_version harus \"" + SCHEMA_VERSION + "\"."));
        }
        String docType = text(body.get("doc_type"));
        if (docType == null) {
            out.add(new Violation("doc_type", "doc_type wajib diisi."));
        } else if (documentType != null && !docType.equals(documentType)) {
            out.add(new Violation("doc_type", "doc_type tidak sesuai dengan dokumen."));
        }

        JsonNode fields = body.get("fields");
        if (fields == null || !fields.isObject() || fields.isEmpty()) {
            out.add(new Violation("fields", "fields wajib diisi dan tidak boleh kosong."));
            return out;
        }
        if (countLeaves(fields) > MAX_FIELDS) {
            out.add(new Violation("fields", "Jumlah field melebihi " + MAX_FIELDS + "."));
            return out;
        }
        checkGroup(fields, "", 1, out);
        return out;
    }

    private static void checkGroup(JsonNode group, String path, int depth, List<Violation> out) {
        if (depth > MAX_DEPTH) {
            out.add(new Violation(path, "Kedalaman field melebihi " + MAX_DEPTH + "."));
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> it = group.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String child = path.isEmpty() ? e.getKey() : path + "." + e.getKey();
            JsonNode node = e.getValue();
            if (node.isArray()) {
                // Array (mis. pegawai, pengikut): setiap elemen adalah kelompok. Array kosong diizinkan.
                for (int i = 0; i < node.size(); i++) {
                    checkElement(node.get(i), child + "[" + i + "]", depth + 1, out);
                }
            } else {
                checkElement(node, child, depth, out);
            }
        }
    }

    private static void checkElement(JsonNode node, String path, int depth, List<Violation> out) {
        if (!node.isObject()) {
            out.add(new Violation(path, "Field harus berupa objek."));
        } else if (node.has("source")) {
            checkLeaf(node, path, out);
        } else if (node.isEmpty()) {
            out.add(new Violation(path, "Kelompok field tidak boleh kosong."));
        } else {
            checkGroup(node, path, depth + 1, out);
        }
    }

    private static void checkLeaf(JsonNode leaf, String path, List<Violation> out) {
        String source = text(leaf.get("source"));
        if (source == null || !SOURCES.contains(source)) {
            out.add(new Violation(path, "source harus salah satu dari rule, llm, atau manual."));
        }
        JsonNode value = leaf.get("value");
        if (value == null) {
            out.add(new Violation(path, "value wajib ada (boleh null bila kosong)."));
        } else if (!(value.isTextual() || value.isNumber() || value.isNull() || value.isBoolean())) {
            out.add(new Violation(path, "value harus berupa teks, angka, boolean, atau null."));
        }
    }

    private static int countLeaves(JsonNode node) {
        if (node.isArray()) {
            int n = 0;
            for (JsonNode el : node) n += countLeaves(el);
            return n;
        }
        if (!node.isObject()) return 0;
        if (node.has("source")) return 1;
        int n = 0;
        Iterator<JsonNode> it = node.elements();
        while (it.hasNext()) n += countLeaves(it.next());
        return n;
    }

    private static String text(JsonNode n) {
        return n != null && n.isTextual() ? n.asText() : null;
    }

    public record Violation(String field, String message) {
    }
}

package org.tettyrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pemeriksaan mutu gambar saat unggah (ocr-engine `POST /quality`). Tolak hanya bila gambar jelas tidak
 * terbaca. Bila engine tidak bisa dihubungi, unggahan tetap diterima dengan peringatan, supaya layanan
 * pemeriksaan tidak menghentikan pengguna.
 */
@ApplicationScoped
public class QualityGate {

    public record Issue(String code, String level, String message) {
    }

    public record Decision(boolean blocked, List<Issue> issues) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ConfigProperty(name = "tettyrs.ocr-engine.url", defaultValue = "http://ocr-engine:8000")
    String engineUrl;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public Decision check(byte[] bytes, String mimeType) {
        try {
            String boundary = "----quality" + UUID.randomUUID();
            ByteArrayOutputStream body = new ByteArrayOutputStream(bytes.length + 256);
            body.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"berkas\"\r\n"
                    + "Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(bytes);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpRequest req = HttpRequest.newBuilder(URI.create(engineUrl + "/quality"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                return unavailable();
            }
            return decide(MAPPER.readTree(res.body()));
        } catch (IOException e) {
            return unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unavailable();
        }
    }

    /** Memetakan respons engine ke keputusan. Diuji terpisah, tanpa jaringan. */
    static Decision decide(JsonNode root) {
        List<Issue> issues = new ArrayList<>();
        for (JsonNode n : root.path("issues")) {
            issues.add(new Issue(n.path("code").asText(), n.path("level").asText(), n.path("message").asText()));
        }
        return new Decision(root.path("blocked").asBoolean(false), issues);
    }

    static Decision unavailable() {
        return new Decision(false, List.of(new Issue("pemeriksaan_tidak_tersedia", "peringatan",
                "Pemeriksaan mutu gambar belum tersedia. Hasil pembacaan mungkin kurang akurat.")));
    }
}

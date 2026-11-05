package org.tettyrs.api;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.tettyrs.dto.ApiResponse;
import org.tettyrs.dto.enums.ErrorCode;
import org.tettyrs.security.CurrentUser;
import org.tettyrs.service.RateLimitService;

import java.io.IOException;
import java.util.Map;

@Provider
public class RateLimitingFilter implements ContainerRequestFilter {

    @Inject
    RateLimitService rateLimitService;

    @Inject
    CurrentUser currentUser;

    private static final Map<String, Integer> ENDPOINT_LIMITS = Map.ofEntries(
            // Berlaku per metode. Sebelumnya GET daftar dokumen ikut menghabiskan kuota unggah (20 per jam).
            Map.entry("POST /api/v1/documents", 20),
            Map.entry("POST /api/v1/documents/{id}/reprocess", 5),
            Map.entry("/api/telemetry/timings", 30),
            Map.entry("PUT /api/v1/documents/{id}/verification", 50)
    );

    // GET dipakai polling status (tiap ~2 detik) dan daftar dokumen. 300 per jam habis dalam 10 menit.
    private static final Map<String, Integer> METHOD_LIMITS = Map.ofEntries(
            Map.entry("GET", 3600)
    );

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        String method = requestContext.getMethod();

        if (isPublicEndpoint(path)) {
            return;
        }

        // Endpoint undangan tidak punya identitas pengguna. Dibatasi per IP, dan ada batas total untuk seluruh
        // prefix, supaya pemalsuan header IP tidak bisa mengalahkan pembatasan.
        if ((path.startsWith("/") ? path : "/" + path).startsWith("/api/v1/invitations/")) {
            batasiUndangan(requestContext);
            return;
        }

        if (!currentUser.isAuthenticated()) {
            return;
        }
        String userId = currentUser.userId();

        String endpoint = normalizeEndpoint(path);
        String key = method + " " + endpoint;
        Integer limit = ENDPOINT_LIMITS.containsKey(key) ? ENDPOINT_LIMITS.get(key) : ENDPOINT_LIMITS.get(endpoint);
        // Endpoint dengan batas khusus (unggah, proses ulang, tinjau) ditolak bila Redis gagal; GET umum tetap diizinkan.
        boolean limited = limit != null;
        if (limit == null) {
            limit = METHOD_LIMITS.getOrDefault(method, 300);
        }

        RateLimitService.RateLimitResult result = rateLimitService.checkLimit(
                userId,
                key,
                limit,
                limited
        );

        if (!result.allowed()) {
            Log.warn("Rate limit exceeded for user: " + userId +
                     ", endpoint: " + normalizeEndpoint(path));

            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.RATE_LIMITED.code,
                    "Rate limit exceeded. Try again in " + result.retryAfter().get() + " seconds"
            );

            requestContext.abortWith(
                    Response.status(ErrorCode.RATE_LIMITED.httpStatus)
                            .header("Retry-After", result.retryAfter().get())
                            .entity(response)
                            .build()
            );
        }
    }

    private static final int UNDANGAN_PER_IP_PER_JAM = 30;
    private static final int UNDANGAN_TOTAL_PER_JAM = 600;
    private static final String ENDPOINT_UNDANGAN = "PUBLIC /api/v1/invitations";

    private void batasiUndangan(ContainerRequestContext requestContext) {
        RateLimitService.RateLimitResult perIp = rateLimitService.checkLimit(
                "ip:" + alamatKlien(requestContext), ENDPOINT_UNDANGAN, UNDANGAN_PER_IP_PER_JAM, true);
        RateLimitService.RateLimitResult total = perIp.allowed()
                ? rateLimitService.checkLimit("ip:semua", ENDPOINT_UNDANGAN, UNDANGAN_TOTAL_PER_JAM, true)
                : perIp;
        if (!total.allowed()) {
            Log.warn("Rate limit undangan terlampaui");
            ApiResponse<Void> response = ApiResponse.error(
                    ErrorCode.RATE_LIMITED.code,
                    "Rate limit exceeded. Try again in " + total.retryAfter().get() + " seconds");
            requestContext.abortWith(
                    Response.status(ErrorCode.RATE_LIMITED.httpStatus)
                            .header("Retry-After", total.retryAfter().get())
                            .entity(response)
                            .build());
        }
    }

    /**
     * Alamat klien untuk pembatasan undangan. Header dari Cloudflare dipakai lebih dulu, lalu X-Forwarded-For
     * (nilai terakhir, yang ditambahkan proxy tepercaya). Bila keduanya tidak ada, semua dianggap satu klien.
     */
    private String alamatKlien(ContainerRequestContext requestContext) {
        String cf = requestContext.getHeaderString("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) {
            return cf.trim();
        }
        String xff = requestContext.getHeaderString("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] bagian = xff.split(",");
            return bagian[bagian.length - 1].trim();
        }
        return "unknown";
    }

    private String normalizeEndpoint(String path) {
        // Kunci harus selalu berawalan "/" agar cocok dengan ENDPOINT_LIMITS, apa pun bentuk getPath() dari framework.
        String normalized = path.startsWith("/") ? path : "/" + path;
        return normalized
                .replaceAll("/\\d+(/|$)", "/{id}$1")
                .replaceAll("/[a-f0-9-]{36}(/|$)", "/{id}$1")
                .replaceAll("\\?.*$", "");
    }

    private boolean isPublicEndpoint(String path) {
        return path.contains("/health") ||
               path.contains("/metrics");
    }
}

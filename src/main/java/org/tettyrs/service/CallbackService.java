package org.tettyrs.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.tettyrs.entities.ExtractionCallback;
import org.tettyrs.entities.enums.CallbackEventType;
import org.tettyrs.entities.enums.CallbackStatus;
import io.quarkus.logging.Log;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CallbackService {

    private static final java.util.regex.Pattern IPV4 =
            java.util.regex.Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");

    /**
     * URL webhook yang aman dari SSRF: harus HTTPS, tanpa kredensial di URL, dan host bukan localhost atau alamat
     * privat/loopback/link-local (termasuk metadata cloud 169.254.x.x). Pemeriksaan literal host saja; DNS tidak
     * di-resolve di sini.
     */
    public static boolean urlWebhookAman(String url) {
        if (url == null || url.isBlank()) return false;
        java.net.URI uri;
        try {
            uri = new java.net.URI(url.trim());
        } catch (java.net.URISyntaxException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) return false;
        String host = uri.getHost();
        if (host == null || host.isBlank()) return false;
        String h = host.toLowerCase(java.util.Locale.ROOT);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1); // localhost. sama dengan localhost
        if (h.isEmpty()) return false;
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")) return false;
        if (h.startsWith("[") || h.contains(":")) return false; // alamat IPv6 literal ditolak
        if (IPV4.matcher(h).matches()) {
            String[] o = h.split("\\.");
            int a = Integer.parseInt(o[0]);
            int b = Integer.parseInt(o[1]);
            if (a == 10 || a == 127 || a == 0 || a >= 224) return false;
            if (a == 172 && b >= 16 && b <= 31) return false;
            if (a == 192 && b == 168) return false;
            if (a == 169 && b == 254) return false;
            if (a == 100 && b >= 64 && b <= 127) return false; // CGNAT
        } else {
            // TLD sungguhan selalu huruf. Angka saja (2130706433, 127.1) atau 0x... adalah cara lain menulis IPv4: ditolak.
            String label = h.substring(h.lastIndexOf('.') + 1);
            if (label.matches("[0-9]+") || label.startsWith("0x")) return false;
        }
        return true;
    }

    /**
     * Create webhook callback untuk document
     */
    @Transactional
    public ExtractionCallback createCallback(
            UUID documentId,
            String webhookUrl,
            CallbackEventType eventType,
            String payload) {

        try {
            // Hanya URL HTTPS publik yang diterima. Lihat urlWebhookAman.
            if (!urlWebhookAman(webhookUrl)) {
                throw new IllegalArgumentException("Invalid webhook URL");
            }

            ExtractionCallback callback = new ExtractionCallback();
            callback.documentId = documentId;
            callback.webhookUrl = webhookUrl;
            callback.eventType = eventType;
            callback.payload = payload;
            callback.callbackStatus = CallbackStatus.PENDING;
            callback.retryCount = 0;
            callback.maxRetries = 3;

            callback.persist();
            // Hanya host yang dicatat: query string URL pelanggan bisa memuat token.
            Log.info("Callback created for document " + documentId +
                    " - event: " + eventType + ", host: " + java.net.URI.create(webhookUrl.trim()).getHost());

            return callback;
        } catch (Exception e) {
            Log.error("Failed to create callback", e);
            throw new RuntimeException("Failed to create callback: " + e.getMessage());
        }
    }

    /**
     * Get pending callbacks untuk retry
     */
    public List<ExtractionCallback> getPendingCallbacks() {
        return ExtractionCallback.find(
                "callbackStatus IN ('PENDING', 'RETRYING') ORDER BY createdAt ASC"
        ).list();
    }

    /**
     * Get callbacks untuk document dengan optional status filter
     */
    public List<ExtractionCallback> getCallbacksForDocument(
            UUID documentId,
            CallbackStatus statusFilter) {

        if (statusFilter != null) {
            return ExtractionCallback.find(
                    "documentId = ?1 AND callbackStatus = ?2 ORDER BY createdAt DESC",
                    documentId, statusFilter
            ).list();
        } else {
            return ExtractionCallback.find(
                    "documentId = ?1 ORDER BY createdAt DESC",
                    documentId
            ).list();
        }
    }

    /**
     * Mark callback sebagai sent (success)
     */
    @Transactional
    public void markAsSent(
            Long callbackId,
            Integer responseCode) {

        try {
            ExtractionCallback callback = ExtractionCallback.findById(callbackId);
            if (callback != null) {
                callback.callbackStatus = CallbackStatus.SENT;
                callback.lastResponseCode = responseCode;
                callback.lastRetryAt = LocalDateTime.now();

                Log.info("Callback " + callbackId + " marked as SENT with code " + responseCode);
            }
        } catch (Exception e) {
            Log.warn("Failed to mark callback as sent", e);
        }
    }

    /**
     * Mark callback sebagai failed (max retries reached)
     */
    @Transactional
    public void markAsFailed(
            Long callbackId,
            String errorMessage) {

        try {
            ExtractionCallback callback = ExtractionCallback.findById(callbackId);
            if (callback != null) {
                callback.callbackStatus = CallbackStatus.FAILED;
                callback.lastErrorMessage = errorMessage;
                callback.lastRetryAt = LocalDateTime.now();

                Log.warn("Callback " + callbackId + " marked as FAILED - " + errorMessage);
            }
        } catch (Exception e) {
            Log.warn("Failed to mark callback as failed", e);
        }
    }

    /**
     * Increment retry count
     */
    @Transactional
    public void incrementRetryCount(Long callbackId) {
        try {
            ExtractionCallback callback = ExtractionCallback.findById(callbackId);
            if (callback != null) {
                callback.retryCount++;

                // Check if max retries reached
                if (callback.retryCount >= callback.maxRetries) {
                    callback.callbackStatus = CallbackStatus.FAILED;
                    Log.warn("Callback " + callbackId + " max retries reached");
                } else {
                    callback.callbackStatus = CallbackStatus.RETRYING;
                    callback.lastRetryAt = LocalDateTime.now();
                }
            }
        } catch (Exception e) {
            Log.warn("Failed to increment retry count", e);
        }
    }

    /**
     * Manual retry - reset retry count & status
     */
    @Transactional
    public ExtractionCallback manualRetry(Long callbackId) {
        try {
            ExtractionCallback callback = ExtractionCallback.findById(callbackId);
            if (callback == null) {
                throw new IllegalArgumentException("Callback not found: " + callbackId);
            }

            callback.retryCount = 0;
            callback.callbackStatus = CallbackStatus.RETRYING;
            callback.lastRetryAt = LocalDateTime.now();
            callback.lastErrorMessage = null;

            Log.info("Manual retry triggered for callback " + callbackId);

            return callback;
        } catch (Exception e) {
            Log.error("Failed to manual retry callback", e);
            throw new RuntimeException("Failed to retry callback: " + e.getMessage());
        }
    }

    /**
     * Get callback delivery statistics
     */
    public CallbackStats getCallbackStats(UUID documentId) {
        List<ExtractionCallback> callbacks = ExtractionCallback.find(
                "documentId = ?1",
                documentId
        ).list();

        CallbackStats stats = new CallbackStats();
        stats.totalCallbacks = callbacks.size();
        stats.sentCallbacks = (int) callbacks.stream()
                .filter(c -> c.callbackStatus == CallbackStatus.SENT)
                .count();
        stats.failedCallbacks = (int) callbacks.stream()
                .filter(c -> c.callbackStatus == CallbackStatus.FAILED)
                .count();
        stats.pendingCallbacks = (int) callbacks.stream()
                .filter(c -> c.callbackStatus == CallbackStatus.PENDING ||
                        c.callbackStatus == CallbackStatus.RETRYING)
                .count();

        return stats;
    }

    /**
     * Helper class untuk stats
     */
    public static class CallbackStats {
        public int totalCallbacks;
        public int sentCallbacks;
        public int failedCallbacks;
        public int pendingCallbacks;

        public int getSuccessPercentage() {
            if (totalCallbacks == 0) return 0;
            return (int) ((sentCallbacks * 100.0) / totalCallbacks);
        }
    }
}

package org.tettyrs.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.tettyrs.entities.ExtractionCallback;
import org.tettyrs.entities.enums.CallbackEventType;
import org.tettyrs.entities.enums.CallbackStatus;
import io.quarkus.logging.Log;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class CallbackService {

    /**
     * Create webhook callback untuk document
     */
    @Transactional
    public ExtractionCallback createCallback(
            Long documentId,
            String webhookUrl,
            CallbackEventType eventType,
            String payload) {

        try {
            // Validate webhook URL format
            if (webhookUrl == null || !webhookUrl.startsWith("http")) {
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
            Log.info("Callback created for document " + documentId +
                    " - event: " + eventType + ", URL: " + webhookUrl);

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
            Long documentId,
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
    public CallbackStats getCallbackStats(Long documentId) {
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

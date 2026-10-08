package org.tettyrs.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.tettyrs.entities.TelemetryTiming;
import org.tettyrs.entities.enums.TelemetryStatus;
import io.quarkus.logging.Log;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TelemetryService {

    @Transactional
    public LocalDateTime startTiming(UUID documentId, String component) {
        try {
            TelemetryTiming timing = new TelemetryTiming();
            timing.documentId = documentId;
            timing.component = component;
            timing.startTime = LocalDateTime.now();
            timing.status = TelemetryStatus.SUCCESS;

            timing.persist();
            Log.info("Timing started for document " + documentId + " - component: " + component);

            return timing.startTime;
        } catch (Exception e) {
            Log.error("Failed to start timing", e);
            throw new RuntimeException("Failed to start timing: " + e.getMessage());
        }
    }

    @Transactional
    public LocalDateTime endTiming(
            UUID documentId,
            String component,
            TelemetryStatus status) {

        try {
            TelemetryTiming timing = TelemetryTiming.find(
                    "documentId = ?1 AND component = ?2 ORDER BY startTime DESC",
                    documentId, component
            ).firstResult();

            if (timing == null) {
                throw new IllegalArgumentException("Timing not found for component: " + component);
            }

            timing.endTime = LocalDateTime.now();
            timing.status = status;

            // Calculate duration in milliseconds
            if (timing.startTime != null && timing.endTime != null) {
                timing.durationMs = java.time.temporal.ChronoUnit.MILLIS.between(
                        timing.startTime,
                        timing.endTime
                );
            }

            Log.info("Timing ended for document " + documentId +
                    " - component: " + component +
                    " - duration: " + timing.durationMs + "ms");

            return timing.endTime;
        } catch (Exception e) {
            Log.error("Failed to end timing", e);
            throw new RuntimeException("Failed to end timing: " + e.getMessage());
        }
    }

    public List<TelemetryTiming> getTimingsForDocument(UUID documentId) {
        return TelemetryTiming.find(
                "documentId = ?1 ORDER BY createdAt DESC",
                documentId
        ).list();
    }

    public List<TelemetryTiming> getTimingsForComponent(UUID documentId, String component) {
        return TelemetryTiming.find(
                "documentId = ?1 AND component = ?2 ORDER BY createdAt DESC",
                documentId, component
        ).list();
    }

    public Double getAverageDurationForComponent(String component) {
        List<TelemetryTiming> timings = TelemetryTiming.find(
                "component = ?1 AND durationMs > 0",
                component
        ).list();

        if (timings.isEmpty()) {
            return 0.0;
        }

        return timings.stream()
                .mapToLong(t -> t.durationMs != null ? t.durationMs : 0)
                .average()
                .orElse(0.0);
    }

    public ProcessingStats getProcessingStats(UUID documentId) {
        List<TelemetryTiming> timings = getTimingsForDocument(documentId);

        ProcessingStats stats = new ProcessingStats();
        stats.totalTimings = timings.size();
        stats.successCount = (int) timings.stream()
                .filter(t -> t.status == TelemetryStatus.SUCCESS)
                .count();
        stats.failedCount = (int) timings.stream()
                .filter(t -> t.status == TelemetryStatus.FAILED)
                .count();
        stats.timeoutCount = (int) timings.stream()
                .filter(t -> t.status == TelemetryStatus.TIMEOUT)
                .count();

        if (stats.totalTimings > 0) {
            stats.totalDurationMs = timings.stream()
                    .mapToLong(t -> t.durationMs != null ? t.durationMs : 0)
                    .sum();
        }

        return stats;
    }

    @Transactional
    public void recordError(
            UUID documentId,
            String component,
            String errorMessage) {

        try {
            TelemetryTiming timing = TelemetryTiming.find(
                    "documentId = ?1 AND component = ?2 ORDER BY startTime DESC",
                    documentId, component
            ).firstResult();

            if (timing != null) {
                timing.errorMessage = errorMessage;
                Log.debug("Error recorded for " + component + ": " + errorMessage);
            }
        } catch (Exception e) {
            Log.warn("Failed to record error", e);
        }
    }

    public static class ProcessingStats {
        public int totalTimings;
        public int successCount;
        public int failedCount;
        public int timeoutCount;
        public long totalDurationMs;

        public double successPercentage() {
            if (totalTimings == 0) return 0.0;
            return (successCount * 100.0) / totalTimings;
        }

        public double failurePercentage() {
            if (totalTimings == 0) return 0.0;
            return ((failedCount + timeoutCount) * 100.0) / totalTimings;
        }

        public double averageDurationMs() {
            if (totalTimings == 0) return 0.0;
            return totalDurationMs / (double) totalTimings;
        }
    }
}

package org.tettyrs.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.TelemetryTiming;
import org.tettyrs.entities.enums.DocumentType;
import org.tettyrs.entities.enums.ProcessingStatus;
import org.tettyrs.entities.enums.TelemetryStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@DisplayName("TelemetryService Tests")
@Transactional
class TelemetryServiceTest {

    @Inject
    TelemetryService telemetryService;

    private Document testDoc;
    private UUID testDocumentId;

    @BeforeEach
    void setup() {
        testDoc = new Document();
        testDoc.filename = "test_telemetry.pdf";
        testDoc.documentType = DocumentType.PASSPORT;
        testDoc.mimeType = "application/pdf";
        testDoc.fileSize = 1024L;
        testDoc.createdBy = "test@example.com";
        testDoc.processingStatus = ProcessingStatus.PROCESSING;
        testDoc.persist();

        testDocumentId = testDoc.id;
        assertNotNull(testDocumentId);
    }

    @Test
    @DisplayName("Should start timing successfully")
    void testStartTiming() {
        LocalDateTime startTime = telemetryService.startTiming(testDocumentId, "ocr_extraction");

        assertNotNull(startTime);
        assertTrue(startTime.isBefore(LocalDateTime.now()) || startTime.isEqual(LocalDateTime.now()));
    }

    @Test
    @DisplayName("Should end timing and calculate duration")
    void testEndTiming() throws InterruptedException {
        telemetryService.startTiming(testDocumentId, "test_component");
        Thread.sleep(100);

        LocalDateTime endTime = telemetryService.endTiming(
                testDocumentId,
                "test_component",
                TelemetryStatus.SUCCESS
        );

        assertNotNull(endTime);

        List<TelemetryTiming> timings = telemetryService.getTimingsForDocument(testDocumentId);
        assertTrue(timings.size() > 0);

        TelemetryTiming timing = timings.get(0);
        assertTrue(timing.durationMs >= 100, "Duration should be at least 100ms");
    }

    @Test
    @DisplayName("Should get timings for document")
    void testGetTimingsForDocument() {
        telemetryService.startTiming(testDocumentId, "extraction");
        telemetryService.endTiming(testDocumentId, "extraction", TelemetryStatus.SUCCESS);

        telemetryService.startTiming(testDocumentId, "classification");
        telemetryService.endTiming(testDocumentId, "classification", TelemetryStatus.SUCCESS);

        List<TelemetryTiming> timings = telemetryService.getTimingsForDocument(testDocumentId);

        assertEquals(2, timings.size());
        assertTrue(timings.stream().anyMatch(t -> "extraction".equals(t.component)));
        assertTrue(timings.stream().anyMatch(t -> "classification".equals(t.component)));
    }

    @Test
    @DisplayName("Should get timings for specific component")
    void testGetTimingsForComponent() {
        telemetryService.startTiming(testDocumentId, "ocr");
        telemetryService.endTiming(testDocumentId, "ocr", TelemetryStatus.SUCCESS);

        telemetryService.startTiming(testDocumentId, "validation");
        telemetryService.endTiming(testDocumentId, "validation", TelemetryStatus.SUCCESS);

        List<TelemetryTiming> ocrTimings = telemetryService.getTimingsForComponent(testDocumentId, "ocr");

        assertEquals(1, ocrTimings.size());
        assertEquals("ocr", ocrTimings.get(0).component);
    }

    @Test
    @DisplayName("Should track failed processing")
    void testTrackFailedProcessing() {
        telemetryService.startTiming(testDocumentId, "failing_component");
        telemetryService.endTiming(
                testDocumentId,
                "failing_component",
                TelemetryStatus.FAILED
        );

        List<TelemetryTiming> timings = telemetryService.getTimingsForDocument(testDocumentId);
        assertEquals(1, timings.size());
        assertEquals(TelemetryStatus.FAILED, timings.get(0).status);
    }

    @Test
    @DisplayName("Should track timeout processing")
    void testTrackTimeoutProcessing() {
        telemetryService.startTiming(testDocumentId, "slow_component");
        telemetryService.endTiming(
                testDocumentId,
                "slow_component",
                TelemetryStatus.TIMEOUT
        );

        List<TelemetryTiming> timings = telemetryService.getTimingsForDocument(testDocumentId);
        assertEquals(1, timings.size());
        assertEquals(TelemetryStatus.TIMEOUT, timings.get(0).status);
    }

    @Test
    @DisplayName("Should record error message")
    void testRecordError() {
        String errorMessage = "Processing failed due to timeout";

        telemetryService.startTiming(testDocumentId, "test_component");
        telemetryService.recordError(testDocumentId, "test_component", errorMessage);
        telemetryService.endTiming(testDocumentId, "test_component", TelemetryStatus.FAILED);

        List<TelemetryTiming> timings = telemetryService.getTimingsForDocument(testDocumentId);
        assertNotNull(timings.get(0).errorMessage);
        assertEquals(errorMessage, timings.get(0).errorMessage);
    }

    @Test
    @DisplayName("Should get processing statistics")
    void testGetProcessingStats() {
        telemetryService.startTiming(testDocumentId, "ocr");
        telemetryService.endTiming(testDocumentId, "ocr", TelemetryStatus.SUCCESS);

        telemetryService.startTiming(testDocumentId, "validation");
        telemetryService.endTiming(testDocumentId, "validation", TelemetryStatus.SUCCESS);

        telemetryService.startTiming(testDocumentId, "failing");
        telemetryService.endTiming(testDocumentId, "failing", TelemetryStatus.FAILED);

        TelemetryService.ProcessingStats stats = telemetryService.getProcessingStats(testDocumentId);

        assertNotNull(stats);
        assertEquals(3, stats.totalTimings);
        assertEquals(2, stats.successCount);
        assertEquals(1, stats.failedCount);
        assertEquals(66, (int) stats.successPercentage());
    }

    @Test
    @DisplayName("Should calculate average duration for component")
    void testGetAverageDurationForComponent() {
        telemetryService.startTiming(testDocumentId, "ocr");
        telemetryService.endTiming(testDocumentId, "ocr", TelemetryStatus.SUCCESS);

        Double average = telemetryService.getAverageDurationForComponent("ocr");

        assertNotNull(average);
        assertTrue(average >= 0);
    }

    @Test
    @DisplayName("Should handle non-existent timing end gracefully")
    void testEndNonExistentTiming() {
        assertThrows(
                RuntimeException.class,
                () -> telemetryService.endTiming(
                        testDocumentId,
                        "non_existent",
                        TelemetryStatus.SUCCESS
                )
        );
    }
}

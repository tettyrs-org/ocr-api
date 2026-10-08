package org.tettyrs.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.ExtractionCallback;
import org.tettyrs.entities.enums.CallbackEventType;
import org.tettyrs.entities.enums.CallbackStatus;
import org.tettyrs.entities.enums.DocumentType;
import org.tettyrs.entities.enums.ProcessingStatus;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@DisplayName("CallbackService Tests")
@Transactional
class CallbackServiceTest {

    @Inject
    CallbackService callbackService;

    private Document testDoc;
    private UUID testDocumentId;
    private String testWebhookUrl = "https://webhook.example.com/ocr/events";

    @BeforeEach
    void setup() {
        testDoc = new Document();
        testDoc.filename = "test_callback.pdf";
        testDoc.documentType = DocumentType.PASSPORT;
        testDoc.mimeType = "application/pdf";
        testDoc.fileSize = 1024L;
        testDoc.createdBy = "test@example.com";
        testDoc.processingStatus = ProcessingStatus.COMPLETED;
        testDoc.persist();

        testDocumentId = testDoc.id;
    }

    @Test
    @DisplayName("Should create callback successfully")
    void testCreateCallback() {
        ExtractionCallback callback = callbackService.createCallback(
                testDocumentId,
                testWebhookUrl,
                CallbackEventType.EXTRACTION_COMPLETE,
                "{\"status\": \"success\"}"
        );

        assertNotNull(callback);
        assertNotNull(callback.id);
        assertEquals(testDocumentId, callback.documentId);
        assertEquals(testWebhookUrl, callback.webhookUrl);
        assertEquals(CallbackEventType.EXTRACTION_COMPLETE, callback.eventType);
        assertEquals(CallbackStatus.PENDING, callback.callbackStatus);
    }

    @Test
    @DisplayName("Should reject invalid webhook URL")
    void testCreateCallbackInvalidUrl() {
        assertThrows(
                RuntimeException.class,
                () -> callbackService.createCallback(
                        testDocumentId,
                        "not-a-valid-url",
                        CallbackEventType.EXTRACTION_COMPLETE,
                        "{}"
                )
        );
    }

    @Test
    @DisplayName("Should get pending callbacks for retry")
    void testGetPendingCallbacks() {
        callbackService.createCallback(
                testDocumentId,
                testWebhookUrl,
                CallbackEventType.EXTRACTION_COMPLETE,
                "{}"
        );

        List<ExtractionCallback> pending = callbackService.getPendingCallbacks();
        assertTrue(pending.size() > 0);
        assertTrue(pending.stream().anyMatch(c -> c.documentId.equals(testDocumentId)));
    }

    @Test
    @DisplayName("Should get callbacks for document with status filter")
    void testGetCallbacksWithFilter() {
        ExtractionCallback callback = callbackService.createCallback(
                testDocumentId,
                testWebhookUrl,
                CallbackEventType.EXTRACTION_COMPLETE,
                "{}"
        );

        callbackService.markAsSent(callback.id, 200);

        List<ExtractionCallback> callbacks =
                callbackService.getCallbacksForDocument(testDocumentId, CallbackStatus.SENT);

        assertEquals(1, callbacks.size());
    }

    @Test
    @DisplayName("Should mark callback as sent")
    void testMarkAsSent() {
        ExtractionCallback callback = callbackService.createCallback(
                testDocumentId,
                testWebhookUrl,
                CallbackEventType.EXTRACTION_COMPLETE,
                "{}"
        );

        callbackService.markAsSent(callback.id, 200);

        ExtractionCallback updated = ExtractionCallback.findById(callback.id);
        assertEquals(CallbackStatus.SENT, updated.callbackStatus);
        assertEquals(200, updated.lastResponseCode);
    }

    @Test
    @DisplayName("Should mark callback as failed")
    void testMarkAsFailed() {
        ExtractionCallback callback = callbackService.createCallback(
                testDocumentId,
                testWebhookUrl,
                CallbackEventType.EXTRACTION_COMPLETE,
                "{}"
        );

        callbackService.markAsFailed(callback.id, "Connection timeout");

        ExtractionCallback updated = ExtractionCallback.findById(callback.id);
        assertEquals("Connection timeout", updated.lastErrorMessage);
    }

    @Test
    @DisplayName("Should increment retry count")
    void testIncrementRetryCount() {
        ExtractionCallback callback = callbackService.createCallback(
                testDocumentId,
                testWebhookUrl,
                CallbackEventType.EXTRACTION_COMPLETE,
                "{}"
        );

        callbackService.incrementRetryCount(callback.id);

        ExtractionCallback updated = ExtractionCallback.findById(callback.id);
        assertEquals(1, updated.retryCount);
    }

    @Test
    @DisplayName("Should manual retry callback")
    void testManualRetry() {
        ExtractionCallback callback = callbackService.createCallback(
                testDocumentId,
                testWebhookUrl,
                CallbackEventType.EXTRACTION_COMPLETE,
                "{}"
        );

        ExtractionCallback retried = callbackService.manualRetry(callback.id);

        assertNotNull(retried);
        assertEquals(CallbackStatus.RETRYING, retried.callbackStatus);
        assertEquals(0, retried.retryCount);
    }

    @Test
    @DisplayName("Should get callback statistics")
    void testGetCallbackStats() {
        callbackService.createCallback(
                testDocumentId,
                testWebhookUrl,
                CallbackEventType.EXTRACTION_COMPLETE,
                "{}"
        );

        CallbackService.CallbackStats stats = callbackService.getCallbackStats(testDocumentId);

        assertNotNull(stats);
        assertTrue(stats.getSuccessPercentage() >= 0);
    }
}

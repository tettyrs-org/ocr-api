package org.tettyrs.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.enums.DocumentType;
import org.tettyrs.entities.enums.ProcessingStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
public class ProcessingServiceTest {

    @Inject
    ProcessingService processingService;

    @Test
    @Transactional
    public void testProcessingStarted() {
        Document doc = new Document();
        doc.filename = "test.pdf";
        doc.documentType = DocumentType.PASSPORT;
        doc.mimeType = "application/pdf";
        doc.fileSize = 1024L;
        doc.createdBy = "test@test.com";
        doc.processingStatus = ProcessingStatus.PENDING;
        doc.persist();

        UUID docId = doc.id;
        assertNotNull(docId);

        processingService.startProcessing(docId);
    }
}

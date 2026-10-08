package org.tettyrs.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.persistence.EntityManager;
import org.tettyrs.dto.ClassificationResult;
import org.tettyrs.dto.OcrResult;
import org.tettyrs.entities.Document;
import org.tettyrs.entities.DocumentProcessingResult;
import org.tettyrs.entities.AuditEvent;
import org.tettyrs.entities.enums.ProcessingStatus;
import org.tettyrs.entities.enums.AuditAction;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class ProcessingService {

    @Inject
    OcrService ocrService;

    @Inject
    LlmService llmService;

    @Inject
    EntityManager em;

    public void startProcessing(Long documentId) {
        CompletableFuture.runAsync(() -> processDocument(documentId));
    }

    @Transactional
    protected void processDocument(Long documentId) {
        Document doc = (Document) Document.findById(documentId);
        if (doc == null) return;

        DocumentProcessingResult result = new DocumentProcessingResult();
        result.documentId = documentId;
        result.processingStatus = ProcessingStatus.PROCESSING;
        result.processingStartedAt = LocalDateTime.now();
        em.persist(result);

        doc.processingStatus = ProcessingStatus.PROCESSING;
        doc.persist();

        try {
            OcrResult ocrResult = ocrService.extractText(doc.filename, null);
            result.ocrText = ocrResult.text;
            result.ocrConfidence = ocrResult.confidence;

            ClassificationResult classResult = llmService.classify(ocrResult.text, doc.documentType.toString());

            result.classificationCategory = classResult.category;
            result.classificationConfidence = classResult.confidence;
            result.summary = classResult.summary;

            result.processingStatus = ProcessingStatus.COMPLETED;
            result.processingCompletedAt = LocalDateTime.now();
            em.merge(result);

            doc.processingStatus = ProcessingStatus.COMPLETED;
            doc.persist();

            AuditEvent audit = new AuditEvent();
            audit.document = doc;
            audit.action = AuditAction.PROCESSING_COMPLETED;
            audit.details = "Document processed successfully";
            audit.actor = "system";
            audit.persist();

        } catch (Exception e) {
            result.processingStatus = ProcessingStatus.FAILED;
            result.errorMessage = e.getMessage();
            result.processingCompletedAt = LocalDateTime.now();
            em.merge(result);

            doc.processingStatus = ProcessingStatus.FAILED;
            doc.persist();

            AuditEvent audit = new AuditEvent();
            audit.document = doc;
            audit.action = AuditAction.PROCESSING_FAILED;
            audit.details = "Error: " + e.getMessage();
            audit.actor = "system";
            audit.persist();
        }
    }
}

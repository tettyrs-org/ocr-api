package org.tettyrs.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.tettyrs.entities.enums.ProcessingStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ocr_document_processing_results")
public class DocumentProcessingResult extends PanacheEntity {
    @Column(name = "document_id")
    public UUID documentId;

    @Column(name = "ocr_text", columnDefinition = "TEXT")
    public String ocrText;

    @Column(name = "ocr_confidence")
    public Double ocrConfidence;

    @Column(name = "classification_category")
    public String classificationCategory;

    @Column(name = "classification_confidence")
    public Double classificationConfidence;

    @Column(name = "summary", columnDefinition = "TEXT")
    public String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status")
    public ProcessingStatus processingStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;

    @Column(name = "processing_started_at")
    public LocalDateTime processingStartedAt;

    @Column(name = "processing_completed_at")
    public LocalDateTime processingCompletedAt;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

}

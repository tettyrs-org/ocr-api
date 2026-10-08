package org.tettyrs.entities;


import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.tettyrs.entities.enums.CallbackEventType;
import org.tettyrs.entities.enums.CallbackStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ocr_extraction_callbacks", indexes = {
        @Index(name = "idx_callback_document_id", columnList = "document_id"),
        @Index(name = "idx_callback_status", columnList = "callback_status"),
        @Index(name = "idx_callback_event_type", columnList = "event_type"),
        @Index(name = "idx_callback_created_at", columnList = "created_at")
})
@NoArgsConstructor
public class ExtractionCallback extends PanacheEntity {

    @Column(name = "document_id", nullable = false)
    public UUID documentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", insertable = false, updatable = false)
    public Document document;

    @Column(name = "webhook_url", nullable = false, length = 2048)
    public String webhookUrl;

    @Column(name = "event_type", nullable = false, length = 100)
    @Enumerated(EnumType.STRING)
    public CallbackEventType eventType;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    public String payload;

    @Column(name = "callback_status", nullable = false)
    @Enumerated(EnumType.STRING)
    public CallbackStatus callbackStatus;

    @Column(name = "retry_count")
    public Integer retryCount = 0;

    @Column(name = "max_retries")
    public Integer maxRetries = 3;

    @Column(name = "last_retry_at")
    public LocalDateTime lastRetryAt;

    @Column(name = "last_response_code")
    public Integer lastResponseCode;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    public String lastErrorMessage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, name = "created_at")
    public LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, name = "updated_at")
    public LocalDateTime updatedAt;
}

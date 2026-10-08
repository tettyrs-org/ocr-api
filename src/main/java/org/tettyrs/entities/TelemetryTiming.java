package org.tettyrs.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.tettyrs.entities.enums.TelemetryStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "ocr_telemetry_timings", indexes = {
        @Index(name = "idx_telemetry_document_id", columnList = "document_id"),
        @Index(name = "idx_telemetry_component", columnList = "component"),
        @Index(name = "idx_telemetry_created_at", columnList = "created_at")
})
@NoArgsConstructor
public class TelemetryTiming extends PanacheEntity {

    @Column(name = "document_id", nullable = false)
    public Long documentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", insertable = false, updatable = false)
    public Document document;

    @Column(name = "component", nullable = false, length = 100)
    public String component;

    @Column(name = "start_time", nullable = false)
    public LocalDateTime startTime;
    @Column(name = "end_time")
    public LocalDateTime endTime;

    @Column(name = "duration_ms")
    public Long durationMs;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    public TelemetryStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, name = "created_at")
    public LocalDateTime createdAt;
}

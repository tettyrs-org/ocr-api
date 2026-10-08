package org.tettyrs.entities;


import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.tettyrs.entities.enums.AuditAction;

import java.time.LocalDateTime;


@Entity
@Table(name = "ocr_audit_events", indexes = {
        @Index(name = "idx_ocr_document_id", columnList = "document_id"),
        @Index(name = "idx_ocr_action", columnList = "action"),
        @Index(name = "idx_ocr_timestamp", columnList = "timestamp"),
        @Index(name = "idx_ocr_actor", columnList = "actor")
})

@NoArgsConstructor

public class AuditEvent extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    public Document document;

    @Column(nullable = false, name = "action")
    @Enumerated(EnumType.STRING)
    public AuditAction action;

    @Column(columnDefinition = "TEXT", name = "details")
    public String details; // json format

    @Column(nullable = false, name = "actor")
    public String actor; // userId

    @CreationTimestamp
    @Column(nullable = false, updatable = false, name = "timestamp")
    public LocalDateTime timestamp;

}
